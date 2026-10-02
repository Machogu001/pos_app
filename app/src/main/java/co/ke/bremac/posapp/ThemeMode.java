package co.ke.bremac.posapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

/** The user's appearance choice (device / light / dark), kept separately from the sign-in session. */
public final class ThemeMode {
    public static final String SYSTEM = "system";
    public static final String LIGHT = "light";
    public static final String DARK = "dark";

    public static final String[] VALUES = {SYSTEM, LIGHT, DARK};
    public static final String[] LABELS = {"Use device theme", "Light", "Dark"};

    private static final String PREFS = "bremac_settings";
    private static final String KEY = "theme_mode";

    private ThemeMode() {
    }

    public static String get(Context context) {
        String value = prefs(context).getString(KEY, SYSTEM);
        return LIGHT.equals(value) || DARK.equals(value) ? value : SYSTEM;
    }

    public static String label(Context context) {
        String value = get(context);
        for (int i = 0; i < VALUES.length; i++) {
            if (VALUES[i].equals(value)) {
                return LABELS[i];
            }
        }
        return LABELS[0];
    }

    /** Saves the choice and applies it; open screens are recreated in the new theme. */
    public static void set(Context context, String value) {
        prefs(context).edit().putString(KEY, value).apply();
        apply(value);
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            // Lets the system launch splash screen use the same theme next time.
            android.app.UiModeManager uiModeManager = context.getSystemService(android.app.UiModeManager.class);
            if (uiModeManager != null) {
                uiModeManager.setApplicationNightMode(LIGHT.equals(value) ? android.app.UiModeManager.MODE_NIGHT_NO
                        : DARK.equals(value) ? android.app.UiModeManager.MODE_NIGHT_YES
                        : android.app.UiModeManager.MODE_NIGHT_AUTO);
            }
        }
    }

    /** Applies the saved choice; call once at app start. */
    public static void applySaved(Context context) {
        apply(get(context));
    }

    /** True when the screen is currently drawn in the dark theme. */
    public static boolean isNight(Context context) {
        int mode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private static void apply(String value) {
        int mode = LIGHT.equals(value) ? AppCompatDelegate.MODE_NIGHT_NO
                : DARK.equals(value) ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode);
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
