package co.ke.bremac.posapp.data;

import org.json.JSONObject;

public class Business {
    public final int id;
    public final String name;
    public final Currency currency;
    public final String logoUrl;

    public Business(int id, String name, Currency currency, String logoUrl) {
        this.id = id;
        this.name = name;
        this.currency = currency;
        this.logoUrl = logoUrl;
    }

    public static Business fromJson(JSONObject object) {
        if (object == null) {
            return new Business(0, "BreMac360 POS", Currency.fromJson(null), "");
        }
        return new Business(
                object.optInt("id"),
                object.optString("name", "BreMac360 POS"),
                Currency.fromJson(object.optJSONObject("currency")),
                object.optString("logo_url", ""));
    }
}
