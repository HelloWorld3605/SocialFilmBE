package com.filmbe.config;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;

class MailConfigTests {

    private final MailConfig mailConfig = new MailConfig();

    @Test
    void removesWhitespaceFromGmailAppPassword() {
        JavaMailSenderImpl sender = (JavaMailSenderImpl) mailConfig.javaMailSender(
                "smtp.gmail.com",
                587,
                " sender@gmail.com ",
                "ab cd ef gh ij kl mn op",
                true,
                true,
                true,
                " smtp.gmail.com "
        );

        assertThat(sender.getHost()).isEqualTo("smtp.gmail.com");
        assertThat(sender.getUsername()).isEqualTo("sender@gmail.com");
        assertThat(sender.getPassword()).isEqualTo("abcdefghijklmnop");
        assertThat(sender.getJavaMailProperties().getProperty("mail.smtp.ssl.trust"))
                .isEqualTo("smtp.gmail.com");
    }

    @Test
    void preservesInternalWhitespaceForNonGmailPasswords() {
        JavaMailSenderImpl sender = (JavaMailSenderImpl) mailConfig.javaMailSender(
                "smtp.office365.com",
                587,
                "sender@company.com",
                " pass with spaces ",
                true,
                true,
                false,
                ""
        );

        assertThat(sender.getPassword()).isEqualTo("pass with spaces");
        assertThat(sender.getJavaMailProperties().getProperty("mail.smtp.starttls.required"))
                .isEqualTo("false");
    }
}
