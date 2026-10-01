package co.ke.bremac.posapp;

import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import co.ke.bremac.posapp.data.Json;
import co.ke.bremac.posapp.data.SaleSummary;
import co.ke.bremac.posapp.ui.Ui;

import org.json.JSONObject;

public class SalesActivity extends BaseActivity {
    private String status = "final";
    private int page = 1;
    private int lastPage = 1;
    private LinearLayout list;
    private EditText search;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addNav();
        render();
        loadSales(true);
    }

    private void render() {
        content.removeAllViews();
        title("Sales history");
        addTabs();
        search = Ui.input(this, "Search invoice/customer", InputType.TYPE_CLASS_TEXT);
        content.addView(search, Ui.params(this, -1, 54, 8));
        Button searchButton = Ui.secondary(this, "Search");
        content.addView(searchButton, Ui.params(this, -1, 48, 6));
        list = Ui.column(this);
        content.addView(list);
        Button more = Ui.secondary(this, "Load more");
        content.addView(more, Ui.params(this, -1, 48, 8));
        searchButton.setOnClickListener(view -> loadSales(true));
        more.setOnClickListener(view -> {
            if (page <= lastPage) {
                loadSales(false);
            }
        });
    }

    private void addTabs() {
        LinearLayout tabs = Ui.row(this);
        for (String item : new String[]{"final", "draft", "quotation"}) {
            Button button = item.equals(status) ? Ui.primary(this, cap(item)) : Ui.secondary(this, cap(item));
            button.setOnClickListener(view -> {
                status = item;
                loadSales(true);
            });
            tabs.addView(button, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        }
        content.addView(tabs, Ui.params(this, -1, -2, 8));
    }

    private void loadSales(boolean reset) {
        if (reset) {
            page = 1;
            lastPage = 1;
            if (list != null) {
                list.removeAllViews();
            }
        }
        runAsync("Loading sales…",
                () -> session.api().sales(
                        status,
                        session.locationId,
                        search == null ? "" : search.getText().toString(),
                        page),
                result -> {
                    JSONObject meta = result.optJSONObject("meta");
                    if (meta != null) {
                        lastPage = meta.optInt("last_page", page);
                    }
                    if (result.optJSONArray("data") == null || result.optJSONArray("data").length() == 0) {
                        if (page == 1) {
                            paragraph("No sales found.");
                        }
                    } else {
                        for (SaleSummary sale : Json.list(result.optJSONArray("data"), SaleSummary::fromJson)) {
                            addSaleRow(sale);
                        }
                    }
                    page++;
                });
    }

    private void addSaleRow(SaleSummary sale) {
        Button row = Ui.secondary(this, sale.invoiceNo + " • " + sale.customerName + "\n"
                + session.money().format(sale.finalTotal) + " • " + sale.paymentStatus
                + " • " + sale.transactionDate);
        row.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        row.setOnClickListener(view -> SaleDetailActivity.open(this, sale.id));
        list.addView(row, Ui.params(this, -1, 64, 6));
    }

    private static String cap(String value) {
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }
}
