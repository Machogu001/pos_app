package co.ke.bremac.posapp;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import java.net.URISyntaxException;

public class MainActivity extends AppCompatActivity {
    private static final String PREFS = "pos_app";
    private static final String SERVER_URL = "server_url";
    private static final int INK = Color.rgb(24, 39, 35);
    private static final int MUTED = Color.rgb(101, 117, 111);
    private static final int GREEN = Color.rgb(19, 115, 91);
    private static final int CANVAS = Color.rgb(247, 249, 247);

    private LinearLayout root;
    private LinearLayout setupPanel;
    private EditText addressInput;
    private TextView setupMessage;
    private Button connectButton;
    private Button serverButton;
    private ProgressBar progressBar;
    private WebView webView;
    private String serverUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(16, 47, 43));
        window.setNavigationBarColor(Color.rgb(16, 47, 43));

        serverUrl = getSharedPreferences(PREFS, MODE_PRIVATE).getString(SERVER_URL, "");
        createLayout();
        if (serverUrl.isEmpty()) {
            showSetup("");
        } else {
            connect(serverUrl);
        }
    }

    private void createLayout() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CANVAS);
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(12), dp(16), dp(12));
        header.setBackgroundColor(Color.rgb(16, 47, 43));
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));

        TextView brandMark = text("B", 18, Color.WHITE, Typeface.BOLD);
        brandMark.setGravity(Gravity.CENTER);
        brandMark.setBackground(rounded(Color.rgb(17, 166, 131), dp(12)));
        header.addView(brandMark, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        brandText.setPadding(dp(12), 0, 0, 0);
        header.addView(brandText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        brandText.addView(text("BreMac POS", 17, Color.WHITE, Typeface.BOLD));
        TextView subtitle = text("MOBILE WORKSPACE", 10, Color.rgb(181, 208, 200), Typeface.BOLD);
        subtitle.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(3);
        brandText.addView(subtitle, subtitleParams);

        serverButton = button("Server", false);
        serverButton.setVisibility(View.GONE);
        serverButton.setOnClickListener(view -> showServerDialog());
        header.addView(serverButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(false);
        progressBar.setMax(100);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        webView.setVisibility(View.GONE);
        configureWebView();
        content.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView setupScroll = new ScrollView(this);
        setupScroll.setFillViewport(true);
        setupPanel = new LinearLayout(this);
        setupPanel.setOrientation(LinearLayout.VERTICAL);
        setupPanel.setGravity(Gravity.CENTER_VERTICAL);
        setupPanel.setPadding(dp(24), dp(32), dp(24), dp(32));
        setupPanel.setBackgroundColor(CANVAS);
        setupScroll.addView(setupPanel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(setupScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        buildSetupPanel();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(false);
        webView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if (isSameServerOrigin(uri)) {
                    return false;
                }
                if ("http".equalsIgnoreCase(scheme)) {
                    Toast.makeText(MainActivity.this,
                            "For your security, POS links must use HTTPS.", Toast.LENGTH_LONG).show();
                    return true;
                }
                openExternal(uri);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                setupPanel.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                serverButton.setVisibility(View.VISIBLE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        android.webkit.WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    progressBar.setVisibility(View.GONE);
                    showSetup("The POS website could not be reached. Check your connection and try again.");
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
            }
        });
    }

    private boolean isSameServerOrigin(Uri uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            return false;
        }
        Uri configured = Uri.parse(serverUrl);
        int configuredPort = configured.getPort() == -1 ? 443 : configured.getPort();
        int requestPort = uri.getPort() == -1 ? 443 : uri.getPort();
        return uri.getHost().equalsIgnoreCase(configured.getHost())
                && requestPort == configuredPort;
    }

    private void buildSetupPanel() {
        TextView eyebrow = text("YOUR BUSINESS, ON THE GO", 11, GREEN, Typeface.BOLD);
        eyebrow.setLetterSpacing(0.08f);
        setupPanel.addView(eyebrow);

        TextView title = text("Connect to your POS", 28, INK, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = wrapParams();
        titleParams.topMargin = dp(10);
        setupPanel.addView(title, titleParams);

        TextView description = text(
                "Enter your BreMac POS website to sign in and manage your business from your phone.",
                15, MUTED, Typeface.NORMAL);
        description.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams descriptionParams = wrapParams();
        descriptionParams.topMargin = dp(10);
        setupPanel.addView(description, descriptionParams);

        setupMessage = text("", 13, Color.rgb(166, 55, 44), Typeface.NORMAL);
        setupMessage.setVisibility(View.GONE);
        setupMessage.setPadding(dp(12), dp(10), dp(12), dp(10));
        setupMessage.setBackground(rounded(Color.rgb(255, 238, 235), dp(10)));
        LinearLayout.LayoutParams messageParams = wrapParams();
        messageParams.topMargin = dp(22);
        setupPanel.addView(setupMessage, messageParams);

        TextView label = text("POS WEBSITE ADDRESS", 11, INK, Typeface.BOLD);
        label.setLetterSpacing(0.05f);
        LinearLayout.LayoutParams labelParams = wrapParams();
        labelParams.topMargin = dp(24);
        setupPanel.addView(label, labelParams);

        addressInput = new EditText(this);
        addressInput.setSingleLine(true);
        addressInput.setTextSize(15);
        addressInput.setHint("https://pos.yourcompany.com");
        addressInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        addressInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressInput.setPadding(dp(14), 0, dp(14), 0);
        addressInput.setBackground(rounded(Color.WHITE, dp(10)));
        addressInput.setElevation(dp(1));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        inputParams.topMargin = dp(9);
        setupPanel.addView(addressInput, inputParams);

        connectButton = button("Connect to POS", true);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        buttonParams.topMargin = dp(14);
        setupPanel.addView(connectButton, buttonParams);
        connectButton.setOnClickListener(view -> connect(addressInput.getText().toString()));
        addressInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                connect(addressInput.getText().toString());
                return true;
            }
            return false;
        });

        TextView security = text("Your address is saved on this device. Secure HTTPS is required to protect your sign-in.",
                12, MUTED, Typeface.NORMAL);
        security.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams securityParams = wrapParams();
        securityParams.topMargin = dp(18);
        setupPanel.addView(security, securityParams);
    }

    private void connect(String input) {
        final String normalized;
        try {
            normalized = ServerUrl.normalize(input);
        } catch (IllegalArgumentException | URISyntaxException error) {
            showSetup(error.getMessage());
            return;
        }

        serverUrl = normalized;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(SERVER_URL, serverUrl).apply();
        addressInput.setText(serverUrl);
        setupMessage.setVisibility(View.GONE);
        serverButton.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        webView.setVisibility(View.VISIBLE);
        setupPanel.setVisibility(View.GONE);
        webView.loadUrl(serverUrl);
    }

    private void showSetup(String message) {
        serverButton.setVisibility(View.GONE);
        webView.setVisibility(View.GONE);
        setupPanel.setVisibility(View.VISIBLE);
        if (message == null || message.isEmpty()) {
            setupMessage.setVisibility(View.GONE);
        } else {
            setupMessage.setText(message);
            setupMessage.setVisibility(View.VISIBLE);
        }
    }

    private void showServerDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(serverUrl);
        input.setSelectAllOnFocus(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        input.setPadding(dp(18), dp(12), dp(18), dp(12));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("POS website")
                .setMessage("Change the website connected to this app.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Connect", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    try {
                        ServerUrl.normalize(input.getText().toString());
                        dialog.dismiss();
                        connect(input.getText().toString());
                    } catch (IllegalArgumentException | URISyntaxException error) {
                        input.setError(error.getMessage());
                    }
                }));
        dialog.show();
    }

    private void openExternal(Uri uri) {
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme)
                && !"tel".equalsIgnoreCase(scheme)
                && !"mailto".equalsIgnoreCase(scheme)) {
            Toast.makeText(this, "This link cannot be opened securely.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "No app is available to open this link.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.getVisibility() == View.VISIBLE && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }

    private Button button(String label, boolean primary) {
        Button result = new Button(this);
        result.setText(label);
        result.setTextSize(14);
        result.setAllCaps(false);
        result.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        result.setTextColor(primary ? Color.WHITE : Color.rgb(211, 234, 226));
        result.setPadding(dp(16), 0, dp(16), 0);
        result.setBackground(rounded(primary ? GREEN : Color.rgb(30, 72, 64), dp(10)));
        return result;
    }

    private TextView text(String value, int size, int color, int typeface) {
        TextView result = new TextView(this);
        result.setText(value);
        result.setTextSize(size);
        result.setTextColor(color);
        result.setTypeface(Typeface.DEFAULT, typeface);
        return result;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private LinearLayout.LayoutParams wrapParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
