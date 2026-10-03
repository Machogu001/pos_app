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

    static void open(BaseActivity activity, String html) {
        activity.runAsync("Opening invoice...", () -> {
            File folder = new File(activity.getCacheDir(), "sale_documents");
            if (!folder.isDirectory() && !folder.mkdirs()) {
                throw new IOException("Cannot create the document folder.");
            }
            File file = File.createTempFile("website-invoice-", ".html", folder);
            Files.write(file.toPath(), html.getBytes(StandardCharsets.UTF_8));
            return new JSONObject().put("file", file.getAbsolutePath());
        }, result -> activity.startActivity(new Intent(activity, WebsiteInvoiceActivity.class)
                .putExtra("file", result.getString("file"))));
    }

    @Override
    protected Chrome chrome() {
        return Chrome.BACK;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setScreenTitle("Website invoice");
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
        print.setOnClickListener(view -> ReceiptPrinters.showHtml(this, html));
        share.setOnClickListener(view -> WebsiteReceipt.renderHtml(this, html, 80, bitmap ->
                runAsync("Preparing invoice PDF...", () -> {
                    try {
                        File file = File.createTempFile("INVOICE-", ".pdf",
                                new File(getCacheDir(), "sale_documents"));
                        SaleDocuments.writeReceiptPdf(bitmap, file);
                        return new JSONObject().put("file", file.getAbsolutePath());
                    } finally {
                        bitmap.recycle();
                    }
                }, result -> SaleDocuments.shareFile(this, new File(result.getString("file")), "Website invoice"))));
    }
}
