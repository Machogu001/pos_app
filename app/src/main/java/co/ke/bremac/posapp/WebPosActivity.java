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
                if (webView != null && webView.canGoBack() && errorPanel.getVisibility() != View.VISIBLE) {
                    webView.goBack();
                } else {
                    finish();
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
        webView.setBackgroundColor(Color.WHITE);
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
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
        webView.addJavascriptInterface(new PrintBridge(), PRINT_BRIDGE);
        webView.setWebViewClient(new PosWebClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                Ui.visible(progress, newProgress < 100);
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
        runAsync("Opening " + screenName() + "…", () -> session.api().webSession(webTarget()), result -> {
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

    private void reload() {
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

    private void showLoadError(String message) {
        errorMessage.setText(message);
        Ui.visible(errorPanel, true);
        Ui.visible(progress, false);
    }

    private boolean isOwnServer(Uri uri) {
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
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                showLoadError("Couldn't reach the server (" + error.getDescription() + ").");
            }
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
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
