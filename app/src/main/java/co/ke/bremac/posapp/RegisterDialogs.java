package co.ke.bremac.posapp;

import android.graphics.Typeface;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;

import co.ke.bremac.posapp.ui.Ui;

public final class RegisterDialogs {
    private RegisterDialogs() {
    }

    public static void open(BaseActivity activity, Runnable afterOpen) {
        LinearLayout box = Ui.dialogBox(activity);
        box.addView(Ui.text(activity, "Count the cash in the drawer before you start selling.", 14, Ui.MUTED,
                Typeface.NORMAL), Ui.params(activity, -1, -2, 4));
        EditText amount = Ui.input(activity, "0", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(Ui.field(activity, "Opening cash", amount), Ui.params(activity, -1, -2, 14));
        new AlertDialog.Builder(activity)
                .setTitle("Open cash register")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open register", (dialog, which) -> activity.runAsync("Opening register…",
                        () -> activity.session.api().openRegister(activity.session.locationId, number(amount)),
                        result -> activity.loadMeForRegister(afterOpen)))
                .show();
    }

    public static void close(BaseActivity activity, Runnable afterClose) {
        LinearLayout box = Ui.dialogBox(activity);
        box.addView(Ui.text(activity, "Count the cash in the drawer and enter the total.", 14, Ui.MUTED,
                Typeface.NORMAL), Ui.params(activity, -1, -2, 4));
        EditText amount = Ui.input(activity, "0", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText note = Ui.input(activity, "Optional", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(Ui.field(activity, "Closing cash", amount), Ui.params(activity, -1, -2, 14));
        box.addView(Ui.field(activity, "Note", note), Ui.params(activity, -1, -2, 12));
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Close cash register")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Close register", null)
                .create();
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (amount.getText().toString().trim().isEmpty()) {
                amount.setError("Enter the counted cash");
                return;
            }
            dialog.dismiss();
            activity.runAsync("Closing register…",
                    () -> activity.session.api().closeRegister(number(amount), note.getText().toString().trim()),
                    result -> activity.loadMeForRegister(afterClose));
        }));
        dialog.show();
    }

    public static void openAfterClosed(BaseActivity activity, Runnable afterOpen) {
        new AlertDialog.Builder(activity)
                .setTitle("Cash register is closed")
                .setMessage("Open your cash register to record sales.")
                .setPositiveButton("Open register", (dialog, which) -> open(activity, afterOpen))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static double number(EditText input) {
        try {
            return Double.parseDouble(input.getText().toString().replace(",", "").trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
