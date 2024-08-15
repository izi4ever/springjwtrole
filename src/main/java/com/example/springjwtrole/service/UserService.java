package com.example.springjwtrole.service;

import com.example.springjwtrole.model.ConfirmationToken;
import com.example.springjwtrole.model.User;
import com.example.springjwtrole.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ConfirmationTokenService tokenService;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;

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
        String domain = "http://localhost:8080";  // Заменить на динамическое получение хоста
        String confirmationUrl = domain + "/confirm?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Подтверждение регистрации");
        message.setText("Перейдите по следующей ссылке для подтверждения регистрации: " + confirmationUrl);
        mailSender.send(message);
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
}