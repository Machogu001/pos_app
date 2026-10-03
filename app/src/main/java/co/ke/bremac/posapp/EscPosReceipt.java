package co.ke.bremac.posapp;

import co.ke.bremac.posapp.data.Business;
import co.ke.bremac.posapp.data.Sale;
import co.ke.bremac.posapp.data.SaleItem;
import co.ke.bremac.posapp.data.SalePayment;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;

/** A deliberately ASCII-only baseline for ESC/POS-compatible printers. */
final class EscPosReceipt {
    private EscPosReceipt() {
    }

    static int columns(int paperMm) {
        if (paperMm == 58) return 32;
        if (paperMm == 80) return 48;
        throw new IllegalArgumentException("Select 58 mm or 80 mm paper.");
    }

    static byte[] encode(Sale sale, Business business, int paperMm, boolean cut) {
        String text = text(sale, business, paperMm);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] initialize = {27, 64, 27, 77, 0, 27, 33, 0, 29, 33, 0, 27, 97, 0, 27, 116, 0};
        output.write(initialize, 0, initialize.length);
        byte[] content = (text + "\n\n\n").getBytes(StandardCharsets.US_ASCII);
        output.write(content, 0, content.length);
        if (cut) {
            byte[] command = {29, 86, 0};
            output.write(command, 0, command.length);
        }
        return output.toByteArray();
    }

    static String text(Sale sale, Business business, int paperMm) {
        if (sale == null || sale.id <= 0 || sale.items.isEmpty()) {
            throw new IllegalArgumentException("Load a saved sale with item details before printing.");
        }
        int width = columns(paperMm);
        int precision = Math.max(0, Math.min(6, business.currency.precision));
        String currency = safe(business.currency.code);
        StringBuilder out = new StringBuilder();
        line(out, business.name, width);
        String status = safe(sale.status).toLowerCase(Locale.ROOT);
        String kind = status.contains("quotation") || status.contains("quote") ? "QUOTATION"
                : status.contains("draft") ? "DRAFT" : "INVOICE";
        line(out, kind, width);
        line(out, "No: " + (sale.invoiceNo.isEmpty() ? String.valueOf(sale.id) : sale.invoiceNo), width);
        optional(out, "Date: ", sale.transactionDate, width);
        optional(out, "Location: ", sale.locationName, width);
        optional(out, "Customer: ", sale.customerName, width);
        line(out, "Currency: " + currency, width);
        separator(out, width);
        for (SaleItem item : sale.items) {
            line(out, item.name, width);
            optional(out, "SKU: ", item.sku, width);
            line(out, number(item.quantity, 6, true) + " " + safe(item.unit)
                    + " x " + amount(item.unitPriceIncTax, currency, precision), width);
            pair(out, "Line total", amount(item.lineTotal, currency, precision), width);
        }
        separator(out, width);
        pair(out, "Subtotal", amount(sale.subtotal, currency, precision), width);
        pair(out, "Discount", amount(sale.discountAmount, currency, precision), width);
        pair(out, "Tax", amount(sale.taxAmount, currency, precision), width);
        pair(out, "TOTAL", amount(sale.finalTotal, currency, precision), width);
        pair(out, "Paid", amount(sale.totalPaid, currency, precision), width);
        pair(out, "Change", amount(sale.changeReturn, currency, precision), width);
        optional(out, "Payment status: ", sale.paymentStatus, width);
        if (!sale.payments.isEmpty()) {
            separator(out, width);
            line(out, "Payments", width);
            for (SalePayment payment : sale.payments) {
                pair(out, payment.method, amount(payment.amount, currency, precision), width);
                optional(out, "Reference: ", payment.reference, width);
                optional(out, "Paid on: ", payment.paidOn, width);
            }
        }
        separator(out, width);
        if (!"INVOICE".equals(kind)) line(out, "Not a finalized invoice", width);
        return out.toString();
    }

    static String safe(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
        StringBuilder out = new StringBuilder();
        for (int offset = 0; offset < normalized.length();) {
            int ch = normalized.codePointAt(offset);
            offset += Character.charCount(ch);
            int type = Character.getType(ch);
            if (type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK) continue;
            if (ch >= 32 && ch <= 126) out.append((char) ch);
            else if (Character.isISOControl(ch) || Character.isWhitespace(ch)
                    || type == Character.FORMAT) out.append(' ');
            else out.append('?');
        }
        return out.toString().trim();
    }

    private static String amount(double value, String currency, int precision) {
        return currency + " " + number(value, precision, false);
    }

    private static String number(double value, int precision, boolean trimZeros) {
        if (!Double.isFinite(value)) return "-";
        BigDecimal decimal = BigDecimal.valueOf(value).setScale(precision, RoundingMode.HALF_UP);
        return (trimZeros ? decimal.stripTrailingZeros() : decimal).toPlainString();
    }

    private static void optional(StringBuilder out, String label, String value, int width) {
        if (!safe(value).isEmpty()) line(out, label + safe(value), width);
    }

    private static void separator(StringBuilder out, int width) {
        for (int i = 0; i < width; i++) out.append('-');
        out.append('\n');
    }

    private static void pair(StringBuilder out, String label, String value, int width) {
        label = safe(label);
        value = safe(value);
        int gap = width - label.length() - value.length();
        if (gap < 1) {
            line(out, label, width);
            line(out, value, width);
        } else {
            out.append(label);
            for (int i = 0; i < gap; i++) out.append(' ');
            out.append(value).append('\n');
        }
    }

    private static void line(StringBuilder out, String value, int width) {
        String remaining = safe(value);
        if (remaining.isEmpty()) {
            out.append('\n');
            return;
        }
        while (remaining.length() > width) {
            int split = remaining.lastIndexOf(' ', width);
            if (split <= 0) split = width;
            out.append(remaining, 0, split).append('\n');
            remaining = remaining.substring(split).trim();
        }
        out.append(remaining).append('\n');
    }
}
