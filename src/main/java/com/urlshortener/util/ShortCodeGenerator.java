package com.urlshortener.util;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

import com.urlshortener.config.AppProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ShortCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppProperties appProperties;

    /** Generate. */
    public String generate() {
        return generate(appProperties.getShortCodeLength());
    }

    /** Generate. */
    public String generate(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
