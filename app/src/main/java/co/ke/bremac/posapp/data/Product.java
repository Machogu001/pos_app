package co.ke.bremac.posapp.data;

import org.json.JSONObject;

public class Product {
    public final int variationId;
    public final int productId;
    public final String name;
    public final String sku;
    public final String type;
    public final String unit;
    public final boolean enableStock;
    public final double stock;
    public final boolean stockKnown;
    public final double priceIncTax;

    public Product(JSONObject object) {
        variationId = object.optInt("variation_id");
        productId = object.optInt("product_id");
        name = object.optString("name");
        sku = object.optString("sku");
        type = object.optString("type");
        unit = object.optString("unit");
        enableStock = object.optBoolean("enable_stock");
        stockKnown = !object.isNull("stock");
        stock = object.optDouble("stock");
        priceIncTax = object.optDouble("price_inc_tax");
    }

    public static Product fromJson(JSONObject object) {
        return new Product(object);
    }

    public boolean isOutOfStock() {
        return enableStock && stock <= 0;
    }
}
