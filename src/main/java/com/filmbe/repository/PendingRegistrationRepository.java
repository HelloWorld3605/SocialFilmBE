package com.filmbe.repository;

import com.filmbe.model.PendingRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {
    Optional<PendingRegistration> findByVerificationToken(String verificationToken);

    void deleteByEmailIgnoreCase(String email);
}
