package com.filmbe.controller;

import com.filmbe.dto.CatalogDtos;
import com.filmbe.service.PhimApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogController {

    private final PhimApiService phimApiService;

    @GetMapping("/catalog/home")
    public CatalogDtos.HomeResponse home() {
        return phimApiService.home();
    }

    @GetMapping("/catalog/latest")
    public CatalogDtos.PagedMovieResponse latest(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "v1") String version
    ) {
        return phimApiService.latest(page, version);
    }

    @GetMapping("/catalog/list/{type}")
    public CatalogDtos.PagedMovieResponse list(
            @PathVariable String type,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "24") Integer limit,
            @RequestParam(defaultValue = "modified.time") String sortField,
            @RequestParam(defaultValue = "desc") String sortType,
            @RequestParam(required = false) String sortLang,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String year
    ) {
        return phimApiService.list(type, phimApiService.baseParams(page, limit, sortField, sortType, sortLang, category, country, year));
    }

    @GetMapping("/catalog/search")
    public CatalogDtos.PagedMovieResponse search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "24") Integer limit,
            @RequestParam(defaultValue = "modified.time") String sortField,
            @RequestParam(defaultValue = "desc") String sortType,
            @RequestParam(required = false) String sortLang,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String year
    ) {
        Map<String, String> params = phimApiService.baseParams(page, limit, sortField, sortType, sortLang, category, country, year);
        params.put("keyword", keyword);
        return phimApiService.search(params);
    }

    @GetMapping("/catalog/categories")
    public JsonNode categories() {
        return phimApiService.categories();
    }

    @GetMapping("/catalog/countries")
    public JsonNode countries() {
        return phimApiService.countries();
    }

    @GetMapping("/catalog/category/{slug}")
    public CatalogDtos.PagedMovieResponse category(
            @PathVariable String slug,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "24") Integer limit,
            @RequestParam(defaultValue = "modified.time") String sortField,
            @RequestParam(defaultValue = "desc") String sortType,
            @RequestParam(required = false) String sortLang,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String year
    ) {
        return phimApiService.categoryDetail(slug, phimApiService.baseParams(page, limit, sortField, sortType, sortLang, null, country, year));
    }

    @GetMapping("/catalog/country/{slug}")
    public CatalogDtos.PagedMovieResponse country(
            @PathVariable String slug,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "24") Integer limit,
            @RequestParam(defaultValue = "modified.time") String sortField,
            @RequestParam(defaultValue = "desc") String sortType,
            @RequestParam(required = false) String sortLang,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String year
    ) {
        return phimApiService.countryDetail(slug, phimApiService.baseParams(page, limit, sortField, sortType, sortLang, category, null, year));
    }

    @GetMapping("/catalog/year/{yearValue}")
    public CatalogDtos.PagedMovieResponse year(
            @PathVariable String yearValue,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "24") Integer limit,
            @RequestParam(defaultValue = "modified.time") String sortField,
            @RequestParam(defaultValue = "desc") String sortType,
            @RequestParam(required = false) String sortLang,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String country
    ) {
        return phimApiService.yearDetail(yearValue, phimApiService.baseParams(page, limit, sortField, sortType, sortLang, category, country, null));
    }

    @GetMapping("/movies/{slug}")
    public CatalogDtos.MovieDetailResponse movie(@PathVariable String slug) {
        return phimApiService.movie(slug);
    }

    @GetMapping("/tmdb/{type}/{id}")
    public JsonNode tmdb(@PathVariable String type, @PathVariable String id) {
        return phimApiService.tmdb(type, id);
    }

    @GetMapping("/images/webp")
    public Map<String, String> imageProxy(@RequestParam String url) {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("originalUrl", url);
        response.put("proxyUrl", phimApiService.imageProxy(url));
        return response;
    }
}
