package co.ke.bremac.posapp;

import co.ke.bremac.posapp.cart.Cart;

import org.json.JSONArray;
import org.json.JSONObject;

final class CartStock {
    private CartStock() {
    }

    static void verify(BaseActivity activity, Runnable success) {
        try {
            String location = activity.session.locationId;
            JSONArray items = new JSONArray();
            for (Cart.Line line : activity.session.cart.lines) {
                items.put(new JSONObject().put("variation_id", line.variationId).put("quantity", line.quantity));
            }
            String snapshot = items.toString();
            activity.runAsync("Checking available stock...", () -> activity.session.api().validateStock(location, items),
                    result -> {
                        JSONArray current = new JSONArray();
                        for (Cart.Line line : activity.session.cart.lines) {
                            current.put(new JSONObject().put("variation_id", line.variationId).put("quantity", line.quantity));
                        }
                        if (!location.equals(activity.session.locationId) || !snapshot.equals(current.toString())) {
                            activity.showError("The cart or location changed. Check stock again before continuing.");
                            return;
                        }
                        JSONArray stock = result.getJSONObject("data").getJSONArray("items");
                        for (Cart.Line line : activity.session.cart.lines) {
                            boolean found = false;
                            for (int i = 0; i < stock.length(); i++) {
                                JSONObject item = stock.getJSONObject(i);
                                if (item.getInt("variation_id") == line.variationId) {
                                    line.setStock(item.getBoolean("enable_stock"), !item.isNull("stock"),
                                            item.optDouble("stock", 0));
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                activity.showError("The server did not return stock for " + line.name + ".");
                                return;
                            }
                        }
                        String warning = activity.session.cart.stockWarning();
                        if (!warning.isEmpty()) {
                            activity.showError(warning);
                            return;
                        }
                        success.run();
                    });
        } catch (org.json.JSONException exception) {
            activity.showError("Could not prepare the stock check: " + exception.getMessage());
        }
    }
}
