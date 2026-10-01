package co.ke.bremac.posapp.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EnvelopeParser {
    private EnvelopeParser() {
    }

    public static JSONObject parse(String body, int httpStatus) throws ApiException {
        try {
            JSONObject envelope = body == null || body.trim().isEmpty() ? new JSONObject() : new JSONObject(body);
            boolean ok = httpStatus >= 200 && httpStatus < 300 && envelope.optBoolean("success", false);
            if (!ok) {
                throw error(envelope, httpStatus);
            }
            return envelope;
        } catch (JSONException e) {
            throw new ApiException(httpStatus, "", "Invalid server response.", null);
        }
    }

    public static Object data(JSONObject envelope) {
        return envelope.opt("data");
    }

    private static ApiException error(JSONObject envelope, int status) {
        String message = envelope.optString("message", status >= 500 ? "Server error." : "Request failed.");
        String code = envelope.optString("code", "");
        Map<String, List<String>> fields = new LinkedHashMap<>();
        JSONObject errors = envelope.optJSONObject("errors");
        if (errors != null) {
            JSONArray names = errors.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String name = names.optString(i);
                    JSONArray values = errors.optJSONArray(name);
                    ArrayList<String> messages = new ArrayList<>();
                    if (values != null) {
                        for (int j = 0; j < values.length(); j++) messages.add(values.optString(j));
                    } else {
                        messages.add(errors.optString(name));
                    }
                    fields.put(name, messages);
                }
            }
        }
        return new ApiException(status, code, message, fields);
    }
}
