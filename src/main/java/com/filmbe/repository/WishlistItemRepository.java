package com.filmbe.repository;

import com.filmbe.model.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {
    List<WishlistItem> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<WishlistItem> findByUserIdAndMovieSlug(Long userId, String movieSlug);

    void deleteByUserIdAndMovieSlug(Long userId, String movieSlug);
}

