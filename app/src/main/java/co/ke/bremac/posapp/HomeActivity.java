package co.ke.bremac.posapp;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import co.ke.bremac.posapp.data.Location;
import co.ke.bremac.posapp.data.Permissions;
import co.ke.bremac.posapp.data.SaleSummary;
import co.ke.bremac.posapp.ui.Formats;
import co.ke.bremac.posapp.ui.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class HomeActivity extends BaseActivity {
    private static final String[] PERIODS = {"today", "week", "month", "custom"};
    private static final String[] PERIOD_LABELS = {"Today", "Week", "Month", "Custom"};
    private static final DateTimeFormatter RANGE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault());
    private static final String WEB_MENU_KEY = "_web_menu";

    private String period = "today";
    private LocalDate customStart;
    private LocalDate customEnd;
    private JSONObject dashboard;
    private boolean dashboardLoading;

    @Override
    protected NavDrawer.Item navItem() {
        return NavDrawer.Item.HOME;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenTitle("Home");
        render();
        loadProfile(!session.hasProfile());
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        loadProfile(false);
    }

    private void loadProfile(boolean showProgress) {
        if (!session.isSignedIn()) {
            return;
        }
        runAsync(showProgress ? "Loading your workspace…" : "", () -> {
            JSONObject me = session.api().me();
            JSONObject profile = me.optJSONObject("data");
            JSONObject permissions = profile == null ? null : profile.optJSONObject("permissions");
            if (permissions != null && permissions.optBoolean("is_admin")) {
                try {
                    // The business system menu (Purchases, Products, Reports, …) for the app's drawer.
                    JSONObject menu = session.api().webMenu().optJSONObject("data");
                    if (menu != null && menu.optJSONArray("items") != null) {
                        me.put(WEB_MENU_KEY, menu.optJSONArray("items"));
                    }
                } catch (Exception ignored) {
                    // Older server or temporary error: keep the menu remembered from before.
                }
            }
            return me;
        }, result -> {
            JSONObject data = result.optJSONObject("data");
            if (data != null) {
                session.applyMe(data);
            }
            JSONArray webMenu = result.optJSONArray(WEB_MENU_KEY);
            if (webMenu != null && webMenu.length() > 0) {
                session.saveWebMenu(webMenu);
            }
            refreshChrome();
            render();
            if (session.permissions.viewDashboard) {
                loadDashboard();
            }
        });
    }

    private void loadDashboard() {
        dashboardLoading = true;
        render();
        String startDate = "custom".equals(period) && customStart != null ? customStart.toString() : null;
        String endDate = "custom".equals(period) && customEnd != null ? customEnd.toString() : null;
        runAsync("", () -> session.api().dashboard(session.locationId, period, startDate, endDate), result -> {
            dashboardLoading = false;
            dashboard = result.optJSONObject("data");
            render();
        });
    }

    private void render() {
        content.removeAllViews();
        addGreeting();
        addLocationSelector();
        addQuickActions();
        addRegisterCard();
        if (session.permissions.viewDashboard) {
            addPerformance();
            addRecentSales();
        } else if (session.hasProfile()) {
            content.addView(Ui.banner(this, "Sales figures are hidden for your role. Use the menu to open the "
                    + "screens available to you.", Ui.MUTED, Ui.TRACK), Ui.params(this, -1, -2, 16));
        }
    }

    private void addGreeting() {
        LinearLayout hero = Ui.column(this);
        hero.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20));
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Ui.PRIMARY_DARK, Ui.PRIMARY});
        background.setCornerRadius(Ui.dp(this, 20));
        hero.setBackground(background);

        String name = session.user.greetingName();
        hero.addView(Ui.text(this, Formats.greeting() + ",", 14, Ui.withAlpha(Color.WHITE, 0.8f), Typeface.NORMAL));
        hero.addView(Ui.text(this, name == null || name.isEmpty() ? "Welcome" : name, 24, Color.WHITE,
                Typeface.BOLD), Ui.params(this, -2, -2, 2));
        hero.addView(Ui.text(this, session.business.name, 13, Ui.withAlpha(Color.WHITE, 0.8f), Typeface.NORMAL),
                Ui.params(this, -2, -2, 8));
        if (session.permissions.sellCreate) {
            Button openPos = Ui.button(this, "Open POS", Color.WHITE, Ui.PRIMARY_DARK, 0);
            openPos.setOnClickListener(view -> startActivity(new Intent(this, WebPosActivity.class)));
            hero.addView(openPos, Ui.params(this, -1, 50, 16));
        }
        content.addView(hero, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addLocationSelector() {
        if (session.locations.size() < 2) {
            return;
        }
        List<String> names = new ArrayList<>();
        int selected = 0;
        for (int i = 0; i < session.locations.size(); i++) {
            Location location = session.locations.get(i);
            names.add(location.name);
            if (String.valueOf(location.id).equals(session.locationId)) {
                selected = i;
            }
        }
        Spinner spinner = Ui.spinner(this, names, selected);
        spinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            String picked = String.valueOf(session.locations.get(position).id);
            if (picked.equals(session.locationId)) {
                return;
            }
            session.setLocationId(picked);
            session.paymentMethods = new ArrayList<>();
            refreshChrome();
            if (session.permissions.viewDashboard) {
                loadDashboard();
            }
        }));
        content.addView(Ui.field(this, "LOCATION", spinner), Ui.params(this, -1, -2, 16));
    }

    private void addQuickActions() {
        Permissions permissions = session.permissions;
        List<View> tiles = new ArrayList<>();
        if (permissions.sellCreate) {
            tiles.add(tile(R.drawable.ic_cart, "Quick sale", Ui.PRIMARY, Ui.PRIMARY_SOFT, PosActivity.class));
        }
        if (permissions.viewSales) {
            tiles.add(tile(R.drawable.ic_receipt, "Sales history", Ui.INFO, Ui.INFO_SOFT, SalesActivity.class));
        }
        if (permissions.viewProducts) {
            tiles.add(tile(R.drawable.ic_inventory, "Products", Ui.WARNING, Ui.WARNING_SOFT, ProductsActivity.class));
        }
        if (permissions.viewCustomers) {
            tiles.add(tile(R.drawable.ic_people, "Customers", Ui.SUCCESS, Ui.SUCCESS_SOFT, CustomersActivity.class));
        }
        if (tiles.isEmpty()) {
            return;
        }
        sectionHeader(content, "Quick actions", null, null);
        content.addView(Ui.grid(this, tiles, 2), Ui.params(this, -1, -2, 10));
    }

    private View tile(int icon, String label, int accent, int soft, Class<? extends Activity> target) {
        LinearLayout tile = Ui.actionTile(this, icon, label, accent, soft);
        tile.setOnClickListener(view -> startActivity(new Intent(this, target)));
        return tile;
    }

    private void addRegisterCard() {
        if (!session.permissions.canUseRegister() || !session.hasProfile()) {
            return;
        }
        boolean open = session.register != null && session.register.isOpen();
        String subtitle;
        if (open) {
            String since = Formats.time(session.register.openedAt);
            subtitle = (since.isEmpty() ? "Open" : "Open since " + since) + " • Float "
                    + session.money().format(session.register.openingAmount);
        } else {
            subtitle = "Open the register to start selling";
        }
        LinearLayout row = Ui.listRow(this, "Cash register", subtitle, null,
                Ui.statusPill(this, open ? "open" : "closed"));
        row.addView(Ui.iconBubble(this, R.drawable.ic_register, open ? Ui.SUCCESS : Ui.DANGER,
                open ? Ui.SUCCESS_SOFT : Ui.DANGER_SOFT, 40), 0);
        ((LinearLayout.LayoutParams) row.getChildAt(1).getLayoutParams()).setMarginStart(Ui.dp(this, 12));
        row.setOnClickListener(view -> startActivity(new Intent(this, RegisterActivity.class)));
        content.addView(row, Ui.params(this, -1, -2, 16));
    }

    /** Shows a From/To dialog (dates up to today) and loads performance for the chosen range. */
    private void pickCustomRange() {
        LocalDate today = LocalDate.now();
        LocalDate[] range = {
                customStart != null ? customStart : today.withDayOfMonth(1),
                customEnd != null ? customEnd : today
        };
        LinearLayout box = Ui.dialogBox(this);
        TextView fromValue = dateChoice(range[0]);
        TextView toValue = dateChoice(range[1]);
        box.addView(Ui.field(this, "From", fromValue));
        box.addView(Ui.field(this, "To", toValue), Ui.params(this, -1, -2, 12));
        fromValue.setOnClickListener(view -> pickDate(range[0], null, today, date -> {
            range[0] = date;
            if (range[1].isBefore(date)) {
                range[1] = date;
                toValue.setText(RANGE_FORMAT.format(date));
            }
            fromValue.setText(RANGE_FORMAT.format(date));
        }));
        toValue.setOnClickListener(view -> pickDate(range[1], range[0], today, date -> {
            range[1] = date;
            toValue.setText(RANGE_FORMAT.format(date));
        }));
        new AlertDialog.Builder(this)
                .setTitle("Custom date range")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", (dialog, which) -> {
                    customStart = range[0];
                    customEnd = range[1];
                    period = "custom";
                    loadDashboard();
                })
                // Closing without applying keeps the previous period highlighted.
                .setOnDismissListener(dialog -> {
                    if (!"custom".equals(period) || customStart == null) {
                        render();
                    }
                })
                .show();
    }

    private TextView dateChoice(LocalDate date) {
        TextView value = Ui.text(this, RANGE_FORMAT.format(date), 16, Ui.INK, Typeface.NORMAL);
        value.setGravity(Gravity.CENTER_VERTICAL);
        value.setMinHeight(Ui.dp(this, 48));
        value.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        value.setBackground(Ui.inputBackground(this));
        value.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null,
                Ui.tinted(this, R.drawable.ic_calendar, Ui.PRIMARY), null);
        return value;
    }

    private void pickDate(LocalDate initial, LocalDate min, LocalDate max, Consumer<LocalDate> onPicked) {
        DatePickerDialog dialog = new DatePickerDialog(this,
                (picker, year, month, day) -> onPicked.accept(LocalDate.of(year, month + 1, day)),
                initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth());
        if (min != null) {
            dialog.getDatePicker().setMinDate(epochMillis(min));
        }
        dialog.getDatePicker().setMaxDate(epochMillis(max));
        dialog.show();
    }
    private static long epochMillis(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private void addPerformance() {
        sectionHeader(content, "Performance", null, null);
        int selected = 0;
        for (int i = 0; i < PERIODS.length; i++) {
            if (PERIODS[i].equals(period)) {
                selected = i;
            }
        }
        content.addView(Ui.segmented(this, PERIOD_LABELS, selected, index -> {
            if ("custom".equals(PERIODS[index])) {
                pickCustomRange();
                return;
            }
            period = PERIODS[index];
            loadDashboard();
        }), Ui.params(this, -1, -2, 10));

        if ("custom".equals(period) && customStart != null && customEnd != null) {
            LinearLayout range = Ui.row(this);
            range.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
            range.setBackground(Ui.ripple(Ui.rounded(Ui.SURFACE, Ui.dp(this, 12)), Ui.withAlpha(Ui.PRIMARY, 0.12f), Ui.dp(this, 12)));
            TextView label = Ui.text(this, RANGE_FORMAT.format(customStart)
                    + "  \u2013  " + RANGE_FORMAT.format(customEnd), 14, Ui.INK, Typeface.BOLD);
            label.setCompoundDrawablesRelativeWithIntrinsicBounds(Ui.tinted(this, R.drawable.ic_calendar, Ui.PRIMARY), null, null, null);
            label.setCompoundDrawablePadding(Ui.dp(this, 10));
            range.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            range.addView(Ui.text(this, "Change", 14, Ui.PRIMARY, Typeface.BOLD));
            range.setOnClickListener(view -> pickCustomRange());
            content.addView(range, Ui.params(this, -1, -2, 10));
        }

        List<View> tiles = new ArrayList<>();
        tiles.add(Ui.statTile(this, "TOTAL SALES", money("total_sales"), Ui.PRIMARY));
        tiles.add(Ui.statTile(this, "TRANSACTIONS", count("sales_count"), Ui.INFO));
        tiles.add(Ui.statTile(this, "PAID", money("total_paid"), Ui.SUCCESS));
        tiles.add(Ui.statTile(this, "DUE", money("total_due"), Ui.DANGER));
        tiles.add(Ui.statTile(this, "EXPENSES", money("total_expense"), Ui.WARNING));
        tiles.add(Ui.statTile(this, "NET", money("net"), Ui.PRIMARY_TEXT));
        content.addView(Ui.grid(this, tiles, 2), Ui.params(this, -1, -2, 12));
    }

    private String money(String key) {
        if (dashboard == null) {
            return dashboardLoading ? "…" : "—";
        }
        return session.money().format(dashboard.optDouble(key, 0));
    }

    private String count(String key) {
        if (dashboard == null) {
            return dashboardLoading ? "…" : "—";
        }
        return String.valueOf(dashboard.optInt(key));
    }

    private void addRecentSales() {
        boolean canOpen = session.permissions.viewSales;
        sectionHeader(content, "Recent sales", canOpen ? "See all" : null,
                () -> startActivity(new Intent(this, SalesActivity.class)));
        JSONArray sales = dashboard == null ? null : dashboard.optJSONArray("recent_sales");
        if (sales == null || sales.length() == 0) {
            content.addView(Ui.emptyState(this, R.drawable.ic_receipt,
                    dashboardLoading ? "Loading…" : "No sales yet",
                    dashboardLoading ? null : "Sales made in this period will appear here."),
                    Ui.params(this, -1, -2, 10));
            return;
        }
        for (int i = 0; i < sales.length(); i++) {
            SaleSummary sale = SaleSummary.fromJson(sales.optJSONObject(i));
            LinearLayout row = Ui.listRow(this, sale.invoiceNo,
                    sale.customerName + " • " + Formats.dateTime(sale.transactionDate),
                    session.money().format(sale.finalTotal),
                    Ui.statusPill(this, sale.paymentStatus));
            if (canOpen) {
                row.setOnClickListener(view -> SaleDetailActivity.open(this, sale.id));
            }
            content.addView(row, Ui.params(this, -1, -2, 8));
        }
    }

    @Override
    protected boolean canPullToRefresh() {
        return true;
    }

    @Override
    protected void onPullToRefresh() {
        loadProfile(false);
    }
}
