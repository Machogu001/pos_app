package co.ke.bremac.posapp.data;

import org.json.JSONObject;

/**
 * What the signed-in user may do, as reported by GET /me. Everything defaults to
 * "not allowed" so features stay hidden until the server confirms access.
 */
public class Permissions {
    public final boolean isAdmin;
    public final boolean sellCreate;
    public final boolean viewSales;
    public final boolean viewProducts;
    public final boolean viewCustomers;
    public final boolean createCustomer;
    public final boolean viewDashboard;
    public final boolean closeRegister;
    public final boolean editPrice;
    public final boolean discount;

    public Permissions(JSONObject object) {
        JSONObject json = object == null ? new JSONObject() : object;
        isAdmin = json.optBoolean("is_admin", false);
        sellCreate = json.optBoolean("sell_create", false);
        viewSales = json.optBoolean("view_sales", false);
        viewProducts = json.optBoolean("view_products", false);
        viewCustomers = json.optBoolean("view_customers", false);
        createCustomer = json.optBoolean("create_customer", false);
        viewDashboard = json.optBoolean("view_dashboard", false);
        closeRegister = json.optBoolean("close_register", false);
        editPrice = json.optBoolean("edit_price", false);
        discount = json.optBoolean("discount", false);
    }

    public boolean canUseRegister() {
        return sellCreate || closeRegister;
    }
}
