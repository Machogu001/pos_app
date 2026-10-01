package co.ke.bremac.posapp.data;

import org.json.JSONObject;

public class Customer {
    public final int id;
    public final String name;
    public final String mobile;
    public final String email;
    public final boolean isDefault;
    public final String contactCode;
    public final double balanceDue;

    public Customer(JSONObject object) {
        id = object.optInt("id");
        name = object.optString("name");
        mobile = clean(object.optString("mobile"));
        email = clean(object.optString("email"));
        isDefault = object.optBoolean("is_default");
        contactCode = clean(object.optString("contact_id"));
        balanceDue = object.optDouble("balance_due", 0);
    }

    private static String clean(String value) {
        return value == null || "null".equals(value) ? "" : value;
    }

    public static Customer fromJson(JSONObject object) {
        return new Customer(object);
    }
}
