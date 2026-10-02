package com.urlshortener.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

@Component
public class IpHasher {

    private static final String ALGORITHM = "SHA-256";

    /** Returns a SHA-256 digest of the normalized client IP. */
    public String hash(String ip) {
        if (ip == null || ip.isBlank()) {
            return hashBytes("unknown".getBytes(StandardCharsets.UTF_8));
        }

        // Normalize IP address
        String normalizedIp = normalizeIp(ip);
        return hashBytes(normalizedIp.getBytes(StandardCharsets.UTF_8));
    }

    private String hashBytes(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String normalizeIp(String ip) {
        // Handle X-Forwarded-For format (may contain multiple IPs)
        if (ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        // Remove port if present (IPv4)
        if (ip.contains(":") && !ip.contains("[")) {
            int lastColon = ip.lastIndexOf(':');
            // Simple check: if there's only one colon, it's likely port separator
            if (ip.indexOf(':') == lastColon) {
                ip = ip.substring(0, lastColon);
            }
        }

        return ip.trim().toLowerCase();
    }
}
