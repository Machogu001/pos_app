package co.ke.bremac.posapp;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import co.ke.bremac.posapp.api.ApiException;
import co.ke.bremac.posapp.ui.LoadingDialog;
import co.ke.bremac.posapp.ui.Ui;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Shared screen shell: app bar (menu or back button), slide-out navigation drawer,
 * scrollable content, optional sticky bottom bar, system-bar insets and async helpers.
 */
public abstract class BaseActivity extends AppCompatActivity {
    protected enum Chrome { DRAWER, BACK, NONE }

    private static final int SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b);

    protected AppSession session;
    protected LinearLayout content;
    private LinearLayout appBar;
    private LinearLayout actions;
    private LinearLayout bottomBar;
    private ScrollView scrollView;
    private TextView screenTitle;
    private TextView screenSubtitle;
    private String customSubtitle;
    private DrawerLayout drawerLayout;
    private NavDrawer navDrawer;
    private LoadingDialog loadingDialog;
    private ExecutorService executor;
    private boolean destroyed;
    private final List<Future<?>> futures = new ArrayList<>();

    protected interface Job {
        JSONObject run() throws Exception;
    }

    protected interface Success {
        void accept(JSONObject result) throws Exception;
    }

    /** DRAWER for top-level screens, BACK for detail screens, NONE for sign-in screens. */
    protected Chrome chrome() {
        return Chrome.DRAWER;
    }

    /** Drawer entry highlighted while this screen is visible. */
    protected NavDrawer.Item navItem() {
        return null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this,
                chrome() == Chrome.NONE
                        ? SystemBarStyle.light(Color.TRANSPARENT, SCRIM)
                        : SystemBarStyle.dark(Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, SCRIM));
        session = AppSession.get(this);
        executor = Executors.newSingleThreadExecutor();
        loadingDialog = new LoadingDialog(this);
        buildShell();
        if (chrome() != Chrome.NONE && !session.isSignedIn()) {
            goToLogin("Please sign in to continue.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshChrome();
    }

    // ---- Shell ------------------------------------------------------------------------------

    private void buildShell() {
        LinearLayout main = Ui.column(this);
        main.setBackgroundColor(Ui.CANVAS);

        if (chrome() != Chrome.NONE) {
            appBar = buildAppBar();
            main.addView(appBar, new LinearLayout.LayoutParams(-1, -2));
        }

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        content = Ui.column(this);
        content.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 24));
        scrollView.addView(content, new ScrollView.LayoutParams(-1, -2));
        main.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        bottomBar = Ui.column(this);
        bottomBar.setBackgroundColor(Ui.SURFACE);
        bottomBar.setElevation(Ui.dp(this, 12));
        bottomBar.setVisibility(View.GONE);
        main.addView(bottomBar, new LinearLayout.LayoutParams(-1, -2));

        View root = main;
        if (chrome() == Chrome.DRAWER) {
            drawerLayout = new DrawerLayout(this);
            drawerLayout.setScrimColor(Ui.withAlpha(Color.BLACK, 0.45f));
            drawerLayout.addView(main, new DrawerLayout.LayoutParams(-1, -1));
            navDrawer = new NavDrawer(this);
            int width = Math.min(Ui.dp(this, 304),
                    getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 56));
            drawerLayout.addView(navDrawer.view(),
                    new DrawerLayout.LayoutParams(width, -1, GravityCompat.START));
            installDrawerBackHandling();
            root = drawerLayout;
        }
        setContentView(root);
        applyInsets(main);
    }

    private LinearLayout buildAppBar() {
        LinearLayout bar = Ui.row(this);
        bar.setBackgroundColor(Ui.PRIMARY_DARK);
        bar.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));

        boolean drawer = chrome() == Chrome.DRAWER;
        ImageButton navigation = new ImageButton(this);
        navigation.setImageDrawable(Ui.tinted(this, drawer ? R.drawable.ic_menu : R.drawable.ic_arrow_back,
                Color.WHITE));
        navigation.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Ui.withAlpha(Color.WHITE, 0.25f)), null, null));
        navigation.setContentDescription(drawer ? "Open menu" : "Back");
        navigation.setOnClickListener(view -> {
            if (drawerLayout != null) {
                drawerLayout.openDrawer(GravityCompat.START);
            } else {
                getOnBackPressedDispatcher().onBackPressed();
            }
        });
        bar.addView(navigation, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));

        LinearLayout titles = Ui.column(this);
        titles.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        screenTitle = Ui.text(this, getString(R.string.app_name), 19, Color.WHITE, Typeface.BOLD);
        screenTitle.setSingleLine(true);
        screenTitle.setEllipsize(TextUtils.TruncateAt.END);
        screenSubtitle = Ui.text(this, "", 12, Ui.withAlpha(Color.WHITE, 0.75f), Typeface.NORMAL);
        screenSubtitle.setSingleLine(true);
        screenSubtitle.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(screenTitle);
        titles.addView(screenSubtitle);
        bar.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));

        actions = Ui.row(this);
        bar.addView(actions);
        return bar;
    }

    private void applyInsets(View main) {
        ViewCompat.setOnApplyWindowInsetsListener(main, (view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            int bottom = Math.max(bars.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
            view.setPadding(bars.left, 0, bars.right, 0);
            if (appBar != null) {
                appBar.setPadding(Ui.dp(this, 4), bars.top + Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
            }
            boolean bottomVisible = bottomBar.getVisibility() == View.VISIBLE;
            scrollView.setPadding(0, appBar == null ? bars.top : 0, 0, bottomVisible ? 0 : bottom);
            bottomBar.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12) + bottom);
            // Do not consume: the navigation drawer is a sibling and needs the same insets.
            return insets;
        });
    }

    private void installDrawerBackHandling() {
        OnBackPressedCallback closeDrawer = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                drawerLayout.closeDrawer(GravityCompat.START);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, closeDrawer);
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                closeDrawer.setEnabled(true);
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                closeDrawer.setEnabled(false);
            }
        });
    }

    protected void setScreenTitle(String title) {
        if (screenTitle != null) {
            screenTitle.setText(title);
        }
        setTitle(title);
    }

    protected void setScreenSubtitle(String subtitle) {
        customSubtitle = subtitle;
        refreshChrome();
    }

    /** Re-applies the profile (user, location, permissions) to the app bar and drawer. */
    protected void refreshChrome() {
        if (screenSubtitle != null) {
            String subtitle = customSubtitle;
            if (subtitle == null) {
                String location = session.selectedLocationName();
                subtitle = session.business.name + (location.isEmpty() ? "" : " • " + location);
            }
            screenSubtitle.setText(subtitle);
            Ui.visible(screenSubtitle, !subtitle.isEmpty());
        }
        if (navDrawer != null) {
            navDrawer.render(navItem());
        }
    }

    protected void addAppBarAction(int iconRes, String description, View.OnClickListener listener) {
        if (actions == null) {
            return;
        }
        ImageButton button = new ImageButton(this);
        button.setImageDrawable(Ui.tinted(this, iconRes, Color.WHITE));
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Ui.withAlpha(Color.WHITE, 0.25f)), null, null));
        button.setContentDescription(description);
        button.setOnClickListener(listener);
        actions.addView(button, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
    }

    /** Sticky area below the scrolling content (e.g. cart total + checkout). */
    protected LinearLayout bottomBar() {
        return bottomBar;
    }

    protected void showBottomBar(boolean visible) {
        if ((bottomBar.getVisibility() == View.VISIBLE) != visible) {
            Ui.visible(bottomBar, visible);
            ViewCompat.requestApplyInsets(bottomBar.getRootView());
        }
    }

    protected void scrollToTop() {
        scrollView.post(() -> scrollView.smoothScrollTo(0, 0));
    }

    // ---- Navigation & access ----------------------------------------------------------------

    /** Opens a drawer destination, keeping Home as the single root of the back stack. */
    void openTopLevel(Class<? extends Activity> target) {
        if (drawerLayout != null) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
        if (getClass() == target) {
            return;
        }
        Intent home = new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (target == HomeActivity.class) {
            startActivity(home);
        } else {
            startActivities(new Intent[]{home, new Intent(this, target)});
        }
        if (!(this instanceof HomeActivity)) {
            finish();
        }
    }

    /** Closes the screen when the user's role does not allow it. Returns true when allowed. */
    protected boolean ensureAccess(boolean allowed) {
        if (allowed && session.isSignedIn()) {
            return true;
        }
        if (session.isSignedIn()) {
            toast("Your account does not have permission to open this screen.");
        }
        finish();
        return false;
    }

    // ---- Async work -------------------------------------------------------------------------

    protected void runAsync(String message, Job job, Success success) {
        if (message != null && !message.isEmpty()) {
            loadingDialog.show(message);
        }
        Future<?> future = executor.submit(() -> {
            try {
                JSONObject result = job.run();
                runOnUiThreadSafe(() -> {
                    loadingDialog.dismiss();
                    try {
                        success.accept(result);
                    } catch (Exception exception) {
                        showError(exception.getMessage());
                    }
                });
            } catch (Exception exception) {
                runOnUiThreadSafe(() -> {
                    loadingDialog.dismiss();
                    handleError(exception);
                });
            }
        });
        futures.add(future);
    }

    protected void handleError(Exception exception) {
        if (exception instanceof ApiException) {
            ApiException apiException = (ApiException) exception;
            if (apiException.isUnauthenticated()) {
                expireSession(apiException.getMessage());
            } else if ("otp_expired".equals(apiException.code)) {
                goToLogin("Your verification code expired. Please sign in again.");
            } else if ("register_closed".equals(apiException.code)) {
                RegisterDialogs.openAfterClosed(this, this::retryAfterRegisterOpened);
            } else if (apiException.httpStatus == 403 && "forbidden".equals(apiException.code)) {
                showError("Your account does not have permission to do this.");
            } else {
                showError(apiException.getMessage());
            }
        } else {
            showError(exception.getMessage() == null ? "Network error. Please try again." : exception.getMessage());
        }
    }

    protected void retryAfterRegisterOpened() {
    }

    protected void expireSession() {
        expireSession(null);
    }

    protected void expireSession(String serverMessage) {
        boolean freshLogin = session.tokenIsFresh();
        session.clearAuth();
        String message;
        if (serverMessage != null && !serverMessage.isEmpty()
                && !serverMessage.toLowerCase(Locale.ROOT).startsWith("unauthenticated")) {
            message = serverMessage;
        } else if (freshLogin) {
            message = "Signed in, but the server rejected the login token. Ask the administrator to "
                    + "update the server (git pull) so the Authorization header reaches the app API.";
        } else {
            message = "Your session has expired. Please sign in again.";
        }
        goToLogin(message);
    }

    protected void goToLogin(String message) {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra("message", message);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    void confirmSignOut() {
        new AlertDialog.Builder(this)
                .setTitle("Sign out?")
                .setMessage("You will need your password to sign in again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Sign out", (dialog, which) -> signOut())
                .show();
    }

    protected void signOut() {
        runAsync("Signing out…", () -> {
            try {
                return session.api().logout();
            } catch (Exception ignored) {
                return new JSONObject().put("success", true);
            }
        }, result -> {
            session.clearAuth();
            goToLogin("You have been signed out.");
        });
    }

    protected void loadMeForRegister(Runnable afterLoad) {
        runAsync("Refreshing…", () -> session.api().me(), result -> {
            JSONObject data = result.optJSONObject("data");
            if (data != null) {
                session.applyMe(data);
            }
            refreshChrome();
            afterLoad.run();
        });
    }

    protected void runOnUiThreadSafe(Runnable runnable) {
        if (isAlive()) {
            runOnUiThread(() -> {
                if (isAlive()) {
                    runnable.run();
                }
            });
        }
    }

    protected boolean isAlive() {
        return !isFinishing() && !destroyed;
    }

    // ---- Content helpers --------------------------------------------------------------------

    protected TextView title(String text) {
        TextView view = Ui.text(this, text, 24, Ui.INK, Typeface.BOLD);
        content.addView(view, Ui.params(this, -1, -2, 4));
        return view;
    }

    protected TextView section(String text) {
        TextView view = Ui.text(this, text, 17, Ui.INK, Typeface.BOLD);
        content.addView(view, Ui.params(this, -1, -2, 22));
        return view;
    }

    /** Section title with an optional action link on the right. */
    protected LinearLayout sectionHeader(LinearLayout parent, String text, String action, Runnable onAction) {
        LinearLayout row = Ui.row(this);
        row.addView(Ui.text(this, text, 17, Ui.INK, Typeface.BOLD), new LinearLayout.LayoutParams(0, -2, 1));
        if (action != null) {
            TextView link = Ui.link(this, action, Ui.PRIMARY);
            link.setOnClickListener(view -> onAction.run());
            row.addView(link);
        }
        parent.addView(row, Ui.params(this, -1, -2, 22));
        return row;
    }

    protected TextView paragraph(String text) {
        TextView view = Ui.text(this, text, 14, Ui.MUTED, Typeface.NORMAL);
        view.setLineSpacing(Ui.dp(this, 3), 1f);
        content.addView(view, Ui.params(this, -1, -2, 8));
        return view;
    }

    protected void showError(String message) {
        if (message == null || message.isEmpty() || !isAlive()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Unable to continue")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    protected TextView banner(String message, int foreground, int background) {
        TextView view = Ui.banner(this, message, foreground, background);
        content.addView(view, Ui.params(this, -1, -2, 12));
        return view;
    }

    protected void card(String label, String value) {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.label(this, label));
        card.addView(Ui.text(this, value, 17, Ui.INK, Typeface.BOLD), Ui.params(this, -2, -2, 4));
        content.addView(card, Ui.params(this, -1, -2, 10));
    }

    protected void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        for (Future<?> future : futures) {
            future.cancel(true);
        }
        executor.shutdownNow();
        loadingDialog.dismiss();
        super.onDestroy();
    }
}
