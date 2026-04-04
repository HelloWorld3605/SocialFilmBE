package com.filmbe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "wishlist_items", uniqueConstraints = {
        @UniqueConstraint(name = "uk_wishlist_user_slug", columnNames = {"user_id", "movie_slug"})
})
public class WishlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "movie_slug", nullable = false, length = 255)
    private String movieSlug;

    @Column(name = "movie_name", nullable = false, length = 255)
    private String movieName;

    @Column(name = "origin_name", length = 255)
    private String originName;

    @Column(name = "poster_url", length = 1000)
    private String posterUrl;

    @Column(name = "thumb_url", length = 1000)
    private String thumbUrl;

    @Column(length = 50)
    private String quality;

    @Column(length = 100)
    private String lang;

    @Column(name = "movie_year", length = 100)
    private String year;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
