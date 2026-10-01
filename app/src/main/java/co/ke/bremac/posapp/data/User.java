package co.ke.bremac.posapp.data;

import org.json.JSONObject;

public class User {
    public final int id;
    public final String username;
    public final String fullName;
    public final String email;

    public User(int id, String username, String fullName, String email) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
    }

    public static User fromJson(JSONObject object) {
        if (object == null) {
            return new User(0, "", "", "");
        }
        return new User(
                object.optInt("id"),
                object.optString("username"),
                object.optString("full_name", object.optString("first_name")),
                object.optString("email"));
    }
}
