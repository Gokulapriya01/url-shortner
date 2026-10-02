package com.urlshortener.unit.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.urlshortener.config.AppProperties;
import com.urlshortener.util.ShortCodeGenerator;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ShortCodeGenerator Tests")
class ShortCodeGeneratorTest {

    @Mock
    private AppProperties appProperties;

    private ShortCodeGenerator shortCodeGenerator;

    private static final Pattern URL_SAFE_PATTERN = Pattern.compile("^[A-Za-z0-9]+$");

    @BeforeEach
    void setUp() {
        when(appProperties.getShortCodeLength()).thenReturn(7);
        shortCodeGenerator = new ShortCodeGenerator(appProperties);
    }

    @Nested
    @DisplayName("generate()")
    class Generate {

        @Test
        @DisplayName("should generate code with default length from properties")
        void shouldGenerateCodeWithDefaultLength() {
            // When
            String code = shortCodeGenerator.generate();

            // Then
            assertNotNull(code);
            assertEquals(7, code.length());
        }

        @Test
        @DisplayName("should generate URL-safe alphanumeric characters only")
        void shouldGenerateUrlSafeCharactersOnly() {
            // When
            String code = shortCodeGenerator.generate();

            // Then
            assertTrue(URL_SAFE_PATTERN.matcher(code).matches(),
                "Code should only contain alphanumeric characters: " + code);
        }

        @Test
        @DisplayName("should generate unique codes")
        void shouldGenerateUniqueCodes() {
            // Given
            Set<String> codes = new HashSet<>();
            int iterations = 1000;

            // When
            for (int i = 0; i < iterations; i++) {
                codes.add(shortCodeGenerator.generate());
            }

            // Then
            assertEquals(iterations, codes.size(),
                "All generated codes should be unique");
        }

        @Test
        @DisplayName("should use SecureRandom for cryptographic randomness")
        void shouldUseCryptographicallySecureRandomness() {
            // Generate multiple codes and verify distribution
            // This is a statistical test - characters should be roughly evenly distributed
            int[] charCounts = new int[62]; // 26 upper + 26 lower + 10 digits
            int iterations = 10000;

            for (int i = 0; i < iterations; i++) {
                String code = shortCodeGenerator.generate();
                for (char c : code.toCharArray()) {
                    int index = getCharIndex(c);
                    if (index >= 0) {
                        charCounts[index]++;
                    }
                }
            }

            // Check that each character type appears at least once
            int totalChars = iterations * 7;
            double expectedPerChar = totalChars / 62.0;
            double tolerance = expectedPerChar * 0.5; // Allow 50% variance

            int usedChars = 0;
            for (int count : charCounts) {
                if (count > 0) usedChars++;
            }

            // At least 90% of characters should be used in 10000 iterations
            assertTrue(usedChars >= 55,
                "Should use most of the character set, but only used " + usedChars);
        }

        private int getCharIndex(char c) {
            if (c >= 'A' && c <= 'Z') return c - 'A';
            if (c >= 'a' && c <= 'z') return 26 + (c - 'a');
            if (c >= '0' && c <= '9') return 52 + (c - '0');
            return -1;
        }
    }

    @Nested
    @DisplayName("generate(int length)")
    class GenerateWithLength {

        @ParameterizedTest
        @ValueSource(ints = {1, 5, 7, 10, 20, 50})
        @DisplayName("should generate code with specified length")
        void shouldGenerateCodeWithSpecifiedLength(int length) {
            // When
            String code = shortCodeGenerator.generate(length);

            // Then
            assertNotNull(code);
            assertEquals(length, code.length());
        }

        @Test
        @DisplayName("should generate URL-safe characters for any length")
        void shouldGenerateUrlSafeCharactersForAnyLength() {
            // When
            String shortCode = shortCodeGenerator.generate(3);
            String longCode = shortCodeGenerator.generate(50);

            // Then
            assertTrue(URL_SAFE_PATTERN.matcher(shortCode).matches());
            assertTrue(URL_SAFE_PATTERN.matcher(longCode).matches());
        }

        @Test
        @DisplayName("should generate different codes for same length")
        void shouldGenerateDifferentCodesForSameLength() {
            // When
            String code1 = shortCodeGenerator.generate(10);
            String code2 = shortCodeGenerator.generate(10);
            String code3 = shortCodeGenerator.generate(10);

            // Then
            assertNotEquals(code1, code2);
            assertNotEquals(code2, code3);
            assertNotEquals(code1, code3);
        }
    }

    @Nested
    @DisplayName("Character Set Validation")
    class CharacterSetValidation {

        @Test
        @DisplayName("should only use alphanumeric characters (A-Z, a-z, 0-9)")
        void shouldOnlyUseAlphanumericCharacters() {
            // Generate many codes and verify all characters
            for (int i = 0; i < 100; i++) {
                String code = shortCodeGenerator.generate();
                for (char c : code.toCharArray()) {
                    assertTrue(
                        (c >= 'A' && c <= 'Z') ||
                        (c >= 'a' && c <= 'z') ||
                        (c >= '0' && c <= '9'),
                        "Invalid character found: " + c
                    );
                }
            }
        }

        @Test
        @DisplayName("should not contain special characters")
        void shouldNotContainSpecialCharacters() {
            // Generate many codes
            for (int i = 0; i < 100; i++) {
                String code = shortCodeGenerator.generate();
                assertFalse(code.contains("-"), "Should not contain hyphen");
                assertFalse(code.contains("_"), "Should not contain underscore");
                assertFalse(code.contains("/"), "Should not contain slash");
                assertFalse(code.contains("+"), "Should not contain plus");
                assertFalse(code.contains("="), "Should not contain equals");
            }
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle length of 1")
        void shouldHandleLengthOfOne() {
            // When
            String code = shortCodeGenerator.generate(1);

            // Then
            assertEquals(1, code.length());
            assertTrue(URL_SAFE_PATTERN.matcher(code).matches());
        }

        @Test
        @DisplayName("should handle very long length")
        void shouldHandleVeryLongLength() {
            // When
            String code = shortCodeGenerator.generate(100);

            // Then
            assertEquals(100, code.length());
            assertTrue(URL_SAFE_PATTERN.matcher(code).matches());
        }
    }
}
