package com.aasthaanand.urlshortener.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "daily_stats",
    uniqueConstraints = @UniqueConstraint(columnNames = {"short_url_id", "stat_date"}),
    indexes = @Index(name = "idx_daily_stats_url_date", columnList = "short_url_id, stat_date")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "short_url_id", nullable = false)
    private ShortUrl shortUrl;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "click_count", nullable = false)
    @Builder.Default
    private Long clickCount = 0L;
}
