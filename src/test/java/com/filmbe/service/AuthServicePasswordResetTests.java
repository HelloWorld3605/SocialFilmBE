package com.filmbe.service;

import com.filmbe.dto.AuthDtos;
import com.filmbe.model.PasswordResetToken;
import com.filmbe.model.User;
import com.filmbe.repository.PasswordResetTokenRepository;
import com.filmbe.repository.PendingRegistrationRepository;
import com.filmbe.repository.UserRepository;
import com.filmbe.security.JwtService;
import com.filmbe.service.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServicePasswordResetTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PendingRegistrationRepository pendingRegistrationRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() throws Exception {
        setField(authService, "passwordResetTokenExpirationMinutes", 60L);
        setField(authService, "frontendUrl", "http://localhost:5173");
        setField(authService, "mailDevMode", false);
    }

    @Test
    void forgotPasswordCreatesResetTokenAndSendsEmail() {
        User user = user(7L, "forgot@example.com");
        when(userRepository.findByEmailIgnoreCase("forgot@example.com")).thenReturn(Optional.of(user));

        AuthDtos.MessageResponse response = authService.forgotPassword(
                new AuthDtos.ForgotPasswordRequest("forgot@example.com")
        );

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).deleteByUserId(7L);
        verify(passwordResetTokenRepository).save(captor.capture());
        verify(emailService).sendPasswordResetEmail(
                org.mockito.ArgumentMatchers.eq("forgot@example.com"),
                org.mockito.ArgumentMatchers.anyString()
        );

        PasswordResetToken savedToken = captor.getValue();
        assertEquals(user, savedToken.getUser());
        assertTrue(savedToken.getExpiresAt().isAfter(Instant.now().plusSeconds(59 * 60)));
        assertEquals(
                "Nếu email tồn tại trong hệ thống, liên kết đặt lại mật khẩu đã được gửi.",
                response.message()
        );
    }

    @Test
    void resetPasswordUpdatesHashAndDeletesToken() {
        User user = user(3L, "reset@example.com");
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setResetToken("token-123");
        resetToken.setExpiresAt(Instant.now().plusSeconds(600));

        when(passwordResetTokenRepository.findByResetToken("token-123")).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("NewPass1")).thenReturn("encoded-password");

        AuthDtos.MessageResponse response = authService.resetPassword(
                new AuthDtos.ResetPasswordRequest("token-123", "NewPass1")
        );

        assertEquals("encoded-password", user.getPasswordHash());
        assertEquals(
                "Mật khẩu đã được đặt lại thành công. Bạn có thể đăng nhập ngay bây giờ.",
                response.message()
        );
        verify(userRepository).save(user);
        verify(passwordResetTokenRepository).delete(resetToken);
    }

    private User user(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName("Test User");
        return user;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
