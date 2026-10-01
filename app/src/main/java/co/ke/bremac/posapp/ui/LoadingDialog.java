package co.ke.bremac.posapp.ui;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

public class LoadingDialog {
    private final AlertDialog dialog;
    private final TextView messageView;

    public LoadingDialog(Context context) {
        LinearLayout content = Ui.row(context);
        content.setPadding(Ui.dp(context, 20), Ui.dp(context, 18), Ui.dp(context, 24), Ui.dp(context, 18));
        content.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(context, 18)));
        ProgressBar progressBar = new ProgressBar(context);
        progressBar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.PRIMARY));
        content.addView(progressBar, new LinearLayout.LayoutParams(Ui.dp(context, 36), Ui.dp(context, 36)));
        messageView = Ui.text(context, "Please wait…", 15, Ui.INK, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1);
        textParams.setMarginStart(Ui.dp(context, 16));
        content.addView(messageView, textParams);
        content.setGravity(Gravity.CENTER_VERTICAL);
        dialog = new AlertDialog.Builder(context).setView(content).create();
        dialog.setCanceledOnTouchOutside(false);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                    android.graphics.Color.TRANSPARENT));
        }
    }

    public void show(String message) {
        messageView.setText(message == null || message.isEmpty() ? "Please wait…" : message);
        if (!dialog.isShowing()) {
            dialog.show();
        }
    }

    public void dismiss() {
        if (dialog.isShowing()) {
            dialog.dismiss();
        }
    }
}
