package com.urlshortener.service;

import com.urlshortener.exception.ShortCodeNotFoundException;
import com.urlshortener.model.ShortUrl;
import com.urlshortener.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedirectService {

    private final ShortUrlRepository shortUrlRepository;
    private final CacheService cacheService;
    private final AnalyticsService analyticsService;

    /**
     * Cache-first redirect resolution.
     *
     * 1. Check Redis — O(1) lookup, p95 < 5ms
     * 2. On miss, fallback to DB — validates status and expiry
     * 3. Warm the cache for subsequent requests
     * 4. Record analytics asynchronously
     *
     * Interview talking point: this path is intentionally read-optimised.
     * The analytics write is the only mutation here and could be moved to
     * an async queue (Kafka/RabbitMQ) in a v2 to reduce redirect latency.
     */
    @Transactional
    public String resolveAndRecord(String shortCode, String clientIp, String referrer, String userAgent) {
        // Step 1: Cache lookup
        String cachedUrl = cacheService.getUrl(shortCode).orElse(null);
        if (cachedUrl != null) {
            analyticsService.recordClick(shortCode, clientIp, referrer, userAgent);
            return cachedUrl;
        }

        // Step 2: DB fallback
        ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortCodeNotFoundException(shortCode));

        if (!shortUrl.isAccessible()) {
            log.debug("Short code not accessible: {} status={} expired={}", shortCode, shortUrl.getStatus(), shortUrl.isExpired());
            throw new ShortCodeNotFoundException(shortCode);
        }

        // Step 3: Warm cache
        cacheService.putUrl(shortCode, shortUrl.getOriginalUrl());

        // Step 4: Record analytics
        analyticsService.recordClick(shortCode, clientIp, referrer, userAgent);

        return shortUrl.getOriginalUrl();
    }
}
