package com.filmbe.repository;

import com.filmbe.model.WatchHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchHistoryRepository extends JpaRepository<WatchHistory, Long> {
    List<WatchHistory> findAllByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<WatchHistory> findByUserIdAndMovieSlugAndLastEpisodeIndex(Long userId, String movieSlug, Integer lastEpisodeIndex);

    Optional<WatchHistory> findByUserIdAndMovieSlugAndLastEpisodeName(Long userId, String movieSlug, String lastEpisodeName);

    Optional<WatchHistory> findTopByUserIdAndMovieSlugOrderByUpdatedAtDesc(Long userId, String movieSlug);

    long deleteByIdAndUserId(Long id, Long userId);
}
