package com.filmbe.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "auth_page_images")
public class AuthPageImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_url", nullable = false, length = 1000)
    private String imageUrl;

    @Column(length = 120)
    private String title;

    @Column(length = 320)
    private String description;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 1;

    @Column(name = "focal_point_x", nullable = false)
    private Integer focalPointX = 50;

    @Column(name = "focal_point_y", nullable = false)
    private Integer focalPointY = 50;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
