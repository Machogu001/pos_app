package co.ke.bremac.posapp;

import android.content.ClipData;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import co.ke.bremac.posapp.data.Sale;
import co.ke.bremac.posapp.data.SaleDocument;

final class SaleDocuments {
    private SaleDocuments() {
    }

    static void open(BaseActivity activity, Sale sale, boolean share) {
        activity.runAsync("Preparing document...", () -> {
            SaleDocument document = new SaleDocument(
                    activity.session.api().saleDocument(sale.id).getJSONObject("data"));
            File directory = new File(activity.getCacheDir(), "sale_documents");
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("Cannot create the document folder.");
            }
            File file = new File(directory, sale.id + "-" + document.filename);
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(document.bytes);
            }
            return new JSONObject().put("file", file.getAbsolutePath());
        }, result -> {
            File file = new File(result.getString("file"));
            if (!share) {
                DocumentActivity.open(activity, file, sale);
                return;
            }
            shareFile(activity, file, sale.invoiceNo);
        });
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
