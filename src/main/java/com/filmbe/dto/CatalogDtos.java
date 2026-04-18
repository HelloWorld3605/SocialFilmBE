package com.filmbe.dto;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class CatalogDtos {
    private CatalogDtos() {
    }

    public record MovieSummary(
            String slug,
            String name,
            String originName,
            String posterUrl,
            String thumbUrl,
            String quality,
            String lang,
            String episodeCurrent,
            Integer year,
            String type,
            List<String> categories,
            List<String> categorySlugs,
            List<String> countries,
            List<String> countrySlugs,
            Integer tmdbId,
            String sourceId,
            String modifiedTime
    ) {
    }

    public record PagedMovieResponse(
            Integer page,
            Integer totalPages,
            Integer totalItems,
            List<MovieSummary> items,
            JsonNode raw
    ) {
    }

    public record HomeResponse(
            List<MovieSummary> latest,
            List<MovieSummary> series,
            List<MovieSummary> single,
            List<MovieSummary> animation,
            List<MovieSummary> tvShows
    ) {
    }

    public record AuthPageImageItem(
            Long id,
            String imageUrl,
            String title,
            String description,
            Integer focalPointX,
            Integer focalPointY,
            Instant createdAt
    ) {
    }

    public record AuthPageImageListResponse(
            List<AuthPageImageItem> items
    ) {
    }

    public record MovieDetailResponse(
            MovieSummary movie,
            JsonNode raw,
            JsonNode episodes,
            Map<String, Object> metadata
    ) {
    }
}
