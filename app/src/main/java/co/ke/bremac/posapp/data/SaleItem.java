package co.ke.bremac.posapp.data;

import org.json.JSONObject;

public class SaleItem {
    public final String name;
    public final String sku;
    public final double quantity;
    public final String unit;
    public final double unitPriceIncTax;
    public final double lineTotal;

    public SaleItem(JSONObject object) {
        name = Json.string(object, "name");
        sku = Json.string(object, "sku");
        quantity = object.optDouble("quantity");
        unit = Json.string(object, "unit");
        unitPriceIncTax = object.optDouble("unit_price_inc_tax");
        lineTotal = object.optDouble("line_total");
    }

    public static SaleItem fromJson(JSONObject object) {
        return new SaleItem(object);
    }
}
