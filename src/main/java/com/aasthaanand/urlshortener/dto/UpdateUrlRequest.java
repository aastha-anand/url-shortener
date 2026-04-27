package com.aasthaanand.urlshortener.dto;

import com.aasthaanand.urlshortener.entity.ShortUrl;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdateUrlRequest {

    @Size(max = 255)
    private String title;

    private LocalDateTime expiresAt;

    private ShortUrl.Status status;
}
