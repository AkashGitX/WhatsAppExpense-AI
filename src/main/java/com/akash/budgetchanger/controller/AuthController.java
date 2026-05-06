package com.akash.budgetchanger.controller;

import com.akash.budgetchanger.dto.AuthResponse;
import com.akash.budgetchanger.dto.LoginRequest;
import com.akash.budgetchanger.dto.RegisterRequest;
import com.akash.budgetchanger.security.LoginRateLimiter;
import com.akash.budgetchanger.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService      authService;
    private final LoginRateLimiter rateLimiter;

    /**
     * POST /auth/register
     * Register a new user. Phone number must be in E.164 format (+919876543210).
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("POST /auth/register — email: {}", request.getEmail());
        AuthResponse response = authService.register(request);
        HttpStatus status = response.isSuccess() ? HttpStatus.CREATED : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(response);
    }

    /**
     * POST /auth/login
     * Authenticate user. Returns a JWT token in the response body on success.
     * Rate-limited to 5 failed attempts per IP per 60 seconds.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {

        String ip = getClientIp(servletRequest);
        log.info("POST /auth/login — email: {}, ip: {}", request.getEmail(), ip);

        // Check rate limit before hitting the database
        if (!rateLimiter.isAllowed(ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(AuthResponse.fail(
                            "Too many failed login attempts. Please wait 60 seconds and try again."));
        }

        AuthResponse response = authService.login(request);

        if (response.isSuccess()) {
            rateLimiter.resetFailures(ip);
        } else {
            rateLimiter.recordFailure(ip);
        }

        HttpStatus status = response.isSuccess() ? HttpStatus.OK : HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(status).body(response);
    }

    /** Extract real client IP, accounting for reverse proxies. */
    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
