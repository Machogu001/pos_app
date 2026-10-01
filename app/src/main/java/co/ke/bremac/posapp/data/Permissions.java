package co.ke.bremac.posapp.data;

import org.json.JSONObject;

public class Permissions {
    public final boolean sellCreate;
    public final boolean viewSales;
    public final boolean viewProducts;
    public final boolean createCustomer;
    public final boolean viewDashboard;
    public final boolean editPrice;
    public final boolean discount;

    public Permissions(JSONObject object) {
        JSONObject json = object == null ? new JSONObject() : object;
        sellCreate = json.optBoolean("sell_create", true);
        viewSales = json.optBoolean("view_sales", true);
        viewProducts = json.optBoolean("view_products", true);
        createCustomer = json.optBoolean("create_customer", true);
        viewDashboard = json.optBoolean("view_dashboard", true);
        editPrice = json.optBoolean("edit_price", false);
        discount = json.optBoolean("discount", false);
    }
}
