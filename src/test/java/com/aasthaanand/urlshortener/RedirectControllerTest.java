package com.urlshortener;

import com.urlshortener.exception.ShortCodeNotFoundException;
import com.urlshortener.service.RateLimitService;
import com.urlshortener.service.RedirectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("RedirectController integration tests")
class RedirectControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean  private RedirectService redirectService;
    @MockBean  private RateLimitService rateLimitService;

    @Test
    @DisplayName("GET /{shortCode} returns 302 redirect for a valid code")
    void redirect_validCode() throws Exception {
        when(redirectService.resolveAndRecord(eq("abc123"), any(), any(), any()))
                .thenReturn("https://www.example.com/original-long-path");

        mockMvc.perform(get("/abc123"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://www.example.com/original-long-path"));
    }

    @Test
    @DisplayName("GET /{shortCode} returns 404 for unknown / expired / deleted codes")
    void redirect_unknownCode() throws Exception {
        when(redirectService.resolveAndRecord(eq("missing"), any(), any(), any()))
                .thenThrow(new ShortCodeNotFoundException("missing"));

        mockMvc.perform(get("/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /{shortCode} returns 429 when rate limit is exceeded")
    void redirect_rateLimited() throws Exception {
        doThrow(new com.urlshortener.exception.RateLimitExceededException())
                .when(rateLimitService).checkRedirectLimit(any());

        mockMvc.perform(get("/abc123"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Redirect URL is preserved exactly including query params")
    void redirect_preservesQueryParams() throws Exception {
        String target = "https://example.com/page?ref=twitter&utm_source=link";
        when(redirectService.resolveAndRecord(eq("xkcd"), any(), any(), any()))
                .thenReturn(target);

        mockMvc.perform(get("/xkcd"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", target));
    }
}
