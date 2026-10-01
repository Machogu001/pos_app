package co.ke.bremac.posapp;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import co.ke.bremac.posapp.data.Register;
import co.ke.bremac.posapp.ui.Formats;
import co.ke.bremac.posapp.ui.Ui;

public class RegisterActivity extends BaseActivity {
    @Override
    protected NavDrawer.Item navItem() {
        return NavDrawer.Item.REGISTER;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureAccess(session.permissions.canUseRegister())) {
            return;
        }
        setScreenTitle("Cash register");
        render();
        loadMeForRegister(this::render);
    }

    private void render() {
        content.removeAllViews();
        Register register = session.register;
        boolean open = register != null && register.isOpen();

        LinearLayout hero = Ui.card(this);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(Ui.dp(this, 20), Ui.dp(this, 24), Ui.dp(this, 20), Ui.dp(this, 24));
        hero.addView(Ui.iconBubble(this, R.drawable.ic_register, open ? Ui.SUCCESS : Ui.DANGER,
                open ? Ui.SUCCESS_SOFT : Ui.DANGER_SOFT, 64));
        TextView status = Ui.text(this, open ? "Register is open" : "Register is closed", 20, Ui.INK, Typeface.BOLD);
        status.setGravity(Gravity.CENTER);
        hero.addView(status, Ui.params(this, -2, -2, 14));
        TextView detail = Ui.text(this, open
                ? "You can record sales at " + session.selectedLocationName() + "."
                : "Open the register with your starting cash to record sales.", 14, Ui.MUTED, Typeface.NORMAL);
        detail.setGravity(Gravity.CENTER);
        hero.addView(detail, Ui.params(this, -2, -2, 4));
        content.addView(hero, new LinearLayout.LayoutParams(-1, -2));

        if (open) {
            LinearLayout card = Ui.card(this);
            String location = locationName(register.locationId);
            if (!location.isEmpty()) {
                card.addView(Ui.summaryRow(this, "Location", location, false));
            }
            String opened = Formats.dateTime(register.openedAt);
            if (!opened.isEmpty()) {
                card.addView(Ui.summaryRow(this, "Opened", opened, false));
            }
            card.addView(Ui.summaryRow(this, "Opening cash", session.money().format(register.openingAmount), false));
            content.addView(card, Ui.params(this, -1, -2, 12));
        }

        if (open && session.permissions.closeRegister) {
            Button close = Ui.danger(this, "Close register");
            close.setOnClickListener(view -> RegisterDialogs.close(this, () -> {
                toast("Register closed.");
                render();
            }));
            content.addView(close, Ui.params(this, -1, 52, 20));
        } else if (open) {
            content.addView(Ui.banner(this, "Your role cannot close the register. Ask a supervisor to close it.",
                    Ui.INFO, Ui.INFO_SOFT), Ui.params(this, -1, -2, 16));
        } else if (session.permissions.sellCreate) {
            Button openButton = Ui.primary(this, "Open register");
            openButton.setOnClickListener(view -> RegisterDialogs.open(this, () -> {
                toast("Register opened.");
                render();
            }));
            content.addView(openButton, Ui.params(this, -1, 52, 20));
        }

        if (open && session.permissions.sellCreate) {
            Button sell = Ui.secondary(this, "Start selling");
            sell.setOnClickListener(view -> openTopLevel(PosActivity.class));
            content.addView(sell, Ui.params(this, -1, 48, 10));
        }
    }

    private String locationName(int id) {
        for (co.ke.bremac.posapp.data.Location location : session.locations) {
            if (location.id == id) {
                return location.name;
            }
        }
        return "";
    }
}
