package com.filmbe.service.email;

import com.filmbe.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
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

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Override
    public void sendEmailVerification(User user, String verificationToken) {
        String verificationUrl = frontendUrl + "/verify-email/" + verificationToken;
        String emailContent = String.format(
                "Chào %s,\n\n" +
                        "Cảm ơn bạn đã đăng ký tài khoản tại SocialFilm.\n\n" +
                        "Vui lòng nhấp vào liên kết bên dưới để xác thực email của bạn:\n\n" +
                        "%s\n\n" +
                        "Liên kết này sẽ hết hạn sau 24 giờ.\n\n" +
                        "Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email này.\n\n" +
                        "Trân trọng,\n" +
                        "Đội ngũ SocialFilm",
                displayNameOf(user),
                verificationUrl
        );

        sendTextEmail(
                user.getEmail(),
                "Xác thực email - SocialFilm",
                emailContent,
                "email verification",
                "Không thể gửi email xác thực. Vui lòng thử lại.",
                true
        );
    }

    @Override
    public void sendPasswordResetEmail(String email, String resetToken) {
        String resetUrl = frontendUrl + "/reset-password/" + resetToken;
        String emailContent = String.format(
                "Chào bạn,\n\n" +
                        "Bạn đã yêu cầu đặt lại mật khẩu cho tài khoản SocialFilm.\n\n" +
                        "Nhấp vào liên kết bên dưới để đặt lại mật khẩu:\n\n" +
                        "%s\n\n" +
                        "Liên kết này sẽ hết hạn sau 1 giờ.\n\n" +
                        "Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email này.\n\n" +
                        "Trân trọng,\n" +
                        "Đội ngũ SocialFilm",
                resetUrl
        );

        sendTextEmail(
                email,
                "Đặt lại mật khẩu - SocialFilm",
                emailContent,
                "password reset",
                "Không thể gửi email đặt lại mật khẩu. Vui lòng thử lại.",
                true
        );
    }

    @Override
    public void sendWelcomeEmail(User user) {
        String emailContent = String.format(
                "Chào %s,\n\n" +
                        "Chào mừng bạn đã gia nhập SocialFilm.\n\n" +
                        "Email của bạn đã được xác thực thành công.\n" +
                        "Chúc bạn có những trải nghiệm xem phim thật tốt.\n\n" +
                        "Trân trọng,\n" +
                        "Đội ngũ SocialFilm",
                displayNameOf(user)
        );

        sendTextEmail(
                user.getEmail(),
                "Chào mừng đến với SocialFilm!",
                emailContent,
                "welcome email",
                null,
                false
        );
    }

    @Override
    public void sendRegistrationVerification(String email, String verificationToken) {
        String verificationUrl = frontendUrl + "/validate-token/" + verificationToken;
        String emailContent = String.format(
                "Chào bạn,\n\n" +
                        "Cảm ơn bạn đã quan tâm đến SocialFilm.\n\n" +
                        "Để hoàn tất quá trình đăng ký, vui lòng nhấp vào liên kết bên dưới để xác thực email:\n\n" +
                        "%s\n\n" +
                        "Liên kết này sẽ hết hạn sau 15 phút.\n\n" +
                        "Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email này.\n\n" +
                        "Trân trọng,\n" +
                        "Đội ngũ SocialFilm",
                verificationUrl
        );

        sendTextEmail(
                email,
                "Xác thực email để đăng ký - SocialFilm",
                emailContent,
                "registration verification",
                "Không thể gửi email xác thực. Vui lòng thử lại.",
                true
        );
    }

    private void sendTextEmail(
            String toEmail,
            String subject,
            String body,
            String logContext,
            String errorMessage,
            boolean failOnError
    ) {
        String normalizedFromEmail = normalizedFromEmail();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(normalizedFromEmail);
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.send(message);
            log.info("{} email sent successfully to {}", logContext, toEmail);
        } catch (MailAuthenticationException exception) {
            log.error("SMTP authentication failed while sending {} email to {}", logContext, toEmail, exception);
            if (failOnError) {
                throw new IllegalStateException("Xác thực SMTP thất bại. Hãy kiểm tra tài khoản gửi và App Password Gmail.");
            }
        } catch (Exception exception) {
            log.error("Failed to send {} email to {}", logContext, toEmail, exception);
            if (failOnError && errorMessage != null && !errorMessage.isBlank()) {
                throw new IllegalStateException(errorMessage);
            }
        }
    }

    private String normalizedFromEmail() {
        String normalizedFromEmail = fromEmail == null ? "" : fromEmail.trim();
        if (normalizedFromEmail.isBlank()) {
            throw new IllegalStateException("Email gửi đi chưa được cấu hình.");
        }
        return normalizedFromEmail;
    }

    private String displayNameOf(User user) {
        String fullName = user.getFullName();
        if (fullName == null || fullName.isBlank()) {
            return "bạn";
        }
        return fullName.trim();
    }
}
