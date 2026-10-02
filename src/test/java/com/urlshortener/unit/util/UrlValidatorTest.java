package com.urlshortener.unit.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.urlshortener.util.UrlValidator;
import com.urlshortener.util.UrlValidator.ValidationResult;

@DisplayName("UrlValidator Tests")
class UrlValidatorTest {

    private UrlValidator urlValidator;

    @BeforeEach
    void setUp() {
        urlValidator = new UrlValidator();
    }

    @Nested
    @DisplayName("validate() - Valid URLs")
    class ValidUrls {

        @Test
        @DisplayName("should accept valid HTTPS URL")
        void shouldAcceptValidHttpsUrl() {
            // When
            ValidationResult result = urlValidator.validate("https://example.com");

            // Then
            assertTrue(result.isValid());
            assertNotNull(result.getNormalizedUrl());
            assertNull(result.getError());
        }

        @Test
        @DisplayName("should accept valid HTTP URL")
        void shouldAcceptValidHttpUrl() {
            // When
            ValidationResult result = urlValidator.validate("http://example.com");

            // Then
            assertTrue(result.isValid());
            assertNotNull(result.getNormalizedUrl());
        }

        @Test
        @DisplayName("should accept URL with path")
        void shouldAcceptUrlWithPath() {
            // When
            ValidationResult result = urlValidator.validate("https://example.com/path/to/resource");

            // Then
            assertTrue(result.isValid());
            assertTrue(result.getNormalizedUrl().contains("/path/to/resource"));
        }

        @Test
        @DisplayName("should accept URL with query parameters")
        void shouldAcceptUrlWithQueryParameters() {
            // When
            ValidationResult result = urlValidator.validate("https://example.com/search?q=test&page=1");

            // Then
            assertTrue(result.isValid());
            assertTrue(result.getNormalizedUrl().contains("q=test"));
        }

        @Test
        @DisplayName("should accept URL with fragment")
        void shouldAcceptUrlWithFragment() {
            // When
            ValidationResult result = urlValidator.validate("https://example.com/page#section");

            // Then
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("should accept URL with port")
        void shouldAcceptUrlWithPort() {
            // When
            ValidationResult result = urlValidator.validate("https://example.com:8080/api");

            // Then
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("should accept URL with subdomain")
        void shouldAcceptUrlWithSubdomain() {
            // When
            ValidationResult result = urlValidator.validate("https://www.sub.example.com");

            // Then
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("should add https:// prefix when protocol is missing")
        void shouldAddHttpsPrefixWhenMissing() {
            // When
            ValidationResult result = urlValidator.validate("example.com/path");

            // Then
            assertTrue(result.isValid());
            assertTrue(result.getNormalizedUrl().startsWith("https://"));
        }
    }

    @Nested
    @DisplayName("validate() - Invalid URLs")
    class InvalidUrls {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("should reject null, empty, or whitespace-only URLs")
        void shouldRejectNullEmptyOrWhitespaceUrls(String url) {
            // When
            ValidationResult result = urlValidator.validate(url);

            // Then
            assertFalse(result.isValid());
            assertNotNull(result.getError());
            assertNull(result.getNormalizedUrl());
        }

        @Test
        @DisplayName("should reject URL exceeding maximum length")
        void shouldRejectUrlExceedingMaxLength() {
            // Given
            String longUrl = "https://example.com/" + "a".repeat(2100);

            // When
            ValidationResult result = urlValidator.validate(longUrl);

            // Then
            assertFalse(result.isValid());
            assertTrue(result.getError().contains("maximum length"));
        }
    }

    @Nested
    @DisplayName("validate() - Blocked Protocols (Security)")
    class BlockedProtocols {

        @Test
        @DisplayName("should reject javascript: protocol")
        void shouldRejectJavascriptProtocol() {
            // When
            ValidationResult result = urlValidator.validate("javascript:alert('XSS')");

            // Then
            assertFalse(result.isValid());
            assertTrue(result.getError().contains("javascript"));
        }

        @Test
        @DisplayName("should reject data: protocol")
        void shouldRejectDataProtocol() {
            // When
            ValidationResult result = urlValidator.validate("data:text/html,<script>alert('XSS')</script>");

            // Then
            assertFalse(result.isValid());
            assertTrue(result.getError().contains("data"));
        }

        @Test
        @DisplayName("should reject vbscript: protocol")
        void shouldRejectVbscriptProtocol() {
            // When
            ValidationResult result = urlValidator.validate("vbscript:msgbox('XSS')");

            // Then
            assertFalse(result.isValid());
            assertTrue(result.getError().contains("vbscript"));
        }

        @Test
        @DisplayName("should reject file: protocol")
        void shouldRejectFileProtocol() {
            // When
            ValidationResult result = urlValidator.validate("file:///etc/passwd");

            // Then
            assertFalse(result.isValid());
            assertTrue(result.getError().contains("file"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "JAVASCRIPT:alert(1)",
            "JavaScript:alert(1)",
            "DATA:text/html,test",
            "FILE:///path"
        })
        @DisplayName("should reject blocked protocols case-insensitively")
        void shouldRejectBlockedProtocolsCaseInsensitively(String url) {
            // When
            ValidationResult result = urlValidator.validate(url);

            // Then
            assertFalse(result.isValid());
        }
    }

    @Nested
    @DisplayName("validate() - Protocol Restrictions")
    class ProtocolRestrictions {

        @Test
        @DisplayName("should reject ftp: protocol")
        void shouldRejectFtpProtocol() {
            // When
            ValidationResult result = urlValidator.validate("ftp://files.example.com/file.txt");

            // Then
            assertFalse(result.isValid());
            assertTrue(result.getError().contains("HTTP") || result.getError().contains("HTTPS"));
        }

        @Test
        @DisplayName("should reject mailto: protocol")
        void shouldRejectMailtoProtocol() {
            // When
            ValidationResult result = urlValidator.validate("mailto:user@example.com");

            // Then
            assertFalse(result.isValid());
        }

        @Test
        @DisplayName("should reject tel: protocol")
        void shouldRejectTelProtocol() {
            // When
            ValidationResult result = urlValidator.validate("tel:+1234567890");

            // Then
            assertFalse(result.isValid());
        }
    }

    @Nested
    @DisplayName("validate() - Malformed URLs")
    class MalformedUrls {

        @Test
        @DisplayName("should reject URL with missing host")
        void shouldRejectUrlWithMissingHost() {
            // When
            ValidationResult result = urlValidator.validate("https:///path");

            // Then
            assertFalse(result.isValid());
            assertTrue(result.getError().contains("host") || result.getError().contains("Invalid"));
        }
    }

    @Nested
    @DisplayName("isValidCustomAlias()")
    class CustomAliasValidation {

        @ParameterizedTest
        @ValueSource(strings = {
            "abc",
            "my-link",
            "my_link",
            "MyLink123",
            "a1b2c3",
            "link-with-dashes",
            "link_with_underscores"
        })
        @DisplayName("should accept valid custom aliases")
        void shouldAcceptValidCustomAliases(String alias) {
            // When
            boolean result = urlValidator.isValidCustomAlias(alias);

            // Then
            assertTrue(result, "Should accept alias: " + alias);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "ab",           // Too short (min 3)
            "a",            // Too short
            ""              // Empty
        })
        @DisplayName("should reject aliases shorter than 3 characters")
        void shouldRejectShortAliases(String alias) {
            // When
            boolean result = urlValidator.isValidCustomAlias(alias);

            // Then
            assertFalse(result, "Should reject short alias: " + alias);
        }

        @Test
        @DisplayName("should reject aliases longer than 50 characters")
        void shouldRejectLongAliases() {
            // Given
            String longAlias = "a".repeat(51);

            // When
            boolean result = urlValidator.isValidCustomAlias(longAlias);

            // Then
            assertFalse(result);
        }

        @Test
        @DisplayName("should accept aliases exactly 50 characters")
        void shouldAcceptAliasesExactly50Characters() {
            // Given
            String exactAlias = "a".repeat(50);

            // When
            boolean result = urlValidator.isValidCustomAlias(exactAlias);

            // Then
            assertTrue(result);
        }

        @Test
        @DisplayName("should accept aliases exactly 3 characters")
        void shouldAcceptAliasesExactly3Characters() {
            // When
            boolean result = urlValidator.isValidCustomAlias("abc");

            // Then
            assertTrue(result);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "my link",      // Contains space
            "my.link",      // Contains dot
            "my/link",      // Contains slash
            "my@link",      // Contains @
            "my#link",      // Contains #
            "my$link",      // Contains $
            "my%link",      // Contains %
            "my&link",      // Contains &
            "my+link",      // Contains +
            "my=link"       // Contains =
        })
        @DisplayName("should reject aliases with special characters")
        void shouldRejectAliasesWithSpecialCharacters(String alias) {
            // When
            boolean result = urlValidator.isValidCustomAlias(alias);

            // Then
            assertFalse(result, "Should reject alias with special char: " + alias);
        }

        @Test
        @DisplayName("should reject null alias")
        void shouldRejectNullAlias() {
            // When
            boolean result = urlValidator.isValidCustomAlias(null);

            // Then
            assertFalse(result);
        }

        @ParameterizedTest
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("should reject whitespace-only aliases")
        void shouldRejectWhitespaceOnlyAliases(String alias) {
            // When
            boolean result = urlValidator.isValidCustomAlias(alias);

            // Then
            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("ValidationResult")
    class ValidationResultTests {

        @Test
        @DisplayName("valid() should create valid result with normalized URL")
        void validShouldCreateValidResult() {
            // When
            ValidationResult result = ValidationResult.valid("https://example.com");

            // Then
            assertTrue(result.isValid());
            assertEquals("https://example.com", result.getNormalizedUrl());
            assertNull(result.getError());
        }

        @Test
        @DisplayName("invalid() should create invalid result with error")
        void invalidShouldCreateInvalidResult() {
            // When
            ValidationResult result = ValidationResult.invalid("Error message");

            // Then
            assertFalse(result.isValid());
            assertNull(result.getNormalizedUrl());
            assertEquals("Error message", result.getError());
        }
    }
}
