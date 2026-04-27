package com.urlshortener.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String baseUrl = "http://localhost:8080";
    private int shortCodeLength = 6;
    private int defaultTtlDays = 30;
    private RateLimit rateLimit = new RateLimit();
    private Jwt jwt = new Jwt();

    @Data
    public static class RateLimit {
        private int redirectPerMinute = 60;
        private int createPerMinute = 10;
    }

    @Data
    public static class Jwt {
        private String secret;
        private long expirationMs = 86400000L;
    }
}
