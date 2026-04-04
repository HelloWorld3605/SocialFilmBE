package com.filmbe.service;

import com.filmbe.dto.AuthDtos;
import com.filmbe.model.PendingRegistration;
import com.filmbe.model.Role;
import com.filmbe.model.User;
import com.filmbe.repository.PendingRegistrationRepository;
import com.filmbe.repository.UserRepository;
import com.filmbe.security.JwtService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final EmailService emailService;

    @org.springframework.beans.factory.annotation.Value("${app.verification.token-expiration-minutes:15}")
    private long verificationTokenExpirationMinutes;

    @org.springframework.beans.factory.annotation.Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @org.springframework.beans.factory.annotation.Value("${app.mail.dev-mode:false}")
    private boolean mailDevMode;

    @Transactional
    public AuthDtos.MessageResponse startRegistration(AuthDtos.StartRegistrationRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email đã tồn tại.");
        }

        pendingRegistrationRepository.deleteByEmailIgnoreCase(email);

        PendingRegistration pendingRegistration = new PendingRegistration();
        pendingRegistration.setEmail(email);
        pendingRegistration.setVerificationToken(UUID.randomUUID().toString());
        pendingRegistration.setExpiresAt(
                Instant.now().plus(Duration.ofMinutes(verificationTokenExpirationMinutes))
        );
        pendingRegistrationRepository.save(pendingRegistration);

        try {
            emailService.sendRegistrationVerification(email, pendingRegistration.getVerificationToken());
        } catch (RuntimeException exception) {
            if (!mailDevMode) {
                pendingRegistrationRepository.delete(pendingRegistration);
                throw exception;
            }

            return new AuthDtos.MessageResponse(
                    "SMTP đang lỗi trên máy local. Dùng link xác thực tạm để tiếp tục đăng ký.",
                    buildDebugVerificationUrl(pendingRegistration.getVerificationToken())
            );
        }

        return new AuthDtos.MessageResponse("Email xác thực đã được gửi đến " + email + ".", null);
    }

    @Transactional(readOnly = true)
    public AuthDtos.TokenValidationResponse validateRegistrationToken(String token) {
        PendingRegistration pendingRegistration = pendingRegistrationRepository.findByVerificationToken(token)
                .orElseThrow(() -> new EntityNotFoundException("Token xác thực không hợp lệ."));

        boolean valid = pendingRegistration.getExpiresAt().isAfter(Instant.now());
        if (!valid) {
            throw new IllegalArgumentException("Token xác thực đã hết hạn.");
        }

        return new AuthDtos.TokenValidationResponse(
                true,
                pendingRegistration.getEmail(),
                pendingRegistration.getExpiresAt()
        );
    }

    @Transactional
    public AuthDtos.AuthResponse completeRegistration(AuthDtos.CompleteRegistrationRequest request) {
        PendingRegistration pendingRegistration = pendingRegistrationRepository
                .findByVerificationToken(request.verificationToken())
                .orElseThrow(() -> new IllegalArgumentException("Token xác thực không hợp lệ."));

        if (pendingRegistration.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Token xác thực đã hết hạn.");
        }

        if (userRepository.existsByEmailIgnoreCase(pendingRegistration.getEmail())) {
            throw new IllegalArgumentException("Email đã tồn tại.");
        }

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(pendingRegistration.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER);
        user.setEmailVerified(true);
        userRepository.save(user);

        pendingRegistrationRepository.delete(pendingRegistration);

        return createAuthResponse(user);
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizeEmail(request.email()), request.password())
        );
        User user = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng."));
        return createAuthResponse(user);
    }

    public AuthDtos.UserProfileResponse me(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng."));
        return toProfile(user);
    }

    AuthDtos.AuthResponse createAuthResponse(User user) {
        return new AuthDtos.AuthResponse(jwtService.generateToken(user.getEmail()), toProfile(user));
    }

    AuthDtos.UserProfileResponse toProfile(User user) {
        return new AuthDtos.UserProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name(),
                user.getAvatarUrl(),
                user.getBio(),
                user.isEmailVerified(),
                user.getCreatedAt()
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String buildDebugVerificationUrl(String token) {
        return frontendUrl + "/validate-token/" + token;
    }
}
