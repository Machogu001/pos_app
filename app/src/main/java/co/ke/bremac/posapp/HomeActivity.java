package co.ke.bremac.posapp;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import co.ke.bremac.posapp.data.Location;
import co.ke.bremac.posapp.data.SaleSummary;
import co.ke.bremac.posapp.ui.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends BaseActivity {
    private String period = "today";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addNav();
        loadSessionThenRender();
    }

    private void loadSessionThenRender() {
        runAsync("Loading dashboard…", () -> session.api().me(), result -> {
            JSONObject data = result.optJSONObject("data");
            if (data != null) {
                session.applyMe(data);
            }
            render();
            loadDashboard();
        });
    }

    private void render() {
        content.removeAllViews();
        title(session.business.name);
        paragraph(session.user.fullName + " • " + LoginActivity.deviceName());
        addLocationSelector();
        addPeriodChips();
        addRegisterBanner();
        Button refresh = Ui.secondary(this, "Refresh dashboard");
        content.addView(refresh, Ui.params(this, -1, 48, 10));
        refresh.setOnClickListener(view -> loadDashboard());
    }

    private void loadDashboard() {
        runAsync("Loading dashboard…",
                () -> session.api().dashboard(session.locationId, period),
                result -> renderDashboard(result.optJSONObject("data")));
    }

    private void renderDashboard(JSONObject dashboard) {
        if (dashboard == null || !isAlive()) {
            return;
        }
        render();
        card("Total sales", session.money().format(dashboard.optDouble("total_sales")));
        card("Sales count", String.valueOf(dashboard.optInt("sales_count")));
        card("Paid", session.money().format(dashboard.optDouble("total_paid")));
        card("Due", session.money().format(dashboard.optDouble("total_due")));
        card("Expenses", session.money().format(dashboard.optDouble("total_expense")));
        card("Net", session.money().format(dashboard.optDouble("net")));
        section("Recent sales");
        JSONArray sales = dashboard.optJSONArray("recent_sales");
        if (sales == null || sales.length() == 0) {
            paragraph("No recent sales.");
            return;
        }
        for (int i = 0; i < sales.length(); i++) {
            addSaleRow(SaleSummary.fromJson(sales.optJSONObject(i)));
        }
    }

    private void addLocationSelector() {
        if (session.locations.isEmpty()) {
            return;
        }
        Spinner spinner = new Spinner(this);
        List<String> names = new ArrayList<>();
        int selected = 0;
        for (int i = 0; i < session.locations.size(); i++) {
            Location location = session.locations.get(i);
            names.add(location.name);
            if (String.valueOf(location.id).equals(session.locationId)) {
                selected = i;
            }
        }
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        spinner.setSelection(selected);
        spinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            session.setLocationId(String.valueOf(session.locations.get(position).id));
            loadDashboard();
        }));
        content.addView(spinner, Ui.params(this, -1, 48, 8));
    }

    private void addPeriodChips() {
        LinearLayout chips = Ui.row(this);
        for (String item : new String[]{"today", "week", "month"}) {
            Button button = item.equals(period) ? Ui.primary(this, cap(item)) : Ui.secondary(this, cap(item));
            button.setOnClickListener(view -> {
                period = item;
                loadDashboard();
            });
            chips.addView(button, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        }
        content.addView(chips, Ui.params(this, -1, -2, 8));
    }

    private void addRegisterBanner() {
        boolean open = session.register != null && session.register.isOpen();
        TextView banner = Ui.text(this, open ? "Register open" : "Register closed",
                15, open ? Ui.GREEN : Ui.DANGER, Typeface.BOLD);
        banner.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        banner.setBackground(Ui.rounded(open ? Color.rgb(229, 247, 239) : Color.rgb(255, 238, 235),
                Ui.dp(this, 10)));
        content.addView(banner, Ui.params(this, -1, -2, 8));

        Button action = open ? Ui.secondary(this, "Close register") : Ui.primary(this, "Open register");
        content.addView(action, Ui.params(this, -1, 48, 6));
        action.setOnClickListener(view -> {
            if (open) {
                RegisterDialogs.close(this, this::loadSessionThenRender);
            } else {
                RegisterDialogs.open(this, this::loadSessionThenRender);
            }
        });
    }

    private void addSaleRow(SaleSummary sale) {
        Button row = Ui.secondary(this, sale.invoiceNo + " • " + sale.customerName + "\n"
                + session.money().format(sale.finalTotal) + " • " + sale.paymentStatus);
        row.setGravity(android.view.Gravity.LEFT | android.view.Gravity.CENTER_VERTICAL);
        row.setOnClickListener(view -> SaleDetailActivity.open(this, sale.id));
        content.addView(row, Ui.params(this, -1, 64, 6));
    }

    private static String cap(String value) {
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }
}
