package co.ke.bremac.posapp.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** An entry of the website's sidebar menu (already filtered by the user's permissions on the server). */
public final class WebMenuItem {
    public final String title;
    /** Null for groups that only contain children. */
    public final String url;
    public final List<WebMenuItem> children;

    public WebMenuItem(String title, String url, List<WebMenuItem> children) {
        this.title = title;
        this.url = url;
        this.children = children;
    }

    public boolean isGroup() {
        return !children.isEmpty();
    }

    public static List<WebMenuItem> listFromJson(JSONArray array) {
        if (array == null) {
            return Collections.emptyList();
        }
        List<WebMenuItem> items = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) {
                continue;
            }
            String title = object.optString("title", "").trim();
            String url = object.isNull("url") ? null : object.optString("url", "").trim();
            if (url != null && url.isEmpty()) {
                url = null;
            }
            List<WebMenuItem> children = listFromJson(object.optJSONArray("children"));
            if (title.isEmpty() || (url == null && children.isEmpty())) {
                continue;
            }
            items.add(new WebMenuItem(title, url, children));
        }
        return items;
    }
}
