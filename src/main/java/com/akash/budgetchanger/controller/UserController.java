package com.akash.budgetchanger.controller;

import com.akash.budgetchanger.dto.ApiResponse;
import com.akash.budgetchanger.dto.AuthResponse;
import com.akash.budgetchanger.dto.BudgetRequest;
import com.akash.budgetchanger.dto.UserProfileRequest;
import com.akash.budgetchanger.entity.User;
import com.akash.budgetchanger.security.SecurityUtils;
import com.akash.budgetchanger.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

/**
 * REST API for user profile management.
 *
 * Authentication: JWT required (userId extracted from token — no query param).
 * Users can only read/update their own profile.
 */
@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * GET /users/me
     * Returns the currently authenticated user's profile.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthResponse>> getProfile() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("GET /users/me — userId={}", userId);

        User user = userService.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        AuthResponse profile = AuthResponse.builder()
                .success(true)
                .message("Profile retrieved.")
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .profileImageUrl(user.getProfileImageUrl())
                .build();

        return ResponseEntity.ok(ApiResponse.success("Profile retrieved.", profile));
    }

    /**
     * PATCH /users/me
     * Update name and/or phone number for the authenticated user.
     */
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<AuthResponse>> updateProfile(
            @Valid @RequestBody UserProfileRequest request) {

        Long userId = SecurityUtils.getCurrentUserId();
        log.info("PATCH /users/me — userId={}", userId);
        AuthResponse updated = userService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated.", updated));
    }

    /**
     * POST /users/me/profile-image
     * Upload a profile picture (multipart/form-data, field name: "file").
     * Accepted: JPG, PNG, GIF, WEBP — max 2 MB.
     */
    @PostMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AuthResponse>> uploadProfileImage(
            @RequestParam("file") MultipartFile file) {

        Long userId = SecurityUtils.getCurrentUserId();
        log.info("POST /users/me/profile-image — userId={}, filename={}, size={}B",
                userId, file.getOriginalFilename(), file.getSize());

        try {
            AuthResponse result = userService.uploadProfileImage(userId, file);
            return ResponseEntity.ok(ApiResponse.success("Profile image updated.", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail(e.getMessage()));
        } catch (Exception e) {
            log.error("Profile image upload failed for userId={}", userId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.fail("Upload failed. Please try again."));
        }
    }

    /**
     * PUT /users/budget
     * Set or update the authenticated user's personal monthly budget.
     */
    @PutMapping("/budget")
    public ResponseEntity<ApiResponse<BigDecimal>> updateBudget(
            @Valid @RequestBody BudgetRequest request) {

        Long userId = SecurityUtils.getCurrentUserId();
        log.info("PUT /users/budget — userId={}, budget={}", userId, request.getBudget());

        BigDecimal saved = userService.updateBudget(userId, request.getBudget());
        return ResponseEntity.ok(ApiResponse.success(
                "Monthly budget updated to ₹" + saved.toPlainString() + ".", saved));
    }
}
