package co.ke.bremac.posapp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import co.ke.bremac.posapp.data.Json;
import co.ke.bremac.posapp.data.SaleSummary;
import co.ke.bremac.posapp.ui.Formats;
import co.ke.bremac.posapp.ui.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class SalesActivity extends BaseActivity {
    private static final String[] STATUSES = {"final", "draft", "quotation"};
    private static final String[] STATUS_LABELS = {"Completed", "Drafts", "Quotations"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private String status = "final";
    private int page = 1;
    private int lastPage = 1;
    private LinearLayout tabs;
    private LinearLayout list;
    private Button more;
    private EditText search;

    @Override
    protected NavDrawer.Item navItem() {
        return NavDrawer.Item.SALES;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureAccess(session.permissions.viewSales)) {
            return;
        }
        setScreenTitle("Sales history");
        render();
        loadSales(true);
    }

    private void render() {
        content.removeAllViews();
        tabs = Ui.column(this);
        content.addView(tabs, new LinearLayout.LayoutParams(-1, -2));
        renderTabs();

        search = Ui.input(this, "Search invoice or customer", InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setCompoundDrawablesRelativeWithIntrinsicBounds(
                Ui.tinted(this, R.drawable.ic_search, Ui.MUTED), null, null, null);
        search.setCompoundDrawablePadding(Ui.dp(this, 10));
        content.addView(search, Ui.params(this, -1, -2, 12));

        list = Ui.column(this);
        content.addView(list, Ui.params(this, -1, -2, 4));
        more = Ui.secondary(this, "Load more");
        Ui.visible(more, false);
        content.addView(more, Ui.params(this, -1, 48, 12));

        more.setOnClickListener(view -> loadSales(false));
        search.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                loadSales(true);
                return true;
            }
            return false;
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                if (pendingSearch != null) {
                    handler.removeCallbacks(pendingSearch);
                }
                pendingSearch = () -> loadSales(true);
                handler.postDelayed(pendingSearch, 450);
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
    }

    private void renderTabs() {
        tabs.removeAllViews();
        int selected = 0;
        for (int i = 0; i < STATUSES.length; i++) {
            if (STATUSES[i].equals(status)) {
                selected = i;
            }
        }
        tabs.addView(Ui.segmented(this, STATUS_LABELS, selected, index -> {
            status = STATUSES[index];
            renderTabs();
            loadSales(true);
        }));
    }

    private void loadSales(boolean reset) {
        if (reset) {
            page = 1;
            lastPage = 1;
        }
        int requestedPage = page;
        String requestedStatus = status;
        runAsync(reset ? "Loading sales…" : "",
                () -> session.api().sales(requestedStatus, session.locationId, search.getText().toString(),
                        requestedPage),
                result -> {
                    if (!requestedStatus.equals(status)) {
                        return;
                    }
                    if (reset) {
                        list.removeAllViews();
                    }
                    JSONObject meta = result.optJSONObject("meta");
                    lastPage = meta == null ? requestedPage : meta.optInt("last_page", requestedPage);
                    JSONArray data = result.optJSONArray("data");
                    List<SaleSummary> sales = Json.list(data, SaleSummary::fromJson);
                    if (sales.isEmpty() && requestedPage == 1) {
                        list.addView(Ui.emptyState(this, R.drawable.ic_receipt, "No sales found",
                                "Try another tab or search term."), Ui.params(this, -1, -2, 8));
                    }
                    for (SaleSummary sale : sales) {
                        addSaleRow(sale);
                    }
                    page = requestedPage + 1;
                    Ui.visible(more, page <= lastPage);
                });
    }

    private void addSaleRow(SaleSummary sale) {
        String customer = sale.customerName == null || sale.customerName.isEmpty() ? "Walk-in customer"
                : sale.customerName;
        String pillStatus = "final".equals(status) ? sale.paymentStatus : status;
        LinearLayout row = Ui.listRow(this, sale.invoiceNo,
                customer + "\n" + Formats.dateTime(sale.transactionDate),
                session.money().format(sale.finalTotal),
                Ui.statusPill(this, pillStatus));
        row.setOnClickListener(view -> SaleDetailActivity.open(this, sale.id));
        list.addView(row, Ui.params(this, -1, -2, 8));
    }

    @Override
    protected void onDestroy() {
        if (pendingSearch != null) {
            handler.removeCallbacks(pendingSearch);
        }
        super.onDestroy();
    }

    @Override
    protected boolean canPullToRefresh() {
        return true;
    }

    @Override
    protected void onPullToRefresh() {
        loadSales(true);
    }
}
