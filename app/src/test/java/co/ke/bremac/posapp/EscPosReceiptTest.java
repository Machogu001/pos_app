package co.ke.bremac.posapp;

import co.ke.bremac.posapp.data.Business;
import co.ke.bremac.posapp.data.Sale;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EscPosReceiptTest {
    private Business business() throws Exception {
        return Business.fromJson(new JSONObject().put("name", "Example Company")
                .put("currency", new JSONObject().put("code", "USD").put("precision", 2)));
    }

    private JSONObject saleJson() throws Exception {
        return new JSONObject().put("id", 41).put("invoice_no", "INV-41").put("status", "final")
                .put("customer_name", "Chosen customer").put("transaction_date", "2026-10-03")
                .put("location_name", "Main branch").put("subtotal", 12.50)
                .put("discount_amount", 1).put("tax_amount", .50).put("final_total", 12)
                .put("total_paid", 15).put("change_return", 3).put("payment_status", "paid")
                .put("receipt_text", "DO NOT PRINT API TEXT")
                .put("items", new JSONArray().put(new JSONObject().put("name", "Coffee")
                        .put("quantity", 2).put("unit", "cup").put("unit_price_inc_tax", 6.25)
                        .put("line_total", 12.50).put("sku", "COFFEE")))
                .put("payments", new JSONArray().put(new JSONObject().put("method", "card")
                        .put("amount", 15).put("reference", "PAY-REAL-41").put("paid_on", "2026-10-03")));
    }

    @Test
    public void printsStructuredSaleAndSessionCompanyCurrency() throws Exception {
        String text = EscPosReceipt.text(Sale.fromJson(saleJson()), business(), 58);
        for (String expected : new String[]{"Example Company", "INVOICE", "INV-41", "Chosen customer",
                "Main branch", "Coffee", "2 cup x USD 6.25", "USD 12.50", "Discount", "USD 1.00",
                "Tax", "USD 0.50", "TOTAL", "USD 12.00", "Paid", "USD 15.00", "Change", "USD 3.00",
                "card", "PAY-REAL-41", "2026-10-03"}) {
            assertTrue(expected, text.contains(expected));
        }
        assertFalse(text.contains("DO NOT PRINT API TEXT"));
        assertFalse(text.contains("KES"));
    }

    @Test
    public void draftAndQuotationAreNotLabeledAsInvoices() throws Exception {
        for (String status : new String[]{"draft", "quotation", "quote"}) {
            String text = EscPosReceipt.text(Sale.fromJson(saleJson().put("status", status)), business(), 80);
            assertTrue(text.contains(status.equals("draft") ? "DRAFT" : "QUOTATION"));
            assertTrue(text.contains("Not a finalized invoice"));
            assertFalse(text.contains("\nINVOICE\n"));
        }
    }

    @Test
    public void stripsControlInjectionAndUsesAsciiWithoutChangingReceiptCommands() throws Exception {
        JSONObject sale = saleJson();
        sale.put("customer_name", "Jos\u00e9\n\u001b@\u001dV\u0000\u007f\u0085\u202e\u4e2d");
        sale.getJSONArray("items").getJSONObject(0).put("name", "Tea\u001bp\u0000\nInjected");
        sale.getJSONArray("payments").getJSONObject(0).put("reference", "PAY\u001dV\u0000REF");
        String text = EscPosReceipt.text(Sale.fromJson(sale), business(), 58);
        assertTrue(text.contains("Jose"));
        for (char ch : text.toCharArray()) assertTrue(ch == '\n' || (ch >= 32 && ch <= 126));
        byte[] encoded = EscPosReceipt.encode(Sale.fromJson(sale), business(), 58, false);
        // The first 17 bytes are the fixed initialization commands; all sale bytes are ASCII/LF.
        for (int i = 17; i < encoded.length; i++) {
            int value = encoded[i] & 255;
            assertTrue(value == 10 || (value >= 32 && value <= 126));
        }
        assertEquals(0, encoded[16]);
    }

    @Test
    public void wrapsBothWidthsIncludingLongTokensAndLargeAmountsWithoutTruncation() throws Exception {
        String token = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        JSONObject sale = saleJson().put("customer_name", token).put("final_total", 123456789012345.67);
        sale.getJSONArray("items").getJSONObject(0).put("name", "Long product description " + token);
        for (int paper : new int[]{58, 80}) {
            String text = EscPosReceipt.text(Sale.fromJson(sale), business(), paper);
            for (String line : text.split("\n")) assertTrue(line.length() <= EscPosReceipt.columns(paper));
            assertTrue(text.replace("\n", "").contains(token));
            assertTrue(text.contains("123456789012345.67"));
        }
    }

    @Test
    public void cutIsOptInAndUsesOnlyFixedEscPosCommand() throws Exception {
        Sale sale = Sale.fromJson(saleJson());
        byte[] uncut = EscPosReceipt.encode(sale, business(), 80, false);
        byte[] cut = EscPosReceipt.encode(sale, business(), 80, true);
        assertEquals(uncut.length + 3, cut.length);
        assertEquals(29, cut[cut.length - 3]);
        assertEquals(86, cut[cut.length - 2]);
        assertEquals(0, cut[cut.length - 1]);
        assertTrue(new String(uncut, StandardCharsets.US_ASCII).endsWith("\n\n\n"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnsupportedWidth() throws Exception {
        EscPosReceipt.encode(Sale.fromJson(saleJson()), business(), 76, false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesUnsavedSale() throws Exception {
        EscPosReceipt.text(Sale.fromJson(saleJson().put("id", 0)), business(), 58);
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesSaleWithoutStructuredItems() throws Exception {
        EscPosReceipt.text(Sale.fromJson(saleJson().put("items", new JSONArray())), business(), 58);
    }

    @Test
    public void missingOptionalFieldsAndNonfiniteAmountsRemainSafe() throws Exception {
        JSONObject sale = saleJson();
        // Missing JSON monetary values parse to NaN; do not invent amounts or print Java NaN.
        sale.remove("change_return");
        sale.remove("subtotal");
        sale.put("customer_name", JSONObject.NULL);
        String text = EscPosReceipt.text(Sale.fromJson(sale), business(), 58);
        assertFalse(text.contains("NaN"));
        assertFalse(text.contains("Infinity"));
        assertFalse(text.contains("Customer:"));
        assertTrue(text.contains("USD -"));
    }
}
