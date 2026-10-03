package co.ke.bremac.posapp.api;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PosApiLocationTest {
    private static class RecordingClient extends ApiClient {
        String path;
        JSONObject body;
        String method;

        RecordingClient() {
            super("https://example.com", null);
        }

        @Override
        public JSONObject request(String method, String path, JSONObject body, boolean auth) {
            this.path = path;
            this.method = method;
            this.body = body;
            assertTrue(auth);
            return new JSONObject();
        }
    }

    @Test
    public void stockValidationDoesNotCreateSaleOrPayment() throws Exception {
        RecordingClient client = new RecordingClient();
        org.json.JSONArray items = new org.json.JSONArray().put(
                new JSONObject().put("variation_id", 5).put("quantity", 3));
        new PosApi(client).validateStock("7", items);
        assertEquals("POST", client.method);
        assertEquals("/sales/validate-stock", client.path);
        assertEquals(7, client.body.getInt("location_id"));
        assertEquals(3, client.body.getJSONArray("items").getJSONObject(0).getInt("quantity"));
    }

    @Test
    public void allLocationsOmitsDashboardFilterAndPreservesDateRange() throws Exception {
        RecordingClient client = new RecordingClient();
        new PosApi(client).dashboard(null, "custom", "2026-10-01", "2026-10-02");
        assertEquals("/dashboard?period=custom&start_date=2026-10-01&end_date=2026-10-02", client.path);
    }

    @Test
    public void allLocationsOmitsSalesFilterAndPreservesSearchAndPage() throws Exception {
        RecordingClient client = new RecordingClient();
        new PosApi(client).sales("quotation", null, "Alice", 2);
        assertEquals("/sales?status=quotation&q=Alice&page=2", client.path);
        assertFalse(client.path.contains("location_id"));
    }

    @Test
    public void individualLocationFiltersDashboardAndSales() throws Exception {
        RecordingClient client = new RecordingClient();
        PosApi api = new PosApi(client);
        api.dashboard("7", "today");
        assertEquals("/dashboard?location_id=7&period=today", client.path);
        api.sales("final", "7", "", 1);
        assertTrue(client.path.contains("location_id=7"));
    }

    @Test
    public void fetchesSavedSaleDocumentWithoutPostingAnotherPayment() throws Exception {
        RecordingClient client = new RecordingClient();
        new PosApi(client).saleDocument(123);
        assertEquals("/sales/123/document", client.path);
    }

    @Test
    public void productsAndTransactionsKeepConcreteLocation() throws Exception {
        RecordingClient client = new RecordingClient();
        PosApi api = new PosApi(client);
        api.products("Milk", "7", null, 1);
        assertTrue(client.path.contains("location_id=7"));
        api.openRegister("7", 100);
        assertEquals(7, client.body.getInt("location_id"));
        api.stk("254700000000", 100, "7");
        assertEquals(7, client.body.getInt("location_id"));
    }
}
