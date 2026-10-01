package co.ke.bremac.posapp.api;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

public class PosApi {
    private final ApiClient client;

    public PosApi(ApiClient client) {
        this.client = client;
    }

    public JSONObject login(String username, String password, String deviceName, String method) throws Exception {
        JSONObject body = new JSONObject()
                .put("username", username)
                .put("password", password)
                .put("device_name", deviceName);
        if (method != null) {
            body.put("otp_delivery_method", method);
        }
        return client.request("POST", "/auth/login", body, false);
    }

    public JSONObject verifyOtp(String session, String otp, String deviceName) throws Exception {
        JSONObject body = new JSONObject()
                .put("otp_session", session)
                .put("otp", otp)
                .put("device_name", deviceName);
        return client.request("POST", "/auth/otp/verify", body, false);
    }

    public JSONObject resendOtp(String session, String method) throws Exception {
        JSONObject body = new JSONObject().put("otp_session", session);
        if (method != null) {
            body.put("otp_delivery_method", method);
        }
        return client.request("POST", "/auth/otp/resend", body, false);
    }

    public JSONObject logout() throws Exception {
        return client.request("POST", "/auth/logout", new JSONObject(), true);
    }

    public JSONObject me() throws Exception {
        return client.request("GET", "/me", null, true);
    }

    /** Single-use link that signs this user into the website (e.g. the full POS screen). */
    public JSONObject webSession(String target) throws Exception {
        return client.request("POST", "/web-session", new JSONObject().put("target", target), true);
    }

    public JSONObject dashboard(String locationId, String period) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("location_id", locationId);
        params.put("period", period);
        return client.request("GET", "/dashboard" + ApiClient.q(params), null, true);
    }

    public JSONObject products(String query, String locationId, Integer contactId, int page) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("q", query);
        params.put("location_id", locationId);
        if (contactId != null) {
            params.put("contact_id", String.valueOf(contactId));
        }
        params.put("page", String.valueOf(page));
        params.put("per_page", "20");
        return client.request("GET", "/products" + ApiClient.q(params), null, true);
    }

    public JSONObject lookup(String code, String locationId, Integer contactId) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("code", code);
        params.put("location_id", locationId);
        if (contactId != null) {
            params.put("contact_id", String.valueOf(contactId));
        }
        return client.request("GET", "/products/lookup" + ApiClient.q(params), null, true);
    }

    public JSONObject customers(String query, int page) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("q", query);
        params.put("page", String.valueOf(page));
        return client.request("GET", "/customers" + ApiClient.q(params), null, true);
    }

    public JSONObject createCustomer(String name, String mobile, String email) throws Exception {
        JSONObject body = new JSONObject()
                .put("name", name)
                .put("mobile", mobile);
        if (email != null) {
            body.put("email", email);
        }
        return client.request("POST", "/customers", body, true);
    }

    public JSONObject paymentMethods(String locationId) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("location_id", locationId);
        return client.request("GET", "/payment-methods" + ApiClient.q(params), null, true);
    }

    public JSONObject openRegister(String locationId, double amount) throws Exception {
        JSONObject body = new JSONObject()
                .put("location_id", Integer.parseInt(locationId))
                .put("opening_amount", amount);
        return client.request("POST", "/cash-register/open", body, true);
    }

    public JSONObject closeRegister(double amount, String note) throws Exception {
        JSONObject body = new JSONObject()
                .put("closing_amount", amount)
                .put("closing_note", note == null ? "" : note);
        return client.request("POST", "/cash-register/close", body, true);
    }

    public JSONObject stk(String phone, double amount, String locationId) throws Exception {
        JSONObject body = new JSONObject()
                .put("phone", phone)
                .put("amount", amount)
                .put("location_id", Integer.parseInt(locationId));
        return client.request("POST", "/mpesa/stk-push", body, true);
    }

    public JSONObject mpesaStatus(String id) throws Exception {
        return client.request("GET", "/mpesa/status/" + id, null, true);
    }

    public JSONObject createSale(JSONObject body) throws Exception {
        return client.request("POST", "/sales", body, true);
    }

    public JSONObject sales(String status, String locationId, String query, int page) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("status", status);
        params.put("location_id", locationId);
        params.put("q", query);
        params.put("page", String.valueOf(page));
        return client.request("GET", "/sales" + ApiClient.q(params), null, true);
    }

    public JSONObject sale(int id) throws Exception {
        return client.request("GET", "/sales/" + id, null, true);
    }
}
