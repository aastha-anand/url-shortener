package com.aasthaanand.urlshortener.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsResponse {
    private Long shortUrlId;
    private String shortCode;
    private Long totalClicks;
    private List<DailyStat> dailyStats;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DailyStat {
        private LocalDate date;
        private Long clicks;
    }
}
