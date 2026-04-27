package com.aasthaanand.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDateTime;

@Data
public class CreateUrlRequest {

    @NotBlank(message = "Original URL is required")
    @URL(message = "Must be a valid URL")
    @Size(max = 2048)
    private String originalUrl;

    @Pattern(regexp = "^[a-zA-Z0-9_-]{3,20}$",
             message = "Alias must be 3-20 alphanumeric characters (hyphens and underscores allowed)")
    private String alias;

    @Size(max = 255)
    private String title;

    private LocalDateTime expiresAt;
}
