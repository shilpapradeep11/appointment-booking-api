package com.dm.appt.email;

import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.MimeMessageHelper;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @SneakyThrows
    public void sendAppointmentNotification(String to, String subject, String body) {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false);

        // ✅ Set the sender with display name
        helper.setFrom(new InternetAddress("admin@example.com", "Appointment Admin"));
        System.out.println("**** To: "+to + "**** Subject: "+ "**** Body: "+body + "**** From: "+ mimeMessage.getFrom());
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(body);
        mailSender.send(mimeMessage);
    }
}
