package com.filmbe.repository;

import com.filmbe.model.WatchHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchHistoryRepository extends JpaRepository<WatchHistory, Long> {
    List<WatchHistory> findTop20ByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<WatchHistory> findByUserIdAndMovieSlug(Long userId, String movieSlug);
}

