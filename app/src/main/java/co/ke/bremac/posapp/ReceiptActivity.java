package co.ke.bremac.posapp;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
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
    }

    private void render() {
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
            TextView total = Ui.text(this, session.money().format(sale.finalTotal), 28, Ui.PRIMARY_DARK, Typeface.BOLD);
            total.setGravity(Gravity.CENTER);
            hero.addView(total, Ui.params(this, -2, -2, 4));
        }
        content.addView(hero, new LinearLayout.LayoutParams(-1, -2));

        if (sale != null) {
            SaleDetailActivity.addSummary(this, content, sale);
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
        Button invoice = Ui.secondary(this, "Open invoice");
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1);
        right.setMarginStart(Ui.dp(this, 10));
        actions.addView(share, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        actions.addView(invoice, right);
        content.addView(actions, Ui.params(this, -1, -2, 16));
        share.setOnClickListener(view -> share(this, sale == null ? "" : sale.receiptText));
        invoice.setOnClickListener(view -> openInvoice(sale == null ? "" : sale.receiptUrl));
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

    static void share(BaseActivity activity, String text) {
        if (text == null || text.isEmpty()) {
            activity.toast("Receipt text is not available.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        activity.startActivity(Intent.createChooser(intent, "Share receipt"));
    }

    private void openInvoice(String url) {
        if (url == null || !url.startsWith("https://")) {
            toast("A secure invoice link is not available.");
            return;
        }
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    @Override
    protected boolean allowSwipeForward() {
        return false;
    }
}
