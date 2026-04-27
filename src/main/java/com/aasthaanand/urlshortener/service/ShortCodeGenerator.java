package com.urlshortener.service;

import com.urlshortener.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates unique short codes using two strategies:
 *
 * Option A (default): Base62-encode the auto-increment DB primary key.
 *   Pros: deterministic, no collision possible, short output.
 *   Used when the entity ID is already known.
 *
 * Option B: Random 6-char Base62 string with collision retry.
 *   Used when an ID is not yet available (pre-save alias fallback).
 */
@Component
@RequiredArgsConstructor
public class ShortCodeGenerator {

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = 62;
    private static final int DEFAULT_LENGTH = 6;
    private static final int MAX_RETRIES = 5;

    private final ShortUrlRepository shortUrlRepository;
    private final SecureRandom random = new SecureRandom();

    /**
     * Option A — encode a numeric ID to Base62.
     * e.g. id=125 => "CB", id=1000000 => "4c92"
     */
    public String encodeId(long id) {
        if (id == 0) return String.valueOf(BASE62.charAt(0));
        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            sb.append(BASE62.charAt((int)(id % BASE)));
            id /= BASE;
        }
        return sb.reverse().toString();
    }

    /**
     * Option B — random Base62 string with collision retry.
     */
    public String generateRandom() {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            String code = randomCode(DEFAULT_LENGTH + attempt / 2); // grow length on repeated collision
            if (!shortUrlRepository.existsByShortCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Failed to generate unique short code after " + MAX_RETRIES + " attempts");
    }

    private String randomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(BASE62.charAt(random.nextInt(BASE)));
        }
        return sb.toString();
    }

    /** Reserved words that must not be used as short codes or aliases */
    public boolean isReserved(String code) {
        return switch (code.toLowerCase()) {
            case "api", "admin", "login", "register", "dashboard",
                 "health", "swagger", "static", "assets", "favicon" -> true;
            default -> false;
        };
    }
}
