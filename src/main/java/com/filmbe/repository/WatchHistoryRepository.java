package com.filmbe.repository;

import com.filmbe.model.WatchHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WatchHistoryRepository extends JpaRepository<WatchHistory, Long> {
    interface UserCountProjection {
        Long getUserId();

        long getTotal();
    }

    interface MovieCountProjection {
        String getMovieSlug();

        String getMovieName();

        String getThumbUrl();

        long getTotal();
    }

    List<WatchHistory> findAllByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<WatchHistory> findByIdAndUserId(Long id, Long userId);

    Optional<WatchHistory> findByUserIdAndMovieSlugAndLastEpisodeIndex(Long userId, String movieSlug, Integer lastEpisodeIndex);

    Optional<WatchHistory> findByUserIdAndMovieSlugAndLastEpisodeName(Long userId, String movieSlug, String lastEpisodeName);

    Optional<WatchHistory> findTopByUserIdAndMovieSlugOrderByUpdatedAtDesc(Long userId, String movieSlug);

    List<WatchHistory> findTop5ByUserIdOrderByUpdatedAtDesc(Long userId);

    long countByUserId(Long userId);

    long countByCreatedAtAfter(Instant createdAt);

    @Query("select count(distinct w.user.id) from WatchHistory w where w.updatedAt >= :updatedAfter")
    long countDistinctActiveUsersByUpdatedAtAfter(@Param("updatedAfter") Instant updatedAfter);

    @EntityGraph(attributePaths = "user")
    List<WatchHistory> findTop8ByOrderByUpdatedAtDesc();

    @Query("""
            select w.user.id as userId, count(w) as total
            from WatchHistory w
            where w.user.id in :userIds
            group by w.user.id
            """)
    List<UserCountProjection> countGroupedByUserIds(@Param("userIds") Collection<Long> userIds);

    @Query("""
            select w.movieSlug as movieSlug, max(w.movieName) as movieName, max(w.thumbUrl) as thumbUrl, count(w) as total
            from WatchHistory w
            group by w.movieSlug
            order by count(w) desc
            """)
    List<MovieCountProjection> findTopMovieCounts(Pageable pageable);

    long deleteByIdAndUserId(Long id, Long userId);
}
