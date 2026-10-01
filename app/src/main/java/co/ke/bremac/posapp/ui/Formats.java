package co.ke.bremac.posapp.ui;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Locale;

public final class Formats {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

    private Formats() {
    }

    public static String dateTime(String iso) {
        return format(iso, DATE_TIME);
    }

    public static String time(String iso) {
        return format(iso, TIME);
    }

    public static String quantity(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    public static String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) {
            return "Good morning";
        }
        return hour < 17 ? "Good afternoon" : "Good evening";
    }

    private static String format(String iso, DateTimeFormatter formatter) {
        if (iso == null || iso.isEmpty() || "null".equals(iso)) {
            return "";
        }
        try {
            return OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(formatter);
        } catch (Exception exception) {
            return iso;
        }
    }
}
