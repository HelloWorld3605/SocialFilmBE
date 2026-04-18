package com.filmbe.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record StartRegistrationRequest(
            @NotBlank @Email @Size(max = 160) String email
    ) {
    }

    public record CompleteRegistrationRequest(
            @NotBlank String verificationToken,
            @NotBlank @Size(min = 2, max = 120) String fullName,
            @NotBlank
            @Size(min = 6, max = 120)
            @Pattern(
                    regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{6,}$",
                    message = "Mật khẩu phải có ít nhất 6 ký tự, gồm chữ hoa, chữ thường và số."
            )
            String password
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {
    }

    public record ForgotPasswordRequest(
            @NotBlank @Email @Size(max = 160) String email
    ) {
    }

    public record ResetPasswordRequest(
            @NotBlank String resetToken,
            @NotBlank
            @Size(min = 6, max = 120)
            @Pattern(
                    regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{6,}$",
                    message = "Mật khẩu phải có ít nhất 6 ký tự, gồm chữ hoa, chữ thường và số."
            )
            String password
    ) {
    }

    public record MessageResponse(String message, String debugVerificationUrl) {
    }

    public record TokenValidationResponse(
            boolean valid,
            String email,
            Instant expiresAt
    ) {
    }

    public record PasswordResetTokenValidationResponse(
            boolean valid,
            String email,
            Instant expiresAt
    ) {
    }

    public record UploadResponse(String url) {
    }

    public record AuthResponse(
            String accessToken,
            UserProfileResponse user
    ) {
    }

    public record UserProfileResponse(
            Long id,
            String fullName,
            String email,
            String role,
            String avatarUrl,
            String bio,
            boolean emailVerified,
            Instant createdAt
    ) {
    }
}
