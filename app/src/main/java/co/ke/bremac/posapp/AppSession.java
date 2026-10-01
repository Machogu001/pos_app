package co.ke.bremac.posapp;

import android.content.Context;
import android.content.SharedPreferences;

import co.ke.bremac.posapp.api.ApiClient;
import co.ke.bremac.posapp.api.PosApi;
import co.ke.bremac.posapp.auth.TokenStore;
import co.ke.bremac.posapp.cart.Cart;
import co.ke.bremac.posapp.data.Business;
import co.ke.bremac.posapp.data.Customer;
import co.ke.bremac.posapp.data.Json;
import co.ke.bremac.posapp.data.Location;
import co.ke.bremac.posapp.data.PaymentMethod;
import co.ke.bremac.posapp.data.Permissions;
import co.ke.bremac.posapp.data.Register;
import co.ke.bremac.posapp.data.Sale;
import co.ke.bremac.posapp.data.User;
import co.ke.bremac.posapp.util.Money;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AppSession implements ApiClient.TokenProvider {
    private static final String PREFS = "pos_app";
    private static AppSession instance;

    public final Cart cart = new Cart();
    public String serverUrl = "";
    public String locationId = "";
    public Business business = Business.fromJson(null);
    public User user = User.fromJson(null);
    public Permissions permissions = new Permissions(null);
    public Register register;
    public Customer selectedCustomer;
    public List<Location> locations = new ArrayList<>();
    public List<PaymentMethod> paymentMethods = new ArrayList<>();
    public Sale lastSale;
    private SharedPreferences prefs;
    private TokenStore tokenStore;
    private PosApi api;

    public static AppSession get(Context context) {
        if (instance == null) {
            instance = new AppSession(context.getApplicationContext());
        }
        return instance;
    }

    private AppSession(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        tokenStore = new TokenStore(context);
        serverUrl = prefs.getString("server_url", "");
        locationId = prefs.getString("location_id", "");
        rebuildApi();
    }

    public PosApi api() {
        return api;
    }

    public Money money() {
        return business.currency.toMoney();
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
        prefs.edit().putString("server_url", serverUrl).apply();
        rebuildApi();
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
        prefs.edit().putString("location_id", locationId).apply();
    }

    public void saveToken(String token) throws Exception {
        tokenStore.save(token);
    }

    public void clearAuth() {
        tokenStore.clear();
        cart.clear();
        selectedCustomer = null;
    }

    public boolean isSignedIn() {
        return !serverUrl.isEmpty() && !token().isEmpty();
    }

    public void applyMe(JSONObject data) {
        user = User.fromJson(data.optJSONObject("user"));
        business = Business.fromJson(data.optJSONObject("business"));
        permissions = new Permissions(data.optJSONObject("permissions"));
        register = Register.fromJson(data.optJSONObject("register"));
        locations = Json.list(data.optJSONArray("locations"), Location::fromJson);
        if (locationId.isEmpty() && !locations.isEmpty()) {
            setLocationId(String.valueOf(locations.get(0).id));
        }
    }

    public Location selectedLocation() {
        for (Location location : locations) {
            if (String.valueOf(location.id).equals(locationId)) {
                return location;
            }
        }
        return locations.isEmpty() ? null : locations.get(0);
    }

    @Override
    public String token() {
        return tokenStore.get();
    }

    @Override
    public void onUnauthenticated() {
        clearAuth();
    }

    private void rebuildApi() {
        if (serverUrl == null || serverUrl.isEmpty()) {
            api = null;
        } else {
            api = new PosApi(new ApiClient(serverUrl, this));
        }
    }
}
