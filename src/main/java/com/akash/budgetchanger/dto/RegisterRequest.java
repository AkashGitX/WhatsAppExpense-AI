package com.akash.budgetchanger.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 128, message = "Password must be between 6 and 128 characters")
    private String password;

    /**
     * Phone number must be in E.164 format: + followed by country code and number.
     * Examples: +919876543210, +14155238886
     *
     * Enforcing E.164 at registration time ensures the number stored in the DB
     * will always match the format Twilio sends (after stripping the "whatsapp:" prefix).
     */
    @Pattern(
        regexp = "^\\+[1-9]\\d{7,14}$",
        message = "Phone number must be in E.164 format (e.g. +919876543210)"
    )
    private String phoneNumber;
}
