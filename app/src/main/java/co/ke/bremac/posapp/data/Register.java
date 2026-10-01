package co.ke.bremac.posapp.data;

import org.json.JSONObject;

public class Register {
    public final int id;
    public final int locationId;
    public final String status;
    public final double openingAmount;

    public Register(int id, int locationId, String status, double openingAmount) {
        this.id = id;
        this.locationId = locationId;
        this.status = status;
        this.openingAmount = openingAmount;
    }

    public static Register fromJson(JSONObject object) {
        if (object == null) {
            return null;
        }
        return new Register(
                object.optInt("id"),
                object.optInt("location_id"),
                object.optString("status"),
                object.optDouble("opening_amount"));
    }

    public boolean isOpen() {
        return "open".equals(status);
    }
}
