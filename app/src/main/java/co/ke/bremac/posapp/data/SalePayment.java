package co.ke.bremac.posapp.data;

import org.json.JSONObject;

public class SalePayment {
    public final String method;
    public final double amount;
    public final String paidOn;
    public final String reference;

    public SalePayment(JSONObject object) {
        method = object.optString("method");
        amount = object.optDouble("amount");
        paidOn = object.optString("paid_on");
        reference = object.optString("reference");
    }

    public static SalePayment fromJson(JSONObject object) {
        return new SalePayment(object);
    }
}
