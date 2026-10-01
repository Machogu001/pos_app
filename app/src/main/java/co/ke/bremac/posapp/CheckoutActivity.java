package co.ke.bremac.posapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import co.ke.bremac.posapp.cart.Cart;
import co.ke.bremac.posapp.data.PaymentMethod;
import co.ke.bremac.posapp.data.Sale;
import co.ke.bremac.posapp.ui.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class CheckoutActivity extends BaseActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean mpesaCancelled;
    private Runnable mpesaPoll;
    private String lastSubmitStatus = "final";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addNav();
        render();
    }

    private void render() {
        content.removeAllViews();
        title("Checkout");
        addTotals();
        section("Payments");
        if (session.cart.payments.isEmpty()) {
            paragraph("No payments added.");
        } else {
            for (Cart.Payment payment : session.cart.payments) {
                card(payment.label == null ? payment.method : payment.label, session.money().format(payment.amount));
            }
        }
        int precision = session.money().precision;
        card("Paid", session.money().format(session.cart.paid(precision)));
        card("Balance", session.money().format(session.cart.balance(precision)));
        card("Change", session.money().format(session.cart.change(precision)));

        Button addPayment = Ui.primary(this, "Add payment");
        Button complete = Ui.primary(this, "Complete sale");
        Button draft = Ui.secondary(this, "Save draft");
        Button quotation = Ui.secondary(this, "Save quotation");
        content.addView(addPayment, Ui.params(this, -1, 52, 8));
        content.addView(complete, Ui.params(this, -1, 52, 8));
        content.addView(draft, Ui.params(this, -1, 48, 6));
        content.addView(quotation, Ui.params(this, -1, 48, 6));
        addPayment.setOnClickListener(view -> showPaymentDialog());
        complete.setOnClickListener(view -> {
            if (session.cart.balance(precision) > 0) {
                showError("Final sales require full payment.");
            } else {
                submitSale("final");
            }
        });
        draft.setOnClickListener(view -> submitSale("draft"));
        quotation.setOnClickListener(view -> submitSale("quotation"));
    }

    private void addTotals() {
        int precision = session.money().precision;
        card("Subtotal", session.money().format(session.cart.subtotal(precision)));
        card("Discount", session.money().format(session.cart.discount(precision)));
        card("Total", session.money().format(session.cart.total(precision)));
    }

    private void showPaymentDialog() {
        LinearLayout box = Ui.column(this);
        Spinner methods = new Spinner(this);
        List<String> labels = new ArrayList<>();
        for (PaymentMethod method : session.paymentMethods) {
            labels.add(method.label);
        }
        if (labels.isEmpty()) {
            labels.add("Cash");
        }
        methods.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        box.addView(methods);

        EditText amount = Ui.input(this, "Amount", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setText(String.valueOf(session.cart.balance(session.money().precision)));
        EditText phone = Ui.input(this, "M-Pesa phone", InputType.TYPE_CLASS_PHONE);
        EditText cardNumber = Ui.input(this, "Card last digits", InputType.TYPE_CLASS_TEXT);
        EditText transactionNo = Ui.input(this, "Transaction/reference", InputType.TYPE_CLASS_TEXT);
        box.addView(amount);
        box.addView(phone);
        box.addView(cardNumber);
        box.addView(transactionNo);

        new AlertDialog.Builder(this)
                .setTitle("Add payment")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", (dialog, which) -> addPayment(
                        selectedMethod(methods),
                        amount,
                        phone,
                        cardNumber,
                        transactionNo))
                .show();
    }

    private PaymentMethod selectedMethod(Spinner methods) {
        if (session.paymentMethods.isEmpty()) {
            return new PaymentMethod("cash", "Cash");
        }
        return session.paymentMethods.get(methods.getSelectedItemPosition());
    }

    private void addPayment(PaymentMethod method, EditText amount, EditText phone, EditText card, EditText trx) {
        if ("mpesa".equalsIgnoreCase(method.key)) {
            startMpesa(phone.getText().toString(), number(amount), method.label);
            return;
        }
        Cart.Payment payment = new Cart.Payment();
        payment.method = method.key;
        payment.label = method.label;
        payment.amount = number(amount);
        payment.cardNumber = card.getText().toString();
        payment.transactionNo = trx.getText().toString();
        session.cart.payments.add(payment);
        render();
    }

    private void startMpesa(String phone, double amount, String label) {
        AlertDialog dialog = mpesaDialog();
        mpesaCancelled = false;
        dialog.setOnCancelListener(value -> mpesaCancelled = true);
        dialog.show();
        runAsync("", () -> session.api().stk(phone, amount, session.locationId), result -> {
            String requestId = result.optJSONObject("data").optString("checkout_request_id");
            pollMpesa(dialog, requestId, phone, amount, label, System.currentTimeMillis());
        });
    }

    private AlertDialog mpesaDialog() {
        LinearLayout box = Ui.column(this);
        box.setPadding(Ui.dp(this, 24), Ui.dp(this, 20), Ui.dp(this, 24), Ui.dp(this, 20));
        box.addView(new ProgressBar(this));
        TextView message = Ui.text(this, "Waiting for M-Pesa confirmation…", 15, Ui.INK, 0);
        box.addView(message, Ui.params(this, -1, -2, 12));
        return new AlertDialog.Builder(this)
                .setTitle("M-Pesa STK Push")
                .setView(box)
                .setNegativeButton("Cancel", (dialog, which) -> mpesaCancelled = true)
                .create();
    }

    private void pollMpesa(
            AlertDialog dialog,
            String requestId,
            String phone,
            double amount,
            String label,
            long start) {
        if (mpesaCancelled || !isAlive() || System.currentTimeMillis() - start > 90000) {
            dialog.dismiss();
            toast("M-Pesa confirmation stopped.");
            return;
        }
        mpesaPoll = () -> runAsync("", () -> session.api().mpesaStatus(requestId), result -> {
            JSONObject data = result.optJSONObject("data");
            String status = data.optString("status");
            if ("paid".equals(status)) {
                dialog.dismiss();
                addMpesaPayment(requestId, phone, amount, label, data.optString("receipt_number"));
            } else if ("failed".equals(status) || "cancelled".equals(status)) {
                dialog.dismiss();
                showError(data.optString("message", "M-Pesa payment was not completed."));
            } else {
                pollMpesa(dialog, requestId, phone, amount, label, start);
            }
        });
        handler.postDelayed(mpesaPoll, 3000);
    }

    private void addMpesaPayment(String requestId, String phone, double amount, String label, String receipt) {
        Cart.Payment payment = new Cart.Payment();
        payment.method = "mpesa";
        payment.label = label;
        payment.amount = amount;
        payment.checkoutRequestId = requestId;
        payment.mpesaPhone = phone;
        payment.transactionNo = receipt;
        session.cart.payments.add(payment);
        render();
    }

    private void submitSale(String status) {
        lastSubmitStatus = status;
        try {
            JSONObject body = buildSaleRequest(status);
            runAsync("Saving sale…", () -> session.api().createSale(body), result -> {
                session.lastSale = Sale.fromJson(result.optJSONObject("data"));
                session.cart.clear();
                startActivity(new Intent(this, ReceiptActivity.class));
                finish();
            });
        } catch (Exception exception) {
            showError(exception.getMessage());
        }
    }

    private JSONObject buildSaleRequest(String status) throws Exception {
        JSONObject body = new JSONObject()
                .put("location_id", Integer.parseInt(session.locationId))
                .put("contact_id", session.selectedCustomer == null || session.selectedCustomer.isDefault
                        ? JSONObject.NULL : session.selectedCustomer.id)
                .put("status", status)
                .put("discount_type", session.cart.discountType)
                .put("discount_amount", session.cart.discountAmount)
                .put("note", "")
                .put("change_return", session.cart.change(session.money().precision))
                .put("client_reference", session.cart.clientReference);
        JSONArray items = new JSONArray();
        for (Cart.Line line : session.cart.lines) {
            items.put(new JSONObject()
                    .put("variation_id", line.variationId)
                    .put("quantity", line.quantity)
                    .put("unit_price_inc_tax", line.unitPrice)
                    .put("note", ""));
        }
        body.put("items", items);
        JSONArray payments = new JSONArray();
        for (Cart.Payment payment : session.cart.payments) {
            payments.put(new JSONObject()
                    .put("method", payment.method)
                    .put("amount", payment.amount)
                    .put("note", payment.note)
                    .put("card_number", payment.cardNumber)
                    .put("transaction_no", payment.transactionNo)
                    .put("checkout_request_id", payment.checkoutRequestId)
                    .put("mpesa_phone", payment.mpesaPhone));
        }
        body.put("payments", payments);
        return body;
    }

    @Override
    protected void retryAfterRegisterOpened() {
        submitSale(lastSubmitStatus);
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
        mpesaCancelled = true;
        if (mpesaPoll != null) {
            handler.removeCallbacks(mpesaPoll);
        }
        super.onDestroy();
    }
}
