package co.ke.bremac.posapp;

import co.ke.bremac.posapp.api.ApiClient;
import co.ke.bremac.posapp.api.EnvelopeParser;

import org.json.JSONObject;

public final class ServerDiscovery {
    private ServerDiscovery() {
    }

    public static String discoverCanonicalUrl(String enteredServer) throws Exception {
        return discoverCanonicalUrl(new ApiClient(enteredServer, null));
    }

    static String discoverCanonicalUrl(ApiClient client) throws Exception {
        JSONObject response = client.request("GET", "/discover", null, false);
        JSONObject data = response == null ? null : response.optJSONObject("data");
        if (data == null) {
            Object payload = response == null ? null : EnvelopeParser.data(response);
            if (payload instanceof JSONObject) {
                data = (JSONObject) payload;
            }
        }
        if (data == null && response != null && response.has("server_url")) {
            data = response;
        }
        if (data == null || !data.has("server_url")) {
            throw new IllegalArgumentException("The server did not return a canonical URL.");
        }
        return ServerUrl.normalize(data.getString("server_url"));
    }
}

