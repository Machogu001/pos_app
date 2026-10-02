package co.ke.bremac.posapp.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import co.ke.bremac.posapp.R;

import java.util.List;
import java.util.function.IntConsumer;

/** Shared design system: colours, typography and reusable view builders. */
public final class Ui {
    // Palette: light values by default; applyPalette() swaps them before a screen builds its views.
    public static int PRIMARY;
    /** Brand navy used for the app bar and hero backgrounds in both themes. */
    public static int PRIMARY_DARK;
    /** Strong brand colour for text and icons drawn on SURFACE / PRIMARY_SOFT. */
    public static int PRIMARY_TEXT;
    public static int PRIMARY_SOFT;
    public static int CANVAS;
    public static int SURFACE;
    public static int BORDER;
    public static int BORDER_STRONG;
    public static int TRACK;
    public static int INK;
    public static int MUTED;
    public static int SUCCESS;
    public static int SUCCESS_SOFT;
    public static int WARNING;
    public static int WARNING_SOFT;
    public static int DANGER;
    public static int DANGER_SOFT;
    public static int INFO;
    public static int INFO_SOFT;
    public static int GREEN;
    private static boolean dark;

    static {
        applyPalette(false);
    }

    /** Switches every colour above to the light or dark palette. */
    public static void applyPalette(boolean darkTheme) {
        dark = darkTheme;
        PRIMARY_DARK = Color.rgb(11, 42, 120);
        if (darkTheme) {
            PRIMARY = Color.rgb(59, 110, 230);
            PRIMARY_TEXT = Color.rgb(147, 180, 255);
            PRIMARY_SOFT = Color.rgb(30, 42, 78);
            CANVAS = Color.rgb(11, 15, 25);
            SURFACE = Color.rgb(23, 29, 42);
            BORDER = Color.rgb(41, 50, 67);
            BORDER_STRONG = Color.rgb(66, 77, 97);
            TRACK = Color.rgb(33, 41, 57);
            INK = Color.rgb(233, 237, 244);
            MUTED = Color.rgb(148, 163, 184);
            SUCCESS = Color.rgb(74, 222, 128);
            SUCCESS_SOFT = Color.rgb(20, 52, 36);
            WARNING = Color.rgb(251, 191, 36);
            WARNING_SOFT = Color.rgb(61, 45, 14);
            DANGER = Color.rgb(248, 113, 113);
            DANGER_SOFT = Color.rgb(67, 26, 30);
            INFO = Color.rgb(34, 211, 238);
            INFO_SOFT = Color.rgb(14, 50, 61);
        } else {
            PRIMARY = Color.rgb(29, 79, 196);
            PRIMARY_TEXT = PRIMARY_DARK;
            PRIMARY_SOFT = Color.rgb(226, 234, 252);
            CANVAS = Color.rgb(243, 246, 251);
            SURFACE = Color.WHITE;
            BORDER = Color.rgb(226, 232, 240);
            BORDER_STRONG = Color.rgb(203, 213, 225);
            TRACK = Color.rgb(232, 238, 242);
            INK = Color.rgb(15, 23, 42);
            MUTED = Color.rgb(100, 116, 139);
            SUCCESS = Color.rgb(21, 128, 61);
            SUCCESS_SOFT = Color.rgb(220, 252, 231);
            WARNING = Color.rgb(180, 83, 9);
            WARNING_SOFT = Color.rgb(254, 243, 199);
            DANGER = Color.rgb(220, 38, 38);
            DANGER_SOFT = Color.rgb(254, 226, 226);
            INFO = Color.rgb(8, 145, 178);
            INFO_SOFT = Color.rgb(207, 250, 254);
        }
        GREEN = SUCCESS;
    }

    public static boolean isDark() {
        return dark;
    }
    private Ui() {
    }

    // ---- Text -------------------------------------------------------------------------------

    public static TextView text(Context context, String value, int size, int color, int style) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    public static TextView label(Context context, String value) {
        TextView view = text(context, value, 12, MUTED, Typeface.BOLD);
        view.setAllCaps(true);
        view.setLetterSpacing(0.04f);
        return view;
    }

    public static TextView pill(Context context, String value, int foreground, int background) {
        TextView view = text(context, value, 11, foreground, Typeface.BOLD);
        view.setPadding(dp(context, 10), dp(context, 3), dp(context, 10), dp(context, 3));
        view.setBackground(rounded(background, dp(context, 999)));
        view.setSingleLine(true);
        return view;
    }

    /** Coloured pill for sale/payment states returned by the API. */
    public static TextView statusPill(Context context, String status) {
        String value = status == null ? "" : status.toLowerCase(java.util.Locale.ROOT);
        switch (value) {
            case "paid":
            case "final":
            case "open":
                return pill(context, capitalize(value), SUCCESS, SUCCESS_SOFT);
            case "partial":
            case "draft":
                return pill(context, capitalize(value), WARNING, WARNING_SOFT);
            case "due":
            case "closed":
                return pill(context, capitalize(value), DANGER, DANGER_SOFT);
            case "quotation":
                return pill(context, "Quotation", INFO, INFO_SOFT);
            default:
                return pill(context, value.isEmpty() ? "—" : capitalize(value), MUTED, TRACK);
        }
    }

    // ---- Buttons ----------------------------------------------------------------------------

    public static Button primary(Context context, String label) {
        return button(context, label, PRIMARY, Color.WHITE, 0);
    }

    public static Button secondary(Context context, String label) {
        return button(context, label, SURFACE, PRIMARY, BORDER_STRONG);
    }

    public static Button danger(Context context, String label) {
        return button(context, label, SURFACE, DANGER, DANGER_SOFT);
    }

    public static Button button(Context context, String label, int background, int foreground, int stroke) {
        Button button = new Button(context);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setStateListAnimator(null);
        button.setMinHeight(dp(context, 48));
        button.setMinimumHeight(dp(context, 48));
        button.setPadding(dp(context, 16), 0, dp(context, 16), 0);
        button.setTextColor(new ColorStateList(
                new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
                new int[]{BORDER_STRONG, foreground}));
        GradientDrawable shape = rounded(background, dp(context, 12));
        if (stroke != 0) {
            shape.setStroke(dp(context, 1), stroke);
        }
        int rippleColor = background == SURFACE ? withAlpha(PRIMARY, 0.12f) : withAlpha(Color.WHITE, 0.25f);
        button.setBackground(ripple(shape, rippleColor, dp(context, 12)));
        return button;
    }

    /** Small, borderless text action (e.g. "See all", "Remove"). */
    public static TextView link(Context context, String label, int color) {
        TextView view = text(context, label, 14, color, Typeface.BOLD);
        view.setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8));
        view.setBackground(ripple(null, withAlpha(color, 0.15f), dp(context, 8)));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    // ---- Inputs -----------------------------------------------------------------------------

    public static EditText input(Context context, String hint, int inputType) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setTextSize(15);
        input.setTextColor(INK);
        input.setHintTextColor(MUTED);
        input.setMinHeight(dp(context, 52));
        input.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        input.setInputType(inputType == 0 ? InputType.TYPE_CLASS_TEXT : inputType);
        input.setBackground(inputBackground(context));
        return input;
    }

    public static Drawable inputBackground(Context context) {
        GradientDrawable focused = rounded(SURFACE, dp(context, 12));
        focused.setStroke(dp(context, 2), PRIMARY);
        GradientDrawable normal = rounded(SURFACE, dp(context, 12));
        normal.setStroke(dp(context, 1), BORDER_STRONG);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        return states;
    }

    /** Label stacked above an input, returned as one view. */
    public static LinearLayout field(Context context, String label, View input) {
        LinearLayout box = column(context);
        box.addView(label(context, label));
        box.addView(input, params(context, -1, -2, 6));
        return box;
    }

    public static Spinner spinner(Context context, List<String> items, int selected) {
        Spinner spinner = new Spinner(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        if (selected >= 0 && selected < items.size()) {
            spinner.setSelection(selected);
        }
        GradientDrawable box = rounded(SURFACE, dp(context, 12));
        box.setStroke(dp(context, 1), BORDER_STRONG);
        Drawable chevron = tinted(context, R.drawable.ic_expand_more, MUTED);
        LayerDrawable background = new LayerDrawable(new Drawable[]{box, chevron});
        background.setLayerGravity(1, Gravity.END | Gravity.CENTER_VERTICAL);
        background.setLayerSize(1, dp(context, 22), dp(context, 22));
        background.setLayerInsetEnd(1, dp(context, 12));
        spinner.setBackground(background);
        spinner.setPadding(dp(context, 6), 0, dp(context, 40), 0);
        spinner.setMinimumHeight(dp(context, 50));
        return spinner;
    }

    /** Pill-style segmented control. */
    public static LinearLayout segmented(Context context, String[] labels, int selected, IntConsumer onSelect) {
        LinearLayout track = row(context);
        track.setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4));
        track.setBackground(rounded(TRACK, dp(context, 12)));
        for (int i = 0; i < labels.length; i++) {
            boolean active = i == selected;
            TextView option = text(context, labels[i], 14, active ? PRIMARY_TEXT : MUTED, Typeface.BOLD);
            option.setGravity(Gravity.CENTER);
            option.setBackground(active
                    ? rounded(SURFACE, dp(context, 9))
                    : ripple(null, withAlpha(PRIMARY, 0.12f), dp(context, 9)));
            if (active) {
                option.setElevation(dp(context, 1));
            }
            int index = i;
            option.setOnClickListener(view -> onSelect.accept(index));
            track.addView(option, new LinearLayout.LayoutParams(0, dp(context, 38), 1));
        }
        return track;
    }

    // ---- Containers -------------------------------------------------------------------------

    public static LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    public static LinearLayout column(Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    public static LinearLayout card(Context context) {
        LinearLayout card = column(context);
        card.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));
        card.setBackground(cardBackground(context));
        return card;
    }

    public static GradientDrawable cardBackground(Context context) {
        GradientDrawable drawable = rounded(SURFACE, dp(context, 16));
        drawable.setStroke(dp(context, 1), BORDER);
        return drawable;
    }

    /** Padded column used as the content of AlertDialogs. */
    public static LinearLayout dialogBox(Context context) {
        LinearLayout box = column(context);
        box.setPadding(dp(context, 24), dp(context, 8), dp(context, 24), 0);
        return box;
    }

    /** Clickable two-line list row with an optional trailing value and pill. */
    public static LinearLayout listRow(
            Context context,
            String title,
            String subtitle,
            String trailing,
            View trailingExtra) {
        LinearLayout row = row(context);
        row.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));
        row.setBackground(ripple(cardBackground(context), withAlpha(PRIMARY, 0.10f), dp(context, 16)));
        row.setMinimumHeight(dp(context, 64));

        LinearLayout left = column(context);
        TextView titleView = text(context, title, 15, INK, Typeface.BOLD);
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        left.addView(titleView);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subtitleView = text(context, subtitle, 13, MUTED, Typeface.NORMAL);
            subtitleView.setMaxLines(2);
            subtitleView.setEllipsize(TextUtils.TruncateAt.END);
            left.addView(subtitleView, params(context, -2, -2, 2));
        }
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));

        if ((trailing != null && !trailing.isEmpty()) || trailingExtra != null) {
            LinearLayout right = column(context);
            right.setGravity(Gravity.END);
            right.setPadding(dp(context, 12), 0, 0, 0);
            if (trailing != null && !trailing.isEmpty()) {
                TextView trailingView = text(context, trailing, 15, INK, Typeface.BOLD);
                trailingView.setSingleLine(true);
                right.addView(trailingView, new LinearLayout.LayoutParams(-2, -2));
            }
            if (trailingExtra != null) {
                right.addView(trailingExtra, params(context, -2, -2, 4));
            }
            row.addView(right, new LinearLayout.LayoutParams(-2, -2));
        }
        return row;
    }

    /** Label on the left, value on the right; used inside summary cards. */
    public static LinearLayout summaryRow(Context context, String label, String value, boolean emphasis) {
        LinearLayout row = row(context);
        row.setPadding(0, dp(context, 6), 0, dp(context, 6));
        row.addView(text(context, label, emphasis ? 16 : 14, emphasis ? INK : MUTED,
                emphasis ? Typeface.BOLD : Typeface.NORMAL), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(text(context, value, emphasis ? 18 : 14, INK, Typeface.BOLD));
        return row;
    }

    public static LinearLayout statTile(Context context, String label, String value, int accent) {
        LinearLayout tile = card(context);
        View bar = new View(context);
        bar.setBackground(rounded(accent, dp(context, 2)));
        tile.addView(bar, new LinearLayout.LayoutParams(dp(context, 24), dp(context, 4)));
        tile.addView(label(context, label), params(context, -2, -2, 10));
        TextView valueView = text(context, value, 18, INK, Typeface.BOLD);
        valueView.setSingleLine(true);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(valueView, params(context, -1, -2, 4));
        return tile;
    }

    /** Lays views out in a grid with equal-width columns and consistent gaps. */
    public static LinearLayout grid(Context context, List<View> cells, int columns) {
        LinearLayout grid = column(context);
        int gap = dp(context, 10);
        for (int start = 0; start < cells.size(); start += columns) {
            LinearLayout row = row(context);
            row.setGravity(Gravity.TOP);
            for (int column = 0; column < columns; column++) {
                int index = start + column;
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
                if (column > 0) {
                    params.setMarginStart(gap);
                }
                View cell = index < cells.size() ? cells.get(index) : new android.widget.Space(context);
                row.addView(cell, params);
            }
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.topMargin = start == 0 ? 0 : gap;
            grid.addView(row, rowParams);
        }
        return grid;
    }

    /** Square-ish tile with an icon bubble and a label, used for quick actions. */
    public static LinearLayout actionTile(Context context, int iconRes, String label, int accent, int accentSoft) {
        LinearLayout tile = column(context);
        tile.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16));
        tile.setBackground(ripple(cardBackground(context), withAlpha(accent, 0.12f), dp(context, 16)));
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.addView(iconBubble(context, iconRes, accent, accentSoft, 44));
        TextView text = text(context, label, 14, INK, Typeface.BOLD);
        tile.addView(text, params(context, -2, -2, 12));
        return tile;
    }

    public static ImageView icon(Context context, int iconRes, int tint) {
        ImageView view = new ImageView(context);
        view.setImageDrawable(tinted(context, iconRes, tint));
        return view;
    }

    public static View iconBubble(Context context, int iconRes, int tint, int background, int sizeDp) {
        ImageView view = icon(context, iconRes, tint);
        int padding = dp(context, sizeDp) / 4;
        view.setPadding(padding, padding, padding, padding);
        view.setBackground(rounded(background, dp(context, sizeDp)));
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp)));
        return view;
    }

    public static TextView avatar(Context context, String name, int sizeDp, int foreground, int background) {
        TextView view = text(context, initials(name), sizeDp / 3, foreground, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setBackground(rounded(background, dp(context, sizeDp)));
        return view;
    }

    public static TextView banner(Context context, String message, int foreground, int background) {
        TextView view = text(context, message, 14, foreground, Typeface.NORMAL);
        view.setLineSpacing(dp(context, 2), 1f);
        view.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        view.setBackground(rounded(background, dp(context, 12)));
        return view;
    }

    public static LinearLayout emptyState(Context context, int iconRes, String title, String message) {
        LinearLayout box = column(context);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(context, 24), dp(context, 28), dp(context, 24), dp(context, 28));
        box.setBackground(cardBackground(context));
        box.addView(iconBubble(context, iconRes, MUTED, TRACK, 52));
        TextView titleView = text(context, title, 16, INK, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        box.addView(titleView, params(context, -2, -2, 12));
        if (message != null && !message.isEmpty()) {
            TextView messageView = text(context, message, 13, MUTED, Typeface.NORMAL);
            messageView.setGravity(Gravity.CENTER);
            box.addView(messageView, params(context, -2, -2, 4));
        }
        return box;
    }

    public static View divider(Context context) {
        View view = new View(context);
        view.setBackgroundColor(BORDER);
        view.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.max(1, dp(context, 1))));
        return view;
    }

    // ---- Drawables & helpers ----------------------------------------------------------------

    public static GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    public static RippleDrawable ripple(Drawable content, int color, int radius) {
        Drawable mask = rounded(Color.WHITE, radius);
        return new RippleDrawable(ColorStateList.valueOf(color), content, mask);
    }

    public static Drawable tinted(Context context, int iconRes, int tint) {
        Drawable drawable = ContextCompat.getDrawable(context, iconRes);
        if (drawable == null) {
            return null;
        }
        drawable = drawable.mutate();
        drawable.setTint(tint);
        return drawable;
    }

    public static int withAlpha(int color, float alpha) {
        return Color.argb(Math.round(alpha * 255), Color.red(color), Color.green(color), Color.blue(color));
    }

    public static LinearLayout.LayoutParams params(Context context, int width, int heightDp, int topDp) {
        int height = heightDp < 0 ? heightDp : dp(context, heightDp);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.topMargin = dp(context, topDp);
        return params;
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static void visible(View view, boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + value.substring(1);
    }

    public static String initials(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase(java.util.Locale.ROOT);
    }
}
