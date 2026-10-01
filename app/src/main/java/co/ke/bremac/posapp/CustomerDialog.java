package co.ke.bremac.posapp;

import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;

import co.ke.bremac.posapp.data.Customer;
import co.ke.bremac.posapp.data.Json;
import co.ke.bremac.posapp.ui.Ui;

public final class CustomerDialog {
    public interface Picked {
        void accept(Customer customer);
    }

    private CustomerDialog() {
    }

    public static void show(BaseActivity activity, Picked picked) {
        LinearLayout box = Ui.column(activity);
        EditText search = Ui.input(activity, "Search customer", InputType.TYPE_CLASS_TEXT);
        Button add = Ui.secondary(activity, "Add customer");
        LinearLayout results = Ui.column(activity);
        box.addView(search);
        box.addView(add, Ui.params(activity, -1, 48, 6));
        box.addView(results);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Customer")
                .setView(box)
                .setNegativeButton("Close", null)
                .create();
        Runnable load = () -> loadCustomers(activity, search, results, customer -> {
            picked.accept(customer);
            dialog.dismiss();
        });
        search.setOnEditorActionListener((view, actionId, event) -> {
            load.run();
            return true;
        });
        add.setOnClickListener(view -> addCustomer(activity, dialog, picked));
        dialog.setOnShowListener(value -> load.run());
        dialog.show();
    }

    private static void loadCustomers(BaseActivity activity, EditText search, LinearLayout results, Picked picked) {
        activity.runAsync("", () -> activity.session.api().customers(search.getText().toString(), 1), result -> {
            results.removeAllViews();
            for (Customer customer : Json.list(result.optJSONArray("data"), Customer::fromJson)) {
                Button row = Ui.secondary(activity, customer.name + " " + customer.mobile);
                row.setOnClickListener(view -> picked.accept(customer));
                results.addView(row, Ui.params(activity, -1, 48, 6));
            }
        });
    }

    private static void addCustomer(BaseActivity activity, AlertDialog parent, Picked picked) {
        LinearLayout box = Ui.column(activity);
        EditText name = Ui.input(activity, "Name", InputType.TYPE_CLASS_TEXT);
        EditText mobile = Ui.input(activity, "Mobile", InputType.TYPE_CLASS_PHONE);
        EditText email = Ui.input(activity, "Email", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        box.addView(name);
        box.addView(mobile);
        box.addView(email);
        new AlertDialog.Builder(activity)
                .setTitle("Add customer")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> activity.runAsync("Saving customer…",
                        () -> activity.session.api().createCustomer(
                                name.getText().toString(),
                                mobile.getText().toString(),
                                email.getText().toString()),
                        result -> {
                            Customer customer = Customer.fromJson(result.optJSONObject("data"));
                            activity.session.selectedCustomer = customer;
                            picked.accept(customer);
                            parent.dismiss();
                        }))
                .show();
    }
}
