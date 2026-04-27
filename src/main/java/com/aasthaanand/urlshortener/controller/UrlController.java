package com.urlshortener.controller;

import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.dto.UpdateUrlRequest;
import com.urlshortener.dto.UrlResponse;
import com.urlshortener.service.RateLimitService;
import com.urlshortener.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/urls")
@RequiredArgsConstructor
@Tag(name = "URL Management", description = "Create and manage short URLs")
@SecurityRequirement(name = "Bearer Authentication")
public class UrlController {

    private final UrlService urlService;
    private final RateLimitService rateLimitService;

    @PostMapping
    @Operation(summary = "Create a new short URL")
    public ResponseEntity<UrlResponse> create(
            @Valid @RequestBody CreateUrlRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        rateLimitService.checkCreateLimit(httpRequest.getRemoteAddr());
        UrlResponse response = urlService.createShortUrl(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List all URLs owned by the authenticated user")
    public ResponseEntity<Page<UrlResponse>> list(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<UrlResponse> result = urlService.listByUser(
                userDetails.getUsername(),
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a URL by ID")
    public ResponseEntity<UrlResponse> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(urlService.getById(id, userDetails.getUsername()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a URL's metadata or status")
    public ResponseEntity<UrlResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUrlRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(urlService.update(id, request, userDetails.getUsername()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a URL")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        urlService.softDelete(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
