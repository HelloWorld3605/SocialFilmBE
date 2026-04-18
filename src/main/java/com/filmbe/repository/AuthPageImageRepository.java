package com.filmbe.repository;

import com.filmbe.model.AuthPageImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuthPageImageRepository extends JpaRepository<AuthPageImage, Long> {
    List<AuthPageImage> findAllByOrderByCreatedAtDescIdDesc();
}
