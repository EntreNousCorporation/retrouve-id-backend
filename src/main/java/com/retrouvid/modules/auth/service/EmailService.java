package com.retrouvid.modules.auth.service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${app.mail.from:no-reply@retrouvid.local}")
    private String from;

    @Value("${spring.mail.host:}")
    private String mailHost;

    public void send(String to, String subject, String htmlBody) {
        if (mailSender == null || mailHost == null || mailHost.isBlank() || to == null || to.isBlank()) {
            log.warn("Email non envoyé (SMTP non configuré ou destinataire vide). to={} subject={} body={}",
                    to, subject, htmlBody);
            return;
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(msg);
            log.info("Email envoyé à {} sujet='{}'", to, subject);
        } catch (Exception e) {
            log.error("Échec envoi email à {} : {}", to, e.getMessage(), e);
        }
    }
}
