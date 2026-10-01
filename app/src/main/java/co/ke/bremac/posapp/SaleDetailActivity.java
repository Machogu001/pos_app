package co.ke.bremac.posapp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import co.ke.bremac.posapp.data.Sale;
import co.ke.bremac.posapp.data.SaleItem;
import co.ke.bremac.posapp.data.SalePayment;
import co.ke.bremac.posapp.ui.Ui;

public class SaleDetailActivity extends BaseActivity {
    private static final String EXTRA_ID = "sale_id";

    public static void open(Context context, int saleId) {
        Intent intent = new Intent(context, SaleDetailActivity.class);
        intent.putExtra(EXTRA_ID, saleId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addNav();
        int saleId = getIntent().getIntExtra(EXTRA_ID, 0);
        runAsync("Loading sale…",
                () -> session.api().sale(saleId),
                result -> render(Sale.fromJson(result.optJSONObject("data"))));
    }

    private void render(Sale sale) {
        content.removeAllViews();
        title(sale.invoiceNo);
        card("Customer", sale.customerName);
        card("Total", session.money().format(sale.finalTotal));
        section("Items");
        for (SaleItem item : sale.items) {
            card(item.name, item.quantity + " " + item.unit + " × "
                    + session.money().format(item.unitPriceIncTax)
                    + " = " + session.money().format(item.lineTotal));
        }
        section("Payments");
        for (SalePayment payment : sale.payments) {
            card(payment.method, session.money().format(payment.amount) + " " + payment.reference);
        }
        Button share = Ui.primary(this, "Share receipt");
        content.addView(share, Ui.params(this, -1, 48, 8));
        share.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, sale.receiptText);
            startActivity(Intent.createChooser(intent, "Share receipt"));
        });
    }
}
