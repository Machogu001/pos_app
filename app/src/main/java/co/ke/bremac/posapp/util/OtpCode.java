package co.ke.bremac.posapp.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds the one-time sign-in code in an SMS. */
public final class OtpCode {
    private static final Pattern SIX_DIGITS = Pattern.compile("(?<!\\d)\\d{6}(?!\\d)");

    private OtpCode() {
    }

    /** The first stand-alone 6-digit number in the message, or null. */
    public static String extract(String message) {
        if (message == null) {
            return null;
        }
        Matcher matcher = SIX_DIGITS.matcher(message);
        return matcher.find() ? matcher.group() : null;
    }
}
