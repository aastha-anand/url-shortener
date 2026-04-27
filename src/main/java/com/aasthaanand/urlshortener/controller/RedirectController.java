package com.urlshortener.controller;

import com.urlshortener.service.RateLimitService;
import com.urlshortener.service.RedirectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Redirect", description = "Public redirect endpoint")
public class RedirectController {

    private final RedirectService redirectService;
    private final RateLimitService rateLimitService;

    /**
     * The hot path — keep this thin.
     * Rate check -> resolve (cache-first) -> 302 redirect.
     * No auth required; accessible by everyone including guests.
     */
    @GetMapping("/{shortCode}")
    @Operation(summary = "Resolve a short code and redirect to the original URL")
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode,
            HttpServletRequest request) {

        rateLimitService.checkRedirectLimit(request.getRemoteAddr());

        String originalUrl = redirectService.resolveAndRecord(
                shortCode,
                request.getRemoteAddr(),
                request.getHeader("Referer"),
                request.getHeader("User-Agent")
        );

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, originalUrl)
                .build();
    }
}
