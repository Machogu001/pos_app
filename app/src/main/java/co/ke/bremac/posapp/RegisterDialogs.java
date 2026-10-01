package co.ke.bremac.posapp;

import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;

import co.ke.bremac.posapp.ui.Ui;

public final class RegisterDialogs {
    private RegisterDialogs() {
    }

    public static void open(BaseActivity activity, Runnable afterOpen) {
        EditText amount = Ui.input(activity, "Opening amount", InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        new AlertDialog.Builder(activity)
                .setTitle("Open register")
                .setView(amount)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open", (dialog, which) -> activity.runAsync("Opening register…",
                        () -> activity.session.api().openRegister(
                                activity.session.locationId,
                                number(amount)),
                        result -> {
                            activity.loadMeForRegister(afterOpen);
                        }))
                .show();
    }

    public static void close(BaseActivity activity, Runnable afterClose) {
        LinearLayout box = Ui.column(activity);
        EditText amount = Ui.input(activity, "Closing amount", InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText note = Ui.input(activity, "Note", InputType.TYPE_CLASS_TEXT);
        box.addView(amount);
        box.addView(note);
        new AlertDialog.Builder(activity)
                .setTitle("Close register")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Close", (dialog, which) -> activity.runAsync("Closing register…",
                        () -> activity.session.api().closeRegister(number(amount), note.getText().toString()),
                        result -> {
                            activity.loadMeForRegister(afterClose);
                        }))
                .show();
    }

    public static void openAfterClosed(BaseActivity activity, Runnable afterOpen) {
        new AlertDialog.Builder(activity)
                .setTitle("Register closed")
                .setMessage("No open cash register is available for this user.")
                .setPositiveButton("Open register", (dialog, which) -> open(activity, afterOpen))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static double number(EditText input) {
        try {
            return Double.parseDouble(input.getText().toString());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
