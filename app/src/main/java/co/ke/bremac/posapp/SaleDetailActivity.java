package co.ke.bremac.posapp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;

import co.ke.bremac.posapp.data.Sale;
import co.ke.bremac.posapp.data.SaleItem;
import co.ke.bremac.posapp.data.SalePayment;
import co.ke.bremac.posapp.ui.Formats;
import co.ke.bremac.posapp.ui.Ui;

public class SaleDetailActivity extends BaseActivity {
    private static final String EXTRA_ID = "sale_id";

    public static void open(Context context, int saleId) {
        Intent intent = new Intent(context, SaleDetailActivity.class);
        intent.putExtra(EXTRA_ID, saleId);
        context.startActivity(intent);
    }

    @Override
    protected Chrome chrome() {
        return Chrome.BACK;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureAccess(session.permissions.viewSales)) {
            return;
        }
        setScreenTitle("Sale details");
        int saleId = getIntent().getIntExtra(EXTRA_ID, 0);
        runAsync("Loading sale…",
                () -> session.api().sale(saleId),
                result -> render(Sale.fromJson(result.optJSONObject("data"))));
    }

    private void render(Sale sale) {
        content.removeAllViews();
        setScreenSubtitle(sale.invoiceNo);
        addSummary(this, content, sale);
        addItems(sale);
        addPayments(sale);

        Button share = Ui.primary(this, "Share receipt");
        content.addView(share, Ui.params(this, -1, 52, 20));
        share.setOnClickListener(view -> ReceiptActivity.share(this, sale.receiptText));
        if (sale.receiptUrl != null && sale.receiptUrl.startsWith("https://")) {
            Button invoice = Ui.secondary(this, "Open invoice");
            content.addView(invoice, Ui.params(this, -1, 48, 10));
            invoice.setOnClickListener(view -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(sale.receiptUrl))));
        }
    }

    /** Header card with invoice, customer, status and totals; shared with the receipt screen. */
    static void addSummary(BaseActivity activity, LinearLayout parent, Sale sale) {
        LinearLayout card = Ui.card(activity);
        LinearLayout top = Ui.row(activity);
        LinearLayout titles = Ui.column(activity);
        titles.addView(Ui.text(activity, sale.invoiceNo, 18, Ui.INK, Typeface.BOLD));
        titles.addView(Ui.text(activity, Formats.dateTime(sale.transactionDate), 13, Ui.MUTED, Typeface.NORMAL),
                Ui.params(activity, -2, -2, 2));
        top.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(Ui.statusPill(activity, "final".equals(sale.status) ? sale.paymentStatus : sale.status));
        card.addView(top);

        card.addView(Ui.divider(activity), Ui.params(activity, -1, 1, 12));
        String customer = sale.customerName == null || sale.customerName.isEmpty() ? "Walk-in customer"
                : sale.customerName;
        card.addView(Ui.summaryRow(activity, "Customer", customer, false), Ui.params(activity, -1, -2, 6));
        if (sale.locationName != null && !sale.locationName.isEmpty()) {
            card.addView(Ui.summaryRow(activity, "Location", sale.locationName, false));
        }
        card.addView(Ui.summaryRow(activity, "Subtotal", activity.session.money().format(sale.subtotal), false));
        if (sale.discountAmount > 0) {
            card.addView(Ui.summaryRow(activity, "Discount",
                    "-" + activity.session.money().format(sale.discountAmount), false));
        }
        if (sale.taxAmount > 0) {
            card.addView(Ui.summaryRow(activity, "Tax", activity.session.money().format(sale.taxAmount), false));
        }
        card.addView(Ui.divider(activity), Ui.params(activity, -1, 1, 6));
        card.addView(Ui.summaryRow(activity, "Total", activity.session.money().format(sale.finalTotal), true),
                Ui.params(activity, -1, -2, 4));
        card.addView(Ui.summaryRow(activity, "Paid", activity.session.money().format(sale.totalPaid), false));
        if (sale.changeReturn > 0) {
            card.addView(Ui.summaryRow(activity, "Change", activity.session.money().format(sale.changeReturn), false));
        }
        parent.addView(card, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addItems(Sale sale) {
        section("Items (" + sale.items.size() + ")");
        LinearLayout card = Ui.card(this);
        for (int i = 0; i < sale.items.size(); i++) {
            SaleItem item = sale.items.get(i);
            if (i > 0) {
                card.addView(Ui.divider(this), Ui.params(this, -1, 1, 10));
            }
            LinearLayout row = Ui.row(this);
            LinearLayout left = Ui.column(this);
            left.addView(Ui.text(this, item.name, 14, Ui.INK, Typeface.BOLD));
            String unit = item.unit == null || item.unit.isEmpty() ? "" : " " + item.unit;
            left.addView(Ui.text(this, Formats.quantity(item.quantity) + unit + " × "
                    + session.money().format(item.unitPriceIncTax), 13, Ui.MUTED, Typeface.NORMAL));
            row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(Ui.text(this, session.money().format(item.lineTotal), 14, Ui.INK, Typeface.BOLD));
            card.addView(row, Ui.params(this, -1, -2, i == 0 ? 0 : 10));
        }
        content.addView(card, Ui.params(this, -1, -2, 10));
    }

    private void addPayments(Sale sale) {
        if (sale.payments.isEmpty()) {
            return;
        }
        section("Payments");
        LinearLayout card = Ui.card(this);
        for (int i = 0; i < sale.payments.size(); i++) {
            SalePayment payment = sale.payments.get(i);
            String detail = Formats.dateTime(payment.paidOn);
            if (payment.reference != null && !payment.reference.isEmpty() && !"null".equals(payment.reference)) {
                detail = detail.isEmpty() ? payment.reference : detail + " • " + payment.reference;
            }
            LinearLayout row = Ui.row(this);
            LinearLayout left = Ui.column(this);
            left.addView(Ui.text(this, Ui.capitalize(payment.method.replace('_', ' ')), 14, Ui.INK, Typeface.BOLD));
            if (!detail.isEmpty()) {
                left.addView(Ui.text(this, detail, 12, Ui.MUTED, Typeface.NORMAL));
            }
            row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(Ui.text(this, session.money().format(payment.amount), 14, Ui.INK, Typeface.BOLD));
            card.addView(row, Ui.params(this, -1, -2, i == 0 ? 0 : 12));
        }
        content.addView(card, Ui.params(this, -1, -2, 10));
    }
}
