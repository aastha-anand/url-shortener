package com.urlshortener;

import com.urlshortener.exception.ShortCodeNotFoundException;
import com.urlshortener.model.ShortUrl;
import com.urlshortener.model.User;
import com.urlshortener.repository.ShortUrlRepository;
import com.urlshortener.service.AnalyticsService;
import com.urlshortener.service.CacheService;
import com.urlshortener.service.RedirectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedirectService unit tests")
class RedirectServiceTest {

    @Mock private ShortUrlRepository shortUrlRepository;
    @Mock private CacheService cacheService;
    @Mock private AnalyticsService analyticsService;

    private RedirectService redirectService;

    private final User testUser = User.builder()
            .id(1L).email("user@example.com").name("Test")
            .role(User.Role.USER).status(User.Status.ACTIVE).build();

    @BeforeEach
    void setUp() {
        redirectService = new RedirectService(shortUrlRepository, cacheService, analyticsService);
    }

    @Test
    @DisplayName("Cache HIT — returns URL from Redis, DB is not queried")
    void resolve_cacheHit_doesNotQueryDb() {
        when(cacheService.getUrl("abc")).thenReturn(Optional.of("https://example.com"));

        String result = redirectService.resolveAndRecord("abc", "1.2.3.4", null, null);

        assertThat(result).isEqualTo("https://example.com");
        verify(shortUrlRepository, never()).findByShortCode(any());
        verify(analyticsService).recordClick(eq("abc"), any(), any(), any());
    }

    @Test
    @DisplayName("Cache MISS — falls back to DB and warms cache")
    void resolve_cacheMiss_queriesDbAndWarmsCache() {
        ShortUrl url = ShortUrl.builder()
                .id(1L).shortCode("abc").originalUrl("https://example.com")
                .user(testUser).status(ShortUrl.Status.ACTIVE)
                .expiresAt(LocalDateTime.now().plusDays(10)).clickCount(0L).build();

        when(cacheService.getUrl("abc")).thenReturn(Optional.empty());
        when(shortUrlRepository.findByShortCode("abc")).thenReturn(Optional.of(url));

        String result = redirectService.resolveAndRecord("abc", "1.2.3.4", null, null);

        assertThat(result).isEqualTo("https://example.com");
        verify(cacheService).putUrl("abc", "https://example.com");
        verify(analyticsService).recordClick(eq("abc"), any(), any(), any());
    }

    @Test
    @DisplayName("Unknown short code throws ShortCodeNotFoundException")
    void resolve_unknownCode_throws() {
        when(cacheService.getUrl("missing")).thenReturn(Optional.empty());
        when(shortUrlRepository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> redirectService.resolveAndRecord("missing", "1.2.3.4", null, null))
                .isInstanceOf(ShortCodeNotFoundException.class);
    }

    @Test
    @DisplayName("Expired URL throws ShortCodeNotFoundException")
    void resolve_expiredUrl_throws() {
        ShortUrl expired = ShortUrl.builder()
                .id(2L).shortCode("old").originalUrl("https://example.com")
                .user(testUser).status(ShortUrl.Status.ACTIVE)
                .expiresAt(LocalDateTime.now().minusDays(1)).clickCount(0L).build();

        when(cacheService.getUrl("old")).thenReturn(Optional.empty());
        when(shortUrlRepository.findByShortCode("old")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> redirectService.resolveAndRecord("old", "1.2.3.4", null, null))
                .isInstanceOf(ShortCodeNotFoundException.class);
    }

    @Test
    @DisplayName("Inactive URL throws ShortCodeNotFoundException")
    void resolve_inactiveUrl_throws() {
        ShortUrl inactive = ShortUrl.builder()
                .id(3L).shortCode("off").originalUrl("https://example.com")
                .user(testUser).status(ShortUrl.Status.INACTIVE)
                .expiresAt(LocalDateTime.now().plusDays(10)).clickCount(0L).build();

        when(cacheService.getUrl("off")).thenReturn(Optional.empty());
        when(shortUrlRepository.findByShortCode("off")).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> redirectService.resolveAndRecord("off", "1.2.3.4", null, null))
                .isInstanceOf(ShortCodeNotFoundException.class);
    }
}
