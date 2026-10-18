package co.ke.bremac.posapp;

import co.ke.bremac.posapp.api.ApiClient;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ServerDiscoveryTest {
    private static class StubClient extends ApiClient {
        private final JSONObject response;

        StubClient(JSONObject response) {
            super("https://example.com", null);
            this.response = response;
        }

        @Override
        public JSONObject request(String method, String path, JSONObject body, boolean auth) {
            assertEquals("GET", method);
            assertEquals("/discover", path);
            return response;
        }
    }

    @Test
    public void normalizesCanonicalUrlFromDiscoveryResponse() throws Exception {
        JSONObject response = new JSONObject().put("server_url", "https://POS.example.com/retail/");
        String canonical = ServerDiscovery.discoverCanonicalUrl(new StubClient(response));
        assertEquals("https://pos.example.com/retail", canonical);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDiscoveryResponseWithoutCanonicalUrl() throws Exception {
        ServerDiscovery.discoverCanonicalUrl(new StubClient(new JSONObject()));
    }
}
