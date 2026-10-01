package co.ke.bremac.posapp;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;

import co.ke.bremac.posapp.ui.Ui;

import org.json.JSONObject;

import java.net.URISyntaxException;

public class LoginActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        render();
    }

    private void render() {
        title("Sign in");
        paragraph("Connect to your BreMac POS backend with Mobile API v1. HTTPS is required.");
        String message = getIntent().getStringExtra("message");
        if (message != null) {
            showError(message);
        }

        EditText server = Ui.input(this, "https://pos.example.com", InputType.TYPE_TEXT_VARIATION_URI);
        server.setText(session.serverUrl);
        content.addView(server, Ui.params(this, -1, 54, 8));

        EditText username = Ui.input(this, "Username", InputType.TYPE_CLASS_TEXT);
        EditText password = Ui.input(this, "Password", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        content.addView(username, Ui.params(this, -1, 54, 8));
        content.addView(password, Ui.params(this, -1, 54, 8));

        Button signIn = Ui.primary(this, "Sign in");
        content.addView(signIn, Ui.params(this, -1, 52, 12));
        signIn.setOnClickListener(view -> signIn(server, username, password));
    }

    private void signIn(EditText server, EditText username, EditText password) {
        try {
            session.setServerUrl(ServerUrl.normalize(server.getText().toString()));
        } catch (IllegalArgumentException | URISyntaxException exception) {
            showError(exception.getMessage());
            return;
        }

        runAsync("Signing in…",
                () -> session.api().login(
                        username.getText().toString(),
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
        return (Build.MANUFACTURER + " " + Build.MODEL).trim();
    }
}
