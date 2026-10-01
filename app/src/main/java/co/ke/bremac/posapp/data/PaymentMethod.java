package co.ke.bremac.posapp.data;

import org.json.JSONObject;

public class PaymentMethod {
    public final String key;
    public final String label;

    public PaymentMethod(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public static PaymentMethod fromJson(JSONObject object) {
        return new PaymentMethod(
                object.optString("key"),
                object.optString("label", object.optString("key")));
    }
}
