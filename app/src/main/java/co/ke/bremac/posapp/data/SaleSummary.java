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
        invoiceNo = object.optString("invoice_no");
        transactionDate = object.optString("transaction_date");
        customerName = object.optString("customer_name");
        finalTotal = object.optDouble("final_total");
        totalPaid = object.optDouble("total_paid");
        paymentStatus = object.optString("payment_status");
        status = object.optString("status");
        locationName = object.optString("location_name");
    }

    public static SaleSummary fromJson(JSONObject object) {
        return new SaleSummary(object);
    }
}
