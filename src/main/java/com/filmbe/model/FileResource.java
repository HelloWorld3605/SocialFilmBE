package com.filmbe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "file_resources")
public class FileResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String hash;

    @Column(nullable = false, length = 1000)
    private String url;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
