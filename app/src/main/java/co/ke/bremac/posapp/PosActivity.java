package co.ke.bremac.posapp;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import co.ke.bremac.posapp.api.ApiException;
import co.ke.bremac.posapp.cart.Cart;
import co.ke.bremac.posapp.data.Customer;
import co.ke.bremac.posapp.data.Json;
import co.ke.bremac.posapp.data.Location;
import co.ke.bremac.posapp.data.PaymentMethod;
import co.ke.bremac.posapp.data.Product;
import co.ke.bremac.posapp.ui.Formats;
import co.ke.bremac.posapp.ui.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PosActivity extends BaseActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout productResults;
    private LinearLayout cartContainer;
    private LinearLayout customerCard;
    private Button moreProducts;
    private TextView bottomCount;
    private TextView bottomTotal;
    private Button checkoutButton;
    private String lastSearch = "";
    private int productPage = 1;
    private int productLastPage = 1;
    private Runnable pendingSearch;

    @Override
    protected NavDrawer.Item navItem() {
        return NavDrawer.Item.POS;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureAccess(session.permissions.sellCreate)) {
            return;
        }
        setScreenTitle("Quick sale");
        buildBottomBar();
        render();
        loadPaymentMethods();
        if (!session.cart.lines.isEmpty()) {
            repriceCart();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cartContainer != null) {
            renderCustomer();
            renderCart();
        }
    }

    private void render() {
        content.removeAllViews();
        addLocationSelector();
        customerCard = Ui.card(this);
        content.addView(customerCard, Ui.params(this, -1, -2, session.locations.size() > 1 ? 12 : 0));
        renderCustomer();
        addProductFinder();
        cartContainer = Ui.column(this);
        content.addView(cartContainer, new LinearLayout.LayoutParams(-1, -2));
        renderCart();
    }

    // ---- Location & customer ----------------------------------------------------------------

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
            refreshChrome();
            loadPaymentMethods();
            productResults.removeAllViews();
            Ui.visible(moreProducts, false);
            repriceCart();
        }));
        content.addView(Ui.field(this, "SELLING FROM", spinner), Ui.params(this, -1, -2, 0));
    }

    private void renderCustomer() {
        customerCard.removeAllViews();
        Customer customer = session.selectedCustomer;
        boolean walkIn = customer == null || customer.isDefault;
        LinearLayout row = Ui.row(this);
        row.addView(Ui.iconBubble(this, R.drawable.ic_person, Ui.PRIMARY, Ui.PRIMARY_SOFT, 40));
        LinearLayout texts = Ui.column(this);
        texts.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
        texts.addView(Ui.label(this, "Customer"));
        texts.addView(Ui.text(this, walkIn ? (customer == null ? "Walk-in customer" : customer.name) : customer.name,
                15, Ui.INK, Typeface.BOLD), Ui.params(this, -2, -2, 2));
        if (!walkIn && customer.mobile != null && !customer.mobile.isEmpty()) {
            texts.addView(Ui.text(this, customer.mobile, 12, Ui.MUTED, Typeface.NORMAL));
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(Ui.link(this, walkIn ? "Choose" : "Change", Ui.PRIMARY));
        customerCard.addView(row);
        customerCard.setOnClickListener(view -> CustomerDialog.show(this, picked -> {
            session.selectedCustomer = picked;
            renderCustomer();
            repriceCart();
        }));
        customerCard.setBackground(Ui.ripple(Ui.cardBackground(this), Ui.withAlpha(Ui.PRIMARY, 0.10f),
                Ui.dp(this, 16)));
    }

    // ---- Product finder ---------------------------------------------------------------------

    private void addProductFinder() {
        sectionHeader(content, "Add products", null, null);
        LinearLayout card = Ui.card(this);

        EditText barcode = Ui.input(this, "Scan or type barcode / SKU", InputType.TYPE_CLASS_TEXT);
        barcode.setImeOptions(EditorInfo.IME_ACTION_DONE);
        barcode.setSingleLine(true);
        card.addView(barcode, new LinearLayout.LayoutParams(-1, Ui.dp(this, 50)));
        barcode.setOnEditorActionListener((view, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_DONE || enter) {
                lookupProduct(barcode.getText().toString());
                barcode.setText("");
                return true;
            }
            return event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
        });

        EditText search = Ui.input(this, "Search products by name or SKU", InputType.TYPE_CLASS_TEXT);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setCompoundDrawablesRelativeWithIntrinsicBounds(
                Ui.tinted(this, R.drawable.ic_search, Ui.MUTED), null, null, null);
        search.setCompoundDrawablePadding(Ui.dp(this, 10));
        card.addView(search, Ui.params(this, -1, 50, 10));
        content.addView(card, Ui.params(this, -1, -2, 10));

        productResults = Ui.column(this);
        content.addView(productResults, new LinearLayout.LayoutParams(-1, -2));
        moreProducts = Ui.secondary(this, "Load more products");
        Ui.visible(moreProducts, false);
        content.addView(moreProducts, Ui.params(this, -1, 46, 8));
        moreProducts.setOnClickListener(view -> {
            if (productPage <= productLastPage) {
                searchProducts(lastSearch, false);
            }
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
                String query = value.toString();
                pendingSearch = () -> searchProducts(query, true);
                handler.postDelayed(pendingSearch, 350);
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
    }

    private void searchProducts(String query, boolean reset) {
        lastSearch = query;
        if (reset) {
            productPage = 1;
            productLastPage = 1;
            productResults.removeAllViews();
            Ui.visible(moreProducts, false);
        }
        if (query.trim().isEmpty()) {
            return;
        }
        int requestedPage = productPage;
        runAsync("",
                () -> session.api().products(query, session.locationId, pricingContactId(), requestedPage),
                result -> {
                    if (!query.equals(lastSearch)) {
                        return;
                    }
                    JSONObject meta = result.optJSONObject("meta");
                    productLastPage = meta == null ? requestedPage : meta.optInt("last_page", requestedPage);
                    JSONArray data = result.optJSONArray("data");
                    if (requestedPage == 1 && (data == null || data.length() == 0)) {
                        productResults.addView(Ui.emptyState(this, R.drawable.ic_search, "No products found",
                                "Check the spelling or scan the barcode."), Ui.params(this, -1, -2, 8));
                    }
                    for (Product product : Json.list(data, Product::fromJson)) {
                        addProductRow(product);
                    }
                    productPage = requestedPage + 1;
                    Ui.visible(moreProducts, productPage <= productLastPage);
                });
    }

    private void addProductRow(Product product) {
        String subtitle = product.sku;
        TextView pill = null;
        if (product.enableStock) {
            if (product.isOutOfStock()) {
                pill = Ui.pill(this, "Out of stock", Ui.DANGER, Ui.DANGER_SOFT);
            } else if (product.stockKnown) {
                pill = Ui.pill(this, Formats.quantity(product.stock) + " in stock", Ui.SUCCESS, Ui.SUCCESS_SOFT);
            }
        }
        LinearLayout row = Ui.listRow(this, product.name, subtitle, session.money().format(product.priceIncTax), pill);
        if (product.isOutOfStock()) {
            row.setAlpha(0.6f);
        }
        row.setOnClickListener(view -> addProduct(product));
        productResults.addView(row, Ui.params(this, -1, -2, 8));
    }

    private void lookupProduct(String code) {
        if (code.trim().isEmpty()) {
            return;
        }
        runAsync("Looking up product…",
                () -> session.api().lookup(code.trim(), session.locationId, pricingContactId()),
                result -> addProduct(Product.fromJson(result.optJSONObject("data"))));
    }

    private void addProduct(Product product) {
        if (product.isOutOfStock()) {
            toast(product.name + " is out of stock.");
            return;
        }
        session.cart.add(product.variationId, product.name, product.sku, product.priceIncTax);
        renderCart();
        toast("Added " + product.name);
    }

    private Integer pricingContactId() {
        Customer customer = session.selectedCustomer;
        return customer == null || customer.isDefault ? null : customer.id;
    }

    /**
     * Prices depend on the customer (price group/markup) and location, so refresh
     * every line the cashier has not manually overridden from the server.
     */
    private void repriceCart() {
        List<Cart.Line> lines = new ArrayList<>();
        for (Cart.Line line : session.cart.lines) {
            if (!line.priceEdited && line.sku != null && !line.sku.isEmpty()) {
                lines.add(line);
            }
        }
        if (lines.isEmpty()) {
            renderCart();
            return;
        }
        String locationId = session.locationId;
        Integer contactId = pricingContactId();
        runAsync("Updating prices…", () -> {
            JSONObject prices = new JSONObject();
            for (Cart.Line line : lines) {
                try {
                    JSONObject data = session.api().lookup(line.sku, locationId, contactId).optJSONObject("data");
                    if (data != null) {
                        Product product = Product.fromJson(data);
                        if (product.variationId == line.variationId) {
                            prices.put(String.valueOf(line.variationId), product.priceIncTax);
                        }
                    }
                } catch (ApiException exception) {
                    if (exception.isUnauthenticated()) {
                        throw exception;
                    }
                }
            }
            return prices;
        }, prices -> {
            for (Cart.Line line : session.cart.lines) {
                String key = String.valueOf(line.variationId);
                if (!line.priceEdited && prices.has(key)) {
                    line.unitPrice = prices.getDouble(key);
                }
            }
            renderCart();
        });
    }

    // ---- Cart -------------------------------------------------------------------------------

    private void renderCart() {
        cartContainer.removeAllViews();
        int count = session.cart.lines.size();
        sectionHeader(cartContainer, count == 0 ? "Cart" : "Cart (" + count + ")",
                count == 0 ? null : "Clear", this::confirmClearCart);
        if (count == 0) {
            cartContainer.addView(Ui.emptyState(this, R.drawable.ic_cart, "Cart is empty",
                    "Search or scan products above to add them."), Ui.params(this, -1, -2, 10));
        } else {
            for (Cart.Line line : new ArrayList<>(session.cart.lines)) {
                addCartLine(line);
            }
            addDiscountControls();
            addTotals();
        }
        updateBottomBar();
    }

    private void confirmClearCart() {
        new AlertDialog.Builder(this)
                .setTitle("Clear cart?")
                .setMessage("All items and payments in this sale will be removed.")
                .setNegativeButton("Keep", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    session.cart.clear();
                    renderCart();
                })
                .show();
    }

    private void addCartLine(Cart.Line line) {
        LinearLayout card = Ui.card(this);
        LinearLayout top = Ui.row(this);
        top.setGravity(Gravity.TOP);
        LinearLayout texts = Ui.column(this);
        texts.addView(Ui.text(this, line.name, 15, Ui.INK, Typeface.BOLD));
        String detail = (line.sku == null || line.sku.isEmpty() ? "" : line.sku + " • ")
                + session.money().format(line.unitPrice) + " each" + (line.priceEdited ? " (edited)" : "");
        texts.addView(Ui.text(this, detail, 12, Ui.MUTED, Typeface.NORMAL), Ui.params(this, -2, -2, 2));
        top.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView remove = Ui.icon(this, R.drawable.ic_close, Ui.MUTED);
        remove.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        remove.setBackground(Ui.ripple(null, Ui.withAlpha(Ui.DANGER, 0.15f), Ui.dp(this, 16)));
        remove.setContentDescription("Remove " + line.name);
        top.addView(remove, new LinearLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 32)));
        card.addView(top);

        LinearLayout bottom = Ui.row(this);
        bottom.addView(stepper(line));
        bottom.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        bottom.addView(Ui.text(this, session.money().format(line.quantity * line.unitPrice), 16, Ui.INK,
                Typeface.BOLD));
        card.addView(bottom, Ui.params(this, -1, -2, 12));
        addPriceEditorIfAllowed(card, line);
        cartContainer.addView(card, Ui.params(this, -1, -2, 10));

        remove.setOnClickListener(view -> {
            session.cart.remove(line.variationId);
            renderCart();
        });
    }

    private LinearLayout stepper(Cart.Line line) {
        LinearLayout stepper = Ui.row(this);
        stepper.setBackground(Ui.rounded(Ui.TRACK, Ui.dp(this, 12)));
        TextView minus = stepButton("−");
        TextView plus = stepButton("+");
        TextView quantity = Ui.text(this, Formats.quantity(line.quantity), 16, Ui.INK, Typeface.BOLD);
        quantity.setGravity(Gravity.CENTER);
        quantity.setMinWidth(Ui.dp(this, 44));
        stepper.addView(minus, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
        stepper.addView(quantity, new LinearLayout.LayoutParams(-2, Ui.dp(this, 42)));
        stepper.addView(plus, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
        minus.setOnClickListener(view -> {
            line.quantity--;
            if (line.quantity <= 0) {
                session.cart.remove(line.variationId);
            }
            renderCart();
        });
        plus.setOnClickListener(view -> {
            line.quantity++;
            renderCart();
        });
        return stepper;
    }

    private TextView stepButton(String label) {
        TextView button = Ui.text(this, label, 20, Ui.PRIMARY_DARK, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(Ui.ripple(null, Ui.withAlpha(Ui.PRIMARY, 0.18f), Ui.dp(this, 12)));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private void addPriceEditorIfAllowed(LinearLayout card, Cart.Line line) {
        if (!session.permissions.editPrice) {
            return;
        }
        EditText price = Ui.input(this, "Unit price", InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        price.setText(plain(line.unitPrice));
        price.setImeOptions(EditorInfo.IME_ACTION_DONE);
        Runnable apply = () -> {
            double edited = number(price);
            if (edited != line.unitPrice) {
                line.unitPrice = edited;
                line.priceEdited = true;
                renderCart();
            }
        };
        price.setOnFocusChangeListener((view, focused) -> {
            if (!focused) {
                apply.run();
            }
        });
        price.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                apply.run();
                return true;
            }
            return false;
        });
        card.addView(Ui.field(this, "Unit price", price), Ui.params(this, -1, -2, 10));
    }

    private void addDiscountControls() {
        if (!session.permissions.discount) {
            return;
        }
        LinearLayout card = Ui.card(this);
        card.addView(Ui.label(this, "Order discount"));
        boolean percent = "percentage".equals(session.cart.discountType);
        EditText amount = Ui.input(this, percent ? "Discount %" : "Discount amount",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setImeOptions(EditorInfo.IME_ACTION_DONE);
        if (session.cart.discountAmount > 0) {
            amount.setText(plain(session.cart.discountAmount));
        }
        card.addView(Ui.segmented(this, new String[]{"Fixed amount", "Percentage"}, percent ? 1 : 0,
                index -> setDiscount(index == 1 ? "percentage" : "fixed", amount)), Ui.params(this, -1, -2, 8));
        card.addView(amount, Ui.params(this, -1, 50, 10));
        amount.setOnFocusChangeListener((view, focused) -> {
            if (!focused && number(amount) != session.cart.discountAmount) {
                setDiscount(session.cart.discountType, amount);
            }
        });
        amount.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                setDiscount(session.cart.discountType, amount);
                return true;
            }
            return false;
        });
        cartContainer.addView(card, Ui.params(this, -1, -2, 10));
    }

    private void setDiscount(String type, EditText amount) {
        session.cart.discountType = type;
        session.cart.discountAmount = Math.max(0, number(amount));
        renderCart();
    }

    private void addTotals() {
        int precision = session.money().precision;
        LinearLayout card = Ui.card(this);
        card.addView(Ui.summaryRow(this, "Subtotal", session.money().format(session.cart.subtotal(precision)), false));
        double discount = session.cart.discount(precision);
        if (discount > 0) {
            card.addView(Ui.summaryRow(this, "Discount", "-" + session.money().format(discount), false));
        }
        card.addView(Ui.divider(this), Ui.params(this, -1, 1, 6));
        card.addView(Ui.summaryRow(this, "Total", session.money().format(session.cart.total(precision)), true),
                Ui.params(this, -1, -2, 4));
        cartContainer.addView(card, Ui.params(this, -1, -2, 10));
    }

    // ---- Bottom bar -------------------------------------------------------------------------

    private void buildBottomBar() {
        LinearLayout bar = bottomBar();
        bar.removeAllViews();
        LinearLayout row = Ui.row(this);
        LinearLayout texts = Ui.column(this);
        bottomCount = Ui.text(this, "", 12, Ui.MUTED, Typeface.NORMAL);
        bottomTotal = Ui.text(this, "", 20, Ui.INK, Typeface.BOLD);
        texts.addView(bottomCount);
        texts.addView(bottomTotal);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        checkoutButton = Ui.primary(this, "Checkout");
        checkoutButton.setOnClickListener(view -> {
            if (session.cart.lines.isEmpty()) {
                toast("Add at least one product first.");
                return;
            }
            View focused = getCurrentFocus();
            if (focused != null) {
                focused.clearFocus();
            }
            startActivity(new Intent(this, CheckoutActivity.class));
        });
        row.addView(checkoutButton, new LinearLayout.LayoutParams(Ui.dp(this, 150), Ui.dp(this, 50)));
        bar.addView(row, new LinearLayout.LayoutParams(-1, -2));
        showBottomBar(true);
    }

    private void updateBottomBar() {
        if (bottomTotal == null) {
            return;
        }
        double units = 0;
        for (Cart.Line line : session.cart.lines) {
            units += line.quantity;
        }
        bottomCount.setText(units == 0 ? "No items yet"
                : Formats.quantity(units) + (units == 1 ? " item" : " items"));
        bottomTotal.setText(session.money().format(session.cart.total(session.money().precision)));
        checkoutButton.setEnabled(!session.cart.lines.isEmpty());
        checkoutButton.setAlpha(session.cart.lines.isEmpty() ? 0.5f : 1f);
    }

    private void loadPaymentMethods() {
        runAsync("", () -> session.api().paymentMethods(session.locationId), result ->
                session.paymentMethods = Json.list(result.optJSONArray("data"), PaymentMethod::fromJson));
    }

    private static String plain(double value) {
        return Formats.quantity(value).replace(",", "");
    }

    private double number(EditText input) {
        try {
            return Double.parseDouble(input.getText().toString().replace(",", "").trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
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
        loadPaymentMethods();
        if (!session.cart.lines.isEmpty()) {
            repriceCart();
        }
        if (!lastSearch.trim().isEmpty()) {
            searchProducts(lastSearch, true);
        }
    }
}
