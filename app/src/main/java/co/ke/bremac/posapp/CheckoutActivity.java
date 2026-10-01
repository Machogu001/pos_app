package co.ke.bremac.posapp;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import co.ke.bremac.posapp.cart.Cart;
import co.ke.bremac.posapp.data.PaymentMethod;
import co.ke.bremac.posapp.data.Sale;
import co.ke.bremac.posapp.ui.Formats;
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
    private TextView bottomLabel;
    private TextView bottomValue;
    private Button completeButton;
    private AlertDialog activeMpesaDialog;

    @Override
    protected Chrome chrome() {
        return Chrome.BACK;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureAccess(session.permissions.sellCreate)) {
            return;
        }
        if (session.cart.lines.isEmpty()) {
            toast("The cart is empty.");
            finish();
            return;
        }
        setScreenTitle("Checkout");
        buildBottomBar();
        render();
    }

    private void render() {
        content.removeAllViews();
        int precision = session.money().precision;
        addSummary(precision);

        sectionHeader(content, "Payments", "+ Add payment", this::showPaymentDialog);
        if (session.cart.payments.isEmpty()) {
            content.addView(Ui.emptyState(this, R.drawable.ic_receipt, "No payments yet",
                    "Add cash, M-Pesa, card or other payments to complete the sale."), Ui.params(this, -1, -2, 10));
        } else {
            LinearLayout card = Ui.card(this);
            for (int i = 0; i < session.cart.payments.size(); i++) {
                if (i > 0) {
                    card.addView(Ui.divider(this), Ui.params(this, -1, 1, 10));
                }
                card.addView(paymentRow(session.cart.payments.get(i)), Ui.params(this, -1, -2, i == 0 ? 0 : 10));
            }
            content.addView(card, Ui.params(this, -1, -2, 10));
        }

        Button addPayment = Ui.secondary(this, "Add payment");
        addPayment.setOnClickListener(view -> showPaymentDialog());
        content.addView(addPayment, Ui.params(this, -1, 48, 10));

        sectionHeader(content, "Other options", null, null);
        LinearLayout actions = Ui.row(this);
        Button draft = Ui.secondary(this, "Save as draft");
        Button quotation = Ui.secondary(this, "Save quotation");
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1);
        right.setMarginStart(Ui.dp(this, 10));
        actions.addView(draft, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        actions.addView(quotation, right);
        content.addView(actions, Ui.params(this, -1, -2, 10));
        draft.setOnClickListener(view -> submitSale("draft"));
        quotation.setOnClickListener(view -> submitSale("quotation"));

        updateBottomBar();
    }

    private void addSummary(int precision) {
        LinearLayout card = Ui.card(this);
        double units = 0;
        for (Cart.Line line : session.cart.lines) {
            units += line.quantity;
        }
        String customer = session.selectedCustomer == null ? "Walk-in customer" : session.selectedCustomer.name;
        card.addView(Ui.summaryRow(this, "Customer", customer, false));
        card.addView(Ui.summaryRow(this, "Items", Formats.quantity(units), false));
        card.addView(Ui.summaryRow(this, "Subtotal", session.money().format(session.cart.subtotal(precision)), false));
        double discount = session.cart.discount(precision);
        if (discount > 0) {
            card.addView(Ui.summaryRow(this, "Discount", "-" + session.money().format(discount), false));
        }
        card.addView(Ui.divider(this), Ui.params(this, -1, 1, 6));
        card.addView(Ui.summaryRow(this, "Total due", session.money().format(session.cart.total(precision)), true),
                Ui.params(this, -1, -2, 4));
        card.addView(Ui.summaryRow(this, "Paid", session.money().format(session.cart.paid(precision)), false));
        double balance = session.cart.balance(precision);
        double change = session.cart.change(precision);
        if (balance > 0) {
            card.addView(valueRow("Balance", session.money().format(balance), Ui.DANGER));
        } else if (change > 0) {
            card.addView(valueRow("Change to give", session.money().format(change), Ui.SUCCESS));
        } else {
            card.addView(valueRow("Balance", session.money().format(0), Ui.SUCCESS));
        }
        content.addView(card, new LinearLayout.LayoutParams(-1, -2));
    }

    private LinearLayout valueRow(String label, String value, int color) {
        LinearLayout row = Ui.summaryRow(this, label, value, false);
        ((TextView) row.getChildAt(1)).setTextColor(color);
        return row;
    }

    private LinearLayout paymentRow(Cart.Payment payment) {
        LinearLayout row = Ui.row(this);
        boolean mpesa = "mpesa".equalsIgnoreCase(payment.method);
        row.addView(Ui.iconBubble(this, R.drawable.ic_register, mpesa ? Ui.SUCCESS : Ui.PRIMARY,
                mpesa ? Ui.SUCCESS_SOFT : Ui.PRIMARY_SOFT, 36));
        LinearLayout texts = Ui.column(this);
        texts.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
        texts.addView(Ui.text(this, payment.label == null || payment.label.isEmpty() ? payment.method : payment.label,
                14, Ui.INK, Typeface.BOLD));
        String detail = paymentDetail(payment);
        if (!detail.isEmpty()) {
            texts.addView(Ui.text(this, detail, 12, Ui.MUTED, Typeface.NORMAL));
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(Ui.text(this, session.money().format(payment.amount), 15, Ui.INK, Typeface.BOLD));
        if (!mpesa) {
            ImageView remove = Ui.icon(this, R.drawable.ic_close, Ui.MUTED);
            remove.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
            remove.setBackground(Ui.ripple(null, Ui.withAlpha(Ui.DANGER, 0.15f), Ui.dp(this, 16)));
            remove.setContentDescription("Remove payment");
            remove.setOnClickListener(view -> {
                session.cart.payments.remove(payment);
                render();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 32));
            params.setMarginStart(Ui.dp(this, 8));
            row.addView(remove, params);
        }
        return row;
    }

    private static String paymentDetail(Cart.Payment payment) {
        if ("mpesa".equalsIgnoreCase(payment.method)) {
            return (payment.transactionNo == null || payment.transactionNo.isEmpty() ? "Confirmed"
                    : "Receipt " + payment.transactionNo) + (payment.mpesaPhone.isEmpty() ? "" : " • " + payment.mpesaPhone);
        }
        if (payment.cardNumber != null && !payment.cardNumber.isEmpty()) {
            return "Card •••• " + payment.cardNumber;
        }
        if (payment.transactionNo != null && !payment.transactionNo.isEmpty()) {
            return "Ref " + payment.transactionNo;
        }
        return "";
    }

    // ---- Bottom bar -------------------------------------------------------------------------

    private void buildBottomBar() {
        LinearLayout bar = bottomBar();
        bar.removeAllViews();
        LinearLayout row = Ui.row(this);
        LinearLayout texts = Ui.column(this);
        bottomLabel = Ui.text(this, "", 12, Ui.MUTED, Typeface.NORMAL);
        bottomValue = Ui.text(this, "", 20, Ui.INK, Typeface.BOLD);
        texts.addView(bottomLabel);
        texts.addView(bottomValue);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        completeButton = Ui.primary(this, "Complete sale");
        completeButton.setOnClickListener(view -> {
            if (session.cart.balance(session.money().precision) > 0) {
                showPaymentDialog();
                toast("Add payment for the remaining balance first.");
            } else {
                submitSale("final");
            }
        });
        row.addView(completeButton, new LinearLayout.LayoutParams(Ui.dp(this, 170), Ui.dp(this, 50)));
        bar.addView(row, new LinearLayout.LayoutParams(-1, -2));
        showBottomBar(true);
    }

    private void updateBottomBar() {
        int precision = session.money().precision;
        double balance = session.cart.balance(precision);
        if (balance > 0) {
            bottomLabel.setText("Balance due");
            bottomValue.setText(session.money().format(balance));
            bottomValue.setTextColor(Ui.DANGER);
            completeButton.setAlpha(0.6f);
        } else {
            double change = session.cart.change(precision);
            bottomLabel.setText(change > 0 ? "Change to give" : "Fully paid");
            bottomValue.setText(session.money().format(change > 0 ? change : session.cart.total(precision)));
            bottomValue.setTextColor(change > 0 ? Ui.SUCCESS : Ui.INK);
            completeButton.setAlpha(1f);
        }
    }

    // ---- Payments ---------------------------------------------------------------------------

    private void showPaymentDialog() {
        List<PaymentMethod> methods = new ArrayList<>(session.paymentMethods);
        if (methods.isEmpty()) {
            methods.add(new PaymentMethod("cash", "Cash"));
        }
        List<String> labels = new ArrayList<>();
        for (PaymentMethod method : methods) {
            labels.add(method.label);
        }

        LinearLayout box = Ui.dialogBox(this);
        Spinner methodSpinner = Ui.spinner(this, labels, 0);
        box.addView(Ui.field(this, "Payment method", methodSpinner), Ui.params(this, -1, -2, 8));

        EditText amount = Ui.input(this, "Amount", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        double balance = session.cart.balance(session.money().precision);
        amount.setText(balance > 0 ? Formats.quantity(balance) : "");
        box.addView(Ui.field(this, "Amount", amount), Ui.params(this, -1, -2, 12));

        EditText phone = Ui.input(this, "07XX XXX XXX", InputType.TYPE_CLASS_PHONE);
        if (session.selectedCustomer != null && !session.selectedCustomer.isDefault
                && session.selectedCustomer.mobile != null) {
            phone.setText(session.selectedCustomer.mobile);
        }
        LinearLayout phoneField = Ui.field(this, "M-Pesa phone number", phone);
        box.addView(phoneField, Ui.params(this, -1, -2, 12));

        EditText cardNumber = Ui.input(this, "Last 4 digits", InputType.TYPE_CLASS_NUMBER);
        LinearLayout cardField = Ui.field(this, "Card number", cardNumber);
        box.addView(cardField, Ui.params(this, -1, -2, 12));

        EditText transactionNo = Ui.input(this, "Optional", InputType.TYPE_CLASS_TEXT);
        LinearLayout referenceField = Ui.field(this, "Reference / transaction no.", transactionNo);
        box.addView(referenceField, Ui.params(this, -1, -2, 12));

        TextView hint = Ui.text(this, "", 12, Ui.MUTED, Typeface.NORMAL);
        box.addView(hint, Ui.params(this, -1, -2, 10));

        Runnable updateFields = () -> {
            String key = methods.get(Math.max(0, methodSpinner.getSelectedItemPosition())).key;
            boolean mpesa = "mpesa".equalsIgnoreCase(key);
            boolean card = "card".equalsIgnoreCase(key);
            boolean cash = "cash".equalsIgnoreCase(key);
            Ui.visible(phoneField, mpesa);
            Ui.visible(cardField, card);
            Ui.visible(referenceField, !mpesa && !cash);
            hint.setText(mpesa ? "The customer will get a prompt on their phone to enter their M-Pesa PIN."
                    : cash ? "Enter the amount received; change is calculated automatically." : "");
            Ui.visible(hint, mpesa || cash);
        };
        methodSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateFields.run();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        updateFields.run();

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(box);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add payment")
                .setView(scroll)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", null)
                .create();
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            PaymentMethod method = methods.get(Math.max(0, methodSpinner.getSelectedItemPosition()));
            double value = number(amount);
            if (value <= 0) {
                amount.setError("Enter an amount greater than zero");
                return;
            }
            if ("mpesa".equalsIgnoreCase(method.key) && phone.getText().toString().trim().length() < 9) {
                phone.setError("Enter the customer's M-Pesa number");
                return;
            }
            dialog.dismiss();
            addPayment(method, value, phone, cardNumber, transactionNo);
        }));
        dialog.show();
    }

    private void addPayment(PaymentMethod method, double amount, EditText phone, EditText card, EditText trx) {
        if ("mpesa".equalsIgnoreCase(method.key)) {
            startMpesa(phone.getText().toString().trim(), amount, method.label);
            return;
        }
        Cart.Payment payment = new Cart.Payment();
        payment.method = method.key;
        payment.label = method.label;
        payment.amount = amount;
        payment.cardNumber = card.getText().toString().trim();
        payment.transactionNo = trx.getText().toString().trim();
        session.cart.payments.add(payment);
        render();
    }

    private void startMpesa(String phone, double amount, String label) {
        AlertDialog dialog = mpesaDialog(phone, amount);
        activeMpesaDialog = dialog;
        mpesaCancelled = false;
        dialog.setOnCancelListener(value -> mpesaCancelled = true);
        dialog.show();
        runAsync("", () -> session.api().stk(phone, amount, session.locationId), result -> {
            String requestId = result.optJSONObject("data").optString("checkout_request_id");
            pollMpesa(dialog, requestId, phone, amount, label, System.currentTimeMillis());
        });
    }

    private AlertDialog mpesaDialog(String phone, double amount) {
        LinearLayout box = Ui.dialogBox(this);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(Ui.dp(this, 24), Ui.dp(this, 16), Ui.dp(this, 24), Ui.dp(this, 8));
        box.addView(new ProgressBar(this));
        TextView message = Ui.text(this, "Waiting for the customer to confirm "
                + session.money().format(amount) + " on " + phone + "…", 15, Ui.INK, Typeface.NORMAL);
        message.setGravity(Gravity.CENTER);
        box.addView(message, Ui.params(this, -1, -2, 14));
        TextView note = Ui.text(this, "This can take up to 90 seconds.", 12, Ui.MUTED, Typeface.NORMAL);
        note.setGravity(Gravity.CENTER);
        box.addView(note, Ui.params(this, -1, -2, 6));
        return new AlertDialog.Builder(this)
                .setTitle("M-Pesa payment")
                .setView(box)
                .setNegativeButton("Stop waiting", (dialog, which) -> mpesaCancelled = true)
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
            String status = data == null ? "" : data.optString("status");
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
        toast("M-Pesa payment confirmed.");
    }

    // ---- Submit -----------------------------------------------------------------------------

    private void submitSale(String status) {
        lastSubmitStatus = status;
        try {
            JSONObject body = buildSaleRequest(status);
            String message = "final".equals(status) ? "Completing sale…"
                    : "draft".equals(status) ? "Saving draft…" : "Saving quotation…";
            runAsync(message, () -> session.api().createSale(body), result -> {
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
    protected void handleError(Exception exception) {
        // A failed STK request or status poll ends the M-Pesa wait; don't leave the spinner up.
        if (activeMpesaDialog != null && activeMpesaDialog.isShowing()) {
            mpesaCancelled = true;
            activeMpesaDialog.dismiss();
        }
        super.handleError(exception);
    }

    @Override
    protected void retryAfterRegisterOpened() {
        submitSale(lastSubmitStatus);
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
        mpesaCancelled = true;
        if (mpesaPoll != null) {
            handler.removeCallbacks(mpesaPoll);
        }
        super.onDestroy();
    }
}
