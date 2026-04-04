package com.filmbe.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Override
    public void sendRegistrationVerification(String email, String verificationToken) {
        if (fromEmail == null || fromEmail.isBlank()) {
            throw new IllegalStateException("Email gửi đi chưa được cấu hình.");
        }

        String verificationUrl = frontendUrl + "/validate-token/" + verificationToken;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(email);
        message.setSubject("Xác thực email để đăng ký - SocialFilm");
        message.setText(
                "Chào bạn,\n\n" +
                        "Vui lòng nhấp vào liên kết bên dưới để hoàn tất đăng ký tài khoản SocialFilm:\n\n" +
                        verificationUrl + "\n\n" +
                        "Liên kết này sẽ hết hạn sau ít phút. Nếu bạn không thực hiện yêu cầu này, hãy bỏ qua email.\n\n" +
                        "SocialFilm"
        );

        try {
            mailSender.send(message);
            log.info("Registration verification email sent to {}", email);
        } catch (Exception exception) {
            log.error("Failed to send registration verification email to {}", email, exception);
            throw new IllegalStateException("Không thể gửi email xác thực. Vui lòng thử lại.");
        }
    }
}
