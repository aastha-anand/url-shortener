package com.urlshortener;

import com.urlshortener.config.AppProperties;
import com.urlshortener.exception.RateLimitExceededException;
import com.urlshortener.service.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitService unit tests")
class RateLimitServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.getRateLimit().setRedirectPerMinute(5);
        props.getRateLimit().setCreatePerMinute(2);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        rateLimitService = new RateLimitService(redisTemplate, props);
    }

    @Test
    @DisplayName("checkRedirectLimit passes when under the limit")
    void redirectLimit_underLimit() {
        when(valueOps.increment(anyString())).thenReturn(3L);
        assertThatCode(() -> rateLimitService.checkRedirectLimit("1.2.3.4"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("checkRedirectLimit throws when count exceeds limit")
    void redirectLimit_exceeded() {
        when(valueOps.increment(anyString())).thenReturn(6L);
        assertThatThrownBy(() -> rateLimitService.checkRedirectLimit("1.2.3.4"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    @DisplayName("checkCreateLimit throws when count exceeds limit")
    void createLimit_exceeded() {
        when(valueOps.increment(anyString())).thenReturn(3L);
        assertThatThrownBy(() -> rateLimitService.checkCreateLimit("1.2.3.4"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    @DisplayName("checkRedirectLimit sets TTL on first request (count == 1)")
    void redirectLimit_setsTtlOnFirstRequest() {
        when(valueOps.increment(anyString())).thenReturn(1L);
        rateLimitService.checkRedirectLimit("1.2.3.4");
        verify(redisTemplate).expire(anyString(), any());
    }

    @Test
    @DisplayName("Redis failure causes fail-open — no exception thrown")
    void failOpen_onRedisError() {
        when(valueOps.increment(anyString())).thenThrow(new RuntimeException("Redis down"));
        assertThatCode(() -> rateLimitService.checkRedirectLimit("1.2.3.4"))
                .doesNotThrowAnyException();
    }
}
