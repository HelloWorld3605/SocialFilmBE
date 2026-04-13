package com.filmbe.config;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
@Slf4j
@NullMarked
public class RedisCacheConfig implements CachingConfigurer {
    public static final String CATALOG_HOME_CACHE = "catalogHome";
    public static final String CATALOG_LATEST_CACHE = "catalogLatest";
    public static final String CATALOG_LIST_CACHE = "catalogList";
    public static final String CATALOG_SEARCH_CACHE = "catalogSearch";
    public static final String CATALOG_TAXONOMY_CACHE = "catalogTaxonomy";
    public static final String CATALOG_CATEGORY_CACHE = "catalogCategory";
    public static final String CATALOG_COUNTRY_CACHE = "catalogCountry";
    public static final String CATALOG_YEAR_CACHE = "catalogYear";
    public static final String MOVIE_DETAIL_CACHE = "movieDetail";
    public static final String TMDB_CACHE = "tmdb";
    public static final String PUBLIC_CACHE_PREFIX_VERSION = "cache-v2:";

    @Bean
    CacheManager cacheManager(RedisConnectionFactory connectionFactory, AppProperties appProperties) {
        AppProperties.Redis redis = appProperties.cache().redis();
        RedisSerializationContext.SerializationPair<Object> valueSerializer =
                RedisSerializationContext.SerializationPair.fromSerializer(
                        GenericJacksonJsonRedisSerializer.builder()
                                .enableUnsafeDefaultTyping()
                                .build()
                );

        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.string()))
                .serializeValuesWith(valueSerializer)
                .computePrefixWith(cacheName -> redis.keyPrefix() + PUBLIC_CACHE_PREFIX_VERSION + cacheName + "::")
                .entryTtl(redis.defaultTtl());

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withCacheConfiguration(CATALOG_HOME_CACHE, defaults.entryTtl(redis.homeTtl()))
                .withCacheConfiguration(CATALOG_LATEST_CACHE, defaults.entryTtl(redis.latestTtl()))
                .withCacheConfiguration(CATALOG_LIST_CACHE, defaults.entryTtl(redis.listTtl()))
                .withCacheConfiguration(CATALOG_SEARCH_CACHE, defaults.entryTtl(redis.searchTtl()))
                .withCacheConfiguration(CATALOG_TAXONOMY_CACHE, defaults.entryTtl(redis.taxonomyTtl()))
                .withCacheConfiguration(CATALOG_CATEGORY_CACHE, defaults.entryTtl(redis.listTtl()))
                .withCacheConfiguration(CATALOG_COUNTRY_CACHE, defaults.entryTtl(redis.listTtl()))
                .withCacheConfiguration(CATALOG_YEAR_CACHE, defaults.entryTtl(redis.listTtl()))
                .withCacheConfiguration(MOVIE_DETAIL_CACHE, defaults.entryTtl(redis.movieTtl()))
                .withCacheConfiguration(TMDB_CACHE, defaults.entryTtl(redis.tmdbTtl()))
                .build();
    }

    @Override
    @Bean
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis cache GET failed for cache={} key={}", cache.getName(), key, exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, @Nullable Object value) {
                log.warn("Redis cache PUT failed for cache={} key={}", cache.getName(), key, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis cache EVICT failed for cache={} key={}", cache.getName(), key, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Redis cache CLEAR failed for cache={}", cache.getName(), exception);
            }
        };
    }
}
