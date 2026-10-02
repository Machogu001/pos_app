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
import android.widget.TextView;

import co.ke.bremac.posapp.data.Json;
import co.ke.bremac.posapp.data.Product;
import co.ke.bremac.posapp.ui.Formats;
import co.ke.bremac.posapp.ui.Ui;

import org.json.JSONObject;

import java.util.List;

public class ProductsActivity extends BaseActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private EditText search;
    private LinearLayout list;
    private Button more;
    private int page = 1;
    private int lastPage = 1;
    private String query = "";

    @Override
    protected NavDrawer.Item navItem() {
        return NavDrawer.Item.PRODUCTS;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureAccess(session.permissions.viewProducts)) {
            return;
        }
        setScreenTitle("Products & stock");
        search = Ui.input(this, "Search name, SKU or barcode", InputType.TYPE_CLASS_TEXT);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setCompoundDrawablesRelativeWithIntrinsicBounds(
                Ui.tinted(this, R.drawable.ic_search, Ui.MUTED), null, null, null);
        search.setCompoundDrawablePadding(Ui.dp(this, 10));
        content.addView(search, new LinearLayout.LayoutParams(-1, -2));
        list = Ui.column(this);
        content.addView(list, Ui.params(this, -1, -2, 4));
        more = Ui.secondary(this, "Load more");
        Ui.visible(more, false);
        content.addView(more, Ui.params(this, -1, 48, 12));
        more.setOnClickListener(view -> load(false));

        search.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                load(true);
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
                pendingSearch = () -> load(true);
                handler.postDelayed(pendingSearch, 400);
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        load(true);
    }

    private void load(boolean reset) {
        if (reset) {
            query = search.getText().toString().trim();
            page = 1;
            lastPage = 1;
        }
        String requestedQuery = query;
        int requestedPage = page;
        runAsync(reset && list.getChildCount() == 0 ? "Loading products…" : "",
                () -> session.api().products(requestedQuery, session.locationId, null, requestedPage),
                result -> {
                    if (!requestedQuery.equals(query)) {
                        return;
                    }
                    if (requestedPage == 1) {
                        list.removeAllViews();
                    }
                    JSONObject meta = result.optJSONObject("meta");
                    lastPage = meta == null ? requestedPage : meta.optInt("last_page", requestedPage);
                    List<Product> products = Json.list(result.optJSONArray("data"), Product::fromJson);
                    if (products.isEmpty() && requestedPage == 1) {
                        list.addView(Ui.emptyState(this, R.drawable.ic_inventory, "No products found",
                                requestedQuery.isEmpty() ? "No products are available at this location."
                                        : "Try a different name or SKU."), Ui.params(this, -1, -2, 10));
                    }
                    for (Product product : products) {
                        list.addView(productRow(product), Ui.params(this, -1, -2, 8));
                    }
                    page = requestedPage + 1;
                    Ui.visible(more, page <= lastPage);
                });
    }

    private LinearLayout productRow(Product product) {
        TextView pill;
        if (!product.enableStock) {
            pill = Ui.pill(this, "Not tracked", Ui.MUTED, Ui.TRACK);
        } else if (product.isOutOfStock()) {
            pill = Ui.pill(this, "Out of stock", Ui.DANGER, Ui.DANGER_SOFT);
        } else if (!product.stockKnown) {
            pill = Ui.pill(this, "Stock n/a", Ui.MUTED, Ui.TRACK);
        } else {
            String unit = product.unit == null || product.unit.isEmpty() || "null".equals(product.unit)
                    ? "" : " " + product.unit;
            boolean low = product.stock <= 5;
            pill = Ui.pill(this, Formats.quantity(product.stock) + unit,
                    low ? Ui.WARNING : Ui.SUCCESS, low ? Ui.WARNING_SOFT : Ui.SUCCESS_SOFT);
        }
        return Ui.listRow(this, product.name, product.sku, session.money().format(product.priceIncTax), pill);
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
        load(true);
    }
}
