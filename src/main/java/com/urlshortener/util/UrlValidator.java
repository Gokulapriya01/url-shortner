package com.urlshortener.util;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Component
public class UrlValidator {

    private static final Set<String> BLOCKED_PROTOCOLS = Set.of(
        "javascript",
        "data",
        "vbscript",
        "file"
    );

    private static final Set<String> ALLOWED_PROTOCOLS = Set.of(
        "http",
        "https"
    );

    private static final Pattern URL_PATTERN = Pattern.compile(
        "^https?://[\\w\\-]+(\\.[\\w\\-]+)+([/?#].*)?$",
        Pattern.CASE_INSENSITIVE
    );

    private static final int MAX_URL_LENGTH = 2048;

    /** Normalizes and validates an HTTP/HTTPS URL without allowing executable schemes. */
    public ValidationResult validate(String url) {
        if (url == null || url.isBlank()) {
            return ValidationResult.invalid("URL is required");
        }

        String trimmedUrl = url.trim();

        if (trimmedUrl.length() > MAX_URL_LENGTH) {
            return ValidationResult.invalid("URL exceeds maximum length of " + MAX_URL_LENGTH + " characters");
        }

        // Check for blocked protocols
        String lowerUrl = trimmedUrl.toLowerCase();
        for (String blocked : BLOCKED_PROTOCOLS) {
            if (lowerUrl.startsWith(blocked + ":")) {
                return ValidationResult.invalid("Protocol '" + blocked + "' is not allowed");
            }
        }

        // Parse and validate URL
        try {
            URI uri = new URI(trimmedUrl);
            String scheme = uri.getScheme();

            if (scheme == null) {
                // Try with https prefix
                trimmedUrl = "https://" + trimmedUrl;
                uri = new URI(trimmedUrl);
                scheme = uri.getScheme();
            }

            if (!ALLOWED_PROTOCOLS.contains(scheme.toLowerCase())) {
                return ValidationResult.invalid("Only HTTP and HTTPS protocols are allowed");
            }

            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return ValidationResult.invalid("Invalid URL: missing host");
            }

            // Validate it's a proper URL
            URL urlObj = uri.toURL();
            String normalizedUrl = urlObj.toString();

            return ValidationResult.valid(normalizedUrl);

        } catch (URISyntaxException | MalformedURLException e) {
            return ValidationResult.invalid("Invalid URL format: " + e.getMessage());
        }
    }

    /** Checks the alias length and permitted ASCII path characters. */
    public boolean isValidCustomAlias(String alias) {
        if (alias == null || alias.isBlank()) {
            return false;
        }

        // 3-50 characters, alphanumeric, hyphens, underscores
        return alias.length() >= 3 &&
               alias.length() <= 50 &&
               alias.matches("^[a-zA-Z0-9_-]+$");
    }

    @Getter
    @AllArgsConstructor
    public static class ValidationResult {
        private final boolean valid;
        private final String normalizedUrl;
        private final String error;

        /** Valid. */
        public static ValidationResult valid(String normalizedUrl) {
            return new ValidationResult(true, normalizedUrl, null);
        }

        /** Invalid. */
        public static ValidationResult invalid(String error) {
            return new ValidationResult(false, null, error);
        }
    }
}
