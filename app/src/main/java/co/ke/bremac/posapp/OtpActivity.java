package co.ke.bremac.posapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import co.ke.bremac.posapp.ui.Ui;

import org.json.JSONObject;
import org.json.JSONException;

public class OtpActivity extends BaseActivity {
    private JSONObject otpData;
    private CountDownTimer resendTimer;
    private Button smsButton;
    private Button emailButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            otpData = new JSONObject(getIntent().getStringExtra("otp"));
        } catch (JSONException exception) {
            goToLogin("Please sign in again.");
            return;
        }
        render(null);
    }

    private void render(String message) {
        content.removeAllViews();
        title("Verification code");
        paragraph("Enter the 6-digit code sent to " + otpData.optString("delivery_target") + ".");
        if (message != null) {
            showError(message);
        }

        EditText code = Ui.input(this, "123456", InputType.TYPE_CLASS_NUMBER);
        code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        content.addView(code, Ui.params(this, -1, 54, 8));

        Button verify = Ui.primary(this, "Verify");
        content.addView(verify, Ui.params(this, -1, 52, 8));
        verify.setOnClickListener(view -> verify(code.getText().toString()));

        LinearLayout row = Ui.row(this);
        smsButton = Ui.secondary(this, "Resend SMS");
        emailButton = Ui.secondary(this, "Resend email");
        row.addView(smsButton, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        row.addView(emailButton, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        content.addView(row, Ui.params(this, -1, -2, 8));
        smsButton.setOnClickListener(view -> resend("sms"));
        emailButton.setOnClickListener(view -> resend("email"));
        startCooldown(otpData.optInt("resend_in"));
    }

    private void verify(String code) {
        runAsync("Verifying…",
                () -> session.api().verifyOtp(otpData.optString("otp_session"), code, LoginActivity.deviceName()),
                result -> {
                    JSONObject data = result.optJSONObject("data");
                    session.saveToken(data.optString("token"));
                    startActivity(new Intent(this, HomeActivity.class));
                    finish();
                });
    }

    private void resend(String method) {
        runAsync("Resending…",
                () -> session.api().resendOtp(otpData.optString("otp_session"), method),
                result -> {
                    JSONObject data = result.optJSONObject("data");
                    data.put("otp_session", otpData.optString("otp_session"));
                    otpData = data;
                    render("Code resent via " + data.optString("delivery_method") + ".");
                });
    }

    private void startCooldown(int seconds) {
        if (resendTimer != null) {
            resendTimer.cancel();
        }
        if (seconds <= 0) {
            setResendEnabled(true, 0);
            return;
        }
        resendTimer = new CountDownTimer(seconds * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                setResendEnabled(false, (int) (millisUntilFinished / 1000L));
            }

            @Override
            public void onFinish() {
                setResendEnabled(true, 0);
            }
        };
        resendTimer.start();
    }

    private void setResendEnabled(boolean enabled, int remaining) {
        smsButton.setEnabled(enabled);
        emailButton.setEnabled(enabled);
        smsButton.setText(enabled ? "Resend SMS" : "SMS (" + remaining + ")");
        emailButton.setText(enabled ? "Resend email" : "Email (" + remaining + ")");
    }

    @Override
    protected void onDestroy() {
        if (resendTimer != null) {
            resendTimer.cancel();
        }
        super.onDestroy();
    }
}
