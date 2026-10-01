package co.ke.bremac.posapp;

import android.content.Intent;
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

import androidx.appcompat.app.AlertDialog;

import co.ke.bremac.posapp.data.Customer;
import co.ke.bremac.posapp.data.Json;
import co.ke.bremac.posapp.ui.Ui;

import org.json.JSONObject;

import java.util.List;

public class CustomersActivity extends BaseActivity {
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
        return NavDrawer.Item.CUSTOMERS;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureAccess(session.permissions.viewCustomers)) {
            return;
        }
        setScreenTitle("Customers");
        search =  Ui.input(this, "Search name or phone", InputType.TYPE_CLASS_TEXT);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setCompoundDrawablesRelativeWithIntrinsicBounds(
                Ui.tinted(this, R.drawable.ic_search, Ui.MUTED), null, null, null);
        search.setCompoundDrawablePadding(Ui.dp(this, 10));
        content.addView(search, new LinearLayout.LayoutParams(-1, -2));
        if (session.permissions.createCustomer) {
            Button add = Ui.secondary(this, "+ Add new customer");
            add.setOnClickListener(view -> CustomerDialog.addCustomer(this, created -> {
                toast("Customer saved.");
                load(true);
            }));
            content.addView(add, Ui.params(this, -1, 46, 10));
        }
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
        runAsync(reset && list.getChildCount() == 0 ? "Loading customers…" : "",
                () -> session.api().customers(requestedQuery, requestedPage),
                result -> {
                    if (!requestedQuery.equals(query)) {
                        return;
                    }
                    if (requestedPage == 1) {
                        list.removeAllViews();
                    }
                    JSONObject meta = result.optJSONObject("meta");
                    lastPage = meta == null ? requestedPage : meta.optInt("last_page", requestedPage);
                    List<Customer> customers = Json.list(result.optJSONArray("data"), Customer::fromJson);
                    if (customers.isEmpty() && requestedPage == 1) {
                        list.addView(Ui.emptyState(this, R.drawable.ic_people, "No customers found",
                                requestedQuery.isEmpty() ? "" : "Try a different name or phone number."),
                                Ui.params(this, -1, -2, 10));
                    }
                    for (Customer customer : customers) {
                        LinearLayout row = CustomerDialog.customerRow(this, customer);
                        row.setOnClickListener(view -> showCustomer(customer));
                        list.addView(row, Ui.params(this, -1, -2, 8));
                    }
                    page = requestedPage + 1;
                    Ui.visible(more, page <= lastPage);
                });
    }

    private void showCustomer(Customer customer) {
        StringBuilder details = new StringBuilder();
        if (!customer.mobile.isEmpty()) {
            details.append("Mobile: ").append(customer.mobile).append('\n');
        }
        if (!customer.email.isEmpty()) {
            details.append("Email: ").append(customer.email).append('\n');
        }
        if (!customer.contactCode.isEmpty()) {
            details.append("Contact ID: ").append(customer.contactCode).append('\n');
        }
        details.append("Balance due: ").append(session.money().format(customer.balanceDue));
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(customer.name)
                .setMessage(details.toString())
                .setNegativeButton("Close", null);
        if (session.permissions.sellCreate) {
            builder.setPositiveButton("New sale", (dialog, which) -> {
                session.selectedCustomer = customer.isDefault ? null : customer;
                startActivity(new Intent(this, PosActivity.class));
            });
        }
        if (!customer.mobile.isEmpty()) {
            builder.setNeutralButton("Call", (dialog, which) ->
                    startActivity(new Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + customer.mobile))));
        }
        builder.show();
    }

    @Override
    protected void onDestroy() {
        if (pendingSearch != null) {
            handler.removeCallbacks(pendingSearch);
        }
        super.onDestroy();
    }
}
