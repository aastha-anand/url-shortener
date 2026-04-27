package com.aasthaanand.urlshortener.dto;

import com.aasthaanand.urlshortener.entity.ShortUrl;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlResponse {
    private Long id;
    private String shortCode;
    private String shortUrl;
    private String originalUrl;
    private String title;
    private ShortUrl.Status status;
    private Long clickCount;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private boolean expired;
}
