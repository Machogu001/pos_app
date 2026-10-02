package co.ke.bremac.posapp;

import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONTokener;

import java.util.List;

import co.ke.bremac.posapp.data.WebMenuItem;

/**
 * The complete BreMac360 business system for admins, shown as part of the app: the server hides the
 * website's own header and sidebar, and its menu is listed in the app's navigation drawer.
 */
public class WebSystemActivity extends WebPosActivity {
    private static final String READ_MENU = "(function(){try{return JSON.stringify(window.__bremacAppMenu||null);}"
            + "catch(e){return null;}})()";

    private List<WebMenuItem> webMenu;

    @Override
    protected Chrome chrome() {
        return Chrome.DRAWER;
    }

    @Override
    protected NavDrawer.Item navItem() {
        return NavDrawer.Item.WEB_SYSTEM;
    }

    @Override
    protected String webTarget() {
        return "home";
    }

    @Override
    protected String screenName() {
        return "Full system";
    }

    @Override
    protected boolean allowed() {
        return session.permissions.isAdmin;
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
            List<WebMenuItem> items = parseMenu(result);
            if (items != null && !items.isEmpty()) {
                webMenu = items;
            }
            showWebMenu(url);
        });
    }

    @Override
    protected void refreshChrome() {
        super.refreshChrome();
        String url = webView() == null ? null : webView().getUrl();
        showWebMenu(url);
    }

    private void showWebMenu(String currentUrl) {
        NavDrawer drawer = navDrawer();
        if (drawer != null && webMenu != null) {
            drawer.setWebMenu(webMenu, currentUrl, this::openFromMenu);
        }
    }

    private void openFromMenu(String url) {
        closeDrawerIfOpen();
        loadWebPage(url);
    }

    private static List<WebMenuItem> parseMenu(String result) {
        try {
            // evaluateJavascript returns the JSON encoding of the returned string.
            Object decoded = new JSONTokener(result).nextValue();
            if (!(decoded instanceof String)) {
                return null;
            }
            Object menu = new JSONTokener((String) decoded).nextValue();
            return menu instanceof JSONArray ? WebMenuItem.listFromJson((JSONArray) menu) : null;
        } catch (Exception exception) {
            return null;
        }
    }
}
