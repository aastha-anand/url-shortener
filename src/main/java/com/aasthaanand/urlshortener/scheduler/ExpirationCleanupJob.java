package com.urlshortener.scheduler;

import com.urlshortener.model.ShortUrl;
import com.urlshortener.repository.ShortUrlRepository;
import com.urlshortener.service.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpirationCleanupJob {

    private final ShortUrlRepository shortUrlRepository;
    private final CacheService cacheService;

    /**
     * Runs every hour. Finds all ACTIVE links that have passed their
     * expiration time, marks them INACTIVE, and evicts them from Redis.
     *
     * Interview note: In a high-traffic system this sweep can fall behind.
     * The redirect path also checks expiry on every DB read (ShortUrl#isAccessible),
     * so correctness is guaranteed even if this job lags.
     */
    @Scheduled(fixedDelay = 3_600_000) // every 1 hour
    @Transactional
    public void expireLinks() {
        List<ShortUrl> expired = shortUrlRepository
                .findByStatusAndExpiresAtBefore(ShortUrl.Status.ACTIVE, LocalDateTime.now());

        if (expired.isEmpty()) return;

        log.info("Expiration sweep: marking {} links as INACTIVE", expired.size());
        for (ShortUrl url : expired) {
            url.setStatus(ShortUrl.Status.INACTIVE);
            cacheService.evict(url.getShortCode());
        }
        shortUrlRepository.saveAll(expired);
    }
}
