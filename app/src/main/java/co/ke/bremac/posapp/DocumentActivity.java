package co.ke.bremac.posapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

import co.ke.bremac.posapp.data.Sale;
import co.ke.bremac.posapp.ui.Ui;

public class DocumentActivity extends BaseActivity {
    private File document;
    private Sale sale;
    private int saleId;
    private int page;
    private int pageCount;
    private float zoom = 1;
    private ImageView image;
    private TextView pageLabel;
    private Button previous;
    private Button next;
    private Button print;
    private boolean rendering;
    private Bitmap renderedPage;

    static void open(BaseActivity activity, File file, Sale sale) {
        activity.startActivity(new Intent(activity, DocumentActivity.class)
                .putExtra("file", file.getAbsolutePath()).putExtra("sale_id", sale.id)
                .putExtra("invoice_no", sale.invoiceNo));
    }

    @Override
    protected Chrome chrome() {
        return Chrome.BACK;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setScreenTitle("Document");
        setScreenSubtitle(getIntent().getStringExtra("invoice_no"));
        try {
            String path = getIntent().getStringExtra("file");
            if (path == null) {
                throw new IOException("No document was provided.");
            }
            document = new File(path).getCanonicalFile();
            File folder = new File(getCacheDir(), "sale_documents").getCanonicalFile();
            if (!document.getPath().startsWith(folder.getPath() + File.separator) || !document.isFile()) {
                throw new IOException("This document is no longer available. Open it again from the receipt.");
            }
        } catch (IOException exception) {
            showError(exception.getMessage());
            return;
        }
        saleId = getIntent().getIntExtra("sale_id", 0);
        if (state != null) {
            page = state.getInt("page");
            zoom = state.getFloat("zoom", 1);
        }
        LinearLayout controls = Ui.row(this);
        previous = Ui.secondary(this, "Previous");
        next = Ui.secondary(this, "Next");
        pageLabel = Ui.text(this, "", 13, Ui.INK, android.graphics.Typeface.NORMAL);
        controls.addView(previous, new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(pageLabel);
        controls.addView(next, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(controls);
        LinearLayout zoomControls = Ui.row(this);
        Button zoomOut = Ui.secondary(this, "Zoom -");
        Button zoomIn = Ui.secondary(this, "Zoom +");
        zoomControls.addView(zoomOut, new LinearLayout.LayoutParams(0, -2, 1));
        zoomControls.addView(zoomIn, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(zoomControls);
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        image = new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setContentDescription("Invoice document page");
        image.setBackgroundColor(Color.WHITE);
        horizontal.addView(image);
        content.addView(horizontal, Ui.params(this, -1, -2, 12));
        LinearLayout actions = Ui.row(this);
        Button share = Ui.secondary(this, "Share PDF");
        print = Ui.primary(this, "Print receipt");
        print.setEnabled(false);
        actions.addView(share, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(print, new LinearLayout.LayoutParams(0, -2, 1));
        bottomBar().addView(actions);
        showBottomBar(true);
        share.setOnClickListener(view -> SaleDocuments.shareFile(this, document,
                getIntent().getStringExtra("invoice_no")));
        print.setOnClickListener(view -> ReceiptPrinters.show(this, sale));
        previous.setOnClickListener(view -> {
            if (!rendering && page > 0) {
                page--;
                renderPage();
            }
        });
        next.setOnClickListener(view -> {
            if (!rendering && page + 1 < pageCount) {
                page++;
                renderPage();
            }
        });
        zoomOut.setOnClickListener(view -> {
            if (!rendering) {
                zoom = Math.max(1, zoom - 0.5f);
                renderPage();
            }
        });
        zoomIn.setOnClickListener(view -> {
            if (!rendering) {
                zoom = Math.min(3, zoom + 0.5f);
                renderPage();
            }
        });
        renderPage();
        if (session.lastSale != null && session.lastSale.id == saleId) {
            sale = session.lastSale;
            print.setEnabled(true);
        } else {
            runAsync("", () -> session.api().sale(saleId), result -> {
                sale = Sale.fromJson(result.getJSONObject("data"));
                print.setEnabled(true);
            });
        }
    }

    private void renderPage() {
        rendering = true;
        previous.setEnabled(false);
        next.setEnabled(false);
        int requestedPage = page;
        int width = Math.min(2200, Math.round(
                (getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 32)) * zoom));
        runAsync("Opening document...", () -> {
            try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(document, ParcelFileDescriptor.MODE_READ_ONLY);
                 PdfRenderer renderer = new PdfRenderer(descriptor)) {
                int count = renderer.getPageCount();
                if (count == 0) {
                    throw new IOException("The document has no pages.");
                }
                int index = Math.min(Math.max(0, requestedPage), count - 1);
                try (PdfRenderer.Page pdfPage = renderer.openPage(index)) {
                    int height = Math.max(1, Math.round(width * (float) pdfPage.getHeight() / pdfPage.getWidth()));
                    int boundedWidth = width;
                    if ((long) width * height > 4_000_000) {
                        float scale = (float) Math.sqrt(4_000_000d / ((long) width * height));
                        boundedWidth = Math.max(1, Math.round(width * scale));
                        height = Math.max(1, Math.round(height * scale));
                    }
                    renderedPage = Bitmap.createBitmap(boundedWidth, height, Bitmap.Config.ARGB_8888);
                    renderedPage.eraseColor(Color.WHITE);
                    pdfPage.render(renderedPage, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                }
                return new JSONObject().put("count", count).put("page", index);
            }
        }, result -> {
            rendering = false;
            pageCount = result.getInt("count");
            page = result.getInt("page");
            image.setImageBitmap(renderedPage);
            image.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                    renderedPage.getWidth(), renderedPage.getHeight()));
            pageLabel.setText("  " + (page + 1) + " / " + pageCount + "  ");
            previous.setEnabled(page > 0);
            next.setEnabled(page + 1 < pageCount);
        });
    }

    @Override
    protected void handleError(Exception exception) {
        rendering = false;
        super.handleError(exception);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("page", page);
        state.putFloat("zoom", zoom);
        super.onSaveInstanceState(state);
    }
}
