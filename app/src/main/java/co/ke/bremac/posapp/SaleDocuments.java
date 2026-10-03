package co.ke.bremac.posapp;

import android.content.ClipData;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import co.ke.bremac.posapp.data.Sale;

final class SaleDocuments {
    private SaleDocuments() {
    }

    static void open(BaseActivity activity, Sale sale, boolean share) {
        WebsiteReceipt.render(activity, sale.id, 80, bitmap ->
                activity.runAsync("Preparing website document...", () -> {
                    try {
                        File directory = new File(activity.getCacheDir(), "sale_documents");
                        if (!directory.isDirectory() && !directory.mkdirs()) {
                            throw new IOException("Cannot create the document folder.");
                        }
                        String label = "quotation".equals(sale.status) ? "QUOTATION"
                                : "draft".equals(sale.status) ? "DRAFT" : "INVOICE";
                        String filename = label + "-" + sale.invoiceNo.replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf";
                        File file = new File(directory, sale.id + "-website-" + filename);
                        writeReceiptPdf(bitmap, file);
                        return new JSONObject().put("file", file.getAbsolutePath());
                    } finally {
                        bitmap.recycle();
                    }
                }, result -> {
                    File file = new File(result.getString("file"));
                    if (share) {
                        shareFile(activity, file, sale.invoiceNo);
                    } else {
                        DocumentActivity.open(activity, file, sale);
                    }
                }));
    }

    static void writeReceiptPdf(Bitmap bitmap, File file) throws IOException {
        // A single receipt-sized page preserves the website layout without cutting rows at A4 page breaks.
        int width = 227; // 80 mm in PDF points.
        int height = Math.max(1, (int) Math.ceil(bitmap.getHeight() * (double) width / bitmap.getWidth()));
        PdfDocument pdf = new PdfDocument();
        try {
            PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(width, height, 1).create());
            try {
                Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
                page.getCanvas().drawBitmap(bitmap, new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()),
                        new RectF(0, 0, width, height), paint);
            } finally {
                pdf.finishPage(page);
            }
            try (FileOutputStream output = new FileOutputStream(file)) {
                pdf.writeTo(output);
            }
        } finally {
            pdf.close();
        }
    }

    static void shareFile(BaseActivity activity, File file, String invoiceNo) {
        Uri uri = FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".documents", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_SUBJECT, invoiceNo);
        intent.setClipData(ClipData.newRawUri("Sale document", uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivity(Intent.createChooser(intent, "Share document"));
        } catch (ActivityNotFoundException exception) {
            activity.showError("No app is available to share PDF documents.");
        }
    }
}
