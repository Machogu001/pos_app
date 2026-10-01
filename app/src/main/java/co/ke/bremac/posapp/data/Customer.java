package co.ke.bremac.posapp.data;

import org.json.JSONObject;

public class Customer {
    public final int id;
    public final String name;
    public final String mobile;
    public final String email;
    public final boolean isDefault;

    public Customer(JSONObject object) {
        id = object.optInt("id");
        name = object.optString("name");
        mobile = object.optString("mobile");
        email = object.optString("email");
        isDefault = object.optBoolean("is_default");
    }

    public static Customer fromJson(JSONObject object) {
        return new Customer(object);
    }
}
