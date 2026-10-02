package co.ke.bremac.posapp;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import co.ke.bremac.posapp.api.ApiException;
import co.ke.bremac.posapp.data.Location;
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
    private SwipeRefreshLayout refreshLayout;
    private int pendingJobs;
    private GestureDetector swipeDetector;
    private boolean leavingByBack;
    /** Last screen closed with Back; a right-to-left swipe reopens it (cleared by any other navigation). */
    private static Intent forwardIntent;

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

    /** False for screens that manage their own scrolling (e.g. a WebView filling the screen). */
    protected boolean scrollable() {
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean night = ThemeMode.isNight(this);
        Ui.applyPalette(night);
        SystemBarStyle navigationBars = night
                ? SystemBarStyle.dark(Color.TRANSPARENT)
                : SystemBarStyle.light(Color.TRANSPARENT, SCRIM);
        EdgeToEdge.enable(this,
                chrome() == Chrome.NONE && !night
                        ? SystemBarStyle.light(Color.TRANSPARENT, SCRIM)
                        : SystemBarStyle.dark(Color.TRANSPARENT),
                navigationBars);
        session = AppSession.get(this);
        executor = Executors.newSingleThreadExecutor();
        loadingDialog = new LoadingDialog(this);
        // Registered first so screen-specific back handling (drawer, web history) runs before it.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                leavingByBack = true;
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });
        buildShell();
        if (chrome() != Chrome.NONE) {
            installSwipeNavigation();
        }
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

        content = Ui.column(this);
        View body;
        if (scrollable()) {
            scrollView = new ScrollView(this);
            scrollView.setFillViewport(true);
            scrollView.setClipToPadding(false);
            content.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 24));
            scrollView.addView(content, new ScrollView.LayoutParams(-1, -2));
            body = scrollView;
        } else {
            body = content;
        }
        if (canPullToRefresh()) {
            refreshLayout = new SwipeRefreshLayout(this);
            refreshLayout.setColorSchemeColors(Ui.PRIMARY, Ui.GREEN);
            refreshLayout.addView(body, new ViewGroup.LayoutParams(-1, -1));
            refreshLayout.setOnChildScrollUpCallback((parent, child) -> contentCanScrollUp());
            refreshLayout.setOnRefreshListener(() -> {
                onPullToRefresh();
                if (pendingJobs == 0) {
                    stopRefreshing();
                }
            });
            main.addView(refreshLayout, new LinearLayout.LayoutParams(-1, 0, 1));
        } else {
            main.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        }

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
            View body = scrollView != null ? scrollView : content;
            body.setPadding(0, appBar == null ? bars.top : 0, 0, bottomVisible ? 0 : bottom);
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
            public void onDrawerSlide(View drawerView, float slideOffset) {
                // The drawer header is white in the light theme: use dark status-bar icons while it is mostly open.
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                        .setAppearanceLightStatusBars(slideOffset > 0.5f && !Ui.isDark());
            }

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
                String location = usesLocationFilter()
                        ? session.locationFilterName() : session.selectedLocationName();
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
        if (scrollView != null) {
            scrollView.post(() -> scrollView.smoothScrollTo(0, 0));
        }
    }

    // ---- Pull to refresh --------------------------------------------------------------------

    /** Screens returning true get pull-down-to-refresh, calling {@link #onPullToRefresh()}. */
    protected boolean canPullToRefresh() {
        return false;
    }

    /** Reloads the screen's data. Jobs started with runAsync keep the refresh spinner until done. */
    protected void onPullToRefresh() {
    }

    /** True while the content is not scrolled to the top (pulling down then scrolls instead). */
    protected boolean contentCanScrollUp() {
        return scrollView != null && scrollView.canScrollVertically(-1);
    }

    protected boolean isRefreshing() {
        return refreshLayout != null && refreshLayout.isRefreshing();
    }

    protected void stopRefreshing() {
        if (refreshLayout != null) {
            refreshLayout.setRefreshing(false);
        }
    }

    private void jobFinished() {
        pendingJobs = Math.max(0, pendingJobs - 1);
        if (pendingJobs == 0) {
            stopRefreshing();
        }
    }

    // ---- Swipe navigation -------------------------------------------------------------------

    /** Left-to-right fling goes back, right-to-left goes forward. */
    private void installSwipeNavigation() {
        float density = getResources().getDisplayMetrics().density;
        float minVelocity = 700 * density;
        float edge = 28 * density;
        swipeDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent start, MotionEvent end, float velocityX, float velocityY) {
                if (start == null || end == null || (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START))) {
                    return false;
                }
                int width = getWindow().getDecorView().getWidth();
                float dx = end.getRawX() - start.getRawX();
                float dy = end.getRawY() - start.getRawY();
                // Ignore edge swipes (menu drawer and system back gesture) and mostly-vertical scrolls.
                if (start.getRawX() < edge || start.getRawX() > width - edge
                        || Math.abs(dx) < width * 0.28f || Math.abs(dx) < Math.abs(dy) * 2
                        || Math.abs(velocityX) < minVelocity) {
                    return false;
                }
                int direction = dx > 0 ? -1 : 1;
                if (scrollsHorizontally(getWindow().getDecorView(), start.getRawX(), start.getRawY(), direction)
                        || blocksSwipe(start.getRawX(), start.getRawY(), direction)) {
                    return false;
                }
                if (dx > 0) {
                    onSwipeBack();
                } else {
                    onSwipeForward();
                }
                return true;
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (swipeDetector != null && swipeDetector.onTouchEvent(event)) {
            // The gesture was a navigation swipe: cancel it for the views below so it is not also a tap.
            MotionEvent cancel = MotionEvent.obtain(event);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel);
            cancel.recycle();
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    /** True when a view under the finger can itself scroll horizontally that way (e.g. a wide table). */
    private static boolean scrollsHorizontally(View view, float rawX, float rawY, int direction) {
        if (view.getVisibility() != View.VISIBLE) {
            return false;
        }
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        if (rawX < location[0] || rawX > location[0] + view.getWidth()
                || rawY < location[1] || rawY > location[1] + view.getHeight()) {
            return false;
        }
        if (view.canScrollHorizontally(direction)) {
            return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (scrollsHorizontally(group.getChildAt(i), rawX, rawY, direction)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Lets screens with their own horizontal gestures (web pages) veto swipe navigation. */
    protected boolean blocksSwipe(float rawX, float rawY, int direction) {
        return false;
    }

    protected void onSwipeBack() {
        if (this instanceof HomeActivity && drawerLayout != null) {
            // Home is the first screen: going "back" would close the app, so show the menu instead.
            drawerLayout.openDrawer(GravityCompat.START);
            return;
        }
        getOnBackPressedDispatcher().onBackPressed();
    }

    protected void onSwipeForward() {
        Intent next = forwardIntent;
        if (next == null) {
            return;
        }
        forwardIntent = null;
        super.startActivity(next);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    /** False for screens that must not be reopened by swiping forward (e.g. checkout). */
    protected boolean allowSwipeForward() {
        return chrome() != Chrome.NONE && !(this instanceof HomeActivity);
    }

    /** Closes the screen as a Back action, so it can be reopened by swiping forward. */
    protected void finishByBack() {
        leavingByBack = true;
        finish();
    }

    @Override
    public void finish() {
        if (leavingByBack && allowSwipeForward() && !isTaskRoot()) {
            forwardIntent = new Intent(getIntent()).setFlags(0);
        }
        super.finish();
    }

    @Override
    public void startActivity(Intent intent) {
        forwardIntent = null;
        super.startActivity(intent);
    }

    @Override
    public void startActivity(Intent intent, Bundle options) {
        forwardIntent = null;
        super.startActivity(intent, options);
    }

    @Override
    public void startActivities(Intent[] intents) {
        forwardIntent = null;
        super.startActivities(intents);
    }

    // ---- Navigation & access ----------------------------------------------------------------

    protected boolean usesLocationFilter() {
        return false;
    }

    void showLocationDialog() {
        List<String> names = new ArrayList<>();
        names.add("All locations (permitted locations only)");
        int selected = session.allLocationsSelected() ? 0 : -1;
        for (int i = 0; i < session.locations.size(); i++) {
            Location location = session.locations.get(i);
            names.add(location.name);
            if (!session.allLocationsSelected() && String.valueOf(location.id).equals(session.locationId)) {
                selected = i + 1;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("Business location")
                .setSingleChoiceItems(names.toArray(new String[0]), selected, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) {
                        if (!session.allLocationsSelected()) {
                            session.selectAllLocations();
                            locationSelectionChanged();
                        }
                        return;
                    }
                    String id = String.valueOf(session.locations.get(which - 1).id);
                    if (id.equals(session.locationId) && !session.allLocationsSelected()) {
                        return;
                    }
                    Runnable change = () -> {
                        session.setLocationId(id);
                        locationSelectionChanged();
                    };
                    if (!id.equals(session.locationId) && !session.cart.lines.isEmpty()) {
                        new AlertDialog.Builder(this)
                                .setTitle("Change selling location?")
                                .setMessage("Changing location will clear the current quick-sale cart so stock and "
                                        + "prices from different branches are not mixed.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Clear cart & change", (confirmation, button) -> {
                                    session.cart.clear();
                                    change.run();
                                }).show();
                    } else {
                        change.run();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void locationSelectionChanged() {
        closeDrawerIfOpen();
        refreshChrome();
        onLocationSelectionChanged();
    }

    protected void onLocationSelectionChanged() {
        openTopLevel(HomeActivity.class);
    }

    /** The slide-out menu, or null on screens without one. */
    protected NavDrawer navDrawer() {
        return navDrawer;
    }

    /** Closes the slide-out menu if it is open; returns true when it was. */
    protected boolean closeDrawerIfOpen() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }
        return false;
    }

    /** Website page shown on this screen (highlighted in the menu); null for native screens. */
    protected String currentWebUrl() {
        return null;
    }

    /** Opens a page of the business website (from the menu) as an app screen. */
    protected void openWebPage(String url) {
        closeDrawerIfOpen();
        Intent home = new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        Intent page = new Intent(this, WebSystemActivity.class).putExtra(WebSystemActivity.EXTRA_URL, url);
        startActivities(new Intent[]{home, page});
        if (!(this instanceof HomeActivity)) {
            finish();
        }
    }

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
        // While pulling to refresh, the refresh spinner replaces the blocking progress dialog.
        if (message != null && !message.isEmpty() && !isRefreshing()) {
            loadingDialog.show(message);
        }
        pendingJobs++;
        Future<?> future = executor.submit(() -> {
            try {
                JSONObject result = job.run();
                runOnUiThreadSafe(() -> {
                    loadingDialog.dismiss();
                    try {
                        success.accept(result);
                    } catch (Exception exception) {
                        showError(exception.getMessage());
                    } finally {
                        // After success, so follow-up loads started there keep the refresh spinner.
                        jobFinished();
                    }
                });
            } catch (Exception exception) {
                runOnUiThreadSafe(() -> {
                    loadingDialog.dismiss();
                    jobFinished();
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
        WebPosActivity.clearWebSession();
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

    /** Lets the user follow the device theme or force light / dark. */
    void showAppearanceDialog() {
        String current = ThemeMode.get(this);
        int selected = 0;
        for (int i = 0; i < ThemeMode.VALUES.length; i++) {
            if (ThemeMode.VALUES[i].equals(current)) {
                selected = i;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("Appearance")
                .setSingleChoiceItems(ThemeMode.LABELS, selected, (dialog, which) -> {
                    dialog.dismiss();
                    if (ThemeMode.VALUES[which].equals(current)) {
                        return;
                    }
                    // Reopen Home fresh in the new theme (an in-place recreate loses the edge-to-edge layout).
                    Intent restart = new Intent(this, HomeActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    ThemeMode.set(this, ThemeMode.VALUES[which]);
                    startActivity(restart);
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                })
                .setNegativeButton("Cancel", null)
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
            WebPosActivity.clearWebSession();
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
