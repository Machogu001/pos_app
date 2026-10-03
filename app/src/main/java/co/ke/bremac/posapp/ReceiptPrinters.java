package co.ke.bremac.posapp;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.InputType;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;

import co.ke.bremac.posapp.data.Business;
import co.ke.bremac.posapp.data.Sale;
import co.ke.bremac.posapp.ui.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

final class ReceiptPrinters {
    private static final String PREFS = "receipt_printers";

    private ReceiptPrinters() {
    }

    static void show(BaseActivity activity, Sale sale) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (sale == null || sale.id <= 0 || sale.items.isEmpty()) {
            message(activity, "Cannot print", "Load a saved sale with item details before printing.");
            return;
        }
        LinearLayout box = Ui.dialogBox(activity);
        box.addView(Ui.text(activity, "ESC/POS-compatible printers only. Choose a connection. "
                + "Bluetooth printers must first be paired in Android settings.",
                14, Ui.MUTED, Typeface.NORMAL));
        Button bluetooth = Ui.primary(activity, "Bluetooth Classic (paired)");
        Button network = Ui.secondary(activity, "Network (IP / host and port)");
        box.addView(bluetooth, Ui.params(activity, -1, -2, 16));
        box.addView(network, Ui.params(activity, -1, -2, 8));
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Send receipt to printer")
                .setView(box).setNegativeButton("Cancel", null).create();
        bluetooth.setOnClickListener(view -> {
            dialog.dismiss();
            withBluetoothPermission(activity, () -> loadPaired(activity, sale));
        });
        network.setOnClickListener(view -> {
            dialog.dismiss();
            network(activity, sale);
        });
        dialog.show();
    }

    private static SharedPreferences settings(BaseActivity activity) {
        return activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void withBluetoothPermission(BaseActivity activity, Runnable granted) {
        if (Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(activity,
                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            granted.run();
            return;
        }
        // Non-lifecycle registration is allowed after STARTED, unlike registerForActivityResult.
        PermissionRequest request = new PermissionRequest(activity, granted);
        request.launch();
    }

    private static final class PermissionRequest {
        private final BaseActivity activity;
        private final Runnable granted;
        private ActivityResultLauncher<String> launcher;
        private final LifecycleEventObserver observer;

        PermissionRequest(BaseActivity activity, Runnable granted) {
            this.activity = activity;
            this.granted = granted;
            observer = (owner, event) -> {
                if (event == Lifecycle.Event.ON_DESTROY) dispose();
            };
        }

        void launch() {
            try {
                launcher = activity.getActivityResultRegistry().register(
                        "receipt-bluetooth-" + UUID.randomUUID(),
                        new ActivityResultContracts.RequestPermission(), allowed -> {
                            dispose();
                            if (activity.isFinishing() || activity.isDestroyed()) return;
                            if (allowed) {
                                granted.run();
                            } else {
                                new AlertDialog.Builder(activity).setTitle("Bluetooth permission needed")
                                        .setMessage("Nearby devices access is required to connect to a paired "
                                                + "printer. No scanning or location access is used. You can "
                                                + "enable permission in app settings, then try again.")
                                        .setNegativeButton("Close", null)
                                        .setPositiveButton("App settings", (dialog, which) -> openSettings(
                                                activity, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                        Uri.parse("package:" + activity.getPackageName()))))
                                        .show();
                            }
                        });
                activity.getLifecycle().addObserver(observer);
                launcher.launch(Manifest.permission.BLUETOOTH_CONNECT);
            } catch (RuntimeException exception) {
                dispose();
                message(activity, "Bluetooth permission", "Could not request Bluetooth permission. "
                        + "Enable Nearby devices in app settings and try again.");
            }
        }

        private void dispose() {
            if (launcher != null) {
                launcher.unregister();
                launcher = null;
            }
            activity.getLifecycle().removeObserver(observer);
        }
    }

    private static BluetoothAdapter adapter(BaseActivity activity) {
        BluetoothManager manager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        return manager == null ? null : manager.getAdapter();
    }

    private static void loadPaired(BaseActivity activity, Sale sale) {
        activity.runAsync("Reading paired devices…", () -> {
            BluetoothAdapter adapter = adapter(activity);
            if (adapter == null) throw new IllegalStateException("This device does not support Bluetooth.");
            if (!adapter.isEnabled()) throw new IllegalStateException(
                    "Bluetooth is off. Enable it in Android Bluetooth settings, then try again.");
            JSONArray devices = new JSONArray();
            for (BluetoothDevice device : adapter.getBondedDevices()) {
                String name = device.getName();
                devices.put(new JSONObject().put("address", device.getAddress())
                        .put("name", name == null || name.trim().isEmpty() ? "Paired device" : name));
            }
            return new JSONObject().put("devices", devices);
        }, result -> {
            JSONArray devices = result.getJSONArray("devices");
            if (devices.length() == 0) {
                new AlertDialog.Builder(activity).setTitle("No paired devices")
                        .setMessage("Pair your Bluetooth Classic ESC/POS printer in Android settings, "
                                + "then open the printer picker again. This app does not scan.")
                        .setNegativeButton("Close", null)
                        .setPositiveButton("Bluetooth settings", (dialog, which) -> openSettings(activity,
                                new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))).show();
                return;
            }
            bluetooth(activity, sale, devices);
        });
    }

    private static void bluetooth(BaseActivity activity, Sale sale, JSONArray devices) throws Exception {
        SharedPreferences prefs = settings(activity);
        List<String> addresses = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int selected = 0;
        for (int i = 0; i < devices.length(); i++) {
            JSONObject device = devices.getJSONObject(i);
            String address = device.getString("address");
            addresses.add(address);
            labels.add(device.getString("name") + " (" + address + ")");
            if (address.equals(prefs.getString("bluetooth_address", ""))) selected = i;
        }
        PrintForm form = new PrintForm(activity);
        Spinner printers = Ui.spinner(activity, labels, selected);
        form.box.addView(Ui.field(activity, "Paired printer", printers), 0);
        form.box.addView(Ui.text(activity, "Only paired devices are listed. Select a Classic SPP ESC/POS "
                + "printer, not a BLE-only printer.", 13, Ui.MUTED, Typeface.NORMAL),
                Ui.params(activity, -1, -2, 12));
        Button pair = Ui.secondary(activity, "Pair in Android settings");
        form.box.addView(pair, Ui.params(activity, -1, -2, 8));
        pair.setOnClickListener(view -> openSettings(activity, new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        AlertDialog dialog = form.dialog("Bluetooth receipt printer");
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String address = addresses.get(printers.getSelectedItemPosition());
            int paper = form.paperMm();
            boolean cut = form.cut.isChecked();
            form.save(prefs.edit().putString("bluetooth_address", address));
            Business business = activity.session.business;
            dialog.dismiss();
            activity.runAsync("Sending receipt…", () -> {
                BluetoothAdapter adapter = adapter(activity);
                if (adapter == null || !adapter.isEnabled()) {
                    throw new IllegalStateException("Bluetooth is unavailable or turned off.");
                }
                BluetoothDevice device = adapter.getRemoteDevice(address);
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    throw new IllegalStateException("Printer is no longer paired. Pair it in Android settings.");
                }
                ReceiptPrinterTransport.bluetooth(device, EscPosReceipt.encode(sale, business, paper, cut));
                return new JSONObject();
            }, result -> sent(activity));
        }));
        dialog.show();
    }

    private static void network(BaseActivity activity, Sale sale) {
        SharedPreferences prefs = settings(activity);
        PrintForm form = new PrintForm(activity);
        EditText host = Ui.input(activity, "192.168.1.100 or printer.local",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        host.setSingleLine(true);
        host.setText(prefs.getString("network_host", ""));
        EditText port = Ui.input(activity, "9100", InputType.TYPE_CLASS_NUMBER);
        port.setSingleLine(true);
        port.setText(String.valueOf(prefs.getInt("network_port", 9100)));
        form.box.addView(Ui.field(activity, "Host / IP (no URL)", host), 0);
        form.box.addView(Ui.field(activity, "TCP port", port), 1);
        form.box.addView(Ui.text(activity, "Raw TCP printing is unencrypted. Use a trusted local network "
                + "and the printer's ESC/POS port (usually 9100).", 13, Ui.MUTED, Typeface.NORMAL),
                Ui.params(activity, -1, -2, 12));
        AlertDialog dialog = form.dialog("Network receipt printer");
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            final String hostname;
            final int portNumber;
            try {
                hostname = ReceiptPrinterTransport.validateHost(host.getText().toString());
            } catch (IllegalArgumentException exception) {
                host.setError(exception.getMessage());
                return;
            }
            try {
                portNumber = ReceiptPrinterTransport.validatePort(port.getText().toString());
            } catch (IllegalArgumentException exception) {
                port.setError(exception.getMessage());
                return;
            }
            int paper = form.paperMm();
            boolean cut = form.cut.isChecked();
            form.save(prefs.edit().putString("network_host", hostname).putInt("network_port", portNumber));
            Business business = activity.session.business;
            dialog.dismiss();
            activity.runAsync("Sending receipt…", () -> {
                ReceiptPrinterTransport.network(hostname, portNumber,
                        EscPosReceipt.encode(sale, business, paper, cut));
                return new JSONObject();
            }, result -> sent(activity));
        }));
        dialog.show();
    }

    private static final class PrintForm {
        final BaseActivity activity;
        final LinearLayout box;
        final Spinner paper;
        final CheckBox cut;

        PrintForm(BaseActivity activity) {
            this.activity = activity;
            box = Ui.dialogBox(activity);
            SharedPreferences prefs = settings(activity);
            paper = Ui.spinner(activity, Arrays.asList("58 mm (32 columns)", "80 mm (48 columns)"),
                    prefs.getInt("paper_mm", 58) == 80 ? 1 : 0);
            box.addView(Ui.field(activity, "Paper width", paper), Ui.params(activity, -1, -2, 12));
            cut = new CheckBox(activity);
            cut.setText("Cut after receipt (supported printers only)");
            cut.setTextColor(Ui.INK);
            cut.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.PRIMARY));
            cut.setChecked(prefs.getBoolean("cut", false));
            box.addView(cut, Ui.params(activity, -1, -2, 12));
            box.addView(Ui.text(activity, "Prints saved sale details using basic ASCII text. Accented text "
                    + "is simplified; unsupported characters become ?. Sending bytes does not confirm "
                    + "paper printed. Check the printer before retrying a failed or timed-out send.",
                    13, Ui.MUTED, Typeface.NORMAL), Ui.params(activity, -1, -2, 12));
        }

        int paperMm() {
            return paper.getSelectedItemPosition() == 1 ? 80 : 58;
        }

        void save(SharedPreferences.Editor editor) {
            editor.putInt("paper_mm", paperMm()).putBoolean("cut", cut.isChecked()).apply();
        }

        AlertDialog dialog(String title) {
            ScrollView scroll = new ScrollView(activity);
            scroll.addView(box);
            return new AlertDialog.Builder(activity).setTitle(title).setView(scroll)
                    .setNegativeButton("Cancel", null).setPositiveButton("Send receipt", null).create();
        }
    }

    private static void sent(BaseActivity activity) {
        message(activity, "Receipt bytes sent", "Receipt bytes were sent to the printer connection. "
                + "This does not confirm paper printed; check the printer.");
    }

    private static void message(BaseActivity activity, String title, String body) {
        if (!activity.isFinishing() && !activity.isDestroyed()) {
            new AlertDialog.Builder(activity).setTitle(title).setMessage(body)
                    .setPositiveButton("OK", null).show();
        }
    }

    private static void openSettings(BaseActivity activity, Intent intent) {
        try {
            activity.startActivity(intent);
        } catch (RuntimeException exception) {
            message(activity, "Settings unavailable", "Open Android settings manually and try again.");
        }
    }
}
