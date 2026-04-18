package com.filmbe.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Slf4j
@Configuration
public class MailConfig {

    @Bean
    public JavaMailSender javaMailSender(
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.port:587}") int port,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${spring.mail.properties.mail.smtp.auth:true}") boolean smtpAuth,
            @Value("${spring.mail.properties.mail.smtp.starttls.enable:true}") boolean startTlsEnable,
            @Value("${spring.mail.properties.mail.smtp.starttls.required:true}") boolean startTlsRequired,
            @Value("${spring.mail.properties.mail.smtp.ssl.trust:}") String sslTrust
    ) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(normalize(host));
        mailSender.setPort(port);
        mailSender.setUsername(normalize(username));
        mailSender.setPassword(normalizePassword(host, password));
        mailSender.setDefaultEncoding("UTF-8");

        Properties properties = mailSender.getJavaMailProperties();
        properties.setProperty("mail.smtp.auth", Boolean.toString(smtpAuth));
        properties.setProperty("mail.smtp.starttls.enable", Boolean.toString(startTlsEnable));
        properties.setProperty("mail.smtp.starttls.required", Boolean.toString(startTlsRequired));

        String normalizedSslTrust = normalize(sslTrust);
        if (!normalizedSslTrust.isBlank()) {
            properties.setProperty("mail.smtp.ssl.trust", normalizedSslTrust);
        }

        return mailSender;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizePassword(String host, String password) {
        String normalizedPassword = normalize(password);
        if (!isGmailHost(host)) {
            return normalizedPassword;
        }

        String compactPassword = normalizedPassword.replaceAll("\\s+", "");
        if (!compactPassword.equals(normalizedPassword)) {
            log.warn("Removed whitespace from Gmail SMTP password before authentication.");
        }
        return compactPassword;
    }

    private boolean isGmailHost(String host) {
        return normalize(host).equalsIgnoreCase("smtp.gmail.com");
    }
}
