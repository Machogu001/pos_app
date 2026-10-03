package co.ke.bremac.posapp.data;

import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

public class SaleDocumentTest {
    private JSONObject document(String filename, String content) throws Exception {
        return new JSONObject().put("filename", filename).put("content_type", "application/pdf")
                .put("content_base64", Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void decodesInvoiceAndPreventsPathTraversal() throws Exception {
        SaleDocument invoice = new SaleDocument(document("../INVOICE-SNV2026-7183.pdf", "%PDF-1.4\n"));
        assertEquals(".._INVOICE-SNV2026-7183.pdf", invoice.filename);
        assertArrayEquals("%PDF-1.4\n".getBytes(StandardCharsets.UTF_8), invoice.bytes);
    }

    @Test
    public void preservesQuotationFilename() throws Exception {
        assertEquals("QUOTATION-123.pdf",
                new SaleDocument(document("QUOTATION-123.pdf", "%PDF-1.4\n")).filename);
    }

    @Test(expected = IOException.class)
    public void rejectsHtmlDisguisedAsPdf() throws Exception {
        new SaleDocument(document("INVOICE.pdf", "<html>Sign in</html>"));
    }

    @Test(expected = IOException.class)
    public void rejectsWrongContentType() throws Exception {
        new SaleDocument(document("INVOICE.pdf", "%PDF-1.4").put("content_type", "text/html"));
    }

    @Test
    public void nullReceiptFieldsAndOptionalValuesAreEmpty() throws Exception {
        JSONObject json = new JSONObject("{\"id\":1,\"receipt_url\":null,\"receipt_text\":null,"
                + "\"customer_name\":null,\"location_name\":null,\"items\":[{\"unit\":null}],"
                + "\"payments\":[{\"reference\":null,\"paid_on\":null}]}");
        Sale sale = Sale.fromJson(json);
        assertEquals("", sale.receiptText);
        assertEquals("", sale.receiptUrl);
        assertEquals("", sale.customerName);
        assertEquals("", sale.locationName);
        assertEquals("", sale.items.get(0).unit);
        assertEquals("", sale.payments.get(0).reference);
        assertEquals("", sale.payments.get(0).paidOn);
    }

    @Test
    public void preservesReceiptAndPaymentReference() throws Exception {
        Sale sale = Sale.fromJson(new JSONObject("{\"receipt_text\":\"Invoice: SNV2026-7183\","
                + "\"payments\":[{\"method\":\"mpesa\",\"reference\":\"UJ3QH8EMAL\"}]}"));
        assertEquals("Invoice: SNV2026-7183", sale.receiptText);
        assertEquals("UJ3QH8EMAL", sale.payments.get(0).reference);
    }
}
