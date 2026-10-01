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

        URI normalizedUri = new URI(normalized);
        String path = normalizedUri.getRawPath();
        if (path != null && !path.isEmpty()) {
            String strippedPath = stripMobileApiSuffix(path);
            if (!strippedPath.equals(path)) {
                normalized = rebuild(normalizedUri, strippedPath);
            }
        }

        return normalized;
    }

    private static String stripMobileApiSuffix(String path) {
        String candidate = path;
        while (candidate.endsWith("/") && candidate.length() > 1) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }

        String lower = candidate.toLowerCase(java.util.Locale.ROOT);
        String suffix = "/api/mobile/v1";
        if (!lower.endsWith(suffix)) {
            return path;
        }

        String stripped = candidate.substring(0, candidate.length() - suffix.length());

        return stripped.isEmpty() ? "" : stripped;
    }

    private static String rebuild(URI uri, String path) throws URISyntaxException {
        URI rebuilt = new URI(uri.getScheme(), uri.getAuthority(), path.isEmpty() ? null : path, null, null);
        String normalized = rebuilt.toASCIIString();

        while (normalized.endsWith("/") && normalized.length() > "https://x".length()) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }
}
