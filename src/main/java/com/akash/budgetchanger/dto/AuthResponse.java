package com.akash.budgetchanger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response returned by /auth/login and /auth/register.
 *
 * The {@code token} field carries the signed JWT on successful login.
 * The frontend must store it and include it as "Authorization: Bearer <token>"
 * on all subsequent API requests.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private boolean success;
    private String  message;
    private Long    userId;
    private String  name;
    private String  email;
    private String  phoneNumber;

    /** JWT access token — non-null only on successful login. */
    private String  token;

    /** URL of the user's profile picture (may be null). */
    private String  profileImageUrl;

    /** Successful response without token (e.g. registration). */
    public static AuthResponse ok(Long userId, String name, String email,
                                   String phoneNumber, String message) {
        return AuthResponse.builder()
                .success(true)
                .message(message)
                .userId(userId)
                .name(name)
                .email(email)
                .phoneNumber(phoneNumber)
                .build();
    }

    /** Successful response with JWT token (login). */
    public static AuthResponse ok(Long userId, String name, String email,
                                   String phoneNumber, String token, String message) {
        return AuthResponse.builder()
                .success(true)
                .message(message)
                .userId(userId)
                .name(name)
                .email(email)
                .phoneNumber(phoneNumber)
                .token(token)
                .build();
    }

    /** Failed response. */
    public static AuthResponse fail(String message) {
        return AuthResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}
