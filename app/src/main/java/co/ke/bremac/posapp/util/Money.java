package co.ke.bremac.posapp.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Money {
    public final String code;
    public final String symbol;
    public final String thousandSeparator;
    public final String decimalSeparator;
    public final int precision;

    public Money(String code, String symbol, String thousandSeparator, String decimalSeparator, int precision) {
        this.code = code == null ? "" : code;
        this.symbol = symbol == null ? "" : symbol;
        this.thousandSeparator = thousandSeparator == null ? "," : thousandSeparator;
        this.decimalSeparator = decimalSeparator == null ? "." : decimalSeparator;
        this.precision = Math.max(0, precision);
    }

    public static Money kes() {
        return new Money("KES", "KSh", ",", ".", 2);
    }

    public BigDecimal round(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(precision, RoundingMode.HALF_UP);
    }

    public String format(double value) {
        BigDecimal rounded = round(BigDecimal.valueOf(value));
        boolean negative = rounded.signum() < 0;
        String plain = rounded.abs().toPlainString();
        String[] parts = plain.split("\\.");
        String whole = group(parts[0]);
        String frac = precision == 0 ? "" : decimalSeparator + (parts.length > 1 ? pad(parts[1]) : pad(""));
        return (negative ? "-" : "") + (symbol.isEmpty() ? "" : symbol + " ") + whole + frac;
    }

    private String group(String whole) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < whole.length(); i++) {
            if (i > 0 && (whole.length() - i) % 3 == 0) out.append(thousandSeparator);
            out.append(whole.charAt(i));
        }
        return out.toString();
    }

    private String pad(String value) {
        String out = value;
        while (out.length() < precision) out += "0";
        return out.length() > precision ? out.substring(0, precision) : out;
    }
}
