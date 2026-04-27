package com.urlshortener;

import com.urlshortener.repository.ShortUrlRepository;
import com.urlshortener.service.ShortCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShortCodeGenerator unit tests")
class ShortCodeGeneratorTest {

    @Mock
    private ShortUrlRepository shortUrlRepository;

    private ShortCodeGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new ShortCodeGenerator(shortUrlRepository);
    }

    // ---- Base62 encoding tests ----

    @Test
    @DisplayName("encodeId(0) returns '0'")
    void encodeId_zero() {
        assertThat(generator.encodeId(0)).isEqualTo("0");
    }

    @Test
    @DisplayName("encodeId(62) returns '10' in Base62")
    void encodeId_sixty_two() {
        assertThat(generator.encodeId(62)).isEqualTo("10");
    }

    @Test
    @DisplayName("encodeId produces short, non-blank strings for large IDs")
    void encodeId_large() {
        String code = generator.encodeId(1_000_000L);
        assertThat(code).isNotBlank().hasSizeLessThanOrEqualTo(8);
    }

    @Test
    @DisplayName("encodeId output is deterministic — same input, same output")
    void encodeId_deterministic() {
        assertThat(generator.encodeId(12345L)).isEqualTo(generator.encodeId(12345L));
    }

    @Test
    @DisplayName("encodeId produces unique codes for sequential IDs")
    void encodeId_unique_for_sequential_ids() {
        Set<String> codes = new HashSet<>();
        for (long i = 1; i <= 1000; i++) {
            codes.add(generator.encodeId(i));
        }
        assertThat(codes).hasSize(1000);
    }

    @Test
    @DisplayName("encodeId only uses Base62 characters")
    void encodeId_base62_chars_only() {
        String allowed = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        for (long i = 0; i < 500; i++) {
            String code = generator.encodeId(i);
            for (char c : code.toCharArray()) {
                assertThat(allowed).contains(String.valueOf(c));
            }
        }
    }

    // ---- Random generation tests ----

    @Test
    @DisplayName("generateRandom returns a non-blank code when no collision")
    void generateRandom_noCollision() {
        when(shortUrlRepository.existsByShortCode(anyString())).thenReturn(false);
        String code = generator.generateRandom();
        assertThat(code).isNotBlank();
    }

    @Test
    @DisplayName("generateRandom retries on collision and eventually succeeds")
    void generateRandom_retriesOnCollision() {
        // First 3 calls return collision, then succeeds
        when(shortUrlRepository.existsByShortCode(anyString()))
                .thenReturn(true, true, true, false);
        String code = generator.generateRandom();
        assertThat(code).isNotBlank();
    }

    @Test
    @DisplayName("generateRandom throws after max retries all collide")
    void generateRandom_throwsAfterMaxRetries() {
        when(shortUrlRepository.existsByShortCode(anyString())).thenReturn(true);
        assertThatThrownBy(() -> generator.generateRandom())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unique short code");
    }

    // ---- Reserved words tests ----

    @Test
    @DisplayName("isReserved returns true for 'api'")
    void isReserved_api() {
        assertThat(generator.isReserved("api")).isTrue();
    }

    @Test
    @DisplayName("isReserved returns true for 'admin'")
    void isReserved_admin() {
        assertThat(generator.isReserved("ADMIN")).isTrue();
    }

    @Test
    @DisplayName("isReserved returns false for a normal alias")
    void isReserved_normalAlias() {
        assertThat(generator.isReserved("my-link")).isFalse();
    }
}
