package co.ke.bremac.posapp;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import co.ke.bremac.posapp.ui.Ui;

import org.json.JSONException;
import org.json.JSONObject;

public class OtpActivity extends BaseActivity {
    private JSONObject otpData;
    private CountDownTimer resendTimer;
    private Button smsButton;
    private Button emailButton;
    private TextView resendHint;

    @Override
    protected Chrome chrome() {
        return Chrome.NONE;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            otpData = new JSONObject(getIntent().getStringExtra("otp"));
        } catch (JSONException | NullPointerException exception) {
            goToLogin("Please sign in again.");
            return;
        }
        render(null);
    }

    private void render(String notice) {
        content.removeAllViews();
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        String method = "email".equals(otpData.optString("delivery_method")) ? "email" : "SMS";
        LoginActivity.addBrandHeader(this, content, "Verify it's you",
                "We sent a 6-digit code by " + method + " to " + otpData.optString("delivery_target"));

        if (notice != null) {
            content.addView(Ui.banner(this, notice, Ui.PRIMARY_DARK, Ui.PRIMARY_SOFT), Ui.params(this, -1, -2, 20));
        }

        LinearLayout form = Ui.card(this);
        form.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20));

        EditText code = Ui.input(this, "••••••", InputType.TYPE_CLASS_NUMBER);
        code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        code.setGravity(Gravity.CENTER);
        code.setTextSize(24);
        code.setLetterSpacing(0.5f);
        code.setTypeface(Typeface.DEFAULT_BOLD);
        code.setImeOptions(EditorInfo.IME_ACTION_DONE);
        form.addView(Ui.field(this, "VERIFICATION CODE", code));

        Button verify = Ui.primary(this, "Verify and continue");
        form.addView(verify, Ui.params(this, -1, 52, 20));
        content.addView(form, Ui.params(this, -1, -2, 20));

        resendHint = Ui.text(this, "", 13, Ui.MUTED, Typeface.NORMAL);
        resendHint.setGravity(Gravity.CENTER);
        content.addView(resendHint, Ui.params(this, -1, -2, 18));

        LinearLayout row = Ui.row(this);
        smsButton = Ui.secondary(this, "Resend by SMS");
        emailButton = Ui.secondary(this, "Resend by email");
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1);
        right.setMarginStart(Ui.dp(this, 10));
        row.addView(smsButton, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        row.addView(emailButton, right);
        content.addView(row, Ui.params(this, -1, -2, 10));

        TextView back = Ui.link(this, "Use a different account", Ui.MUTED);
        back.setOnClickListener(view -> goToLogin(null));
        content.addView(back, Ui.params(this, -2, -2, 12));

        verify.setOnClickListener(view -> verify(code.getText().toString()));
        code.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                verify(code.getText().toString());
                return true;
            }
            return false;
        });
        smsButton.setOnClickListener(view -> resend("sms"));
        emailButton.setOnClickListener(view -> resend("email"));
        startCooldown(otpData.optInt("resend_in"));
        code.requestFocus();
    }

    private void verify(String code) {
        if (code.trim().length() != 6) {
            showError("Enter the 6-digit code.");
            return;
        }
        runAsync("Verifying…",
                () -> session.api().verifyOtp(otpData.optString("otp_session"), code.trim(), LoginActivity.deviceName()),
                result -> {
                    JSONObject data = result.optJSONObject("data");
                    session.saveToken(data.optString("token"));
                    Intent intent = new Intent(this, HomeActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
    }

    private void resend(String method) {
        runAsync("Sending a new code…",
                () -> session.api().resendOtp(otpData.optString("otp_session"), method),
                result -> {
                    JSONObject data = result.optJSONObject("data");
                    data.put("otp_session", otpData.optString("otp_session"));
                    otpData = data;
                    render("A new code was sent by " + ("email".equals(data.optString("delivery_method"))
                            ? "email" : "SMS") + ".");
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
        setResendEnabled(false, seconds);
        resendTimer = new CountDownTimer(seconds * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                setResendEnabled(false, (int) Math.ceil(millisUntilFinished / 1000.0));
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
        resendHint.setText(enabled ? "Didn't get the code?" : "You can request a new code in " + remaining + "s");
    }

    @Override
    protected void onDestroy() {
        if (resendTimer != null) {
            resendTimer.cancel();
        }
        super.onDestroy();
    }
}
