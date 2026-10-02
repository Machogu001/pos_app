package co.ke.bremac.posapp.data;

import org.json.JSONObject;

public class User {
    public final int id;
    public final String username;
    public final String fullName;
    public final String email;
    /** Prefix set on the website user (e.g. "Mr", "Mrs"); may be empty. */
    public final String title;
    public final String firstName;

    public User(int id, String username, String fullName, String email, String title, String firstName) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.title = title;
        this.firstName = firstName;
    }

    public static User fromJson(JSONObject object) {
        if (object == null) {
            return new User(0, "", "", "", "", "");
        }
        return new User(
                object.optInt("id"),
                object.optString("username"),
                object.optString("full_name", object.optString("first_name")),
                object.optString("email"),
                object.isNull("title") ? "" : object.optString("title").trim(),
                object.isNull("first_name") ? "" : object.optString("first_name").trim());
    }

    /** Name used in greetings, e.g. "Mr Brian" — the title alone is never shown. */
    public String greetingName() {
        String first = firstName;
        if (first.isEmpty()) {
            String[] parts = fullName == null ? new String[0] : fullName.trim().split("\\s+");
            for (String part : parts) {
                if (!part.isEmpty() && !part.equalsIgnoreCase(title)) {
                    first = part;
                    break;
                }
            }
        }
        if (first.isEmpty()) {
            first = username == null ? "" : username;
        }
        if (first.isEmpty()) {
            return "";
        }
        return title.isEmpty() ? first : title + " " + first;
    }
}
