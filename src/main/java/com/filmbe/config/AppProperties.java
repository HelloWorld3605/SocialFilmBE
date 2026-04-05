package com.filmbe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Cors cors,
        Jwt jwt,
        PhimApi phimApi,
        Cache cache
) {
    public record Cors(String allowedOrigins) {
    }

    public record Jwt(String secret, long expirationMinutes) {
    }

    public record PhimApi(String baseUrl, String imageProxyUrl) {
    }

    public record Cache(Redis redis, WatchProgress watchProgress) {
    }

    public record Redis(
            String keyPrefix,
            Duration defaultTtl,
            Duration homeTtl,
            Duration latestTtl,
            Duration listTtl,
            Duration searchTtl,
            Duration taxonomyTtl,
            Duration movieTtl,
            Duration tmdbTtl
    ) {
    }

    public record WatchProgress(
            Duration redisTtl,
            Duration dbFlushInterval,
            Integer dbFlushPositionDeltaSeconds,
            Duration schedulerDelay,
            Integer schedulerBatchSize
    ) {
    }
}
