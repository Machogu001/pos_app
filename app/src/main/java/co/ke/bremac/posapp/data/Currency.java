package co.ke.bremac.posapp.data;

import co.ke.bremac.posapp.util.Money;

import org.json.JSONObject;

public class Currency {
    public final String code;
    public final String symbol;
    public final String thousandSeparator;
    public final String decimalSeparator;
    public final int precision;

    public Currency(String code, String symbol, String thousandSeparator, String decimalSeparator, int precision) {
        this.code = code;
        this.symbol = symbol;
        this.thousandSeparator = thousandSeparator;
        this.decimalSeparator = decimalSeparator;
        this.precision = precision;
    }

    public static Currency fromJson(JSONObject object) {
        if (object == null) {
            return new Currency("KES", "KSh", ",", ".", 2);
        }
        return new Currency(
                object.optString("code", "KES"),
                object.optString("symbol", "KSh"),
                object.optString("thousand_separator", ","),
                object.optString("decimal_separator", "."),
                object.optInt("precision", 2));
    }

    public Money toMoney() {
        return new Money(code, symbol, thousandSeparator, decimalSeparator, precision);
    }
}
