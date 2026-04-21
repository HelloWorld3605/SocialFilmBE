package com.filmbe.repository;

import com.filmbe.model.AuthPageImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuthPageImageRepository extends JpaRepository<AuthPageImage, Long> {
    List<AuthPageImage> findAllByOrderByDisplayOrderAscCreatedAtDescIdDesc();

    Optional<AuthPageImage> findFirstByOrderByDisplayOrderDescIdDesc();

    boolean existsByDisplayOrder(Integer displayOrder);

    boolean existsByDisplayOrderAndIdNot(Integer displayOrder, Long id);
}
