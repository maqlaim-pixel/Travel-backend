package com.travelvista.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessagePreparator;
import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;

@Configuration
public class MailConfig {

    @Bean
    @ConditionalOnMissingBean(JavaMailSender.class)
    public JavaMailSender devMailSender() {
        // Extend JavaMailSenderImpl so we inherit all methods,
        // then override only the send methods to log instead of send.
        return new JavaMailSenderImpl() {

            @Override
            public MimeMessage createMimeMessage(InputStream contentStream) {
                return super.createMimeMessage(contentStream);
            }

            @Override
            public void send(SimpleMailMessage simpleMessage) {
                System.out.println("\n═══════════════════════════════════════");
                System.out.println("  DEV EMAIL (not sent — configure SMTP to send)");
                System.out.println("═══════════════════════════════════════");
                System.out.println("  To:      " + (simpleMessage.getTo() != null ? String.join(", ", simpleMessage.getTo()) : "N/A"));
                System.out.println("  Subject: " + simpleMessage.getSubject());
                System.out.println("  Body:\n" + simpleMessage.getText());
                System.out.println("═══════════════════════════════════════\n");
            }

            @Override
            public void send(SimpleMailMessage... simpleMessages) {
                for (SimpleMailMessage msg : simpleMessages) send(msg);
            }

            @Override
            public void send(MimeMessage mimeMessage) {
                System.out.println("[DEV] MimeMessage sent (logged only)");
            }

            @Override
            public void send(MimeMessage... mimeMessages) {
                for (MimeMessage msg : mimeMessages) send(msg);
            }

            @Override
            public void send(MimeMessagePreparator mimeMessagePreparator) {
                System.out.println("[DEV] MimeMessagePreparator sent (logged only)");
            }

            @Override
            public void send(MimeMessagePreparator... mimeMessagePreparators) {
                for (MimeMessagePreparator p : mimeMessagePreparators) send(p);
            }
        };
    }
}
