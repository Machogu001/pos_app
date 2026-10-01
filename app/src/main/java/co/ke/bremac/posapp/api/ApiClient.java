package co.ke.bremac.posapp.api;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class ApiClient {
    public interface TokenProvider {
        String token();
        void onUnauthenticated();
    }

    private final String server;
    private final TokenProvider tokenProvider;

    public ApiClient(String server, TokenProvider tokenProvider) {
        this.server = server;
        this.tokenProvider = tokenProvider;
    }

    public JSONObject request(String method, String path, JSONObject body, boolean auth) throws Exception {
        URL url = new URL(server + "/api/mobile/v1" + path);
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new ApiException(0, "", "HTTPS is required.", null);
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        if (auth) {
            connection.setRequestProperty("Authorization", "Bearer " + tokenProvider.token());
        }
        if (body != null) {
            connection.setDoOutput(true);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String response = read(stream);
        try {
            return EnvelopeParser.parse(response, status);
        } catch (ApiException e) {
            if (e.isUnauthenticated()) tokenProvider.onUnauthenticated();
            throw e;
        } finally {
            connection.disconnect();
        }
    }

    public static String q(Map<String, String> params) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getValue() == null) continue;
            if (out.length() == 0) {
                out.append('?');
            } else {
                out.append('&');
            }
            out.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        }
        return out.toString();
    }

    private static String enc(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
        }
        return out.toString();
    }
}
