package co.ke.bremac.posapp;

import co.ke.bremac.posapp.api.ApiClient;

import org.json.JSONObject;

public final class ServerDiscovery {
    private ServerDiscovery() {
    }

    public static String discoverCanonicalUrl(String enteredServer) throws Exception {
        return discoverCanonicalUrl(new ApiClient(enteredServer, null));
    }

    static String discoverCanonicalUrl(ApiClient client) throws Exception {
        JSONObject response = client.request("GET", "/discover", null, false);
        if (response == null || !response.has("server_url")) {
            throw new IllegalArgumentException("The server did not return a canonical URL.");
        }
        return ServerUrl.normalize(response.getString("server_url"));
    }
}

