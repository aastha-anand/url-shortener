package com.urlshortener.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String URL_PREFIX = "url:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(1);

    public void putUrl(String shortCode, String originalUrl) {
        try {
            redisTemplate.opsForValue().set(URL_PREFIX + shortCode, originalUrl, DEFAULT_TTL);
            log.debug("Cached shortCode={}", shortCode);
        } catch (Exception e) {
            log.warn("Redis write failed for shortCode={}: {}", shortCode, e.getMessage());
        }
    }

    public Optional<String> getUrl(String shortCode) {
        try {
            Object val = redisTemplate.opsForValue().get(URL_PREFIX + shortCode);
            if (val instanceof String s) {
                log.debug("Cache HIT for shortCode={}", shortCode);
                return Optional.of(s);
            }
        } catch (Exception e) {
            log.warn("Redis read failed for shortCode={}: {}", shortCode, e.getMessage());
        }
        log.debug("Cache MISS for shortCode={}", shortCode);
        return Optional.empty();
    }

    public void evict(String shortCode) {
        try {
            redisTemplate.delete(URL_PREFIX + shortCode);
            log.debug("Evicted cache for shortCode={}", shortCode);
        } catch (Exception e) {
            log.warn("Redis evict failed for shortCode={}: {}", shortCode, e.getMessage());
        }
    }
}
