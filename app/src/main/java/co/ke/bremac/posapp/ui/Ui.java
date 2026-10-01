package co.ke.bremac.posapp.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    public static final int DARK = Color.rgb(16, 47, 43);
    public static final int DARK_2 = Color.rgb(19, 62, 56);
    public static final int ACCENT = Color.rgb(17, 166, 131);
    public static final int GREEN = Color.rgb(19, 115, 91);
    public static final int CANVAS = Color.rgb(247, 249, 247);
    public static final int INK = Color.rgb(24, 39, 35);
    public static final int MUTED = Color.rgb(101, 117, 111);
    public static final int DANGER = Color.rgb(176, 52, 52);

    private Ui() {
    }

    public static TextView text(Context context, String value, int size, int color, int style) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    public static Button primary(Context context, String label) {
        return button(context, label, true);
    }

    public static Button secondary(Context context, String label) {
        return button(context, label, false);
    }

    public static Button button(Context context, String label, boolean primary) {
        Button button = new Button(context);
        button.setAllCaps(false);
        button.setText(label);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(primary ? Color.WHITE : Color.rgb(221, 241, 234));
        button.setBackground(rounded(primary ? GREEN : DARK_2, dp(context, 10)));
        button.setMinHeight(dp(context, 48));
        return button;
    }

    public static EditText input(Context context, String hint, int inputType) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setTextSize(15);
        input.setMinHeight(dp(context, 52));
        input.setSingleLine(false);
        input.setInputType(inputType == 0 ? InputType.TYPE_CLASS_TEXT : inputType);
        input.setPadding(dp(context, 12), 0, dp(context, 12), 0);
        input.setBackground(rounded(Color.WHITE, dp(context, 10)));
        return input;
    }

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
        card.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        card.setBackground(rounded(Color.WHITE, dp(context, 12)));
        return card;
    }

    public static GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
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
}
