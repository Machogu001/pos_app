package co.ke.bremac.posapp;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import org.json.JSONObject;

import co.ke.bremac.posapp.api.ApiException;
import co.ke.bremac.posapp.ui.Ui;

/**
 * The website's full POS screen (/pos/create) inside the app. The app swaps its API token for a
 * single-use sign-in link, so the cashier does not have to enter the password and OTP again.
 */
public class WebPosActivity extends BaseActivity {
    private static final String PRINT_BRIDGE = "BreMacPrint";
    private static final String GESTURE_BRIDGE = "BreMacGestures";
    /**
     * Reports, for the element being touched, whether it scrolls sideways (e.g. a wide table, so a
     * sideways swipe scrolls it instead of navigating) and whether its scroll area is scrolled down
     * (so pulling down scrolls the page instead of refreshing it).
     */
    private static final String GESTURE_SCRIPT = "(function(){if(window.__bremacGestures)return;"
            + "window.__bremacGestures=1;"
            + "function wide(el){for(;el&&el.nodeType===1;el=el.parentElement){var s=getComputedStyle(el);"
            + "if((s.overflowX==='auto'||s.overflowX==='scroll')&&el.scrollWidth>el.clientWidth+2)return true;}"
            + "return false;}"
            + "function down(el){var r=document.scrollingElement;if(r&&r.scrollTop>0)return true;"
            + "for(;el&&el.nodeType===1;el=el.parentElement){if(el.scrollTop>0)return true;}return false;}"
            + "document.addEventListener('touchstart',function(e){try{" + GESTURE_BRIDGE
            + ".touch(wide(e.target),down(e.target));}catch(x){}},{capture:true,passive:true});"
            + "document.addEventListener('scroll',function(e){try{var t=e.target&&e.target.nodeType===1?e.target:"
            + "document.scrollingElement;" + GESTURE_BRIDGE + ".scrolled(!!t&&t.scrollTop>0);}catch(x){}},"
            + "{capture:true,passive:true});})();";

    private volatile boolean touchInWideContent;
    private volatile boolean webScrolledDown;

    private WebView webView;
    private ProgressBar progress;
    private LinearLayout errorPanel;
    private TextView errorMessage;
    private String serverHost;
    private boolean signingIn;
    private ValueCallback<Uri[]> pendingUpload;
    private final ActivityResultLauncher<Intent> fileChooser = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (pendingUpload != null) {
                    pendingUpload.onReceiveValue(
                            WebChromeClient.FileChooserParams.parseResult(result.getResultCode(), result.getData()));
                    pendingUpload = null;
                }
            });

    /** Saves website exports (reports, PDFs, spreadsheets) to Downloads using the signed-in session. */
    /** Darkens website pages to match the app when the dark theme is on. */
    @SuppressWarnings("deprecation")
    private static void applyWebTheme(WebSettings settings) {
        boolean dark = Ui.isDark();
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            settings.setAlgorithmicDarkeningAllowed(dark);
        } else if (android.os.Build.VERSION.SDK_INT >= 29) {
            settings.setForceDark(dark ? WebSettings.FORCE_DARK_ON : WebSettings.FORCE_DARK_OFF);
        }
    }

    private void download(String url, String userAgent, String contentDisposition, String mimeType, long length) {
        Uri uri = Uri.parse(url);
        if (!isOwnServer(uri)) {
            return;
        }
        String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
        try {
            DownloadManager.Request request = new DownloadManager.Request(uri)
                    .setMimeType(mimeType)
                    .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url))
                    .addRequestHeader("User-Agent", userAgent)
                    .setTitle(fileName)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            manager.enqueue(request);
            toast("Downloading " + fileName + "…");
        } catch (RuntimeException exception) {
            toast("Couldn't download this file on this phone.");
        }
    }

    @Override
    protected Chrome chrome() {
        return Chrome.BACK;
    }

    @Override
    protected boolean scrollable() {
        return false;
    }

    /** Signs the in-app browser out of the website (called when the app signs out). */
    static void clearWebSession() {
        try {
            CookieManager cookies = CookieManager.getInstance();
            cookies.removeAllCookies(null);
            cookies.flush();
        } catch (RuntimeException ignored) {
            // WebView may be unavailable (e.g. being updated); there is then no web session to clear.
        }
    }

    /** Website destination requested from POST /web-session. */
    protected String webTarget() {
        return "pos";
    }

    protected String screenName() {
        return "POS";
    }

    /** Website page to open after signing in (null for the target's default page). */
    protected String webPath() {
        return null;
    }

    protected boolean allowed() {
        return session.permissions.sellCreate;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureAccess(allowed())) {
            return;
        }
        setScreenTitle(screenName());
        serverHost = Uri.parse(session.serverUrl).getHost();
        buildViews();
        addAppBarAction(R.drawable.ic_refresh, "Reload", view -> reload());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (closeDrawerIfOpen()) {
                    return;
                }
                if (webView != null && webView.canGoBack() && errorPanel.getVisibility() != View.VISIBLE) {
                    webView.goBack();
                } else {
                    finishByBack();
                }
            }
        });
        if (savedInstanceState != null && webView.restoreState(savedInstanceState) != null) {
            return;
        }
        openPos();
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void buildViews() {
        FrameLayout frame = new FrameLayout(this);
        content.addView(frame, new LinearLayout.LayoutParams(-1, -1));

        webView = new WebView(this);
        webView.setBackgroundColor(Ui.isDark() ? Ui.CANVAS : Color.WHITE);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        // Lets the server render its in-app layout (no website header/sidebar; menu handed to the app).
        settings.setUserAgentString(settings.getUserAgentString() + " BreMac360App/" + BuildConfig.VERSION_NAME
                + " (" + co.ke.bremac.posapp.api.ApiClient.deviceName() + ")");
        applyWebTheme(settings);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setOnLongClickListener(view -> {
            // No browser-style link/image previews on long press; text fields keep copy & paste.
            int type = webView.getHitTestResult().getType();
            return type == WebView.HitTestResult.SRC_ANCHOR_TYPE
                    || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                    || type == WebView.HitTestResult.IMAGE_TYPE;
        });
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
        webView.addJavascriptInterface(new PrintBridge(), PRINT_BRIDGE);
        webView.addJavascriptInterface(new GestureBridge(), GESTURE_BRIDGE);
        webView.setWebViewClient(new PosWebClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                Ui.visible(progress, newProgress < 100);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                onWebTitle(title);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (pendingUpload != null) {
                    pendingUpload.onReceiveValue(null);
                }
                pendingUpload = callback;
                try {
                    fileChooser.launch(params.createIntent());
                } catch (ActivityNotFoundException exception) {
                    pendingUpload = null;
                    toast("No app is available to pick a file.");
                    return false;
                }
                return true;
            }
        });
        webView.setDownloadListener(this::download);
        frame.addView(webView, new FrameLayout.LayoutParams(-1, -1));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(ColorStateList.valueOf(Ui.GREEN));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.PRIMARY_SOFT));
        frame.addView(progress, new FrameLayout.LayoutParams(-1, Ui.dp(this, 4), Gravity.TOP));

        errorPanel = Ui.column(this);
        errorPanel.setGravity(Gravity.CENTER);
        errorPanel.setBackgroundColor(Ui.CANVAS);
        int pad = Ui.dp(this, 32);
        errorPanel.setPadding(pad, pad, pad, pad);
        errorPanel.addView(Ui.iconBubble(this, R.drawable.ic_pos, Ui.PRIMARY, Ui.PRIMARY_SOFT, 64));
        TextView heading = Ui.text(this, "Couldn't open " + screenName(), 18, Ui.INK, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        errorPanel.addView(heading, Ui.params(this, -2, -2, 16));
        errorMessage = Ui.text(this, "", 14, Ui.MUTED, Typeface.NORMAL);
        errorMessage.setGravity(Gravity.CENTER);
        errorPanel.addView(errorMessage, Ui.params(this, -1, -2, 6));
        Button retry = Ui.primary(this, "Try again");
        retry.setOnClickListener(view -> openPos());
        errorPanel.addView(retry, Ui.params(this, -1, 50, 20));
        errorPanel.setVisibility(View.GONE);
        frame.addView(errorPanel, new FrameLayout.LayoutParams(-1, -1));
    }

    /** Requests a fresh single-use sign-in link and loads the POS with it. */
    protected void openPos() {
        if (signingIn) {
            return;
        }
        signingIn = true;
        Ui.visible(errorPanel, false);
        String target = webTarget();
        String path = webPath();
        runAsync("Opening " + screenName() + "…", () -> session.api().webSession(target, path), result -> {
            signingIn = false;
            JSONObject data = result.optJSONObject("data");
            String url = data == null ? "" : data.optString("url", "");
            if (!isOwnServer(Uri.parse(url))) {
                showLoadError("The server did not return a valid sign-in link. Ask the administrator to update the "
                        + "server (git pull).");
                return;
            }
            webView.loadUrl(url);
        });
    }

    protected void reload() {
        String url = webView.getUrl();
        if (url == null || url.isEmpty() || errorPanel.getVisibility() == View.VISIBLE || isLoginPage(Uri.parse(url))) {
            openPos();
        } else {
            webView.reload();
        }
    }

    @Override
    protected void handleError(Exception exception) {
        signingIn = false;
        if (exception instanceof ApiException) {
            ApiException apiException = (ApiException) exception;
            if (apiException.isUnauthenticated()) {
                super.handleError(exception);
                return;
            }
            if (apiException.httpStatus == 404) {
                showLoadError("This server does not support opening the website from the app yet. Ask the "
                        + "administrator to update the server (git pull).");
                return;
            }
            if (apiException.httpStatus == 403) {
                showLoadError("Your account does not have permission to open this.");
                return;
            }
        }
        String message = exception.getMessage();
        showLoadError(message == null || message.isEmpty()
                ? "Check your internet connection and try again." : message);
    }

    @Override
    protected boolean contentCanScrollUp() {
        return webView != null && (webView.getScrollY() > 0 || webScrolledDown);
    }

    @Override
    protected void onPullToRefresh() {
        reload();
    }

    @Override
    protected boolean blocksSwipe(float rawX, float rawY, int direction) {
        return touchInWideContent;
    }

    @Override
    protected void onSwipeBack() {
        if (webView != null && webView.canGoBack() && errorPanel.getVisibility() != View.VISIBLE) {
            webView.goBack();
        } else {
            super.onSwipeBack();
        }
    }

    @Override
    protected void onSwipeForward() {
        if (webView != null && webView.canGoForward() && errorPanel.getVisibility() != View.VISIBLE) {
            webView.goForward();
        } else {
            super.onSwipeForward();
        }
    }

    private void showLoadError(String message) {
        stopRefreshing();
        errorMessage.setText(message);
        Ui.visible(errorPanel, true);
        Ui.visible(progress, false);
    }

    protected WebView webView() {
        return webView;
    }

    /** Loads a page of the signed-in website in this screen. */
    protected void loadWebPage(String url) {
        if (webView == null || url == null || !isOwnServer(Uri.parse(url))) {
            return;
        }
        Ui.visible(errorPanel, false);
        webView.loadUrl(url);
    }

    /** Called with the website's page title. */
    protected void onWebTitle(String title) {
    }

    /** Called after a page of the website has finished loading. */
    protected void onWebPageFinished(WebView view, String url) {
    }

    protected boolean isOwnServer(Uri uri) {
        return uri != null && "https".equalsIgnoreCase(uri.getScheme())
                && serverHost != null && serverHost.equalsIgnoreCase(uri.getHost());
    }

    private static boolean isLoginPage(Uri uri) {
        String path = uri.getPath();
        return path != null && (path.equals("/login") || path.startsWith("/login/"));
    }

    private final class PosWebClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (isOwnServer(uri)) {
                return false;
            }
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException ignored) {
                toast("No app can open this link.");
            }
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            stopRefreshing();
            webScrolledDown = false;
            touchInWideContent = false;
            Uri uri = Uri.parse(url);
            if (!isOwnServer(uri)) {
                return;
            }
            if (isLoginPage(uri)) {
                // The web session ended (signed out or expired); sign in again with the app token.
                showLoadError("Your website session ended. Tap \"Try again\" to reopen it.");
                return;
            }
            // Android WebView ignores window.print(); route receipt printing to the system print dialog.
            view.evaluateJavascript("window.print=function(){" + PRINT_BRIDGE + ".print(document.title||'Receipt');};",
                    null);
            view.evaluateJavascript(GESTURE_SCRIPT, null);
            onWebPageFinished(view, url);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                showLoadError("Couldn't reach the server (" + error.getDescription() + ").");
            }
        }
    }

    private final class GestureBridge {
        @JavascriptInterface
        public void touch(boolean wideContent, boolean scrolledDown) {
            touchInWideContent = wideContent;
            webScrolledDown = scrolledDown;
        }

        @JavascriptInterface
        public void scrolled(boolean scrolledDown) {
            webScrolledDown = scrolledDown;
        }
    }

    private final class PrintBridge {
        @JavascriptInterface
        public void print(String title) {
            runOnUiThreadSafe(() -> {
                String current = webView.getUrl();
                if (current == null || !isOwnServer(Uri.parse(current))) {
                    return;
                }
                String jobName = title == null || title.trim().isEmpty() ? "Receipt" : title.trim();
                PrintManager printManager = (PrintManager) getSystemService(PRINT_SERVICE);
                if (printManager != null) {
                    printManager.print(jobName, webView.createPrintDocumentAdapter(jobName),
                            new PrintAttributes.Builder().build());
                }
            });
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    protected void onDestroy() {
        if (pendingUpload != null) {
            pendingUpload.onReceiveValue(null);
            pendingUpload = null;
        }
        if (webView != null) {
            webView.stopLoading();
            webView.removeJavascriptInterface(PRINT_BRIDGE);
            webView.removeJavascriptInterface(GESTURE_BRIDGE);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
