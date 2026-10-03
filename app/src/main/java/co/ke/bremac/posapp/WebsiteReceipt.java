package co.ke.bremac.posapp;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.RenderProcessGoneDetail;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayInputStream;

import co.ke.bremac.posapp.ui.Ui;

final class WebsiteReceipt {
    interface Success {
        void accept(Bitmap bitmap);
    }

    private WebsiteReceipt() {
    }

    static void show(BaseActivity activity, int saleId, LinearLayout container) {
        container.removeAllViews();
        Object request = new Object();
        container.setTag(request);
        LinearLayout preview = Ui.column(activity);
        container.addView(preview, new LinearLayout.LayoutParams(-1, -2));
        android.widget.Button retry = Ui.secondary(activity, "Reload website receipt");
        retry.setOnClickListener(view -> show(activity, saleId, container));
        container.addView(retry, Ui.params(activity, -1, 48, 8));
        TextView loading = Ui.text(activity, "The website receipt will appear here.", 13, Ui.MUTED,
                android.graphics.Typeface.NORMAL);
        preview.addView(loading);
        activity.runAsync("Loading website receipt...", () -> activity.session.api().saleReceipt(saleId), result -> {
            if (container.getTag() != request || !container.isAttachedToWindow()) return;
            String html = result.getJSONObject("data").getString("html");
            preview.removeAllViews();
            Render render = new Render(activity, preview, 0, null);
            render.load(html);
        });
    }

    static void render(BaseActivity activity, int saleId, int paperMm, Success success) {
        activity.runAsync("Loading website receipt...", () -> activity.session.api().saleReceipt(saleId),
                result -> renderHtml(activity, result.getJSONObject("data").getString("html"), paperMm, success));
    }

    static void renderHtml(BaseActivity activity, String html, int paperMm, Success success) {
        int width = EscPosRaster.width(paperMm);
        FrameLayout root = activity.findViewById(android.R.id.content);
        FrameLayout holder = new FrameLayout(activity);
        holder.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        // Keep the renderer attached for WebView visual callbacks, but outside the visible viewport.
        holder.setTranslationX(-width - 16);
        root.addView(holder, new FrameLayout.LayoutParams(width, 1));
        Render render = new Render(activity, holder, width, success);
        render.load(html);
    }

    private static final class Render {
        private static final String MEASURE = "(function(){"
                + "var images=Array.from(document.images);"
                + "var links=Array.from(document.querySelectorAll('link[rel=stylesheet]'));"
                + "var broken=images.some(function(i){return i.complete&&i.naturalWidth===0;});"
                + "var css=links.every(function(l){return !!l.sheet;});"
                + "var fonts=!document.fonts||document.fonts.status==='loaded';"
                + "var ready=document.readyState==='complete'&&css&&fonts&&images.every(function(i){return i.complete;});"
                + "var h=Math.ceil(Math.max(document.body.scrollHeight,document.body.offsetHeight,"
                + "document.documentElement.scrollHeight));"
                + "var w=Math.ceil(Math.max(document.body.scrollWidth,document.documentElement.scrollWidth));"
                + "return JSON.stringify({ready:ready,broken:broken,height:h,width:w});})()";

        private final BaseActivity activity;
        private final ViewGroup holder;
        private final int rasterWidth;
        private final Success success;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final WebView web;
        private final Uri origin;
        private final LifecycleEventObserver observer;
        private final Runnable timeout;
        private final Runnable measure = this::measure;
        private volatile String assetError;
        private boolean closed;
        private boolean ready;
        private int lastHeight;
        private int stable;

        @SuppressLint("SetJavaScriptEnabled")
        @SuppressWarnings("deprecation")
        Render(BaseActivity activity, ViewGroup holder, int rasterWidth, Success success) {
            this.activity = activity;
            this.holder = holder;
            this.rasterWidth = rasterWidth;
            this.success = success;
            timeout = () -> fail("The website receipt or its assets did not finish loading. Check the server's receipt CSS, logo and fonts.");
            origin = Uri.parse(activity.session.serverUrl);
            web = new WebView(activity);
            web.setBackgroundColor(Color.WHITE);
            web.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            web.setVerticalScrollBarEnabled(false);
            web.setHorizontalScrollBarEnabled(false);
            WebSettings settings = web.getSettings();
            // Only evaluate our measurement script; server page scripts are blocked by its CSP.
            settings.setJavaScriptEnabled(true);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setDomStorageEnabled(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            settings.setSupportZoom(false);
            settings.setTextZoom(100);
            if (rasterWidth > 0) web.setInitialScale(100);
            if (Build.VERSION.SDK_INT >= 33) {
                settings.setAlgorithmicDarkeningAllowed(false);
            } else if (Build.VERSION.SDK_INT >= 29) {
                settings.setForceDark(WebSettings.FORCE_DARK_OFF);
            }
            holder.addView(web, new ViewGroup.LayoutParams(
                    rasterWidth == 0 ? ViewGroup.LayoutParams.MATCH_PARENT : rasterWidth, 1));
            web.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View view) {
                }

                @Override
                public void onViewDetachedFromWindow(View view) {
                    close();
                }
            });
            web.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (!closed && rasterWidth == 0 && ready && right - left != oldRight - oldLeft) {
                    ready = false;
                    stable = 0;
                    handler.postDelayed(timeout, 30000);
                    handler.post(measure);
                }
            });
            observer = (owner, event) -> {
                if (event == Lifecycle.Event.ON_DESTROY) close();
            };
            activity.getLifecycle().addObserver(observer);
            web.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return true;
                }

                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    Uri uri = request.getUrl();
                    if ("data".equals(uri.getScheme()) || sameOrigin(uri)) {
                        return null;
                    }
                    assetError = "The receipt contains an asset outside the trusted HTTPS website: " + uri.getHost();
                    return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
                            java.util.Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
                }

                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    if (!closed) fail("Could not load a website receipt asset: " + error.getDescription());
                }

                @Override
                public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                    if (!closed) fail("A website receipt asset returned HTTP " + response.getStatusCode() + ".");
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    if (!closed && !ready) handler.post(measure);
                }

                @Override
                public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                    fail("Android's receipt renderer stopped. Reopen the receipt and try again.");
                    return true;
                }
            });
        }

        private boolean sameOrigin(Uri uri) {
            return "https".equalsIgnoreCase(uri.getScheme())
                    && origin.getHost() != null && origin.getHost().equalsIgnoreCase(uri.getHost())
                    && port(origin) == port(uri);
        }

        private int port(Uri uri) {
            return uri.getPort() == -1 ? 443 : uri.getPort();
        }

        void load(String html) {
            if (!"https".equalsIgnoreCase(origin.getScheme()) || origin.getHost() == null || html.trim().isEmpty()) {
                fail("A valid HTTPS server and website receipt are required.");
                return;
            }
            handler.postDelayed(timeout, 30000);
            holder.post(() -> {
                if (closed) return;
                if (rasterWidth > 0) {
                    web.measure(View.MeasureSpec.makeMeasureSpec(rasterWidth, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(1, View.MeasureSpec.EXACTLY));
                    web.layout(0, 0, rasterWidth, 1);
                }
                web.loadDataWithBaseURL(activity.session.serverUrl.replaceAll("/+$", "") + "/",
                        html, "text/html", "UTF-8", null);
            });
        }

        private void measure() {
            if (closed || ready) return;
            if (assetError != null) {
                fail(assetError);
                return;
            }
            web.evaluateJavascript(MEASURE, value -> {
                if (closed || ready) return;
                try {
                    Object decoded = new JSONTokener(value).nextValue();
                    if (!(decoded instanceof String)) {
                        throw new org.json.JSONException("No receipt measurement returned.");
                    }
                    JSONObject result = new JSONObject((String) decoded);
                    if (result.getBoolean("broken")) {
                        fail("A configured receipt image could not be loaded. Printing was stopped.");
                        return;
                    }
                    if (!result.getBoolean("ready")) {
                        handler.postDelayed(measure, 150);
                        return;
                    }
                    int height = result.getInt("height");
                    stable = height == lastHeight ? stable + 1 : 0;
                    lastHeight = height;
                    if (stable < 2) {
                        handler.postDelayed(measure, 150);
                        return;
                    }
                    float scale = web.getScale();
                    int pixels = Math.max(1, (int) Math.ceil(height * scale));
                    int width = rasterWidth > 0 ? rasterWidth : web.getWidth();
                    if (result.getInt("width") * scale > width + 4) {
                        fail("The configured website receipt is wider than this view. Use 80 mm paper or adjust the website receipt layout.");
                        return;
                    }
                    if (height <= 0 || width <= 0 || pixels > 20000 || (long) width * pixels > 12_000_000) {
                        fail("This website receipt exceeds the safe rendering size. Open the document on the website instead.");
                        return;
                    }
                    ready = true;
                    web.getLayoutParams().height = pixels;
                    web.requestLayout();
                    if (rasterWidth == 0) {
                        handler.removeCallbacks(timeout);
                        return;
                    }
                    web.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(pixels, View.MeasureSpec.EXACTLY));
                    web.layout(0, 0, width, pixels);
                    web.postVisualStateCallback(1, new WebView.VisualStateCallback() {
                        @Override
                        public void onComplete(long id) {
                            if (closed) return;
                            if (!activity.isAlive()) {
                                close();
                                return;
                            }
                            Bitmap bitmap = Bitmap.createBitmap(width, pixels, Bitmap.Config.ARGB_8888);
                            bitmap.eraseColor(Color.WHITE);
                            web.draw(new Canvas(bitmap));
                            close();
                            success.accept(bitmap);
                        }
                    });
                } catch (org.json.JSONException exception) {
                    fail("Could not measure the configured website receipt: " + exception.getMessage());
                }
            });
        }

        private void fail(String message) {
            if (closed) return;
            close();
            if (rasterWidth == 0 && !activity.isDestroyed()) {
                holder.removeAllViews();
                holder.addView(Ui.text(activity, "Receipt unavailable. Reopen the receipt to retry.", 13,
                        Ui.DANGER, android.graphics.Typeface.NORMAL));
            }
            if (!activity.isDestroyed() && !activity.isFinishing()) activity.showError(message);
        }

        private void close() {
            if (closed) return;
            closed = true;
            handler.removeCallbacksAndMessages(null);
            activity.getLifecycle().removeObserver(observer);
            web.stopLoading();
            ViewGroup parent = (ViewGroup) web.getParent();
            if (parent != null) parent.removeView(web);
            web.destroy();
            if (rasterWidth > 0 && holder.getParent() instanceof ViewGroup) {
                ((ViewGroup) holder.getParent()).removeView(holder);
            }
        }
    }
}
