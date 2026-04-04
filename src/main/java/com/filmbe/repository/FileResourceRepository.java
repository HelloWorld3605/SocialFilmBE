package com.filmbe.repository;

import com.filmbe.model.FileResource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FileResourceRepository extends JpaRepository<FileResource, Long> {
    Optional<FileResource> findByHash(String hash);
}
