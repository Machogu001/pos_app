package co.ke.bremac.posapp;

import java.net.URI;
import java.net.URISyntaxException;
public final class ServerUrl {
    private ServerUrl() {
    }

    public static String normalize(String input) throws URISyntaxException {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Enter your POS website address.");
        }
        if (!value.matches("(?i)^https?://.*")) {
            value = "https://" + value;
        }

        URI uri = new URI(value).normalize();
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Use a secure HTTPS address.");
        }
        if (uri.getHost() == null || uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("Enter a valid website address.");
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Use the POS website address without a query or fragment.");
        }

        String normalized = uri.toASCIIString();
        int authorityEnd = normalized.indexOf('/', "https://".length());
        if (authorityEnd < 0) {
            authorityEnd = normalized.length();
        }
        normalized = normalized.substring(0, authorityEnd).toLowerCase(java.util.Locale.ROOT)
                + normalized.substring(authorityEnd);
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
