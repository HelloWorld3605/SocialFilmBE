package com.filmbe.service;

import com.filmbe.config.AppProperties;
import com.filmbe.config.CacheKeys;
import com.filmbe.config.RedisCacheConfig;
import com.filmbe.dto.CatalogDtos;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.time.Duration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

@Service
@Slf4j
@RequiredArgsConstructor
public class PhimApiService {
    private static final List<String> DEFAULT_BROWSE_TYPES = List.of(
            "phim-bo",
            "phim-le",
            "tv-shows",
            "hoat-hinh"
    );
    private static final int MAX_AGGREGATE_SOURCE_PAGE_SIZE = 64;
    private static final int MAX_AGGREGATE_SOURCE_PAGES_PER_TYPE = 4;
    private static final RedisSerializer<String> STRING_SERIALIZER = RedisSerializer.string();

    private final AppProperties appProperties;
    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;
    private final RedisConnectionFactory redisConnectionFactory;

    private RestClient restClient() {
        return RestClient.builder()
                .baseUrl(appProperties.phimApi().baseUrl())
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Cacheable(cacheNames = RedisCacheConfig.CATALOG_HOME_CACHE, sync = true)
    public CatalogDtos.HomeResponse home() {
        return new CatalogDtos.HomeResponse(
                safeHomeSection("latest", () -> latest(1, "v3").items()),
                safeHomeSection("series", () -> list("phim-bo", baseParams(1, 12, "modified.time", "desc", null, null, null, null)).items()),
                safeHomeSection("single", () -> list("phim-le", baseParams(1, 12, "modified.time", "desc", null, null, null, null)).items()),
                safeHomeSection("animation", () -> list("hoat-hinh", baseParams(1, 12, "modified.time", "desc", null, null, null, null)).items()),
                safeHomeSection("tvShows", () -> list("tv-shows", baseParams(1, 12, "modified.time", "desc", null, null, null, null)).items())
        );
    }

    @Cacheable(
            cacheNames = RedisCacheConfig.CATALOG_LATEST_CACHE,
            key = "T(com.filmbe.config.CacheKeys).parts(#page, #version)",
            sync = true
    )
    public CatalogDtos.PagedMovieResponse latest(int page, String version) {
        String endpoint = switch (version == null ? "" : version.toLowerCase()) {
            case "v2" -> "/danh-sach/phim-moi-cap-nhat-v2";
            case "v3" -> "/danh-sach/phim-moi-cap-nhat-v3";
            default -> "/danh-sach/phim-moi-cap-nhat";
        };

        JsonNode raw = get(endpoint, Map.of("page", String.valueOf(page)));
        return normalizePaged(raw, "items");
    }

    @Cacheable(
            cacheNames = RedisCacheConfig.CATALOG_LIST_CACHE,
            key = "T(com.filmbe.config.CacheKeys).parts(#type, #params)",
            sync = true
    )
    public CatalogDtos.PagedMovieResponse list(String type, Map<String, String> params) {
        JsonNode raw = get("/v1/api/danh-sach/" + type, params);
        return normalizePaged(raw, "items");
    }

    @Cacheable(
            cacheNames = RedisCacheConfig.CATALOG_LIST_CACHE,
            key = "T(com.filmbe.config.CacheKeys).parts('all', #types, #page, #limit, #sortField, #sortType, #sortLang, #category, #country, #year)",
            sync = true
    )
    public CatalogDtos.PagedMovieResponse listAll(
            List<String> types,
            Integer page,
            Integer limit,
            String sortField,
            String sortType,
            List<String> sortLang,
            List<String> category,
            List<String> country,
            List<String> year
    ) {
        FilterSelections filterSelections = normalizeFilterSelections(sortLang, category, country, year);
        Map<String, String> upstreamParams = baseParams(
                page,
                limit,
                sortField,
                sortType,
                singleValueOrNull(filterSelections.sortLangs()),
                singleValueOrNull(filterSelections.categories()),
                singleValueOrNull(filterSelections.countries()),
                singleValueOrNull(filterSelections.years())
        );

        if (!filterSelections.hasMultipleValues()) {
            return listAllWithUpstreamFilters(types, upstreamParams);
        }

        return listAllWithLocalFilters(types, upstreamParams, filterSelections);
    }

    @Cacheable(
            cacheNames = RedisCacheConfig.CATALOG_SEARCH_CACHE,
            key = "T(com.filmbe.config.CacheKeys).parts(#keyword, #page, #limit, #sortField, #sortType, #sortLang, #category, #country, #year)",
            sync = true
    )
    public CatalogDtos.PagedMovieResponse search(
            String keyword,
            Integer page,
            Integer limit,
            String sortField,
            String sortType,
            List<String> sortLang,
            List<String> category,
            List<String> country,
            List<String> year
    ) {
        FilterSelections filterSelections = normalizeFilterSelections(sortLang, category, country, year);
        Map<String, String> upstreamParams = baseParams(
                page,
                limit,
                sortField,
                sortType,
                singleValueOrNull(filterSelections.sortLangs()),
                singleValueOrNull(filterSelections.categories()),
                singleValueOrNull(filterSelections.countries()),
                singleValueOrNull(filterSelections.years())
        );
        upstreamParams.put("keyword", keyword);

        if (!filterSelections.hasMultipleValues()) {
            JsonNode raw = get("/v1/api/tim-kiem", upstreamParams);
            return normalizePaged(raw, "items");
        }

        return searchWithLocalFilters(upstreamParams, filterSelections);
    }

    public JsonNode categories() {
        return getCachedJsonDocument(
                RedisCacheConfig.CATALOG_TAXONOMY_CACHE,
                "categories",
                () -> get("/the-loai", Map.of())
        );
    }

    public JsonNode countries() {
        return getCachedJsonDocument(
                RedisCacheConfig.CATALOG_TAXONOMY_CACHE,
                "countries",
                () -> get("/quoc-gia", Map.of())
        );
    }

    @Cacheable(
            cacheNames = RedisCacheConfig.CATALOG_CATEGORY_CACHE,
            key = "T(com.filmbe.config.CacheKeys).parts(#slug, #params)",
            sync = true
    )
    public CatalogDtos.PagedMovieResponse categoryDetail(String slug, Map<String, String> params) {
        JsonNode raw = get("/v1/api/the-loai/" + slug, params);
        return normalizePaged(raw, "items");
    }

    @Cacheable(
            cacheNames = RedisCacheConfig.CATALOG_COUNTRY_CACHE,
            key = "T(com.filmbe.config.CacheKeys).parts(#slug, #params)",
            sync = true
    )
    public CatalogDtos.PagedMovieResponse countryDetail(String slug, Map<String, String> params) {
        JsonNode raw = get("/v1/api/quoc-gia/" + slug, params);
        return normalizePaged(raw, "items");
    }

    @Cacheable(
            cacheNames = RedisCacheConfig.CATALOG_YEAR_CACHE,
            key = "T(com.filmbe.config.CacheKeys).parts(#year, #params)",
            sync = true
    )
    public CatalogDtos.PagedMovieResponse yearDetail(String year, Map<String, String> params) {
        JsonNode raw = get("/v1/api/nam/" + year, params);
        return normalizePaged(raw, "items");
    }

    @Cacheable(cacheNames = RedisCacheConfig.MOVIE_DETAIL_CACHE, key = "#slug", sync = true)
    public CatalogDtos.MovieDetailResponse movie(String slug) {
        JsonNode raw = get("/phim/" + slug, Map.of());
        JsonNode item = raw.path("movie");
        JsonNode episodes = raw.path("episodes");

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("imageProxyBaseUrl", appProperties.phimApi().imageProxyUrl());

        return new CatalogDtos.MovieDetailResponse(toMovieSummary(item), raw, episodes, metadata);
    }

    public JsonNode tmdb(String type, String id) {
        return getCachedJsonDocument(
                RedisCacheConfig.TMDB_CACHE,
                CacheKeys.parts(type, id),
                () -> get("/tmdb/" + type + "/" + id, Map.of())
        );
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

    private CatalogDtos.PagedMovieResponse listAllWithUpstreamFilters(List<String> types, Map<String, String> params) {
        List<String> normalizedTypes = normalizeBrowseTypes(types);
        if (normalizedTypes.size() == 1) {
            return list(normalizedTypes.getFirst(), params);
        }

        AggregatePaging aggregatePaging = resolveAggregatePaging(params);
        int page = aggregatePaging.page();
        int limit = aggregatePaging.limit();
        int pagesToFetch = aggregatePaging.pagesToFetch();
        String sortField = params.getOrDefault("sort_field", "modified.time");
        String sortType = params.getOrDefault("sort_type", "desc");

        Map<String, AggregateCandidate> ranked = new LinkedHashMap<>();
        int totalItems = 0;

        for (int typeIndex = 0; typeIndex < normalizedTypes.size(); typeIndex++) {
            String type = normalizedTypes.get(typeIndex);

            for (int sourcePage = 1; sourcePage <= pagesToFetch; sourcePage++) {
                Map<String, String> sourceParams = new LinkedHashMap<>(params);
                sourceParams.put("page", String.valueOf(sourcePage));
                sourceParams.put("limit", String.valueOf(MAX_AGGREGATE_SOURCE_PAGE_SIZE));

                CatalogDtos.PagedMovieResponse response = list(type, sourceParams);
                if (sourcePage == 1) {
                    totalItems += response.totalItems() == null ? 0 : response.totalItems();
                }

                List<CatalogDtos.MovieSummary> items = response.items();
                for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
                    CatalogDtos.MovieSummary item = items.get(itemIndex);
                    if (item.slug() == null || item.slug().isBlank()) {
                        continue;
                    }

                    ranked.putIfAbsent(
                            item.slug(),
                            new AggregateCandidate(
                                    item,
                                    typeIndex,
                                    (sourcePage - 1) * MAX_AGGREGATE_SOURCE_PAGE_SIZE + itemIndex
                            )
                    );
                }
            }
        }

        List<AggregateCandidate> merged = new ArrayList<>(ranked.values());
        merged.sort((left, right) -> compareAggregateCandidates(left, right, sortField, sortType));

        int fromIndex = Math.min((page - 1) * limit, merged.size());
        int toIndex = Math.min(fromIndex + limit, merged.size());
        List<CatalogDtos.MovieSummary> pageItems = merged.subList(fromIndex, toIndex)
                .stream()
                .map(AggregateCandidate::item)
                .toList();
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) limit));

        return new CatalogDtos.PagedMovieResponse(
                page,
                totalPages,
                totalItems,
                pageItems,
                buildAggregateRaw(page, totalPages, totalItems, normalizedTypes, params)
        );
    }

    private CatalogDtos.PagedMovieResponse listAllWithLocalFilters(
            List<String> types,
            Map<String, String> params,
            FilterSelections filterSelections
    ) {
        List<String> normalizedTypes = normalizeBrowseTypes(types);
        AggregatePaging aggregatePaging = resolveAggregatePaging(params);
        int page = aggregatePaging.page();
        int limit = aggregatePaging.limit();
        String sortField = params.getOrDefault("sort_field", "modified.time");
        String sortType = params.getOrDefault("sort_type", "desc");

        Map<String, AggregateCandidate> ranked = new LinkedHashMap<>();

        for (int typeIndex = 0; typeIndex < normalizedTypes.size(); typeIndex++) {
            String type = normalizedTypes.get(typeIndex);

            for (int sourcePage = 1; sourcePage <= MAX_AGGREGATE_SOURCE_PAGES_PER_TYPE; sourcePage++) {
                Map<String, String> sourceParams = new LinkedHashMap<>(params);
                sourceParams.put("page", String.valueOf(sourcePage));
                sourceParams.put("limit", String.valueOf(MAX_AGGREGATE_SOURCE_PAGE_SIZE));

                CatalogDtos.PagedMovieResponse response = list(type, sourceParams);
                List<CatalogDtos.MovieSummary> items = response.items();
                for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
                    CatalogDtos.MovieSummary item = items.get(itemIndex);
                    if (item.slug() == null || item.slug().isBlank() || !matchesFilterSelections(item, filterSelections)) {
                        continue;
                    }

                    ranked.putIfAbsent(
                            item.slug(),
                            new AggregateCandidate(
                                    item,
                                    typeIndex,
                                    (sourcePage - 1) * MAX_AGGREGATE_SOURCE_PAGE_SIZE + itemIndex
                            )
                    );
                }

                if (response.totalPages() != null && sourcePage >= response.totalPages()) {
                    break;
                }
            }
        }

        List<AggregateCandidate> merged = new ArrayList<>(ranked.values());
        merged.sort((left, right) -> compareAggregateCandidates(left, right, sortField, sortType));

        int totalItems = merged.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) limit));
        int fromIndex = Math.min((page - 1) * limit, merged.size());
        int toIndex = Math.min(fromIndex + limit, merged.size());
        List<CatalogDtos.MovieSummary> pageItems = merged.subList(fromIndex, toIndex)
                .stream()
                .map(AggregateCandidate::item)
                .toList();

        return new CatalogDtos.PagedMovieResponse(
                page,
                totalPages,
                totalItems,
                pageItems,
                buildAggregateRaw(page, totalPages, totalItems, normalizedTypes, params)
        );
    }

    private CatalogDtos.PagedMovieResponse searchWithLocalFilters(
            Map<String, String> params,
            FilterSelections filterSelections
    ) {
        AggregatePaging aggregatePaging = resolveAggregatePaging(params);
        int page = aggregatePaging.page();
        int limit = aggregatePaging.limit();
        String sortField = params.getOrDefault("sort_field", "modified.time");
        String sortType = params.getOrDefault("sort_type", "desc");

        List<AggregateCandidate> matched = new ArrayList<>();

        for (int sourcePage = 1; sourcePage <= MAX_AGGREGATE_SOURCE_PAGES_PER_TYPE; sourcePage++) {
            Map<String, String> sourceParams = new LinkedHashMap<>(params);
            sourceParams.put("page", String.valueOf(sourcePage));
            sourceParams.put("limit", String.valueOf(MAX_AGGREGATE_SOURCE_PAGE_SIZE));

            JsonNode raw = get("/v1/api/tim-kiem", sourceParams);
            CatalogDtos.PagedMovieResponse response = normalizePaged(raw, "items");
            List<CatalogDtos.MovieSummary> items = response.items();
            for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
                CatalogDtos.MovieSummary item = items.get(itemIndex);
                if (item.slug() == null || item.slug().isBlank() || !matchesFilterSelections(item, filterSelections)) {
                    continue;
                }

                matched.add(new AggregateCandidate(
                        item,
                        0,
                        (sourcePage - 1) * MAX_AGGREGATE_SOURCE_PAGE_SIZE + itemIndex
                ));
            }

            if (response.totalPages() != null && sourcePage >= response.totalPages()) {
                break;
            }
        }

        matched.sort((left, right) -> compareAggregateCandidates(left, right, sortField, sortType));

        int totalItems = matched.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) limit));
        int fromIndex = Math.min((page - 1) * limit, matched.size());
        int toIndex = Math.min(fromIndex + limit, matched.size());
        List<CatalogDtos.MovieSummary> pageItems = matched.subList(fromIndex, toIndex)
                .stream()
                .map(AggregateCandidate::item)
                .toList();

        return new CatalogDtos.PagedMovieResponse(
                page,
                totalPages,
                totalItems,
                pageItems,
                buildSearchRaw(page, totalPages, totalItems)
        );
    }

    private AggregatePaging resolveAggregatePaging(Map<String, String> params) {
        int requestedPage = parsePositiveInt(params.get("page"), 1);
        int limit = Math.min(parsePositiveInt(params.get("limit"), 24), MAX_AGGREGATE_SOURCE_PAGE_SIZE);
        int maxSupportedPage = Math.max(
                1,
                (MAX_AGGREGATE_SOURCE_PAGE_SIZE * MAX_AGGREGATE_SOURCE_PAGES_PER_TYPE) / Math.max(1, limit)
        );
        int page = Math.min(requestedPage, maxSupportedPage);
        int targetItemCount = page * limit;
        int pagesToFetch = Math.min(
                MAX_AGGREGATE_SOURCE_PAGES_PER_TYPE,
                Math.max(1, (int) Math.ceil((double) targetItemCount / MAX_AGGREGATE_SOURCE_PAGE_SIZE))
        );
        return new AggregatePaging(page, limit, pagesToFetch);
    }

    private FilterSelections normalizeFilterSelections(
            List<String> sortLangs,
            List<String> categories,
            List<String> countries,
            List<String> years
    ) {
        return new FilterSelections(
                normalizeFilterValues(sortLangs),
                normalizeFilterValues(categories),
                normalizeFilterValues(countries),
                normalizeSingleFilterValue(years)
        );
    }

    private List<String> normalizeFilterValues(List<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values == null ? List.<String>of() : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isBlank()) {
                normalized.add(trimmed);
            }
        }
        return List.copyOf(normalized);
    }

    private List<String> normalizeSingleFilterValue(List<String> values) {
        List<String> normalized = normalizeFilterValues(values);
        return normalized.isEmpty() ? List.of() : List.of(normalized.getFirst());
    }

    private String singleValueOrNull(List<String> values) {
        return values != null && values.size() == 1 ? values.getFirst() : null;
    }

    private boolean matchesFilterSelections(CatalogDtos.MovieSummary item, FilterSelections filterSelections) {
        if (!filterSelections.sortLangs().isEmpty()) {
            String itemLanguage = normalizeComparableValue(item.lang());
            boolean languageMatches = filterSelections.sortLangs().stream()
                    .map(this::normalizeComparableValue)
                    .anyMatch(candidate -> candidate.equals(itemLanguage));
            if (!languageMatches) {
                return false;
            }
        }

        if (!filterSelections.categories().isEmpty()) {
            List<String> categorySlugs = item.categorySlugs() == null ? List.of() : item.categorySlugs();
            List<String> categoryNames = item.categories() == null ? List.of() : item.categories();
            List<String> itemCategories = !categorySlugs.isEmpty() ? categorySlugs : categoryNames;
            boolean categoryMatches = itemCategories.stream()
                    .map(this::normalizeComparableValue)
                    .anyMatch(candidate -> filterSelections.categories().stream()
                            .map(this::normalizeComparableValue)
                            .anyMatch(candidate::equals));
            if (!categoryMatches) {
                return false;
            }
        }

        if (!filterSelections.countries().isEmpty()) {
            List<String> countrySlugs = item.countrySlugs() == null ? List.of() : item.countrySlugs();
            List<String> countryNames = item.countries() == null ? List.of() : item.countries();
            List<String> itemCountries = !countrySlugs.isEmpty() ? countrySlugs : countryNames;
            boolean countryMatches = itemCountries.stream()
                    .map(this::normalizeComparableValue)
                    .anyMatch(candidate -> filterSelections.countries().stream()
                            .map(this::normalizeComparableValue)
                            .anyMatch(candidate::equals));
            if (!countryMatches) {
                return false;
            }
        }

        if (!filterSelections.years().isEmpty()) {
            String itemYear = item.year() == null ? "" : String.valueOf(item.year());
            boolean yearMatches = filterSelections.years().contains(itemYear);
            if (!yearMatches) {
                return false;
            }
        }

        return true;
    }

    private String normalizeComparableValue(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "")
                .replaceAll("-{2,}", "-");
        return normalized;
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

    private JsonNode getCachedJsonDocument(String cacheName, String key, SupplierWithException<JsonNode> loader) {
        JsonNode cachedDocument = readCachedJsonDocument(cacheName, key);
        if (cachedDocument != null) {
            return cachedDocument;
        }

        JsonNode fresh = loadJsonDocument(loader);
        cacheJsonDocument(cacheName, key, fresh);
        return fresh;
    }

    private JsonNode loadJsonDocument(SupplierWithException<JsonNode> loader) {
        try {
            return loader.get();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load JSON document", exception);
        }
    }

    private JsonNode readCachedJsonDocument(String cacheName, String key) {
        try {
            String cachedPayload = readRawCacheValue(cacheName, key);
            if (cachedPayload == null || cachedPayload.isBlank()) {
                return null;
            }

            return decodeCachedJsonDocument(cachedPayload);
        } catch (JacksonException exception) {
            log.warn("Redis raw cache payload was not valid JSON for cache={} key={}", cacheName, key, exception);
            evictRawCacheEntry(cacheName, key, "invalid JSON payload");
            return null;
        } catch (RuntimeException exception) {
            log.warn("Redis raw cache GET failed for cache={} key={}", cacheName, key, exception);
            return null;
        }
    }

    private JsonNode decodeCachedJsonDocument(String cachedPayload) throws JacksonException {
        JsonNode root = objectMapper.readTree(cachedPayload);

        if (root == null || root.isNull()) {
            return null;
        }

        if (root.isTextual()) {
            return objectMapper.readTree(root.asText());
        }

        if (root.isArray()
                && root.size() == 2
                && root.get(0).isTextual()
                && root.get(1).isTextual()) {
            return objectMapper.readTree(root.get(1).asText());
        }

        if (root.isObject() && root.path("value").isTextual()) {
            return objectMapper.readTree(root.path("value").asText());
        }

        return root;
    }

    private String readRawCacheValue(String cacheName, String key) {
        byte[] redisKey = serializeRedisKey(cacheName, key);
        RedisConnection connection = redisConnectionFactory.getConnection();
        try {
            byte[] payload = connection.stringCommands().get(redisKey);
            return payload == null ? null : STRING_SERIALIZER.deserialize(payload);
        } finally {
            connection.close();
        }
    }

    private void cacheJsonDocument(String cacheName, String key, JsonNode value) {
        try {
            writeRawCacheValue(cacheName, key, objectMapper.writeValueAsString(value));
        } catch (JacksonException exception) {
            log.warn("Redis raw cache PUT failed while encoding JSON for cache={} key={}", cacheName, key, exception);
        } catch (RuntimeException exception) {
            log.warn("Redis raw cache PUT failed for cache={} key={}", cacheName, key, exception);
        }
    }

    private void writeRawCacheValue(String cacheName, String key, String payload) {
        byte[] redisKey = serializeRedisKey(cacheName, key);
        byte[] redisValue = STRING_SERIALIZER.serialize(payload);
        RedisConnection connection = redisConnectionFactory.getConnection();
        try {
            connection.stringCommands().set(redisKey, redisValue);
            Duration ttl = resolveDocumentCacheTtl(cacheName);
            if (!ttl.isZero() && !ttl.isNegative()) {
                connection.keyCommands().expire(redisKey, ttl.getSeconds());
            }
        } finally {
            connection.close();
        }
    }

    private void evictRawCacheEntry(String cacheName, String key, String reason) {
        byte[] redisKey = serializeRedisKey(cacheName, key);
        RedisConnection connection = redisConnectionFactory.getConnection();
        try {
            connection.keyCommands().del(redisKey);
        } catch (RuntimeException exception) {
            log.warn("Redis raw cache EVICT failed for cache={} key={} after {}", cacheName, key, reason, exception);
        } finally {
            connection.close();
        }
    }

    private byte[] serializeRedisKey(String cacheName, String key) {
        String fullKey = appProperties.cache().redis().keyPrefix()
                + RedisCacheConfig.PUBLIC_CACHE_PREFIX_VERSION
                + cacheName
                + "::"
                + key;
        return STRING_SERIALIZER.serialize(fullKey);
    }

    private Duration resolveDocumentCacheTtl(String cacheName) {
        return switch (cacheName) {
            case RedisCacheConfig.CATALOG_TAXONOMY_CACHE -> appProperties.cache().redis().taxonomyTtl();
            case RedisCacheConfig.TMDB_CACHE -> appProperties.cache().redis().tmdbTtl();
            default -> appProperties.cache().redis().defaultTtl();
        };
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
        List<String> categorySlugs = new ArrayList<>();
        for (JsonNode category : iterable(item.path("category"))) {
            String categoryName = readText(category, "name", readText(category, "slug", null));
            String categorySlug = readText(category, "slug", null);
            if (categoryName != null && !categoryName.isBlank()) {
                categories.add(categoryName);
            }
            if (categorySlug != null && !categorySlug.isBlank()) {
                categorySlugs.add(categorySlug);
            }
        }

        List<String> countries = new ArrayList<>();
        List<String> countrySlugs = new ArrayList<>();
        for (JsonNode country : iterable(item.path("country"))) {
            String countryName = readText(country, "name", readText(country, "slug", null));
            String countrySlug = readText(country, "slug", null);
            if (countryName != null && !countryName.isBlank()) {
                countries.add(countryName);
            }
            if (countrySlug != null && !countrySlug.isBlank()) {
                countrySlugs.add(countrySlug);
            }
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
                categorySlugs,
                countries,
                countrySlugs,
                readInt(item, "tmdb.id", null),
                readText(item, "_id", null),
                readText(item, "modified.time", null)
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

    private List<String> normalizeBrowseTypes(List<String> types) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String type : types == null ? List.<String>of() : types) {
            if (type != null && DEFAULT_BROWSE_TYPES.contains(type)) {
                normalized.add(type);
            }
        }
        return normalized.isEmpty() ? DEFAULT_BROWSE_TYPES : List.copyOf(normalized);
    }

    private int parsePositiveInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private int compareAggregateCandidates(
            AggregateCandidate left,
            AggregateCandidate right,
            String sortField,
            String sortType
    ) {
        boolean ascending = "asc".equalsIgnoreCase(sortType);
        int compare = switch (sortField == null ? "" : sortField) {
            case "year" -> compareNullableIntegers(left.item().year(), right.item().year(), ascending);
            case "_id" -> compareNullableStrings(left.item().sourceId(), right.item().sourceId(), ascending);
            default -> compareNullableStrings(left.item().modifiedTime(), right.item().modifiedTime(), ascending);
        };

        if (compare != 0) {
            return compare;
        }

        compare = compareNullableStrings(left.item().modifiedTime(), right.item().modifiedTime(), false);
        if (compare != 0) {
            return compare;
        }

        compare = compareNullableStrings(left.item().sourceId(), right.item().sourceId(), false);
        if (compare != 0) {
            return compare;
        }

        compare = Integer.compare(left.typePriority(), right.typePriority());
        if (compare != 0) {
            return compare;
        }

        compare = Integer.compare(left.sourceIndex(), right.sourceIndex());
        if (compare != 0) {
            return compare;
        }

        return Comparator.nullsLast(String::compareToIgnoreCase)
                .compare(left.item().name(), right.item().name());
    }

    private int compareNullableIntegers(Integer left, Integer right, boolean ascending) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return ascending ? Integer.compare(left, right) : Integer.compare(right, left);
    }

    private int compareNullableStrings(String left, String right, boolean ascending) {
        if ((left == null || left.isBlank()) && (right == null || right.isBlank())) {
            return 0;
        }
        if (left == null || left.isBlank()) {
            return 1;
        }
        if (right == null || right.isBlank()) {
            return -1;
        }
        return ascending ? left.compareToIgnoreCase(right) : right.compareToIgnoreCase(left);
    }

    private JsonNode buildSearchRaw(int page, int totalPages, int totalItems) {
        var raw = JsonNodeFactory.instance.objectNode();
        var dataNode = raw.putObject("data");
        var paramsNode = dataNode.putObject("params");
        var paginationNode = paramsNode.putObject("pagination");
        paginationNode.put("currentPage", page);
        paginationNode.put("totalPages", totalPages);
        paginationNode.put("totalItems", totalItems);
        return raw;
    }

    private JsonNode buildAggregateRaw(
            int page,
            int totalPages,
            int totalItems,
            List<String> selectedTypes,
            Map<String, String> params
    ) {
        var raw = JsonNodeFactory.instance.objectNode();
        var dataNode = raw.putObject("data");
        var paramsNode = dataNode.putObject("params");
        var paginationNode = paramsNode.putObject("pagination");
        paginationNode.put("currentPage", page);
        paginationNode.put("totalPages", totalPages);
        paginationNode.put("totalItems", totalItems);

        params.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                paramsNode.put(key, value);
            }
        });

        var typesNode = dataNode.putArray("selectedTypes");
        selectedTypes.forEach(typesNode::add);
        return raw;
    }

    private record AggregateCandidate(
            CatalogDtos.MovieSummary item,
            int typePriority,
            int sourceIndex
    ) {
    }

    private record AggregatePaging(
            int page,
            int limit,
            int pagesToFetch
    ) {
    }

    private record FilterSelections(
            List<String> sortLangs,
            List<String> categories,
            List<String> countries,
            List<String> years
    ) {
        private boolean hasMultipleValues() {
            return sortLangs.size() > 1
                    || categories.size() > 1
                    || countries.size() > 1
                    || years.size() > 1;
        }
    }

}
