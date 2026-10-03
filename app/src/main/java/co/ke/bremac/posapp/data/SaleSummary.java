package co.ke.bremac.posapp.data;

import org.json.JSONObject;

public class SaleSummary {
    public final int id;
    public final String invoiceNo;
    public final String transactionDate;
    public final String customerName;
    public final double finalTotal;
    public final double totalPaid;
    public final String paymentStatus;
    public final String status;
    public final String locationName;

    public SaleSummary(JSONObject object) {
        id = object.optInt("id");
        invoiceNo = Json.string(object, "invoice_no");
        transactionDate = Json.string(object, "transaction_date");
        customerName = Json.string(object, "customer_name");
        finalTotal = object.optDouble("final_total");
        totalPaid = object.optDouble("total_paid");
        paymentStatus = object.optString("payment_status");
        status = object.optString("status");
        locationName = Json.string(object, "location_name");
    }

    public static SaleSummary fromJson(JSONObject object) {
        return new SaleSummary(object);
    }
}
