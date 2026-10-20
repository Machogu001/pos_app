package co.ke.bremac.posapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import co.ke.bremac.posapp.ui.Ui;

public class WebsiteInvoiceActivity extends BaseActivity {
    private String html;
    private int saleId;

    static void openSale(BaseActivity activity, int saleId) {
        activity.runAsync("Opening invoice...", () -> activity.session.api().saleReceipt(saleId),
                result -> open(activity, result.getJSONObject("data").getString("html"), saleId));
    }

    static void open(BaseActivity activity, String html) {
        open(activity, html, 0);
    }

    static void open(BaseActivity activity, String html, int saleId) {
        activity.runAsync("Opening invoice...", () -> {
            File folder = new File(activity.getCacheDir(), "sale_documents");
            if (!folder.isDirectory() && !folder.mkdirs()) {
                throw new IOException("Cannot create the document folder.");
            }
            File file = File.createTempFile("website-invoice-", ".html", folder);
            Files.write(file.toPath(), html.getBytes(StandardCharsets.UTF_8));
            return new JSONObject().put("file", file.getAbsolutePath())
                    .put("sale_id", saleId);
        }, result -> activity.startActivity(new Intent(activity, WebsiteInvoiceActivity.class)
                .putExtra("file", result.getString("file"))
                .putExtra("sale_id", result.optInt("sale_id", 0))));
    }

    @Override
    protected Chrome chrome() {
        return Chrome.BACK;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setScreenTitle("Website invoice");
        saleId = getIntent().getIntExtra("sale_id", 0);
        try {
            String path = getIntent().getStringExtra("file");
            if (path == null) throw new IOException("No invoice was provided.");
            File file = new File(path).getCanonicalFile();
            File folder = new File(getCacheDir(), "sale_documents").getCanonicalFile();
            if (!file.getPath().startsWith(folder.getPath() + File.separator)
                    || !file.isFile() || file.length() > 8_000_000) {
                throw new IOException("This invoice is unavailable. Open it again from POS.");
            }
            html = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            showError(exception.getMessage());
            return;
        }
        LinearLayout preview = Ui.column(this);
        content.addView(preview, new LinearLayout.LayoutParams(-1, -2));
        WebsiteReceipt.showHtml(this, html, preview);
        Button print = Ui.primary(this, "Print receipt");
        Button share = Ui.secondary(this, "Share invoice PDF");
        bottomBar().addView(print, new LinearLayout.LayoutParams(-1, -2));
        bottomBar().addView(share, Ui.params(this, -1, 48, 8));
        showBottomBar(true);
        print.setOnClickListener(view -> {
            if (saleId > 0) {
                ReceiptPrinters.showHtml(this, html,
                        () -> openSale(this, saleId));
            } else {
                ReceiptPrinters.showHtml(this, html);
            }
        });
        share.setOnClickListener(view -> shareInvoiceHtml(this, html, "Website invoice"));
    }

    static void shareSale(BaseActivity activity, int saleId, String invoiceNo) {
        activity.runAsync("Preparing invoice...", () -> activity.session.api().saleReceipt(saleId),
                result -> shareInvoiceHtml(activity, result.getJSONObject("data").getString("html"), invoiceNo));
    }

    private static void shareInvoiceHtml(BaseActivity activity, String html, String invoiceNo) {
        WebsiteReceipt.renderInvoicePdf(activity, html, bitmap ->
                activity.runAsync("Preparing invoice PDF...", () -> {
                    try {
                        File directory = new File(activity.getCacheDir(), "sale_documents");
                        if (!directory.isDirectory() && !directory.mkdirs()) {
                            throw new IOException("Cannot create the document folder.");
                        }
                        File file = File.createTempFile("INVOICE-", ".pdf", directory);
                        SaleDocuments.writeReceiptPdf(bitmap, file);
                        return new JSONObject().put("file", file.getAbsolutePath());
                    } finally {
                        bitmap.recycle();
                    }
                }, result -> SaleDocuments.shareFile(activity,
                        new File(result.getString("file")), invoiceNo)));
    }
}
