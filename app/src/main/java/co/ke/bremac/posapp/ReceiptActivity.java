package co.ke.bremac.posapp;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import co.ke.bremac.posapp.data.Sale;
import co.ke.bremac.posapp.ui.Ui;

public class ReceiptActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addNav();
        render();
    }

    private void render() {
        title("Sale saved");
        Sale sale = session.lastSale;
        TextView receipt = Ui.text(this,
                sale == null ? "Sale completed." : sale.receiptText,
                14,
                Ui.INK,
                Typeface.NORMAL);
        receipt.setTypeface(Typeface.MONOSPACE);
        receipt.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        receipt.setBackground(Ui.rounded(android.graphics.Color.WHITE, Ui.dp(this, 10)));
        content.addView(receipt, Ui.params(this, -1, -2, 8));

        Button share = Ui.primary(this, "Share receipt");
        Button invoice = Ui.secondary(this, "Open invoice");
        Button newSale = Ui.secondary(this, "New sale");
        content.addView(share, Ui.params(this, -1, 48, 8));
        content.addView(invoice, Ui.params(this, -1, 48, 6));
        content.addView(newSale, Ui.params(this, -1, 48, 6));
        share.setOnClickListener(view -> shareText(sale == null ? "" : sale.receiptText));
        invoice.setOnClickListener(view -> openInvoice(sale == null ? "" : sale.receiptUrl));
        newSale.setOnClickListener(view -> startActivity(new Intent(this, PosActivity.class)));
    }

    private void shareText(String text) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(intent, "Share receipt"));
    }

    private void openInvoice(String url) {
        if (url == null || !url.startsWith("https://")) {
            toast("Secure invoice link unavailable.");
            return;
        }
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }
}
