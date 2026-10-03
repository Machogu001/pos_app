package co.ke.bremac.posapp;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import co.ke.bremac.posapp.data.Sale;
import co.ke.bremac.posapp.ui.Ui;

public class ReceiptActivity extends BaseActivity {
    @Override
    protected Chrome chrome() {
        return Chrome.BACK;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenTitle("Receipt");
        render();
        if (session.lastSale != null && session.lastSale.receiptText.isEmpty()) {
            int id = session.lastSale.id;
            runAsync("Loading saved receipt...", () -> session.api().sale(id), result -> {
                session.lastSale = Sale.fromJson(result.getJSONObject("data"));
                render();
            });
        }
    }

    private void render() {
        content.removeAllViews();
        bottomBar().removeAllViews();
        Sale sale = session.lastSale;
        LinearLayout hero = Ui.column(this);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        hero.addView(Ui.iconBubble(this, R.drawable.ic_check_circle, Ui.SUCCESS, Ui.SUCCESS_SOFT, 72));
        String heading = sale == null ? "Sale saved" : headingFor(sale.status);
        TextView title = Ui.text(this, heading, 22, Ui.INK, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        hero.addView(title, Ui.params(this, -2, -2, 14));
        if (sale != null) {
            TextView total = Ui.text(this, session.money().format(sale.finalTotal), 28, Ui.PRIMARY_TEXT, Typeface.BOLD);
            total.setGravity(Gravity.CENTER);
            hero.addView(total, Ui.params(this, -2, -2, 4));
        }
        content.addView(hero, new LinearLayout.LayoutParams(-1, -2));

        if (sale != null) {
            SaleDetailActivity.addSummary(this, content, sale);
            if (!sale.receiptError.isEmpty()) {
                content.addView(Ui.banner(this, sale.receiptError, Ui.WARNING, Ui.WARNING_SOFT),
                        Ui.params(this, -1, -2, 12));
            }
            if (sale.receiptText != null && !sale.receiptText.isEmpty()) {
                section("Receipt");
                TextView receipt = Ui.text(this, sale.receiptText, 13, Ui.INK, Typeface.NORMAL);
                receipt.setTypeface(Typeface.MONOSPACE);
                LinearLayout card = Ui.card(this);
                card.addView(receipt);
                content.addView(card, Ui.params(this, -1, -2, 10));
            }
        }

        Button newSale = Ui.primary(this, "Start a new sale");
        newSale.setOnClickListener(view -> finish());
        bottomBar().addView(newSale, new LinearLayout.LayoutParams(-1, Ui.dp(this, 52)));
        showBottomBar(true);

        LinearLayout actions = Ui.row(this);
        Button share = Ui.secondary(this, "Share");
        Button invoice = Ui.secondary(this, sale != null && "quotation".equals(sale.status)
                ? "Open quotation" : sale != null && "draft".equals(sale.status) ? "Open draft" : "Open invoice");
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1);
        right.setMarginStart(Ui.dp(this, 10));
        actions.addView(share, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        actions.addView(invoice, right);
        content.addView(actions, Ui.params(this, -1, -2, 16));
        share.setEnabled(sale != null);
        invoice.setEnabled(sale != null);
        share.setOnClickListener(view -> SaleDocuments.open(this, sale, true));
        invoice.setOnClickListener(view -> SaleDocuments.open(this, sale, false));
        Button print = Ui.secondary(this, "Print receipt");
        print.setEnabled(sale != null);
        print.setOnClickListener(view -> ReceiptPrinters.show(this, sale));
        content.addView(print, Ui.params(this, -1, 48, 10));
    }

    private static String headingFor(String status) {
        if ("draft".equals(status)) {
            return "Draft saved";
        }
        if ("quotation".equals(status)) {
            return "Quotation saved";
        }
        return "Sale completed";
    }

    @Override
    protected boolean allowSwipeForward() {
        return false;
    }
}
