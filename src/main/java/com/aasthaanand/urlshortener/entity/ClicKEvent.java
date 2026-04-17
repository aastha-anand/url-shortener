package com.aasthaanand.urlshortener.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "click_events",
        indexes = {
                @Index(name = "idx_click_shorturl", columnList = "short_url_id"),
                @Index(name = "idx_click_time", columnList = "clicked_at")
        }
)

public class ClicKEvent {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "short_url_id", nullable = false)
        private ShortUrl shortUrl;

        @Column(name = "clicked_at", nullable = false)
        private LocalDateTime clickedAt = LocalDateTime.now();

        @Column(name = "ip_hash", nullable = false)
        private String ipHash;

        private String referrer;

        @Column(name = "device_type")
        private String deviceType; // mobile, desktop, tablet

        private String country;

}
