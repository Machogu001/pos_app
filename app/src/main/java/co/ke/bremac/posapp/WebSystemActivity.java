package co.ke.bremac.posapp;

import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONTokener;

/**
 * Pages of the BreMac360 business system (for admins) shown as app screens: the server hides the
 * website's own header and sidebar, and its menu is listed in the app's navigation drawer.
 */
public class WebSystemActivity extends WebPosActivity {
    static final String EXTRA_URL = "web_url";

    private static final String READ_MENU = "(function(){try{return JSON.stringify(window.__bremacAppMenu||null);}"
            + "catch(e){return null;}})()";

    private String startUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        startUrl = getIntent().getStringExtra(EXTRA_URL);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected Chrome chrome() {
        return Chrome.DRAWER;
    }

    @Override
    protected String webTarget() {
        return "home";
    }

    @Override
    protected String screenName() {
        return "Business system";
    }

    @Override
    protected boolean allowed() {
        return session.permissions.isAdmin;
    }

    @Override
    protected boolean canPullToRefresh() {
        return true;
    }

    /** Page to open after signing in: the page on screen (when re-signing in) or the menu item tapped. */
    @Override
    protected String webPath() {
        String current = currentWebUrl();
        String url = current != null && isOwnServer(Uri.parse(current)) ? current : startUrl;
        if (url == null || !isOwnServer(Uri.parse(url))) {
            return null;
        }
        Uri uri = Uri.parse(url);
        String path = uri.getEncodedPath() == null ? "/" : uri.getEncodedPath();
        if (path.startsWith("/mobile/web-login") || path.equals("/login")) {
            return null;
        }
        // The server builds the redirect from its own base URL, so drop any install sub-folder.
        String base = Uri.parse(session.serverUrl).getEncodedPath();
        if (base != null) {
            base = base.replaceAll("/+$", "");
            if (!base.isEmpty() && path.startsWith(base + "/")) {
                path = path.substring(base.length());
            }
        }
        return uri.getEncodedQuery() == null ? path : path + "?" + uri.getEncodedQuery();
    }

    @Override
    protected String currentWebUrl() {
        WebView view = webView();
        String url = view == null ? null : view.getUrl();
        return url != null ? url : startUrl;
    }

    @Override
    protected void openWebPage(String url) {
        closeDrawerIfOpen();
        startUrl = url;
        loadWebPage(url);
    }

    @Override
    protected void onWebTitle(String title) {
        if (title == null) {
            return;
        }
        // Website titles look like "Page - Business name"; the business is already in the subtitle.
        String page = title.trim();
        int separator = page.lastIndexOf(" - ");
        if (separator > 0) {
            page = page.substring(0, separator).trim();
        }
        if (!page.isEmpty() && !page.startsWith("http")) {
            setScreenTitle(page);
        }
    }

    @Override
    protected void onWebPageFinished(WebView view, String url) {
        view.evaluateJavascript(READ_MENU, result -> {
            if (!isAlive()) {
                return;
            }
            JSONArray menu = parseMenu(result);
            if (menu != null && menu.length() > 0) {
                session.saveWebMenu(menu);
            }
            refreshChrome();
        });
    }

    private static JSONArray parseMenu(String result) {
        try {
            // evaluateJavascript returns the JSON encoding of the returned string.
            Object decoded = new JSONTokener(result).nextValue();
            if (!(decoded instanceof String)) {
                return null;
            }
            Object menu = new JSONTokener((String) decoded).nextValue();
            return menu instanceof JSONArray ? (JSONArray) menu : null;
        } catch (Exception exception) {
            return null;
        }
    }
}
