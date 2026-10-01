package co.ke.bremac.posapp;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import co.ke.bremac.posapp.api.ApiException;
import co.ke.bremac.posapp.ui.LoadingDialog;
import co.ke.bremac.posapp.ui.Ui;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public abstract class BaseActivity extends AppCompatActivity {
    protected AppSession session;
    protected LinearLayout content;
    private LinearLayout actions;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = AppSession.get(this);
        executor = Executors.newSingleThreadExecutor();
        loadingDialog = new LoadingDialog(this);
        buildShell();
    }

    protected void buildShell() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.CANVAS);
        setContentView(root);

        LinearLayout header = Ui.row(this);
        header.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        header.setBackgroundColor(Ui.DARK);
        root.addView(header, new LinearLayout.LayoutParams(-1, Ui.dp(this, 64)));

        TextView mark = Ui.text(this, "B", 18, Color.WHITE, Typeface.BOLD);
        mark.setGravity(android.view.Gravity.CENTER);
        mark.setBackground(Ui.rounded(Ui.ACCENT, Ui.dp(this, 12)));
        header.addView(mark, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));

        TextView title = Ui.text(this, "BreMac POS", 18, Color.WHITE, Typeface.BOLD);
        title.setPadding(Ui.dp(this, 12), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        actions = Ui.row(this);
        header.addView(actions);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        content = Ui.column(this);
        content.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 24));
        scrollView.addView(content, new ScrollView.LayoutParams(-1, -2));
    }

    protected void addNav() {
        actions.removeAllViews();
        addAction("Home", HomeActivity.class);
        addAction("POS", PosActivity.class);
        addAction("Sales", SalesActivity.class);
        Button signOut = smallButton("Out");
        signOut.setOnClickListener(view -> signOut());
        actions.addView(signOut);
    }

    protected void addAction(String label, Class<?> target) {
        Button button = smallButton(label);
        button.setOnClickListener(view -> startActivity(new Intent(this, target)));
        actions.addView(button);
    }

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
                RegisterDialogs.openAfterClosed(this, () -> retryAfterRegisterOpened());
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
                && !serverMessage.toLowerCase(java.util.Locale.ROOT).startsWith("unauthenticated")) {
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
            goToLogin("Signed out.");
        });
    }

    protected void loadMeForRegister(Runnable afterLoad) {
        runAsync("Refreshing session…", () -> session.api().me(), result -> {
            JSONObject data = result.optJSONObject("data");
            if (data != null) {
                session.applyMe(data);
            }
            afterLoad.run();
        });
    }

    protected void runOnUiThreadSafe(Runnable runnable) {
        if (isAlive()) {
            runOnUiThread(runnable);
        }
    }

    protected boolean isAlive() {
        return !isFinishing() && !destroyed;
    }

    protected TextView title(String text) {
        TextView view = Ui.text(this, text, 28, Ui.INK, Typeface.BOLD);
        content.addView(view, Ui.params(this, -1, -2, 4));
        return view;
    }

    protected TextView section(String text) {
        TextView view = Ui.text(this, text, 20, Ui.INK, Typeface.BOLD);
        content.addView(view, Ui.params(this, -1, -2, 14));
        return view;
    }

    protected TextView paragraph(String text) {
        TextView view = Ui.text(this, text, 14, Ui.MUTED, Typeface.NORMAL);
        view.setLineSpacing(Ui.dp(this, 3), 1f);
        content.addView(view, Ui.params(this, -1, -2, 8));
        return view;
    }

    protected void showError(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        TextView view = Ui.text(this, message, 14, Ui.DANGER, Typeface.NORMAL);
        view.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        view.setBackground(Ui.rounded(Color.rgb(255, 238, 235), Ui.dp(this, 10)));
        content.addView(view, Ui.params(this, -1, -2, 8));
    }

    protected void card(String label, String value) {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.text(this, label, 12, Ui.MUTED, Typeface.BOLD));
        card.addView(Ui.text(this, value, 18, Ui.INK, Typeface.BOLD));
        content.addView(card, Ui.params(this, -1, -2, 8));
    }

    protected Button smallButton(String label) {
        Button button = Ui.secondary(this, label);
        button.setTextSize(12);
        button.setMinWidth(Ui.dp(this, 48));
        return button;
    }

    protected void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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
