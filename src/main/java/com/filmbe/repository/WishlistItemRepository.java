package com.filmbe.repository;

import com.filmbe.model.WishlistItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {
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

    List<WishlistItem> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<WishlistItem> findByUserIdAndMovieSlug(Long userId, String movieSlug);

    List<WishlistItem> findTop5ByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserId(Long userId);

    long countByCreatedAtAfter(Instant createdAt);

    @EntityGraph(attributePaths = "user")
    List<WishlistItem> findTop8ByOrderByCreatedAtDesc();

    @Query("""
            select w.user.id as userId, count(w) as total
            from WishlistItem w
            where w.user.id in :userIds
            group by w.user.id
            """)
    List<UserCountProjection> countGroupedByUserIds(@Param("userIds") Collection<Long> userIds);

    @Query("""
            select w.movieSlug as movieSlug, max(w.movieName) as movieName, max(w.thumbUrl) as thumbUrl, count(w) as total
            from WishlistItem w
            group by w.movieSlug
            order by count(w) desc
            """)
    List<MovieCountProjection> findTopMovieCounts(Pageable pageable);

    void deleteByUserIdAndMovieSlug(Long userId, String movieSlug);
}
