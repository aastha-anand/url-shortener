package com.urlshortener.service;

import com.urlshortener.config.AppProperties;
import com.urlshortener.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final AppProperties appProperties;

    private static final String REDIRECT_PREFIX = "rl:redirect:";
    private static final String CREATE_PREFIX   = "rl:create:";

    public void checkRedirectLimit(String clientIp) {
        int limit = appProperties.getRateLimit().getRedirectPerMinute();
        checkLimit(REDIRECT_PREFIX + clientIp, limit, "redirect");
    }

    public void checkCreateLimit(String clientIp) {
        int limit = appProperties.getRateLimit().getCreatePerMinute();
        checkLimit(CREATE_PREFIX + clientIp, limit, "create");
    }

    private void checkLimit(String key, int maxRequests, String action) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) return;
            if (count == 1) {
                redisTemplate.expire(key, Duration.ofMinutes(1));
            }
            if (count > maxRequests) {
                log.warn("Rate limit exceeded for key={} action={} count={}", key, action, count);
                throw new RateLimitExceededException();
            }
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (Exception e) {
            // Redis unavailable — fail open (allow request) to avoid outage
            log.warn("Rate limit Redis error, failing open: {}", e.getMessage());
        }
    }
}
