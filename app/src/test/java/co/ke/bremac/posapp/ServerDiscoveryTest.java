package co.ke.bremac.posapp;

import co.ke.bremac.posapp.api.ApiClient;
import co.ke.bremac.posapp.api.ApiException;

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

    private static class MissingDiscoverClient extends ApiClient {
        MissingDiscoverClient() {
            super("https://example.com", null);
        }

        @Override
        public JSONObject request(String method, String path, JSONObject body, boolean auth) throws Exception {
            assertEquals("GET", method);
            assertEquals("/discover", path);
            throw new ApiException(404, "not_found", "Not Found", null);
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

    @Test
    public void fallsBackToNormalizedServerWhenDiscoverReturns404() throws Exception {
        String canonical = ServerDiscovery.discoverCanonicalUrl(new MissingDiscoverClient(), "https://POS.example.com/retail/");
        assertEquals("https://pos.example.com/retail", canonical);
    }
}
