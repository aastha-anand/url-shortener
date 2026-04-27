package com.urlshortener.service;

import com.urlshortener.config.AppProperties;
import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.dto.UpdateUrlRequest;
import com.urlshortener.dto.UrlResponse;
import com.urlshortener.exception.AliasConflictException;
import com.urlshortener.exception.BadRequestException;
import com.urlshortener.exception.ResourceNotFoundException;
import com.urlshortener.model.ShortUrl;
import com.urlshortener.model.User;
import com.urlshortener.repository.BlockedDomainRepository;
import com.urlshortener.repository.ShortUrlRepository;
import com.urlshortener.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlService {

    private final ShortUrlRepository shortUrlRepository;
    private final UserRepository userRepository;
    private final BlockedDomainRepository blockedDomainRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final CacheService cacheService;
    private final AppProperties appProperties;

    @Transactional
    public UrlResponse createShortUrl(CreateUrlRequest request, String userEmail) {
        validateUrl(request.getOriginalUrl());

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String shortCode;
        if (StringUtils.hasText(request.getAlias())) {
            shortCode = request.getAlias().toLowerCase();
            if (shortCodeGenerator.isReserved(shortCode)) {
                throw new BadRequestException("Alias '" + shortCode + "' is reserved");
            }
            if (shortUrlRepository.existsByShortCode(shortCode)) {
                throw new AliasConflictException(shortCode);
            }
        } else {
            shortCode = null; // assigned after save via Option A
        }

        LocalDateTime expiresAt = request.getExpiresAt();
        if (expiresAt == null) {
            expiresAt = LocalDateTime.now().plusDays(appProperties.getDefaultTtlDays());
        }

        ShortUrl shortUrl = ShortUrl.builder()
                .shortCode(shortCode != null ? shortCode : "tmp")
                .originalUrl(request.getOriginalUrl())
                .title(request.getTitle())
                .user(user)
                .status(ShortUrl.Status.ACTIVE)
                .expiresAt(expiresAt)
                .build();

        shortUrl = shortUrlRepository.save(shortUrl);

        // Option A: assign Base62(id) if no custom alias was provided
        if (!StringUtils.hasText(request.getAlias())) {
            shortUrl.setShortCode(shortCodeGenerator.encodeId(shortUrl.getId()));
            shortUrl = shortUrlRepository.save(shortUrl);
        }

        cacheService.putUrl(shortUrl.getShortCode(), shortUrl.getOriginalUrl());
        log.info("Created short URL: {} -> {}", shortUrl.getShortCode(), shortUrl.getOriginalUrl());
        return toResponse(shortUrl);
    }

    @Transactional(readOnly = true)
    public Page<UrlResponse> listByUser(String userEmail, Pageable pageable) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return shortUrlRepository
                .findByUserIdAndStatusNotOrderByCreatedAtDesc(user.getId(), ShortUrl.Status.DELETED, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UrlResponse getById(Long id, String userEmail) {
        ShortUrl shortUrl = findOwned(id, userEmail);
        return toResponse(shortUrl);
    }

    @Transactional
    public UrlResponse update(Long id, UpdateUrlRequest request, String userEmail) {
        ShortUrl shortUrl = findOwned(id, userEmail);

        if (request.getTitle() != null) shortUrl.setTitle(request.getTitle());
        if (request.getExpiresAt() != null) shortUrl.setExpiresAt(request.getExpiresAt());
        if (request.getStatus() != null && request.getStatus() != ShortUrl.Status.DELETED) {
            shortUrl.setStatus(request.getStatus());
        }

        shortUrl = shortUrlRepository.save(shortUrl);
        cacheService.evict(shortUrl.getShortCode());

        if (shortUrl.isAccessible()) {
            cacheService.putUrl(shortUrl.getShortCode(), shortUrl.getOriginalUrl());
        }

        return toResponse(shortUrl);
    }

    @Transactional
    public void softDelete(Long id, String userEmail) {
        ShortUrl shortUrl = findOwned(id, userEmail);
        shortUrl.setStatus(ShortUrl.Status.DELETED);
        shortUrlRepository.save(shortUrl);
        cacheService.evict(shortUrl.getShortCode());
        log.info("Soft-deleted shortCode={}", shortUrl.getShortCode());
    }

    // ---- helpers ----

    private ShortUrl findOwned(Long id, String userEmail) {
        ShortUrl shortUrl = shortUrlRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("URL not found with id: " + id));
        if (!shortUrl.getUser().getEmail().equals(userEmail)) {
            throw new AccessDeniedException("You do not own this URL");
        }
        if (shortUrl.getStatus() == ShortUrl.Status.DELETED) {
            throw new ResourceNotFoundException("URL not found with id: " + id);
        }
        return shortUrl;
    }

    private void validateUrl(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new BadRequestException("Only http and https URLs are allowed");
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new BadRequestException("URL has no valid host");
            }
            // Strip 'www.' prefix for domain check
            String domain = host.startsWith("www.") ? host.substring(4) : host;
            if (blockedDomainRepository.existsByDomainAndActiveTrue(domain)) {
                throw new BadRequestException("This domain is not allowed");
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Invalid URL: " + e.getMessage());
        }
    }

    public UrlResponse toResponse(ShortUrl s) {
        return UrlResponse.builder()
                .id(s.getId())
                .shortCode(s.getShortCode())
                .shortUrl(appProperties.getBaseUrl() + "/" + s.getShortCode())
                .originalUrl(s.getOriginalUrl())
                .title(s.getTitle())
                .status(s.getStatus())
                .clickCount(s.getClickCount())
                .expiresAt(s.getExpiresAt())
                .createdAt(s.getCreatedAt())
                .expired(s.isExpired())
                .build();
    }
}
