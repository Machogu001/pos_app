package co.ke.bremac.posapp;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import co.ke.bremac.posapp.data.Customer;
import co.ke.bremac.posapp.data.Json;
import co.ke.bremac.posapp.ui.Ui;

import java.util.List;

public final class CustomerDialog {
    public interface Picked {
        /** Receives the chosen customer, or null for walk-in. */
        void accept(Customer customer);
    }

    private CustomerDialog() {
    }

    public static void show(BaseActivity activity, Picked picked) {
        LinearLayout box = Ui.dialogBox(activity);
        EditText search = Ui.input(activity, "Search name or phone", InputType.TYPE_CLASS_TEXT);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setCompoundDrawablesRelativeWithIntrinsicBounds(
                Ui.tinted(activity, R.drawable.ic_search, Ui.MUTED), null, null, null);
        search.setCompoundDrawablePadding(Ui.dp(activity, 10));
        box.addView(search, Ui.params(activity, -1, -2, 8));

        LinearLayout actions = Ui.row(activity);
        TextView walkIn = Ui.link(activity, "Use walk-in", Ui.PRIMARY);
        actions.addView(walkIn);
        actions.addView(new android.view.View(activity), new LinearLayout.LayoutParams(0, 1, 1));
        TextView add = Ui.link(activity, "+ New customer", Ui.PRIMARY);
        Ui.visible(add, activity.session.permissions.createCustomer);
        actions.addView(add);
        box.addView(actions, Ui.params(activity, -1, -2, 4));

        LinearLayout results = Ui.column(activity);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(results);
        box.addView(scroll, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 320)));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Choose customer")
                .setView(box)
                .setNegativeButton("Close", null)
                .create();
        Picked choose = customer -> {
            picked.accept(customer);
            dialog.dismiss();
        };
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable load = () -> loadCustomers(activity, search.getText().toString(), results, choose);
        search.setOnEditorActionListener((view, actionId, event) -> {
            handler.removeCallbacks(load);
            load.run();
            return true;
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                handler.removeCallbacks(load);
                handler.postDelayed(load, 400);
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        walkIn.setOnClickListener(view -> choose.accept(null));
        add.setOnClickListener(view -> addCustomer(activity, created -> choose.accept(created)));
        dialog.setOnShowListener(value -> load.run());
        dialog.setOnDismissListener(value -> handler.removeCallbacks(load));
        dialog.show();
    }

    private static void loadCustomers(BaseActivity activity, String query, LinearLayout results, Picked picked) {
        activity.runAsync("", () -> activity.session.api().customers(query, 1), result -> {
            results.removeAllViews();
            List<Customer> customers = Json.list(result.optJSONArray("data"), Customer::fromJson);
            if (customers.isEmpty()) {
                results.addView(Ui.emptyState(activity, R.drawable.ic_people, "No customers found",
                        query.isEmpty() ? "" : "Try a different name or phone number."), Ui.params(activity, -1, -2, 8));
            }
            for (Customer customer : customers) {
                LinearLayout row = customerRow(activity, customer);
                row.setOnClickListener(view -> picked.accept(customer));
                results.addView(row, Ui.params(activity, -1, -2, 8));
            }
        });
    }

    static LinearLayout customerRow(BaseActivity activity, Customer customer) {
        String subtitle = customer.mobile;
        if (!customer.contactCode.isEmpty()) {
            subtitle = subtitle.isEmpty() ? customer.contactCode : subtitle + " • " + customer.contactCode;
        }
        TextView pill = null;
        if (customer.isDefault) {
            pill = Ui.pill(activity, "Walk-in", Ui.INFO, Ui.INFO_SOFT);
        } else if (customer.balanceDue > 0) {
            pill = Ui.pill(activity, "Owes " + activity.session.money().format(customer.balanceDue),
                    Ui.WARNING, Ui.WARNING_SOFT);
        }
        LinearLayout row = Ui.listRow(activity, customer.name, subtitle, null, pill);
        row.addView(Ui.avatar(activity, customer.name, 40, Ui.PRIMARY_DARK, Ui.PRIMARY_SOFT), 0,
                new LinearLayout.LayoutParams(Ui.dp(activity, 40), Ui.dp(activity, 40)));
        ((LinearLayout.LayoutParams) row.getChildAt(1).getLayoutParams()).setMarginStart(Ui.dp(activity, 12));
        return row;
    }

    static void addCustomer(BaseActivity activity, Picked created) {
        LinearLayout box = Ui.dialogBox(activity);
        EditText name = Ui.input(activity, "Full name", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText mobile = Ui.input(activity, "07XX XXX XXX", InputType.TYPE_CLASS_PHONE);
        EditText email = Ui.input(activity, "Optional", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        box.addView(Ui.field(activity, "Name", name), Ui.params(activity, -1, -2, 8));
        box.addView(Ui.field(activity, "Mobile", mobile), Ui.params(activity, -1, -2, 12));
        box.addView(Ui.field(activity, "Email", email), Ui.params(activity, -1, -2, 12));
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("New customer")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (name.getText().toString().trim().isEmpty()) {
                name.setError("Name is required");
                return;
            }
            if (mobile.getText().toString().trim().isEmpty()) {
                mobile.setError("Mobile number is required");
                return;
            }
            dialog.dismiss();
            activity.runAsync("Saving customer…",
                    () -> activity.session.api().createCustomer(
                            name.getText().toString().trim(),
                            mobile.getText().toString().trim(),
                            email.getText().toString().trim()),
                    result -> created.accept(Customer.fromJson(result.optJSONObject("data"))));
        }));
        dialog.show();
    }
}
