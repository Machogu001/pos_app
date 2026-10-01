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
    private static final String PROFILE = "profile_json";
    private static AppSession instance;
    private boolean profileLoaded;

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
    private long tokenSavedAt;

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
        restoreProfile();
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
        tokenSavedAt = System.currentTimeMillis();
    }

    /** True when a sign-in happened moments ago, so a 401 indicates a server setup problem. */
    public boolean tokenIsFresh() {
        return tokenSavedAt > 0 && System.currentTimeMillis() - tokenSavedAt < 120_000;
    }

    public void clearAuth() {
        tokenStore.clear();
        cart.clear();
        selectedCustomer = null;
        lastSale = null;
        profileLoaded = false;
        prefs.edit().remove(PROFILE).apply();
        user = User.fromJson(null);
        business = Business.fromJson(null);
        permissions = new Permissions(null);
        register = null;
        locations = new ArrayList<>();
        paymentMethods = new ArrayList<>();
    }

    public boolean isSignedIn() {
        return !serverUrl.isEmpty() && !token().isEmpty();
    }

    /** True once GET /me has been applied (now or in a previous app run). */
    public boolean hasProfile() {
        return profileLoaded;
    }

    public void applyMe(JSONObject data) {
        parseProfile(data);
        prefs.edit().putString(PROFILE, data.toString()).apply();
    }

    private void restoreProfile() {
        String stored = prefs.getString(PROFILE, "");
        if (stored == null || stored.isEmpty() || token().isEmpty()) {
            return;
        }
        try {
            parseProfile(new JSONObject(stored));
        } catch (Exception ignored) {
            prefs.edit().remove(PROFILE).apply();
        }
    }

    private void parseProfile(JSONObject data) {
        user = User.fromJson(data.optJSONObject("user"));
        business = Business.fromJson(data.optJSONObject("business"));
        permissions = new Permissions(data.optJSONObject("permissions"));
        register = Register.fromJson(data.optJSONObject("register"));
        locations = Json.list(data.optJSONArray("locations"), Location::fromJson);
        boolean known = false;
        for (Location location : locations) {
            known |= String.valueOf(location.id).equals(locationId);
        }
        if (!known && !locations.isEmpty()) {
            setLocationId(String.valueOf(locations.get(0).id));
        }
        profileLoaded = true;
    }

    public String selectedLocationName() {
        Location location = selectedLocation();
        return location == null ? "" : location.name;
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
