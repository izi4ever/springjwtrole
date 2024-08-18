package com.example.springjwtrole.service;

import com.example.springjwtrole.model.ConfirmationToken;
import com.example.springjwtrole.model.User;
import com.example.springjwtrole.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ConfirmationTokenService tokenService;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;
    private final UserCleanupService userCleanupService;

    @Transactional
    public User save(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user = userRepository.save(user);
        ConfirmationToken token = tokenService.createToken(user);
        sendConfirmationEmail(user.getEmail(), token.getToken());
        return user;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    private void sendConfirmationEmail(String email, String token) {
        String confirmationUrl = buildUrl("/confirm?token=" + token);
        sendEmail(email, "Подтверждение регистрации", "Перейдите по следующей ссылке для подтверждения регистрации: " + confirmationUrl);
    }

    @Transactional
    public void confirmUser(String token) {
        ConfirmationToken confirmationToken = tokenService.validateToken(token);
        User user = confirmationToken.getUser();
        if (user.isEnabled()) {
            throw new IllegalStateException("Аккаунт уже подтвержден.");
        }
        user.setEnabled(true);
        userRepository.save(user);
        tokenService.deleteToken(confirmationToken);
    }

    public void cleanupUnconfirmedUsers() {
        userCleanupService.removeUnconfirmedUsersManually();
    }

    public void sendPasswordResetEmail(User user) {
        String token = UUID.randomUUID().toString();
        savePasswordResetToken(user, token);
        String resetUrl = buildUrl("/reset-password?token=" + token);
        sendEmail(user.getEmail(), "Сброс пароля", "Перейдите по следующей ссылке для сброса пароля: " + resetUrl);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        ConfirmationToken confirmationToken = tokenService.validateToken(token);
        User user = confirmationToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        tokenService.deleteToken(confirmationToken);
    }

    public boolean isPasswordResetTokenValid(String token) {
        return tokenService.validateToken(token) != null;
    }

    private void savePasswordResetToken(User user, String token) {
        ConfirmationToken confirmationToken = new ConfirmationToken(token, user);
        tokenService.saveToken(confirmationToken);
    }

    private String buildUrl(String path) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String serverName = request.getServerName();
        String scheme = request.getScheme();
        int serverPort = request.getServerPort();

        String domain;
        if (serverName.equals("localhost")) {
            domain = scheme + "://" + serverName + ":" + serverPort;
        } else {
            domain = scheme + "://" + serverName;
        }

        return domain + path;
    }

    private void sendEmail(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}