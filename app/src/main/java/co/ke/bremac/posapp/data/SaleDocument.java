package co.ke.bremac.posapp.data;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Base64;

public final class SaleDocument {
    public final String filename;
    public final byte[] bytes;

    public SaleDocument(JSONObject data) throws Exception {
        if (!"application/pdf".equals(data.getString("content_type"))) {
            throw new IOException("The server did not return a PDF document.");
        }
        bytes = Base64.getDecoder().decode(data.getString("content_base64"));
        if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D'
                || bytes[3] != 'F' || bytes[4] != '-') {
            throw new IOException("The server returned an invalid PDF document.");
        }
        filename = data.getString("filename").replaceAll("[^A-Za-z0-9._-]", "_");
        if (!filename.endsWith(".pdf")) {
            throw new IOException("The server returned an invalid PDF filename.");
        }
    }
}
