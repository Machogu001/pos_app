package co.ke.bremac.posapp;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import co.ke.bremac.posapp.ui.Ui;

import org.json.JSONObject;

import java.net.URISyntaxException;

public class LoginActivity extends BaseActivity {
    private boolean editingServer;

    @Override
    protected Chrome chrome() {
        return Chrome.NONE;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        editingServer = session.serverUrl.isEmpty();
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
        content.addView(form, Ui.params(this, -1, -2, 20));

        if (editingServer) {
            renderServerEditor(form);
        } else {
            renderSignInForm(form);
        }

        TextView footer = Ui.text(this, "A secure HTTPS connection is required.", 12, Ui.MUTED, Typeface.NORMAL);
        footer.setGravity(Gravity.CENTER);
        content.addView(footer, Ui.params(this, -1, -2, 16));

        if (!session.serverUrl.isEmpty()) {
            TextView privacy = Ui.link(this, "Privacy Policy", Ui.PRIMARY_TEXT);
            privacy.setGravity(Gravity.CENTER);
            privacy.setOnClickListener(view -> openExternalUrl(
                    session.serverUrl.replaceAll("/+$", "") + "/privacy-policy"));
            content.addView(privacy, Ui.params(this, -1, -2, 4));
        }
    }

    /** Server address step: shown on first use, or after tapping "Edit". */
    private void renderServerEditor(LinearLayout form) {
        EditText server = Ui.input(this, "https://pos.yourbusiness.co.ke",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        server.setText(session.serverUrl);
        server.setSelection(server.getText().length());
        server.setImeOptions(EditorInfo.IME_ACTION_DONE);
        form.addView(Ui.field(this, "SERVER ADDRESS", server));
        TextView hint = Ui.text(this, "Enter your business system's web address. You only need to do this once.",
                13, Ui.MUTED, Typeface.NORMAL);
        form.addView(hint, Ui.params(this, -1, -2, 8));

        LinearLayout buttons = Ui.row(this);
        boolean hasSaved = !session.serverUrl.isEmpty();
        if (hasSaved) {
            Button cancel = Ui.secondary(this, "Cancel");
            cancel.setOnClickListener(view -> {
                editingServer = false;
                render();
            });
            LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1);
            cancelParams.setMarginEnd(Ui.dp(this, 12));
            buttons.addView(cancel, cancelParams);
        }
        Button save = Ui.primary(this, "Save");
        buttons.addView(save, new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1));
        form.addView(buttons, Ui.params(this, -1, -2, 20));

        save.setOnClickListener(view -> saveServer(server));
        server.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveServer(server);
                return true;
            }
            return false;
        });
        server.requestFocus();
    }

    private void saveServer(EditText server) {
        String normalized;
        try {
            normalized = ServerUrl.normalize(server.getText().toString());
        } catch (IllegalArgumentException | URISyntaxException exception) {
            showError(exception.getMessage());
            return;
        }
        if (!normalized.equals(session.serverUrl)) {
            // A business location from another server would not exist on the new one.
            session.setLocationId("");
        }
        session.setServerUrl(normalized);
        editingServer = false;
        render();
    }

    private void renderSignInForm(LinearLayout form) {
        LinearLayout serverRow = Ui.row(this);
        serverRow.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10));
        serverRow.setBackground(Ui.rounded(Ui.PRIMARY_SOFT, Ui.dp(this, 12)));
        serverRow.addView(Ui.icon(this, R.drawable.ic_web, Ui.PRIMARY_TEXT),
                new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20)));
        LinearLayout serverText = Ui.column(this);
        serverText.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 8), 0);
        serverText.addView(Ui.text(this, "SERVER", 11, Ui.MUTED, Typeface.BOLD));
        Uri serverUri = Uri.parse(session.serverUrl);
        String host = serverUri.getHost() == null ? null
                : serverUri.getHost() + (serverUri.getPort() > 0 ? ":" + serverUri.getPort() : "");
        TextView hostView = Ui.text(this, host == null ? session.serverUrl : host, 14, Ui.INK, Typeface.BOLD);
        hostView.setSingleLine(true);
        hostView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        serverText.addView(hostView);
        serverRow.addView(serverText, new LinearLayout.LayoutParams(0, -2, 1));
        TextView edit = Ui.link(this, "Edit", Ui.PRIMARY_TEXT);
        edit.setContentDescription("Edit server address");
        edit.setOnClickListener(view -> {
            editingServer = true;
            render();
        });
        serverRow.addView(edit);
        form.addView(serverRow, new LinearLayout.LayoutParams(-1, -2));

        EditText username = Ui.input(this, "Username", InputType.TYPE_CLASS_TEXT);
        EditText password = Ui.input(this, "Password",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setTypeface(android.graphics.Typeface.DEFAULT);
        password.setImeOptions(EditorInfo.IME_ACTION_DONE);

        form.addView(Ui.field(this, "USERNAME", username), Ui.params(this, -1, -2, 18));
        form.addView(Ui.field(this, "PASSWORD", withVisibilityToggle(password)), Ui.params(this, -1, -2, 16));

        TextView forgotPassword = Ui.link(this, getString(R.string.forgot_your_password), Ui.PRIMARY_TEXT);
        forgotPassword.setGravity(Gravity.END);
        forgotPassword.setOnClickListener(view ->
                openExternalUrl(session.serverUrl.replaceAll("/+$", "") + "/password/reset"));
        form.addView(forgotPassword, Ui.params(this, -1, -2, 4));

        Button signIn = Ui.primary(this, "Sign in");
        form.addView(signIn, Ui.params(this, -1, 52, 22));

        signIn.setOnClickListener(view -> signIn(username, password));
        password.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                signIn(username, password);
                return true;
            }
            return false;
        });
        username.requestFocus();
    }
    /** Puts an eye button inside the password field to show or hide what was typed. */
    private View withVisibilityToggle(EditText password) {
        FrameLayout frame = new FrameLayout(this);
        int buttonSize = Ui.dp(this, 48);
        password.setPadding(password.getPaddingLeft(), password.getPaddingTop(),
                buttonSize + Ui.dp(this, 4), password.getPaddingBottom());
        frame.addView(password, new FrameLayout.LayoutParams(-1, -2));

        ImageButton eye = new ImageButton(this);
        eye.setBackground(Ui.ripple(null, Ui.withAlpha(Ui.PRIMARY, 0.15f), Ui.dp(this, 24)));
        boolean[] visible = {false};
        Runnable update = () -> {
            eye.setImageDrawable(Ui.tinted(this,
                    visible[0] ? R.drawable.ic_visibility_off : R.drawable.ic_visibility, Ui.MUTED));
            eye.setContentDescription(visible[0] ? "Hide password" : "Show password");
        };
        update.run();
        eye.setOnClickListener(view -> {
            visible[0] = !visible[0];
            int selection = password.getSelectionEnd();
            password.setInputType(InputType.TYPE_CLASS_TEXT | (visible[0]
                    ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            password.setTypeface(Typeface.DEFAULT);
            password.setSelection(Math.max(0, Math.min(selection, password.length())));
            update.run();
        });
        FrameLayout.LayoutParams eyeParams = new FrameLayout.LayoutParams(buttonSize, buttonSize,
                Gravity.END | Gravity.CENTER_VERTICAL);
        eyeParams.setMarginEnd(Ui.dp(this, 4));
        frame.addView(eye, eyeParams);
        return frame;
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

    private void signIn(EditText username, EditText password) {
        if (username.getText().toString().trim().isEmpty() || password.getText().toString().isEmpty()) {
            showError("Enter your username and password.");
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
