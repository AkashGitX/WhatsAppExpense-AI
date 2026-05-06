package com.akash.budgetchanger.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileRequest {

    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    /**
     * Phone number must be in E.164 format (same rule as registration).
     * null is allowed — a null value means "don't change the phone number".
     */
    @Pattern(
        regexp = "^\\+[1-9]\\d{7,14}$",
        message = "Phone number must be in E.164 format (e.g. +919876543210)"
    )
    private String phoneNumber;
}
