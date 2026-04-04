package com.filmbe.service;

import com.filmbe.config.AppProperties;
import com.filmbe.dto.CatalogDtos;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

@Service
@Slf4j
@RequiredArgsConstructor
public class PhimApiService {

    private final AppProperties appProperties;

    private RestClient restClient() {
        return RestClient.builder()
                .baseUrl(appProperties.phimApi().baseUrl())
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public CatalogDtos.HomeResponse home() {
        return new CatalogDtos.HomeResponse(
                safeHomeSection("latest", () -> latest(1, "v3").items()),
                safeHomeSection("series", () -> list("phim-bo", baseParams(1, 12, "modified.time", "desc", null, null, null, null)).items()),
                safeHomeSection("single", () -> list("phim-le", baseParams(1, 12, "modified.time", "desc", null, null, null, null)).items()),
                safeHomeSection("animation", () -> list("hoat-hinh", baseParams(1, 12, "modified.time", "desc", null, null, null, null)).items()),
                safeHomeSection("tvShows", () -> list("tv-shows", baseParams(1, 12, "modified.time", "desc", null, null, null, null)).items())
        );
    }

    public CatalogDtos.PagedMovieResponse latest(int page, String version) {
        String endpoint = switch (version == null ? "" : version.toLowerCase()) {
            case "v2" -> "/danh-sach/phim-moi-cap-nhat-v2";
            case "v3" -> "/danh-sach/phim-moi-cap-nhat-v3";
            default -> "/danh-sach/phim-moi-cap-nhat";
        };

        JsonNode raw = get(endpoint, Map.of("page", String.valueOf(page)));
        return normalizePaged(raw, "items");
    }

    public CatalogDtos.PagedMovieResponse list(String type, Map<String, String> params) {
        JsonNode raw = get("/v1/api/danh-sach/" + type, params);
        return normalizePaged(raw, "items");
    }

    public CatalogDtos.PagedMovieResponse search(Map<String, String> params) {
        JsonNode raw = get("/v1/api/tim-kiem", params);
        return normalizePaged(raw, "items");
    }

    public JsonNode categories() {
        return get("/the-loai", Map.of());
    }

    public JsonNode countries() {
        return get("/quoc-gia", Map.of());
    }

    public CatalogDtos.PagedMovieResponse categoryDetail(String slug, Map<String, String> params) {
        JsonNode raw = get("/v1/api/the-loai/" + slug, params);
        return normalizePaged(raw, "items");
    }

    public CatalogDtos.PagedMovieResponse countryDetail(String slug, Map<String, String> params) {
        JsonNode raw = get("/v1/api/quoc-gia/" + slug, params);
        return normalizePaged(raw, "items");
    }

    public CatalogDtos.PagedMovieResponse yearDetail(String year, Map<String, String> params) {
        JsonNode raw = get("/v1/api/nam/" + year, params);
        return normalizePaged(raw, "items");
    }

    public CatalogDtos.MovieDetailResponse movie(String slug) {
        JsonNode raw = get("/phim/" + slug, Map.of());
        JsonNode item = raw.path("movie");
        JsonNode episodes = raw.path("episodes");

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("imageProxyBaseUrl", appProperties.phimApi().imageProxyUrl());

        return new CatalogDtos.MovieDetailResponse(toMovieSummary(item), raw, episodes, metadata);
    }

    public JsonNode tmdb(String type, String id) {
        return get("/tmdb/" + type + "/" + id, Map.of());
    }

    public String imageProxy(String originalUrl) {
        return UriComponentsBuilder.fromUriString(appProperties.phimApi().imageProxyUrl())
                .queryParam("url", originalUrl)
                .build()
                .toUriString();
    }

    public Map<String, String> baseParams(
            Integer page,
            Integer limit,
            String sortField,
            String sortType,
            String sortLang,
            String category,
            String country,
            String year
    ) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("page", page == null ? "1" : String.valueOf(page));
        params.put("limit", limit == null ? "24" : String.valueOf(limit));
        params.put("sort_field", sortField);
        params.put("sort_type", sortType);
        params.put("sort_lang", sortLang);
        params.put("category", category);
        params.put("country", country);
        params.put("year", year);
        return params;
    }

    private JsonNode get(String path, Map<String, String> params) {
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        params.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                queryParams.add(key, value);
            }
        });

        String uri = UriComponentsBuilder.fromPath(path)
                .queryParams(queryParams)
                .build()
                .toUriString();

        JsonNode body = restClient().get()
                .uri(uri)
                .retrieve()
                .body(JsonNode.class);

        return body == null ? JsonNodeFactory.instance.objectNode() : body;
    }

    private CatalogDtos.PagedMovieResponse normalizePaged(JsonNode raw, String fallbackField) {
        if (raw == null) {
            raw = JsonNodeFactory.instance.objectNode();
        }

        JsonNode dataNode = raw.has("data") ? raw.path("data") : raw;
        JsonNode paginationNode = navigate(dataNode, "params.pagination");
        JsonNode itemsNode = dataNode.path("items");
        if (!itemsNode.isArray()) {
            itemsNode = dataNode.path(fallbackField);
        }
        if (!itemsNode.isArray()) {
            itemsNode = raw.path(fallbackField);
        }

        List<CatalogDtos.MovieSummary> items = new ArrayList<>();
        for (JsonNode item : iterable(itemsNode)) {
            items.add(toMovieSummary(item));
        }

        Integer page = readInt(
                paginationNode,
                "currentPage",
                readInt(dataNode, "currentPage", readInt(raw, "currentPage", readInt(raw, "page", 1)))
        );
        Integer totalPages = readInt(
                paginationNode,
                "totalPages",
                readInt(dataNode, "totalPages", readInt(raw, "totalPages", 1))
        );
        Integer totalItems = readInt(
                paginationNode,
                "totalItems",
                readInt(dataNode, "totalItems", items.size())
        );

        return new CatalogDtos.PagedMovieResponse(page, totalPages, totalItems, items, raw);
    }

    private List<CatalogDtos.MovieSummary> safeHomeSection(String section, SupplierWithException<List<CatalogDtos.MovieSummary>> supplier) {
        try {
            return supplier.get();
        } catch (Exception exception) {
            log.warn("Failed to load home section {}", section, exception);
            return List.of();
        }
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }

    public CatalogDtos.MovieSummary toMovieSummary(JsonNode item) {
        List<String> categories = new ArrayList<>();
        for (JsonNode category : iterable(item.path("category"))) {
            categories.add(readText(category, "name", readText(category, "slug", null)));
        }

        List<String> countries = new ArrayList<>();
        for (JsonNode country : iterable(item.path("country"))) {
            countries.add(readText(country, "name", readText(country, "slug", null)));
        }

        return new CatalogDtos.MovieSummary(
                readText(item, "slug", readText(item, "_id", null)),
                readText(item, "name", readText(item, "title", null)),
                readText(item, "origin_name", null),
                normalizeImageUrl(readText(item, "poster_url", null)),
                normalizeImageUrl(readText(item, "thumb_url", null)),
                readText(item, "quality", null),
                readText(item, "lang", null),
                readText(item, "episode_current", null),
                readInt(item, "year", null),
                readText(item, "type", null),
                categories,
                countries,
                readInt(item, "tmdb.id", null)
        );
    }

    private String normalizeImageUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        return "https://phimimg.com/" + value.replaceFirst("^/+", "");
    }

    private Integer readInt(JsonNode node, String field, Integer fallback) {
        JsonNode target = navigate(node, field);
        if (target == null || target.isMissingNode() || target.isNull()) {
            return fallback;
        }
        if (target.isNumber()) {
            return target.asInt();
        }
        if (target.isTextual()) {
            try {
                return Integer.parseInt(target.asText());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String readText(JsonNode node, String field, String fallback) {
        JsonNode target = navigate(node, field);
        if (target == null || target.isMissingNode() || target.isNull()) {
            return fallback;
        }
        String value = target.asText();
        return value == null || value.isBlank() ? fallback : value;
    }

    private JsonNode navigate(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode current = node;
        for (String part : field.split("\\.")) {
            current = current.path(part);
        }
        return current;
    }

    private Iterable<JsonNode> iterable(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return () -> new Iterator<>() {
            private final Iterator<JsonNode> delegate = node.iterator();

            @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }

            @Override
            public JsonNode next() {
                return delegate.next();
            }
        };
    }
}
