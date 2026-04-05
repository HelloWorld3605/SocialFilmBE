package com.filmbe.service;

import com.filmbe.dto.LibraryDtos;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class BufferedWatchProgressState {
    private Long historyId;
    private Long userId;
    private String movieSlug;
    private String movieName;
    private String originName;
    private String posterUrl;
    private String thumbUrl;
    private String quality;
    private String lang;
    private String year;
    private String lastEpisodeName;
    private Integer lastPositionSeconds;
    private Integer lastServerIndex;
    private Integer lastEpisodeIndex;
    private Integer durationSeconds;
    private Instant updatedAt;
    private Instant lastFlushedAt;
    private Integer lastFlushedPositionSeconds;
    private boolean dirty;

    public LibraryDtos.WatchHistoryResponse toResponse() {
        return new LibraryDtos.WatchHistoryResponse(
                historyId,
                movieSlug,
                movieName,
                originName,
                posterUrl,
                thumbUrl,
                quality,
                lang,
                year,
                lastEpisodeName,
                lastPositionSeconds,
                lastServerIndex,
                lastEpisodeIndex,
                durationSeconds,
                updatedAt
        );
    }
}
