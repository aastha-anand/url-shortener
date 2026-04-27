package com.urlshortener.service;

import com.urlshortener.dto.AnalyticsResponse;
import com.urlshortener.exception.ResourceNotFoundException;
import com.urlshortener.model.ClickEvent;
import com.urlshortener.model.DailyStats;
import com.urlshortener.model.ShortUrl;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.DailyStatsRepository;
import com.urlshortener.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ShortUrlRepository shortUrlRepository;
    private final ClickEventRepository clickEventRepository;
    private final DailyStatsRepository dailyStatsRepository;

    @Transactional
    public void recordClick(String shortCode, String clientIp, String referrer, String userAgent) {
        try {
            ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode).orElse(null);
            if (shortUrl == null) return;

            LocalDateTime now = LocalDateTime.now();
            String ipHash = hashIp(clientIp);
            String deviceType = detectDevice(userAgent);

            ClickEvent event = ClickEvent.builder()
                    .shortUrl(shortUrl)
                    .clickedAt(now)
                    .ipHash(ipHash)
                    .referrer(referrer != null && referrer.length() > 512 ? referrer.substring(0, 512) : referrer)
                    .deviceType(deviceType)
                    .build();
            clickEventRepository.save(event);

            // Increment total click counter on parent entity
            shortUrlRepository.incrementClickCount(shortUrl.getId());

            // Upsert daily stats
            LocalDate today = now.toLocalDate();
            int updated = dailyStatsRepository.incrementDailyCount(shortUrl.getId(), today);
            if (updated == 0) {
                DailyStats stats = DailyStats.builder()
                        .shortUrl(shortUrl)
                        .statDate(today)
                        .clickCount(1L)
                        .build();
                dailyStatsRepository.save(stats);
            }
        } catch (Exception e) {
            // Analytics should never break the redirect path
            log.warn("Analytics recording failed for shortCode={}: {}", shortCode, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse getSummary(Long shortUrlId, String userEmail, int days) {
        ShortUrl shortUrl = shortUrlRepository.findById(shortUrlId)
                .orElseThrow(() -> new ResourceNotFoundException("URL not found"));

        if (!shortUrl.getUser().getEmail().equals(userEmail)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }

        LocalDate from = LocalDate.now().minusDays(days);
        LocalDate to = LocalDate.now();

        List<DailyStats> dailyStats = dailyStatsRepository
                .findByShortUrlIdAndStatDateBetweenOrderByStatDate(shortUrlId, from, to);

        List<AnalyticsResponse.DailyStat> statDtos = dailyStats.stream()
                .map(ds -> new AnalyticsResponse.DailyStat(ds.getStatDate(), ds.getClickCount()))
                .toList();

        return AnalyticsResponse.builder()
                .shortUrlId(shortUrlId)
                .shortCode(shortUrl.getShortCode())
                .totalClicks(shortUrl.getClickCount())
                .dailyStats(statDtos)
                .build();
    }

    private String hashIp(String ip) {
        if (ip == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(ip.getBytes());
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            return null;
        }
    }

    private String detectDevice(String userAgent) {
        if (userAgent == null) return "UNKNOWN";
        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) return "MOBILE";
        if (ua.contains("tablet") || ua.contains("ipad")) return "TABLET";
        return "DESKTOP";
    }
}
