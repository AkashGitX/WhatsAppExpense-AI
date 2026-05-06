package com.akash.budgetchanger.service;

import com.akash.budgetchanger.dto.AuthResponse;
import com.akash.budgetchanger.dto.LoginRequest;
import com.akash.budgetchanger.dto.RegisterRequest;
import com.akash.budgetchanger.entity.User;
import com.akash.budgetchanger.repository.UserRepository;
import com.akash.budgetchanger.security.JwtService;
import com.akash.budgetchanger.util.PhoneNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService      jwtService;

    // ── Register ──────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Registration attempt for email: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration failed — email already in use: {}", request.getEmail());
            return AuthResponse.fail("Email is already registered. Please use a different email.");
        }

        // Normalize phone before ANY check or save so DB always stores E.164
        String normalizedPhone = (request.getPhoneNumber() != null)
                ? PhoneNormalizer.normalize(request.getPhoneNumber())
                : null;

        log.info("Registration phone: raw='{}' → normalized='{}'",
                request.getPhoneNumber(), normalizedPhone);

        if (normalizedPhone != null && userRepository.existsByPhoneNumber(normalizedPhone)) {
            log.warn("Registration failed — phone already in use: {}", normalizedPhone);
            return AuthResponse.fail("Phone number is already registered. Please use a different phone number.");
        }

        User user = User.builder()
                .name(request.getName().trim())
                .email(request.getEmail().trim().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(normalizedPhone)   // always E.164, matches Twilio format exactly
                .build();

        User saved = userRepository.save(user);
        log.info("User registered: id={}, email={}", saved.getId(), saved.getEmail());

        return AuthResponse.ok(
                saved.getId(),
                saved.getName(),
                saved.getEmail(),
                saved.getPhoneNumber(),
                "Registration successful! Welcome to BudgetBot AI."
        );
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        Optional<User> userOpt = userRepository.findByEmail(request.getEmail().trim().toLowerCase());

        if (userOpt.isEmpty()) {
            log.warn("Login failed — no account: {}", request.getEmail());
            return AuthResponse.fail("No account found with this email. Please register first.");
        }

        User user = userOpt.get();

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Login failed — wrong password for email: {}", request.getEmail());
            return AuthResponse.fail("Incorrect password. Please try again.");
        }

        // Generate JWT — contains userId as the "sub" claim
        String token = jwtService.generateToken(user.getId());
        log.info("Login successful: id={}, email={}", user.getId(), user.getEmail());

        return AuthResponse.ok(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhoneNumber(),
                token,
                "Login successful! Welcome back, " + user.getName() + "."
        );
    }
}
