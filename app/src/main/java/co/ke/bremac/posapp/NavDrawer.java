package co.ke.bremac.posapp;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import co.ke.bremac.posapp.data.Permissions;
import co.ke.bremac.posapp.data.WebMenuItem;
import co.ke.bremac.posapp.ui.Ui;

/** Slide-out menu listing only the screens the signed-in user's role allows. */
public final class NavDrawer {
    public enum Item { HOME, WEB_POS, POS, SALES, PRODUCTS, CUSTOMERS, REGISTER }


    private final BaseActivity activity;
    private final ScrollView root;
    private final LinearLayout header;
    private final LinearLayout menu;
    private int topInset;
    private int bottomInset;
    private int startInset;
    private Item current;
    private String webCurrentPath;
    private final Set<String> expandedGroups = new HashSet<>();

    NavDrawer(BaseActivity activity) {
        this.activity = activity;
        root = new ScrollView(activity);
        root.setBackgroundColor(Ui.SURFACE);
        root.setFillViewport(true);
        LinearLayout panel = Ui.column(activity);
        root.addView(panel, new ScrollView.LayoutParams(-1, -2));

        header = Ui.column(activity);
        header.setBackgroundColor(Ui.SURFACE);
        panel.addView(header, new LinearLayout.LayoutParams(-1, -2));
        panel.addView(Ui.divider(activity), new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(activity, 1))));

        menu = Ui.column(activity);
        panel.addView(menu, new LinearLayout.LayoutParams(-1, -2));

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            topInset = bars.top;
            bottomInset = bars.bottom;
            startInset = bars.left;
            applyPadding();
            return insets;
        });
        applyPadding();
    }

    View view() {
        return root;
    }

    void render(Item current) {
        this.current = current;
        renderHeader();
        renderMenu(current);
    }

    /** Highlights (and expands the group of) the website page shown on the current screen. */
    private void trackWebPage(List<WebMenuItem> webMenu) {
        String path = normalizedPath(activity.currentWebUrl());
        if (path == null ? webCurrentPath == null : path.equals(webCurrentPath)) {
            return;
        }
        webCurrentPath = path;
        for (WebMenuItem item : webMenu) {
            for (WebMenuItem child : item.children) {
                if (isCurrentWebPage(child)) {
                    expandedGroups.add(item.title);
                }
            }
        }
    }

    private static String normalizedPath(String url) {
        if (url == null) {
            return null;
        }
        String path = Uri.parse(url).getPath();
        if (path == null) {
            return null;
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private boolean isCurrentWebPage(WebMenuItem item) {
        return item.url != null && webCurrentPath != null && webCurrentPath.equals(normalizedPath(item.url));
    }

    private void applyPadding() {
        int dp20 = Ui.dp(activity, 20);
        header.setPadding(dp20 + startInset, topInset + Ui.dp(activity, 18), dp20, Ui.dp(activity, 18));
        menu.setPadding(startInset, Ui.dp(activity, 10), 0, Ui.dp(activity, 16) + bottomInset);
    }

    private void renderHeader() {
        AppSession session = activity.session;
        header.removeAllViews();
        String name = session.user.fullName == null || session.user.fullName.trim().isEmpty()
                ? session.user.username
                : session.user.fullName;
        String avatarName = !session.user.title.isEmpty() && name.startsWith(session.user.title + " ")
                ? name.substring(session.user.title.length() + 1)
                : name;

        ImageView logo = new ImageView(activity);
        logo.setImageResource(R.drawable.logo_horizontal);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        logo.setContentDescription("BreMac360 POS");
        if (Ui.isDark()) {
            // The logo artwork is made for light backgrounds.
            logo.setBackground(Ui.rounded(Color.WHITE, Ui.dp(activity, 10)));
            logo.setPadding(Ui.dp(activity, 8), Ui.dp(activity, 4), Ui.dp(activity, 8), Ui.dp(activity, 4));
        }
        header.addView(logo, new LinearLayout.LayoutParams(-2, Ui.dp(activity, 44)));

        LinearLayout userRow = Ui.row(activity);
        TextView avatar = Ui.avatar(activity, avatarName, 44, Color.WHITE, Ui.PRIMARY);
        userRow.addView(avatar, new LinearLayout.LayoutParams(Ui.dp(activity, 44), Ui.dp(activity, 44)));
        LinearLayout who = Ui.column(activity);
        who.setPadding(Ui.dp(activity, 12), 0, 0, 0);
        userRow.addView(who, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(userRow, Ui.params(activity, -1, -2, 18));

        TextView nameView = Ui.text(activity, name == null || name.isEmpty() ? "Signed in" : name, 16,
                Ui.INK, Typeface.BOLD);
        nameView.setSingleLine(true);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        who.addView(nameView);

        TextView business = Ui.text(activity, session.business.name, 13, Ui.MUTED, Typeface.NORMAL);
        business.setSingleLine(true);
        business.setEllipsize(TextUtils.TruncateAt.END);
        who.addView(business, Ui.params(activity, -1, -2, 1));

        String location = session.locationFilterName();
        if (!location.isEmpty()) {
            LinearLayout chip = Ui.row(activity);
            chip.setPadding(Ui.dp(activity, 8), Ui.dp(activity, 4), Ui.dp(activity, 10), Ui.dp(activity, 4));
            chip.setBackground(Ui.rounded(Ui.PRIMARY_SOFT, Ui.dp(activity, 999)));
            ImageView pin = Ui.icon(activity, R.drawable.ic_location, Ui.PRIMARY);
            chip.addView(pin, new LinearLayout.LayoutParams(Ui.dp(activity, 14), Ui.dp(activity, 14)));
            TextView text = Ui.text(activity, location, 12, Ui.PRIMARY_TEXT, Typeface.BOLD);
            text.setPadding(Ui.dp(activity, 4), 0, 0, 0);
            text.setSingleLine(true);
            chip.addView(text);
            header.addView(chip, Ui.params(activity, -2, -2, 12));
        }
    }

    private void renderMenu(Item current) {
        menu.removeAllViews();
        Permissions permissions = activity.session.permissions;

        if (activity.session.locations.size() > 1) {
            LinearLayout location = row("Change location", R.drawable.ic_location, Ui.PRIMARY,
                    Ui.INK, false);
            location.setOnClickListener(view -> activity.showLocationDialog());
            menu.addView(location, rowParams());
        }
        addItem(Item.HOME, "Home", R.drawable.ic_dashboard, HomeActivity.class, current);
        if (permissions.sellCreate) {
            addItem(Item.WEB_POS, "POS", R.drawable.ic_pos, WebPosActivity.class, current);
            addItem(Item.POS, "Quick sale", R.drawable.ic_cart, PosActivity.class, current);
        }
        if (permissions.viewSales) {
            addItem(Item.SALES, "Sales history", R.drawable.ic_receipt, SalesActivity.class, current);
        }
        if (permissions.viewProducts) {
            addItem(Item.PRODUCTS, "Products & stock", R.drawable.ic_inventory, ProductsActivity.class, current);
        }
        if (permissions.viewCustomers) {
            addItem(Item.CUSTOMERS, "Customers", R.drawable.ic_people, CustomersActivity.class, current);
        }
        if (permissions.canUseRegister()) {
            addItem(Item.REGISTER, "Cash register", R.drawable.ic_register, RegisterActivity.class, current);
        }
        if (!activity.session.hasProfile()) {
            TextView loading = Ui.text(activity, "Loading your access…", 13, Ui.MUTED, Typeface.NORMAL);
            loading.setPadding(Ui.dp(activity, 28), Ui.dp(activity, 8), Ui.dp(activity, 16), Ui.dp(activity, 8));
            menu.addView(loading);
        }
        if (permissions.isAdmin) {
            renderWebMenu();
        }

        View divider = Ui.divider(activity);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(activity, 1)));
        dividerParams.setMargins(Ui.dp(activity, 24), Ui.dp(activity, 10), Ui.dp(activity, 24), Ui.dp(activity, 10));
        menu.addView(divider, dividerParams);

        LinearLayout appearance = row("Appearance", R.drawable.ic_theme, Ui.MUTED, Ui.INK, false);
        TextView mode = Ui.text(activity, ThemeMode.label(activity).replace("Use device theme", "Device"),
                13, Ui.MUTED, Typeface.NORMAL);
        appearance.addView(mode);
        appearance.setOnClickListener(view -> activity.showAppearanceDialog());
        menu.addView(appearance, rowParams());

        LinearLayout privacy = row("Privacy policy", R.drawable.ic_web, Ui.MUTED, Ui.INK, false);
        privacy.setOnClickListener(view -> activity.openExternalUrl(
            activity.session.serverUrl.replaceAll("/+$", "") + "/privacy-policy"));
        menu.addView(privacy, rowParams());

        LinearLayout signOut = row("Sign out", R.drawable.ic_logout, Ui.DANGER, Ui.DANGER, false);
        signOut.setOnClickListener(view -> activity.confirmSignOut());
        menu.addView(signOut, rowParams());

        String host = Uri.parse(activity.session.serverUrl).getHost();
        TextView footer = Ui.text(activity, (host == null ? "" : host + " • ") + "v" + BuildConfig.VERSION_NAME,
                11, Ui.MUTED, Typeface.NORMAL);
        footer.setPadding(Ui.dp(activity, 28), Ui.dp(activity, 16), Ui.dp(activity, 16), 0);
        menu.addView(footer);
    }

    private void renderWebMenu() {
        List<WebMenuItem> webMenu = activity.session.webMenu();
        if (webMenu.isEmpty()) {
            // Menu not loaded yet (or server not updated): offer the dashboard, which also sends the menu.
            String home = activity.session.serverUrl.replaceAll("/+$", "") + "/home";
            webMenu = Collections.singletonList(new WebMenuItem("Dashboard", home, Collections.emptyList()));
        }
        trackWebPage(webMenu);
        TextView heading = Ui.text(activity, "BUSINESS SYSTEM", 11, Ui.MUTED, Typeface.BOLD);
        heading.setLetterSpacing(0.08f);
        heading.setPadding(Ui.dp(activity, 28), Ui.dp(activity, 18), Ui.dp(activity, 16), Ui.dp(activity, 6));
        menu.addView(heading);

        for (WebMenuItem item : webMenu) {
            if (!item.isGroup()) {
                boolean selected = isCurrentWebPage(item);
                // The app already has its own "Home"; the website's is its dashboard.
                String label = item.title.equalsIgnoreCase("home") ? "Dashboard" : item.title;
                LinearLayout row = row(label, iconFor(item.title), selected ? Ui.PRIMARY : Ui.MUTED,
                        selected ? Ui.PRIMARY_TEXT : Ui.INK, selected);
                row.setOnClickListener(view -> activity.openWebPage(item.url));
                menu.addView(row, rowParams());
                continue;
            }
            boolean expanded = expandedGroups.contains(item.title);
            boolean childSelected = false;
            for (WebMenuItem child : item.children) {
                childSelected |= isCurrentWebPage(child);
            }
            LinearLayout group = row(item.title, iconFor(item.title), childSelected ? Ui.PRIMARY : Ui.MUTED,
                    childSelected ? Ui.PRIMARY_TEXT : Ui.INK, false);
            ImageView chevron = Ui.icon(activity, R.drawable.ic_expand_more, Ui.MUTED);
            chevron.setRotation(expanded ? 0f : -90f);
            group.addView(chevron, new LinearLayout.LayoutParams(Ui.dp(activity, 20), Ui.dp(activity, 20)));
            group.setOnClickListener(view -> {
                if (!expandedGroups.remove(item.title)) {
                    expandedGroups.add(item.title);
                }
                renderMenu(current);
            });
            menu.addView(group, rowParams());
            if (!expanded) {
                continue;
            }
            for (WebMenuItem child : item.children) {
                boolean selected = isCurrentWebPage(child);
                LinearLayout row = Ui.row(activity);
                row.setPadding(Ui.dp(activity, 54), 0, Ui.dp(activity, 16), 0);
                row.setBackground(selected
                        ? Ui.rounded(Ui.PRIMARY_SOFT, Ui.dp(activity, 22))
                        : Ui.ripple(null, Ui.withAlpha(Ui.PRIMARY, 0.10f), Ui.dp(activity, 22)));
                row.setClickable(true);
                row.setFocusable(true);
                TextView text = Ui.text(activity, child.title, 14, selected ? Ui.PRIMARY_TEXT : Ui.INK,
                        selected ? Typeface.BOLD : Typeface.NORMAL);
                text.setSingleLine(true);
                text.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
                row.setOnClickListener(view -> activity.openWebPage(child.url));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Ui.dp(activity, 42));
                params.setMargins(Ui.dp(activity, 12), 0, Ui.dp(activity, 12), 0);
                menu.addView(row, params);
            }
        }
    }

    /** Picks a matching app icon for a website menu entry by its (English) title. */
    private static int iconFor(String title) {
        String t = title.toLowerCase(Locale.ROOT);
        if (t.contains("home") || t.contains("dashboard")) return R.drawable.ic_dashboard;
        if (t.contains("pos")) return R.drawable.ic_pos;
        if (t.contains("report")) return R.drawable.ic_reports;
        if (t.contains("user") || t.contains("role") || t.contains("agent")) return R.drawable.ic_person;
        if (t.contains("contact") || t.contains("customer") || t.contains("supplier") || t.contains("crm")
                || t.contains("hrm") || t.contains("essential") || t.contains("employee")) return R.drawable.ic_people;
        if (t.contains("product") || t.contains("stock") || t.contains("inventory") || t.contains("manufactur"))
            return R.drawable.ic_inventory;
        if (t.contains("purchase") || t.contains("transfer") || t.contains("shipment") || t.contains("deliver"))
            return R.drawable.ic_shipping;
        if (t.contains("sell") || t.contains("sale") || t.contains("invoice") || t.contains("quotation"))
            return R.drawable.ic_receipt;
        if (t.contains("expense") || t.contains("payment") || t.contains("account") || t.contains("bank")
                || t.contains("finance")) return R.drawable.ic_wallet;
        if (t.contains("register") || t.contains("cash")) return R.drawable.ic_register;
        if (t.contains("setting") || t.contains("business") || t.contains("tax") || t.contains("printer"))
            return R.drawable.ic_tune;
        if (t.contains("subscription") || t.contains("website") || t.contains("superadmin")) return R.drawable.ic_web;
        return R.drawable.ic_folder;
    }

    private void addItem(Item item, String label, int icon, Class<? extends Activity> target, Item current) {
        boolean selected = item == current;
        LinearLayout row = row(label, icon, selected ? Ui.PRIMARY : Ui.MUTED,
                selected ? Ui.PRIMARY_TEXT : Ui.INK, selected);
        row.setOnClickListener(view -> activity.openTopLevel(target));
        menu.addView(row, rowParams());
    }

    private LinearLayout row(String label, int icon, int iconTint, int textColor, boolean selected) {
        LinearLayout row = Ui.row(activity);
        row.setPadding(Ui.dp(activity, 16), 0, Ui.dp(activity, 16), 0);
        row.setBackground(selected
                ? Ui.rounded(Ui.PRIMARY_SOFT, Ui.dp(activity, 24))
                : Ui.ripple(null, Ui.withAlpha(Ui.PRIMARY, 0.10f), Ui.dp(activity, 24)));
        row.setClickable(true);
        row.setFocusable(true);
        row.addView(Ui.icon(activity, icon, iconTint),
                new LinearLayout.LayoutParams(Ui.dp(activity, 22), Ui.dp(activity, 22)));
        TextView text = Ui.text(activity, label, 15, textColor, selected ? Typeface.BOLD : Typeface.NORMAL);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setPadding(Ui.dp(activity, 16), 0, 0, 0);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Ui.dp(activity, 50));
        params.setMargins(Ui.dp(activity, 12), Ui.dp(activity, 2), Ui.dp(activity, 12), Ui.dp(activity, 2));
        return params;
    }
}
