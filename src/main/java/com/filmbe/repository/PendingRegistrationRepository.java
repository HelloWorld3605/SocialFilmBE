package com.filmbe.repository;

import com.filmbe.model.PendingRegistration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {
    Optional<PendingRegistration> findByVerificationToken(String verificationToken);

    Optional<PendingRegistration> findByEmailIgnoreCase(String email);

    void deleteByEmailIgnoreCase(String email);

    long countByExpiresAtAfter(Instant expiresAt);

    Page<PendingRegistration> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<PendingRegistration> findAllByEmailContainingIgnoreCaseOrderByCreatedAtDesc(
            String email,
            Pageable pageable
    );
}
