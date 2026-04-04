package com.filmbe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class LibraryDtos {
    private LibraryDtos() {
    }

    public record SaveMovieRequest(
            @NotBlank @Size(max = 255) String movieSlug,
            @NotBlank @Size(max = 255) String movieName,
            @Size(max = 255) String originName,
            @Size(max = 1000) String posterUrl,
            @Size(max = 1000) String thumbUrl,
            @Size(max = 50) String quality,
            @Size(max = 100) String lang,
            @Size(max = 100) String year
    ) {
    }

    public record SaveHistoryRequest(
            @NotBlank @Size(max = 255) String movieSlug,
            @NotBlank @Size(max = 255) String movieName,
            @Size(max = 255) String originName,
            @Size(max = 1000) String posterUrl,
            @Size(max = 1000) String thumbUrl,
            @Size(max = 50) String quality,
            @Size(max = 100) String lang,
            @Size(max = 100) String year,
            @Size(max = 255) String lastEpisodeName,
            Integer lastPositionSeconds
    ) {
    }

    public record LibraryMovieResponse(
            Long id,
            String movieSlug,
            String movieName,
            String originName,
            String posterUrl,
            String thumbUrl,
            String quality,
            String lang,
            String year,
            Instant savedAt
    ) {
    }

    public record WatchHistoryResponse(
            Long id,
            String movieSlug,
            String movieName,
            String originName,
            String posterUrl,
            String thumbUrl,
            String quality,
            String lang,
            String year,
            String lastEpisodeName,
            Integer lastPositionSeconds,
            Instant updatedAt
    ) {
    }

    public record WishlistStateResponse(
            boolean wished,
            List<LibraryMovieResponse> items
    ) {
    }
}

