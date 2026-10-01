package co.ke.bremac.posapp.data;

import org.json.JSONObject;

import java.util.List;

public class Sale extends SaleSummary {
    public final List<SaleItem> items;
    public final double subtotal;
    public final double discountAmount;
    public final double taxAmount;
    public final List<SalePayment> payments;
    public final double changeReturn;
    public final String receiptUrl;
    public final String receiptText;

    public Sale(JSONObject object) {
        super(object);
        items = Json.list(object.optJSONArray("items"), SaleItem::fromJson);
        subtotal = object.optDouble("subtotal");
        discountAmount = object.optDouble("discount_amount");
        taxAmount = object.optDouble("tax_amount");
        payments = Json.list(object.optJSONArray("payments"), SalePayment::fromJson);
        changeReturn = object.optDouble("change_return");
        receiptUrl = object.optString("receipt_url");
        receiptText = object.optString("receipt_text");
    }

    public static Sale fromJson(JSONObject object) {
        return new Sale(object);
    }
}
