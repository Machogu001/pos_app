package co.ke.bremac.posapp;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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

import co.ke.bremac.posapp.data.Permissions;
import co.ke.bremac.posapp.ui.Ui;

/** Slide-out menu listing only the screens the signed-in user's role allows. */
public final class NavDrawer {
    public enum Item { HOME, POS, SALES, PRODUCTS, CUSTOMERS, REGISTER }

    private final BaseActivity activity;
    private final ScrollView root;
    private final LinearLayout header;
    private final LinearLayout menu;
    private int topInset;
    private int bottomInset;
    private int startInset;

    NavDrawer(BaseActivity activity) {
        this.activity = activity;
        root = new ScrollView(activity);
        root.setBackgroundColor(Ui.SURFACE);
        root.setFillViewport(true);
        LinearLayout panel = Ui.column(activity);
        root.addView(panel, new ScrollView.LayoutParams(-1, -2));

        header = Ui.column(activity);
        header.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Ui.PRIMARY_DARK, Ui.PRIMARY}));
        panel.addView(header, new LinearLayout.LayoutParams(-1, -2));

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
        renderHeader();
        renderMenu(current);
    }

    private void applyPadding() {
        int dp20 = Ui.dp(activity, 20);
        header.setPadding(dp20 + startInset, topInset + Ui.dp(activity, 24), dp20, dp20);
        menu.setPadding(startInset, Ui.dp(activity, 10), 0, Ui.dp(activity, 16) + bottomInset);
    }

    private void renderHeader() {
        AppSession session = activity.session;
        header.removeAllViews();
        String name = session.user.fullName == null || session.user.fullName.trim().isEmpty()
                ? session.user.username
                : session.user.fullName;

        TextView avatar = Ui.avatar(activity, name, 56, Ui.PRIMARY_DARK, Color.WHITE);
        header.addView(avatar, new LinearLayout.LayoutParams(Ui.dp(activity, 56), Ui.dp(activity, 56)));

        TextView nameView = Ui.text(activity, name == null || name.isEmpty() ? "Signed in" : name, 18,
                Color.WHITE, Typeface.BOLD);
        nameView.setSingleLine(true);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(nameView, Ui.params(activity, -1, -2, 14));

        TextView business = Ui.text(activity, session.business.name, 13,
                Ui.withAlpha(Color.WHITE, 0.85f), Typeface.NORMAL);
        business.setSingleLine(true);
        business.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(business, Ui.params(activity, -1, -2, 2));

        String location = session.selectedLocationName();
        if (!location.isEmpty()) {
            LinearLayout chip = Ui.row(activity);
            chip.setPadding(Ui.dp(activity, 8), Ui.dp(activity, 4), Ui.dp(activity, 10), Ui.dp(activity, 4));
            chip.setBackground(Ui.rounded(Ui.withAlpha(Color.WHITE, 0.16f), Ui.dp(activity, 999)));
            ImageView pin = Ui.icon(activity, R.drawable.ic_location, Color.WHITE);
            chip.addView(pin, new LinearLayout.LayoutParams(Ui.dp(activity, 14), Ui.dp(activity, 14)));
            TextView text = Ui.text(activity, location, 12, Color.WHITE, Typeface.BOLD);
            text.setPadding(Ui.dp(activity, 4), 0, 0, 0);
            text.setSingleLine(true);
            chip.addView(text);
            header.addView(chip, Ui.params(activity, -2, -2, 12));
        }
    }

    private void renderMenu(Item current) {
        menu.removeAllViews();
        Permissions permissions = activity.session.permissions;

        addItem(Item.HOME, "Home", R.drawable.ic_dashboard, HomeActivity.class, current);
        if (permissions.sellCreate) {
            addItem(Item.POS, "New sale", R.drawable.ic_cart, PosActivity.class, current);
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

        View divider = Ui.divider(activity);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(activity, 1)));
        dividerParams.setMargins(Ui.dp(activity, 24), Ui.dp(activity, 10), Ui.dp(activity, 24), Ui.dp(activity, 10));
        menu.addView(divider, dividerParams);

        LinearLayout signOut = row("Sign out", R.drawable.ic_logout, Ui.DANGER, Ui.DANGER, false);
        signOut.setOnClickListener(view -> activity.confirmSignOut());
        menu.addView(signOut, rowParams());

        String host = Uri.parse(activity.session.serverUrl).getHost();
        TextView footer = Ui.text(activity, (host == null ? "" : host + " • ") + "v" + BuildConfig.VERSION_NAME,
                11, Ui.MUTED, Typeface.NORMAL);
        footer.setPadding(Ui.dp(activity, 28), Ui.dp(activity, 16), Ui.dp(activity, 16), 0);
        menu.addView(footer);
    }

    private void addItem(Item item, String label, int icon, Class<? extends Activity> target, Item current) {
        boolean selected = item == current;
        LinearLayout row = row(label, icon, selected ? Ui.PRIMARY : Ui.MUTED,
                selected ? Ui.PRIMARY_DARK : Ui.INK, selected);
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
