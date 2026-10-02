package co.ke.bremac.posapp;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import co.ke.bremac.posapp.ui.Ui;

import org.json.JSONObject;

import java.net.URISyntaxException;

public class LoginActivity extends BaseActivity {
    @Override
    protected Chrome chrome() {
        return Chrome.NONE;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        render();
    }

    private void render() {
        content.removeAllViews();
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        addBrandHeader(this, content, "Welcome back", "Sign in to your BreMac360 POS account");

        String message = getIntent().getStringExtra("message");
        if (message != null && !message.isEmpty()) {
            boolean neutral = message.startsWith("You have been signed out");
            content.addView(Ui.banner(this, message, neutral ? Ui.PRIMARY_TEXT : Ui.DANGER,
                    neutral ? Ui.PRIMARY_SOFT : Ui.DANGER_SOFT), Ui.params(this, -1, -2, 20));
        }

        LinearLayout form = Ui.card(this);
        form.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20));

        EditText server = Ui.input(this, "https://pos.yourbusiness.co.ke",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        server.setText(session.serverUrl);
        EditText username = Ui.input(this, "Username", InputType.TYPE_CLASS_TEXT);
        EditText password = Ui.input(this, "Password",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setTypeface(android.graphics.Typeface.DEFAULT);
        password.setImeOptions(EditorInfo.IME_ACTION_DONE);

        form.addView(Ui.field(this, "SERVER ADDRESS", server));
        form.addView(Ui.field(this, "USERNAME", username), Ui.params(this, -1, -2, 16));
        form.addView(Ui.field(this, "PASSWORD", password), Ui.params(this, -1, -2, 16));

        Button signIn = Ui.primary(this, "Sign in");
        form.addView(signIn, Ui.params(this, -1, 52, 22));
        content.addView(form, Ui.params(this, -1, -2, 20));

        TextView footer = Ui.text(this, "A secure HTTPS connection is required.", 12, Ui.MUTED, Typeface.NORMAL);
        footer.setGravity(Gravity.CENTER);
        content.addView(footer, Ui.params(this, -1, -2, 16));

        signIn.setOnClickListener(view -> signIn(server, username, password));
        password.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                signIn(server, username, password);
                return true;
            }
            return false;
        });
        if (!session.serverUrl.isEmpty()) {
            username.requestFocus();
        }
    }

    /** Logo, app name and subtitle shared by the sign-in and verification screens. */
    static void addBrandHeader(BaseActivity activity, LinearLayout parent, String heading, String subtitle) {
        ImageView logo = new ImageView(activity);
        logo.setImageResource(R.drawable.logo_stacked);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setContentDescription("BreMac360 POS");
        if (Ui.isDark()) {
            // The logo artwork is made for light backgrounds.
            logo.setBackground(Ui.rounded(android.graphics.Color.WHITE, Ui.dp(activity, 20)));
            logo.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 12), Ui.dp(activity, 16), Ui.dp(activity, 12));
        }
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(Ui.dp(activity, 190), -2);
        logoParams.topMargin = Ui.dp(activity, 20);
        parent.addView(logo, logoParams);

        TextView title = Ui.text(activity, heading, 24, Ui.INK, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        parent.addView(title, Ui.params(activity, -2, -2, 18));

        TextView sub = Ui.text(activity, subtitle, 14, Ui.MUTED, Typeface.NORMAL);
        sub.setGravity(Gravity.CENTER);
        parent.addView(sub, Ui.params(activity, -2, -2, 4));
    }

    private void signIn(EditText server, EditText username, EditText password) {
        if (username.getText().toString().trim().isEmpty() || password.getText().toString().isEmpty()) {
            showError("Enter your username and password.");
            return;
        }
        try {
            session.setServerUrl(ServerUrl.normalize(server.getText().toString()));
        } catch (IllegalArgumentException | URISyntaxException exception) {
            showError(exception.getMessage());
            return;
        }

        runAsync("Signing in…",
                () -> session.api().login(
                        username.getText().toString().trim(),
                        password.getText().toString(),
                        deviceName(),
                        null),
                result -> handleAuth(result.optJSONObject("data")));
    }

    private void handleAuth(JSONObject data) throws Exception {
        if (data == null) {
            showError("Invalid login response.");
            return;
        }
        if ("otp_required".equals(data.optString("status"))) {
            Intent intent = new Intent(this, OtpActivity.class);
            intent.putExtra("otp", data.toString());
            startActivity(intent);
        } else {
            session.saveToken(data.optString("token"));
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        }
    }

    static String deviceName() {
        return co.ke.bremac.posapp.api.ApiClient.deviceName();
    }
}
