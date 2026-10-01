package co.ke.bremac.posapp.cart;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class Cart {
    public static class Line {
        public final int variationId;
        public final String name;
        public final String sku;
        public double quantity;
        public double unitPrice;

        public Line(int variationId, String name, String sku, double quantity, double unitPrice) {
            this.variationId = variationId;
            this.name = name;
            this.sku = sku;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }
    }

    public static class Payment {
        public String method;
        public String label;
        public double amount;
        public String note = "";
        public String cardNumber = "";
        public String transactionNo = "";
        public String checkoutRequestId = "";
        public String mpesaPhone = "";
    }

    public final List<Line> lines = new ArrayList<>();
    public final List<Payment> payments = new ArrayList<>();
    public String discountType = "fixed";
    public double discountAmount = 0;
    public String clientReference = UUID.randomUUID().toString();

    public void clear() {
        lines.clear();
        payments.clear();
        discountType = "fixed";
        discountAmount = 0;
        clientReference = UUID.randomUUID().toString();
    }

    public void add(int variationId, String name, String sku, double price) {
        for (Line line : lines) {
            if (line.variationId == variationId) {
                line.quantity += 1;
                return;
            }
        }
        lines.add(new Line(variationId, name, sku, 1, price));
    }

    public void remove(int variationId) {
        for (Iterator<Line> it = lines.iterator(); it.hasNext();) {
            if (it.next().variationId == variationId) it.remove();
        }
    }

    public double subtotal(int precision) {
        BigDecimal total = BigDecimal.ZERO;
        for (Line line : lines) total = total.add(BigDecimal.valueOf(line.quantity).multiply(BigDecimal.valueOf(line.unitPrice)));
        return round(total, precision);
    }

    public double discount(int precision) {
        BigDecimal sub = BigDecimal.valueOf(subtotal(precision));
        BigDecimal discount = "percentage".equals(discountType)
                ? sub.multiply(BigDecimal.valueOf(discountAmount)).divide(BigDecimal.valueOf(100), precision + 4, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(discountAmount);
        if (discount.compareTo(BigDecimal.ZERO) < 0) discount = BigDecimal.ZERO;
        if (discount.compareTo(sub) > 0) discount = sub;
        return round(discount, precision);
    }

    public double total(int precision) {
        return round(BigDecimal.valueOf(subtotal(precision)).subtract(BigDecimal.valueOf(discount(precision))), precision);
    }

    public double paid(int precision) {
        BigDecimal paid = BigDecimal.ZERO;
        for (Payment payment : payments) paid = paid.add(BigDecimal.valueOf(payment.amount));
        return round(paid, precision);
    }

    public double change(int precision) {
        return round(BigDecimal.valueOf(Math.max(0, paid(precision) - total(precision))), precision);
    }

    public double balance(int precision) {
        return round(BigDecimal.valueOf(Math.max(0, total(precision) - paid(precision))), precision);
    }

    private static double round(BigDecimal value, int precision) {
        return value.setScale(Math.max(0, precision), RoundingMode.HALF_UP).doubleValue();
    }
}
