package co.ke.bremac.posapp.data;

import org.json.JSONObject;

public class Register {
    public final int id;
    public final int locationId;
    public final String status;
    public final double openingAmount;
    public final String openedAt;

    public Register(int id, int locationId, String status, double openingAmount, String openedAt) {
        this.id = id;
        this.locationId = locationId;
        this.status = status;
        this.openingAmount = openingAmount;
        this.openedAt = openedAt;
    }

    public static Register fromJson(JSONObject object) {
        if (object == null) {
            return null;
        }
        return new Register(
                object.optInt("id"),
                object.optInt("location_id"),
                object.optString("status"),
                object.optDouble("opening_amount", 0),
                object.optString("opened_at", ""));
    }

    public boolean isOpen() {
        return "open".equals(status);
    }
}
