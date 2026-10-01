package co.ke.bremac.posapp.data;

import org.json.JSONObject;

public class Location {
    public final int id;
    public final String name;
    public final String code;

    public Location(int id, String name, String code) {
        this.id = id;
        this.name = name;
        this.code = code;
    }

    public static Location fromJson(JSONObject object) {
        return new Location(
                object.optInt("id"),
                object.optString("name"),
                object.optString("location_id"));
    }
}
