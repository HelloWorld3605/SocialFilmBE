package com.filmbe.repository;

import com.filmbe.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByRole(com.filmbe.enums.Role role);

    long countByEmailVerifiedTrue();

    long countByCreatedAtAfter(Instant createdAt);
}
