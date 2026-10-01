package co.ke.bremac.posapp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import co.ke.bremac.posapp.data.Location;
import co.ke.bremac.posapp.data.Permissions;
import co.ke.bremac.posapp.data.SaleSummary;
import co.ke.bremac.posapp.ui.Formats;
import co.ke.bremac.posapp.ui.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends BaseActivity {
    private static final String[] PERIODS = {"today", "week", "month"};
    private static final String[] PERIOD_LABELS = {"Today", "This week", "This month"};

    private String period = "today";
    private JSONObject dashboard;
    private boolean dashboardLoading;

    @Override
    protected NavDrawer.Item navItem() {
        return NavDrawer.Item.HOME;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenTitle("Home");
        render();
        loadProfile(!session.hasProfile());
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        loadProfile(false);
    }

    private void loadProfile(boolean showProgress) {
        if (!session.isSignedIn()) {
            return;
        }
        runAsync(showProgress ? "Loading your workspace…" : "", () -> session.api().me(), result -> {
            JSONObject data = result.optJSONObject("data");
            if (data != null) {
                session.applyMe(data);
            }
            refreshChrome();
            render();
            if (session.permissions.viewDashboard) {
                loadDashboard();
            }
        });
    }

    private void loadDashboard() {
        dashboardLoading = true;
        render();
        runAsync("", () -> session.api().dashboard(session.locationId, period), result -> {
            dashboardLoading = false;
            dashboard = result.optJSONObject("data");
            render();
        });
    }

    private void render() {
        content.removeAllViews();
        addGreeting();
        addLocationSelector();
        addQuickActions();
        addRegisterCard();
        if (session.permissions.viewDashboard) {
            addPerformance();
            addRecentSales();
        } else if (session.hasProfile()) {
            content.addView(Ui.banner(this, "Sales figures are hidden for your role. Use the menu to open the "
                    + "screens available to you.", Ui.MUTED, Ui.TRACK), Ui.params(this, -1, -2, 16));
        }
    }

    private void addGreeting() {
        LinearLayout hero = Ui.column(this);
        hero.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20));
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Ui.PRIMARY_DARK, Ui.PRIMARY});
        background.setCornerRadius(Ui.dp(this, 20));
        hero.setBackground(background);

        String name = session.user.fullName == null || session.user.fullName.trim().isEmpty()
                ? session.user.username
                : session.user.fullName.trim().split("\\s+")[0];
        hero.addView(Ui.text(this, Formats.greeting() + ",", 14, Ui.withAlpha(Color.WHITE, 0.8f), Typeface.NORMAL));
        hero.addView(Ui.text(this, name == null || name.isEmpty() ? "Welcome" : name, 24, Color.WHITE,
                Typeface.BOLD), Ui.params(this, -2, -2, 2));
        hero.addView(Ui.text(this, session.business.name, 13, Ui.withAlpha(Color.WHITE, 0.8f), Typeface.NORMAL),
                Ui.params(this, -2, -2, 8));
        content.addView(hero, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addLocationSelector() {
        if (session.locations.size() < 2) {
            return;
        }
        List<String> names = new ArrayList<>();
        int selected = 0;
        for (int i = 0; i < session.locations.size(); i++) {
            Location location = session.locations.get(i);
            names.add(location.name);
            if (String.valueOf(location.id).equals(session.locationId)) {
                selected = i;
            }
        }
        Spinner spinner = Ui.spinner(this, names, selected);
        spinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            String picked = String.valueOf(session.locations.get(position).id);
            if (picked.equals(session.locationId)) {
                return;
            }
            session.setLocationId(picked);
            session.paymentMethods = new ArrayList<>();
            refreshChrome();
            if (session.permissions.viewDashboard) {
                loadDashboard();
            }
        }));
        content.addView(Ui.field(this, "LOCATION", spinner), Ui.params(this, -1, -2, 16));
    }

    private void addQuickActions() {
        Permissions permissions = session.permissions;
        List<View> tiles = new ArrayList<>();
        if (permissions.sellCreate) {
            tiles.add(tile(R.drawable.ic_cart, "New sale", Ui.PRIMARY, Ui.PRIMARY_SOFT, PosActivity.class));
        }
        if (permissions.viewSales) {
            tiles.add(tile(R.drawable.ic_receipt, "Sales history", Ui.INFO, Ui.INFO_SOFT, SalesActivity.class));
        }
        if (permissions.viewProducts) {
            tiles.add(tile(R.drawable.ic_inventory, "Products", Ui.WARNING, Ui.WARNING_SOFT, ProductsActivity.class));
        }
        if (permissions.viewCustomers) {
            tiles.add(tile(R.drawable.ic_people, "Customers", Ui.SUCCESS, Ui.SUCCESS_SOFT, CustomersActivity.class));
        }
        if (tiles.isEmpty()) {
            return;
        }
        sectionHeader(content, "Quick actions", null, null);
        content.addView(Ui.grid(this, tiles, 2), Ui.params(this, -1, -2, 10));
    }

    private View tile(int icon, String label, int accent, int soft, Class<? extends Activity> target) {
        LinearLayout tile = Ui.actionTile(this, icon, label, accent, soft);
        tile.setOnClickListener(view -> startActivity(new Intent(this, target)));
        return tile;
    }

    private void addRegisterCard() {
        if (!session.permissions.canUseRegister() || !session.hasProfile()) {
            return;
        }
        boolean open = session.register != null && session.register.isOpen();
        String subtitle;
        if (open) {
            String since = Formats.time(session.register.openedAt);
            subtitle = (since.isEmpty() ? "Open" : "Open since " + since) + " • Float "
                    + session.money().format(session.register.openingAmount);
        } else {
            subtitle = "Open the register to start selling";
        }
        LinearLayout row = Ui.listRow(this, "Cash register", subtitle, null,
                Ui.statusPill(this, open ? "open" : "closed"));
        row.addView(Ui.iconBubble(this, R.drawable.ic_register, open ? Ui.SUCCESS : Ui.DANGER,
                open ? Ui.SUCCESS_SOFT : Ui.DANGER_SOFT, 40), 0);
        ((LinearLayout.LayoutParams) row.getChildAt(1).getLayoutParams()).setMarginStart(Ui.dp(this, 12));
        row.setOnClickListener(view -> startActivity(new Intent(this, RegisterActivity.class)));
        content.addView(row, Ui.params(this, -1, -2, 16));
    }

    private void addPerformance() {
        sectionHeader(content, "Performance", null, null);
        int selected = 0;
        for (int i = 0; i < PERIODS.length; i++) {
            if (PERIODS[i].equals(period)) {
                selected = i;
            }
        }
        content.addView(Ui.segmented(this, PERIOD_LABELS, selected, index -> {
            period = PERIODS[index];
            loadDashboard();
        }), Ui.params(this, -1, -2, 10));

        List<View> tiles = new ArrayList<>();
        tiles.add(Ui.statTile(this, "TOTAL SALES", money("total_sales"), Ui.PRIMARY));
        tiles.add(Ui.statTile(this, "TRANSACTIONS", count("sales_count"), Ui.INFO));
        tiles.add(Ui.statTile(this, "PAID", money("total_paid"), Ui.SUCCESS));
        tiles.add(Ui.statTile(this, "DUE", money("total_due"), Ui.DANGER));
        tiles.add(Ui.statTile(this, "EXPENSES", money("total_expense"), Ui.WARNING));
        tiles.add(Ui.statTile(this, "NET", money("net"), Ui.PRIMARY_DARK));
        content.addView(Ui.grid(this, tiles, 2), Ui.params(this, -1, -2, 12));
    }

    private String money(String key) {
        if (dashboard == null) {
            return dashboardLoading ? "…" : "—";
        }
        return session.money().format(dashboard.optDouble(key, 0));
    }

    private String count(String key) {
        if (dashboard == null) {
            return dashboardLoading ? "…" : "—";
        }
        return String.valueOf(dashboard.optInt(key));
    }

    private void addRecentSales() {
        boolean canOpen = session.permissions.viewSales;
        sectionHeader(content, "Recent sales", canOpen ? "See all" : null,
                () -> startActivity(new Intent(this, SalesActivity.class)));
        JSONArray sales = dashboard == null ? null : dashboard.optJSONArray("recent_sales");
        if (sales == null || sales.length() == 0) {
            content.addView(Ui.emptyState(this, R.drawable.ic_receipt,
                    dashboardLoading ? "Loading…" : "No sales yet",
                    dashboardLoading ? null : "Sales made in this period will appear here."),
                    Ui.params(this, -1, -2, 10));
            return;
        }
        for (int i = 0; i < sales.length(); i++) {
            SaleSummary sale = SaleSummary.fromJson(sales.optJSONObject(i));
            LinearLayout row = Ui.listRow(this, sale.invoiceNo,
                    sale.customerName + " • " + Formats.dateTime(sale.transactionDate),
                    session.money().format(sale.finalTotal),
                    Ui.statusPill(this, sale.paymentStatus));
            if (canOpen) {
                row.setOnClickListener(view -> SaleDetailActivity.open(this, sale.id));
            }
            content.addView(row, Ui.params(this, -1, -2, 8));
        }
    }
}
