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
        LinearLayout content = Ui.column(context);
        content.setPadding(Ui.dp(context, 24), Ui.dp(context, 20), Ui.dp(context, 24), Ui.dp(context, 20));
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        ProgressBar progressBar = new ProgressBar(context);
        content.addView(progressBar);
        messageView = Ui.text(context, "Please wait…", 15, Ui.INK, 0);
        content.addView(messageView, Ui.params(context, -2, -2, 12));
        dialog = new AlertDialog.Builder(context).setView(content).create();
        dialog.setCanceledOnTouchOutside(false);
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
