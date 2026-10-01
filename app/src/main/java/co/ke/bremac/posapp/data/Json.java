package co.ke.bremac.posapp.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class Json {
    private Json() {
    }

    public interface Parser<T> {
        T parse(JSONObject object);
    }

    public static <T> List<T> list(JSONArray array, Parser<T> parser) {
        List<T> items = new ArrayList<>();
        if (array == null) {
            return items;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object != null) {
                items.add(parser.parse(object));
            }
        }
        return items;
    }
}
