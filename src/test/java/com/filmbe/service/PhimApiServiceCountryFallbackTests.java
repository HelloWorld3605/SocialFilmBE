package com.filmbe.service;

import com.filmbe.config.AppProperties;
import com.filmbe.dto.CatalogDtos;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhimApiServiceCountryFallbackTests {

    @Test
    void countryFallbackBuildsCatalogLocallyWithoutPassingCountryFilterUpstream() throws Exception {
        TestPhimApiService service = new TestPhimApiService(Map.of(
                "phim-bo", pageOf(
                        movie("match-a", "Vietsub", "tinh-cam", "han-quoc", 2026, "series", "2026-04-18T08:00:00"),
                        movie("skip-country", "Vietsub", "tinh-cam", "nhat-ban", 2026, "series", "2026-04-17T08:00:00")
                ),
                "phim-le", pageOf(
                        movie("skip-category", "Vietsub", "hanh-dong", "han-quoc", 2026, "single", "2026-04-16T08:00:00")
                ),
                "tv-shows", pageOf(
                        movie("skip-year", "Vietsub", "tinh-cam", "han-quoc", 2025, "tvshows", "2026-04-15T08:00:00")
                ),
                "hoat-hinh", pageOf(
                        movie("skip-lang", "Thuyet Minh", "tinh-cam", "han-quoc", 2026, "hoathinh", "2026-04-14T08:00:00")
                )
        ));

        Map<String, String> params = new LinkedHashMap<>();
        params.put("page", "1");
        params.put("limit", "24");
        params.put("sort_field", "modified.time");
        params.put("sort_type", "desc");
        params.put("sort_lang", "Vietsub");
        params.put("category", "tinh-cam");
        params.put("year", "2026");

        CatalogDtos.PagedMovieResponse response = invokeCountryFallback(service, "han-quoc", params);

        assertEquals(1, response.totalItems());
        assertEquals(1, response.items().size());
        assertEquals("match-a", response.items().getFirst().slug());
        assertEquals("han-quoc", response.raw().path("data").path("params").path("country").asText());
        assertEquals("tinh-cam", response.raw().path("data").path("params").path("category").asText());
        assertEquals("2026", response.raw().path("data").path("params").path("year").asText());

        assertEquals(4, service.capturedParams().size());
        for (Map<String, String> requestParams : service.capturedParams()) {
            assertEquals("1", requestParams.get("page"));
            assertEquals("64", requestParams.get("limit"));
            assertEquals("modified.time", requestParams.get("sort_field"));
            assertEquals("desc", requestParams.get("sort_type"));
            assertFalse(requestParams.containsKey("country"));
            assertFalse(requestParams.containsKey("category"));
            assertFalse(requestParams.containsKey("year"));
            assertFalse(requestParams.containsKey("sort_lang"));
        }

        assertTrue(service.requestedTypes().containsAll(List.of("phim-bo", "phim-le", "tv-shows", "hoat-hinh")));
    }

    private CatalogDtos.PagedMovieResponse invokeCountryFallback(
            PhimApiService service,
            String slug,
            Map<String, String> params
    ) throws Exception {
        Method method = PhimApiService.class.getDeclaredMethod("countryDetailFallback", String.class, Map.class);
        method.setAccessible(true);
        return (CatalogDtos.PagedMovieResponse) method.invoke(service, slug, params);
    }

    private static CatalogDtos.PagedMovieResponse pageOf(CatalogDtos.MovieSummary... items) {
        return new CatalogDtos.PagedMovieResponse(
                1,
                1,
                items.length,
                List.of(items),
                JsonNodeFactory.instance.objectNode()
        );
    }

    private static CatalogDtos.MovieSummary movie(
            String slug,
            String lang,
            String categorySlug,
            String countrySlug,
            Integer year,
            String type,
            String modifiedTime
    ) {
        return new CatalogDtos.MovieSummary(
                slug,
                slug,
                slug,
                null,
                null,
                "FHD",
                lang,
                "Full",
                year,
                type,
                List.of(categorySlug),
                List.of(categorySlug),
                List.of(countrySlug),
                List.of(countrySlug),
                null,
                slug,
                modifiedTime
        );
    }

    private static final class TestPhimApiService extends PhimApiService {
        private final Map<String, CatalogDtos.PagedMovieResponse> responses;
        private final List<Map<String, String>> capturedParams = new ArrayList<>();
        private final List<String> requestedTypes = new ArrayList<>();

        private TestPhimApiService(Map<String, CatalogDtos.PagedMovieResponse> responses) {
            super(
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
            this.responses = responses;
        }

        @Override
        public CatalogDtos.PagedMovieResponse list(String type, Map<String, String> params) {
            requestedTypes.add(type);
            capturedParams.add(new LinkedHashMap<>(params));
            return responses.getOrDefault(type, pageOf());
        }

        private List<Map<String, String>> capturedParams() {
            return capturedParams;
        }

        private List<String> requestedTypes() {
            return requestedTypes;
        }
    }
}
