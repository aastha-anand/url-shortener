package com.urlshortener;

import com.urlshortener.config.AppProperties;
import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.dto.UpdateUrlRequest;
import com.urlshortener.dto.UrlResponse;
import com.urlshortener.exception.AliasConflictException;
import com.urlshortener.exception.BadRequestException;
import com.urlshortener.model.ShortUrl;
import com.urlshortener.model.User;
import com.urlshortener.repository.BlockedDomainRepository;
import com.urlshortener.repository.ShortUrlRepository;
import com.urlshortener.repository.UserRepository;
import com.urlshortener.service.CacheService;
import com.urlshortener.service.ShortCodeGenerator;
import com.urlshortener.service.UrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UrlService unit tests")
class UrlServiceTest {

    @Mock private ShortUrlRepository shortUrlRepository;
    @Mock private UserRepository userRepository;
    @Mock private BlockedDomainRepository blockedDomainRepository;
    @Mock private ShortCodeGenerator shortCodeGenerator;
    @Mock private CacheService cacheService;

    private UrlService urlService;

    private User testUser;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.setBaseUrl("http://localhost:8080");
        props.setDefaultTtlDays(30);

        urlService = new UrlService(shortUrlRepository, userRepository,
                blockedDomainRepository, shortCodeGenerator, cacheService, props);

        testUser = User.builder()
                .id(1L).name("Test User").email("test@example.com")
                .role(User.Role.USER).status(User.Status.ACTIVE).build();
    }

    // ---- createShortUrl ----

    @Test
    @DisplayName("createShortUrl with no alias generates a Base62 code from ID")
    void createShortUrl_autoCode() {
        CreateUrlRequest req = new CreateUrlRequest();
        req.setOriginalUrl("https://example.com/very/long/path");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(blockedDomainRepository.existsByDomainAndActiveTrue(anyString())).thenReturn(false);

        ShortUrl saved = ShortUrl.builder().id(10L).shortCode("tmp")
                .originalUrl(req.getOriginalUrl()).user(testUser)
                .status(ShortUrl.Status.ACTIVE).clickCount(0L)
                .expiresAt(LocalDateTime.now().plusDays(30)).build();

        when(shortUrlRepository.save(any())).thenReturn(saved);
        when(shortCodeGenerator.encodeId(10L)).thenReturn("A");

        UrlResponse response = urlService.createShortUrl(req, "test@example.com");

        assertThat(response).isNotNull();
        verify(shortCodeGenerator).encodeId(10L);
        verify(cacheService).putUrl(any(), eq(req.getOriginalUrl()));
    }

    @Test
    @DisplayName("createShortUrl with custom alias stores the alias directly")
    void createShortUrl_customAlias() {
        CreateUrlRequest req = new CreateUrlRequest();
        req.setOriginalUrl("https://example.com");
        req.setAlias("mylink");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(blockedDomainRepository.existsByDomainAndActiveTrue(anyString())).thenReturn(false);
        when(shortCodeGenerator.isReserved("mylink")).thenReturn(false);
        when(shortUrlRepository.existsByShortCode("mylink")).thenReturn(false);

        ShortUrl saved = ShortUrl.builder().id(5L).shortCode("mylink")
                .originalUrl(req.getOriginalUrl()).user(testUser)
                .status(ShortUrl.Status.ACTIVE).clickCount(0L)
                .expiresAt(LocalDateTime.now().plusDays(30)).build();
        when(shortUrlRepository.save(any())).thenReturn(saved);

        UrlResponse response = urlService.createShortUrl(req, "test@example.com");

        assertThat(response.getShortCode()).isEqualTo("mylink");
        verify(shortCodeGenerator, never()).encodeId(anyLong());
    }

    @Test
    @DisplayName("createShortUrl throws AliasConflictException when alias is taken")
    void createShortUrl_aliasConflict() {
        CreateUrlRequest req = new CreateUrlRequest();
        req.setOriginalUrl("https://example.com");
        req.setAlias("taken");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(blockedDomainRepository.existsByDomainAndActiveTrue(anyString())).thenReturn(false);
        when(shortCodeGenerator.isReserved("taken")).thenReturn(false);
        when(shortUrlRepository.existsByShortCode("taken")).thenReturn(true);

        assertThatThrownBy(() -> urlService.createShortUrl(req, "test@example.com"))
                .isInstanceOf(AliasConflictException.class);
    }

    @Test
    @DisplayName("createShortUrl throws BadRequestException for a reserved alias")
    void createShortUrl_reservedAlias() {
        CreateUrlRequest req = new CreateUrlRequest();
        req.setOriginalUrl("https://example.com");
        req.setAlias("api");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(blockedDomainRepository.existsByDomainAndActiveTrue(anyString())).thenReturn(false);
        when(shortCodeGenerator.isReserved("api")).thenReturn(true);

        assertThatThrownBy(() -> urlService.createShortUrl(req, "test@example.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("reserved");
    }

    @Test
    @DisplayName("createShortUrl rejects blocked domains")
    void createShortUrl_blockedDomain() {
        CreateUrlRequest req = new CreateUrlRequest();
        req.setOriginalUrl("https://malicious.com/payload");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(blockedDomainRepository.existsByDomainAndActiveTrue("malicious.com")).thenReturn(true);

        assertThatThrownBy(() -> urlService.createShortUrl(req, "test@example.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not allowed");
    }

    // ---- softDelete ----

    @Test
    @DisplayName("softDelete marks status as DELETED and evicts cache")
    void softDelete_marksDeleted() {
        ShortUrl url = ShortUrl.builder().id(1L).shortCode("abc123")
                .originalUrl("https://example.com").user(testUser)
                .status(ShortUrl.Status.ACTIVE).clickCount(0L).build();

        when(shortUrlRepository.findById(1L)).thenReturn(Optional.of(url));
        when(shortUrlRepository.save(any())).thenReturn(url);

        urlService.softDelete(1L, "test@example.com");

        assertThat(url.getStatus()).isEqualTo(ShortUrl.Status.DELETED);
        verify(cacheService).evict("abc123");
    }

    // ---- update ----

    @Test
    @DisplayName("update flushes cache after status change")
    void update_evictsCache() {
        ShortUrl url = ShortUrl.builder().id(1L).shortCode("xyz")
                .originalUrl("https://example.com").user(testUser)
                .status(ShortUrl.Status.ACTIVE).clickCount(0L)
                .expiresAt(LocalDateTime.now().plusDays(10)).build();

        UpdateUrlRequest req = new UpdateUrlRequest();
        req.setStatus(ShortUrl.Status.INACTIVE);

        when(shortUrlRepository.findById(1L)).thenReturn(Optional.of(url));
        when(shortUrlRepository.save(any())).thenReturn(url);

        urlService.update(1L, req, "test@example.com");

        verify(cacheService).evict("xyz");
    }

    // ---- listByUser ----

    @Test
    @DisplayName("listByUser returns a page of responses")
    void listByUser_returnsMappedPage() {
        ShortUrl url = ShortUrl.builder().id(1L).shortCode("abc")
                .originalUrl("https://example.com").user(testUser)
                .status(ShortUrl.Status.ACTIVE).clickCount(5L)
                .expiresAt(LocalDateTime.now().plusDays(10)).build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(shortUrlRepository.findByUserIdAndStatusNotOrderByCreatedAtDesc(
                eq(1L), eq(ShortUrl.Status.DELETED), any()))
                .thenReturn(new PageImpl<>(List.of(url)));

        var page = urlService.listByUser("test@example.com", PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getShortCode()).isEqualTo("abc");
    }
}
