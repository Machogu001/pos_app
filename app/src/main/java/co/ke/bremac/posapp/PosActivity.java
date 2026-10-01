package co.ke.bremac.posapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
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
import co.ke.bremac.posapp.ui.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PosActivity extends BaseActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout productResults;
    private String lastSearch = "";
    private int productPage = 1;
    private int productLastPage = 1;
    private Runnable pendingSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addNav();
        loadPaymentMethods();
        render();
    }

    private void render() {
        content.removeAllViews();
        title("New sale");
        addLocationSelector();
        addBarcodeInput();
        addProductSearch();
        addCustomerButton();
        section("Cart");
        renderCart();
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
            String picked = String.valueOf(session.locations.get(position).id);
            if (picked.equals(session.locationId)) {
                return;
            }
            session.setLocationId(picked);
            loadPaymentMethods();
            render();
            repriceCart();
        }));
        content.addView(spinner, Ui.params(this, -1, 48, 8));
    }

    private void addBarcodeInput() {
        EditText barcode = Ui.input(this, "Scan barcode/SKU then Enter", InputType.TYPE_CLASS_TEXT);
        content.addView(barcode, Ui.params(this, -1, 54, 6));
        barcode.setOnEditorActionListener((view, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (actionId == EditorInfo.IME_ACTION_DONE || enter) {
                lookupProduct(barcode.getText().toString());
                barcode.setText("");
                return true;
            }
            return false;
        });
    }

    private void addProductSearch() {
        EditText search = Ui.input(this, "Search products", InputType.TYPE_CLASS_TEXT);
        content.addView(search, Ui.params(this, -1, 54, 6));
        productResults = Ui.column(this);
        content.addView(productResults);
        Button more = Ui.secondary(this, "Load more products");
        content.addView(more, Ui.params(this, -1, 48, 6));
        more.setOnClickListener(view -> {
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
                pendingSearch = () -> {
                    productPage = 1;
                    productResults.removeAllViews();
                    searchProducts(value.toString(), true);
                };
                handler.postDelayed(pendingSearch, 350);
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
    }

    private void addCustomerButton() {
        String label = session.selectedCustomer == null ? "Customer: Walk-in / choose"
                : "Customer: " + session.selectedCustomer.name;
        Button customer = Ui.secondary(this, label);
        customer.setOnClickListener(view -> CustomerDialog.show(this, picked -> {
            session.selectedCustomer = picked;
            render();
            repriceCart();
        }));
        content.addView(customer, Ui.params(this, -1, 48, 8));
    }

    private void searchProducts(String query, boolean reset) {
        lastSearch = query;
        if (query.trim().isEmpty()) {
            return;
        }
        runAsync("",
                () -> session.api().products(query, session.locationId, pricingContactId(), productPage),
                result -> {
                    JSONObject meta = result.optJSONObject("meta");
                    if (meta != null) {
                        productLastPage = meta.optInt("last_page", productPage);
                    }
                    JSONArray data = result.optJSONArray("data");
                    if (reset && (data == null || data.length() == 0)) {
                        productResults.addView(Ui.text(this, "No products found.", 14, Ui.MUTED, 0));
                    }
                    for (Product product : Json.list(data, Product::fromJson)) {
                        addProductRow(productResults, product);
                    }
                    productPage++;
                });
    }

    private void lookupProduct(String code) {
        if (code.trim().isEmpty()) {
            return;
        }
        runAsync("Looking up product…",
                () -> session.api().lookup(code, session.locationId, pricingContactId()),
                result -> addProduct(Product.fromJson(result.optJSONObject("data"))));
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
            render();
        });
    }

    private void addProductRow(LinearLayout parent, Product product) {
        String stock = product.stockKnown ? String.valueOf(product.stock) : "n/a";
        Button row = Ui.secondary(this, product.name + "\n" + product.sku + " • "
                + session.money().format(product.priceIncTax) + " • Stock: " + stock
                + (product.isOutOfStock() ? " (out)" : ""));
        row.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        row.setEnabled(!product.isOutOfStock());
        row.setOnClickListener(view -> addProduct(product));
        parent.addView(row, Ui.params(this, -1, 64, 6));
    }

    private void addProduct(Product product) {
        if (product.isOutOfStock()) {
            toast("Out of stock.");
            return;
        }
        session.cart.add(product.variationId, product.name, product.sku, product.priceIncTax);
        render();
    }

    private void renderCart() {
        if (session.cart.lines.isEmpty()) {
            paragraph("Cart is empty. Search or scan to add products.");
            return;
        }
        for (Cart.Line line : new ArrayList<>(session.cart.lines)) {
            addCartLine(line);
        }
        addDiscountControls();
        addTotals();
        Button checkout = Ui.primary(this, "Checkout");
        checkout.setOnClickListener(view -> startActivity(new Intent(this, CheckoutActivity.class)));
        content.addView(checkout, Ui.params(this, -1, 52, 10));
    }

    private void addCartLine(Cart.Line line) {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.text(this, line.name + "\n" + line.sku, 15, Ui.INK, android.graphics.Typeface.BOLD));
        LinearLayout row = Ui.row(this);
        Button minus = Ui.secondary(this, "−");
        Button plus = Ui.secondary(this, "+");
        Button remove = Ui.secondary(this, "Remove");
        TextView quantity = Ui.text(this, String.valueOf(line.quantity), 16, Ui.INK, android.graphics.Typeface.BOLD);
        quantity.setGravity(Gravity.CENTER);
        row.addView(minus, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        row.addView(quantity, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        row.addView(plus, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        row.addView(remove, new LinearLayout.LayoutParams(Ui.dp(this, 96), Ui.dp(this, 48)));
        card.addView(row);
        addPriceEditorIfAllowed(card, line);
        card.addView(Ui.text(this, "Line: " + session.money().format(line.quantity * line.unitPrice),
                14, Ui.GREEN, android.graphics.Typeface.BOLD));
        content.addView(card, Ui.params(this, -1, -2, 8));
        minus.setOnClickListener(view -> {
            line.quantity--;
            if (line.quantity <= 0) {
                session.cart.remove(line.variationId);
            }
            render();
        });
        plus.setOnClickListener(view -> {
            line.quantity++;
            render();
        });
        remove.setOnClickListener(view -> {
            session.cart.remove(line.variationId);
            render();
        });
    }

    private void addPriceEditorIfAllowed(LinearLayout card, Cart.Line line) {
        if (!session.permissions.editPrice) {
            return;
        }
        EditText price = Ui.input(this, "Unit price", InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        price.setText(String.valueOf(line.unitPrice));
        price.setOnFocusChangeListener((view, focused) -> {
            if (!focused) {
                double edited = number(price);
                if (edited != line.unitPrice) {
                    line.unitPrice = edited;
                    line.priceEdited = true;
                }
                render();
            }
        });
        card.addView(price, Ui.params(this, -1, 54, 6));
    }

    private void addDiscountControls() {
        if (!session.permissions.discount) {
            return;
        }
        LinearLayout row = Ui.row(this);
        Button fixed = "fixed".equals(session.cart.discountType)
                ? Ui.primary(this, "Fixed")
                : Ui.secondary(this, "Fixed");
        Button percent = "percentage".equals(session.cart.discountType)
                ? Ui.primary(this, "%")
                : Ui.secondary(this, "%");
        EditText amount = Ui.input(this, "Discount", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setText(String.valueOf(session.cart.discountAmount));
        row.addView(fixed, new LinearLayout.LayoutParams(Ui.dp(this, 90), Ui.dp(this, 52)));
        row.addView(percent, new LinearLayout.LayoutParams(Ui.dp(this, 70), Ui.dp(this, 52)));
        row.addView(amount, new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1));
        content.addView(row, Ui.params(this, -1, -2, 8));
        fixed.setOnClickListener(view -> setDiscount("fixed", amount));
        percent.setOnClickListener(view -> setDiscount("percentage", amount));
        amount.setOnFocusChangeListener((view, focused) -> {
            if (!focused) {
                setDiscount(session.cart.discountType, amount);
            }
        });
    }

    private void setDiscount(String type, EditText amount) {
        session.cart.discountType = type;
        session.cart.discountAmount = number(amount);
        render();
    }

    private void addTotals() {
        int precision = session.money().precision;
        card("Subtotal", session.money().format(session.cart.subtotal(precision)));
        card("Discount", session.money().format(session.cart.discount(precision)));
        card("Total", session.money().format(session.cart.total(precision)));
    }

    private void loadPaymentMethods() {
        runAsync("", () -> session.api().paymentMethods(session.locationId), result -> {
            session.paymentMethods = Json.list(result.optJSONArray("data"), PaymentMethod::fromJson);
        });
    }

    private double number(EditText input) {
        try {
            return Double.parseDouble(input.getText().toString());
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
}
