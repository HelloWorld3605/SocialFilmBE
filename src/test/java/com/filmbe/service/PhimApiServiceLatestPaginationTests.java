package com.filmbe.service;

import com.filmbe.config.AppProperties;
import com.filmbe.dto.CatalogDtos;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhimApiServiceLatestPaginationTests {

    @Test
    void normalizePagedReadsPaginationAtRootForLatestFeeds() throws Exception {
        PhimApiService service = buildService();
        ObjectNode raw = JsonNodeFactory.instance.objectNode();
        ObjectNode pagination = raw.putObject("pagination");
        pagination.put("currentPage", 1);
        pagination.put("totalPages", 32);
        pagination.put("totalItems", 768);

        ObjectNode item = raw.putArray("items").addObject();
        item.put("_id", "movie-1");
        item.put("slug", "movie-1");
        item.put("name", "Movie 1");
        item.put("origin_name", "Movie 1");
        item.put("quality", "FHD");
        item.put("lang", "Vietsub");
        item.put("episode_current", "Tap 1");
        item.put("year", 2026);
        item.put("type", "series");
        item.putObject("modified").put("time", "2026-04-21T00:00:00.000Z");

        CatalogDtos.PagedMovieResponse response = invokeNormalizePaged(service, raw, "items");

        assertEquals(1, response.page());
        assertEquals(32, response.totalPages());
        assertEquals(768, response.totalItems());
        assertEquals(1, response.items().size());
        assertEquals("movie-1", response.items().getFirst().slug());
    }

    private CatalogDtos.PagedMovieResponse invokeNormalizePaged(
            PhimApiService service,
            ObjectNode raw,
            String fallbackField
    ) throws Exception {
        Method method = PhimApiService.class.getDeclaredMethod("normalizePaged", tools.jackson.databind.JsonNode.class, String.class);
        method.setAccessible(true);
        return (CatalogDtos.PagedMovieResponse) method.invoke(service, raw, fallbackField);
    }

    private PhimApiService buildService() {
        return new PhimApiService(
                new AppProperties(
                        new AppProperties.Cors("*"),
                        new AppProperties.Jwt("secret", 60),
                        new AppProperties.PhimApi("https://phimapi.com", "https://phimapi.com/image.php"),
                        new AppProperties.Cache(
                                new AppProperties.Redis(
                                        "filmbe",
                                        Duration.ofMinutes(5),
                                        Duration.ofMinutes(5),
                                        Duration.ofMinutes(5),
                                        Duration.ofMinutes(5),
                                        Duration.ofMinutes(5),
                                        Duration.ofMinutes(5),
                                        Duration.ofMinutes(5),
                                        Duration.ofMinutes(5)
                                ),
                                new AppProperties.WatchProgress(
                                        Duration.ofMinutes(5),
                                        Duration.ofMinutes(1),
                                        30,
                                        Duration.ofSeconds(30),
                                        100
                                )
                        )
                ),
                new ConcurrentMapCacheManager(),
                new ObjectMapper(),
                null
        );
    }
}
