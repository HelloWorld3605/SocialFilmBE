package com.filmbe.controller;

import com.filmbe.dto.AuthDtos;
import com.filmbe.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/start-registration")
    public AuthDtos.MessageResponse startRegistration(
            @Valid @RequestBody AuthDtos.StartRegistrationRequest request
    ) {
        return authService.startRegistration(request);
    }

    @GetMapping("/validate-token/{token}")
    public AuthDtos.TokenValidationResponse validateRegistrationToken(@PathVariable String token) {
        return authService.validateRegistrationToken(token);
    }

    @PostMapping("/complete-registration")
    public AuthDtos.AuthResponse completeRegistration(
            @Valid @RequestBody AuthDtos.CompleteRegistrationRequest request
    ) {
        return authService.completeRegistration(request);
    }

    @PostMapping("/forgot-password")
    public AuthDtos.MessageResponse forgotPassword(
            @Valid @RequestBody AuthDtos.ForgotPasswordRequest request
    ) {
        return authService.forgotPassword(request);
    }

    @GetMapping("/reset-password/validate/{token}")
    public AuthDtos.PasswordResetTokenValidationResponse validatePasswordResetToken(
            @PathVariable String token
    ) {
        return authService.validatePasswordResetToken(token);
    }

    @PostMapping("/reset-password")
    public AuthDtos.MessageResponse resetPassword(
            @Valid @RequestBody AuthDtos.ResetPasswordRequest request
    ) {
        return authService.resetPassword(request);
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public AuthDtos.UserProfileResponse me(Authentication authentication) {
        return authService.me(authentication.getName());
    }
}
