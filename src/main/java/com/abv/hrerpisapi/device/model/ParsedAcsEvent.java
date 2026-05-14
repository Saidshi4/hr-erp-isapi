package com.abv.hrerpisapi.device.model;

import java.net.URI;
import java.time.OffsetDateTime;

/**
 * Internal representation of a single ACS event — used between mapper,
 * alertStream runner, history poller, and ingest service.
 */
public record ParsedAcsEvent(
        long serialNo,
        OffsetDateTime eventTime,
        int majorEventType,
        int subEventType,
        String employeeNoString,
        String cardNo,
        String picturePath,
        String rawJson
) {
    /**
     * pictureURL-dən host hissəsini çıxarıb yalnız path hissəsini qaytarır.
     */
    public static String toPicturePath(String pictureUrl) {
        if (pictureUrl == null || pictureUrl.isBlank()) {
            return null;
        }

        String candidate = pictureUrl.trim();
        try {
            URI uri = URI.create(candidate);
            if (uri.getScheme() != null && uri.getRawAuthority() != null) {
                String path = uri.getRawPath();
                if (path == null || path.isBlank()) return null;
                String query = uri.getRawQuery();
                String fragment = uri.getRawFragment();
                return path
                        + (query != null ? "?" + query : "")
                        + (fragment != null ? "#" + fragment : "");
            }
        } catch (IllegalArgumentException ignored) {
            // Fallback below
        }

        String stripped = candidate.replaceFirst("^(?i)[a-z][a-z0-9+.-]*://[^/]+", "");
        return stripped.isBlank() ? null : stripped;
    }
}
