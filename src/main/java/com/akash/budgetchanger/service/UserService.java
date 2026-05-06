package com.akash.budgetchanger.service;

import com.akash.budgetchanger.dto.AuthResponse;
import com.akash.budgetchanger.dto.UserProfileRequest;
import com.akash.budgetchanger.entity.User;
import com.akash.budgetchanger.repository.UserRepository;
import com.akash.budgetchanger.util.PhoneNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @Transactional(readOnly = true)
    public Optional<User> findById(Long userId) {
        return userRepository.findById(userId);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByPhoneNumber(String phone) {
        return userRepository.findByPhoneNumber(phone);
    }

    /**
     * Update the user's display name and/or phone number.
     * Only non-null, non-blank fields in the request are applied.
     */
    @Transactional
    public AuthResponse updateProfile(Long userId, UserProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName().trim());
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            String phone = PhoneNormalizer.normalize(request.getPhoneNumber().trim());
            log.info("Profile phone update: raw='{}' → normalized='{}'",
                    request.getPhoneNumber(), phone);
            if (!phone.equals(user.getPhoneNumber())) {
                userRepository.findByPhoneNumber(phone).ifPresent(existing -> {
                    if (!existing.getId().equals(userId)) {
                        throw new IllegalArgumentException("Phone number is already in use.");
                    }
                });
            }
            user.setPhoneNumber(phone);
        }

        User saved = userRepository.save(user);
        log.info("Profile updated for userId={}: name='{}', phone='{}'",
                userId, saved.getName(), saved.getPhoneNumber());

        return AuthResponse.builder()
                .success(true)
                .message("Profile updated successfully.")
                .userId(saved.getId())
                .name(saved.getName())
                .email(saved.getEmail())
                .phoneNumber(saved.getPhoneNumber())
                .profileImageUrl(saved.getProfileImageUrl())
                .build();
    }

    /**
     * Upload and store a profile picture for the user.
     * Deletes the previous image if one exists.
     * Accepts: jpg, jpeg, png, gif, webp — max 2 MB (enforced by multipart config).
     *
     * @return updated AuthResponse including the new profileImageUrl
     */
    @Transactional
    public AuthResponse uploadProfileImage(Long userId, MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Only image files are allowed.");
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String ext = originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase()
                : "";
        if (!List.of(".jpg", ".jpeg", ".png", ".gif", ".webp").contains(ext)) {
            throw new IllegalArgumentException("Allowed formats: JPG, PNG, GIF, WEBP.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Delete old image if one exists
        if (user.getProfileImageUrl() != null) {
            String oldRelPath = user.getProfileImageUrl().replaceFirst("^/uploads/", "");
            Path oldFile = Path.of(uploadDir, oldRelPath);
            try {
                Files.deleteIfExists(oldFile);
                log.info("Deleted old profile image: {}", oldFile);
            } catch (Exception ignored) {
                log.warn("Could not delete old profile image: {}", oldFile);
            }
        }

        // Save new image: {userId}_{timestamp}{ext}
        String filename   = userId + "_" + System.currentTimeMillis() + ext;
        Path   profileDir = Path.of(uploadDir, "profile");
        Files.createDirectories(profileDir);
        Files.copy(file.getInputStream(), profileDir.resolve(filename));

        String imageUrl = "/uploads/profile/" + filename;
        user.setProfileImageUrl(imageUrl);
        User saved = userRepository.save(user);

        log.info("Profile image saved for userId={}: {}", userId, imageUrl);

        return AuthResponse.builder()
                .success(true)
                .message("Profile image updated.")
                .userId(saved.getId())
                .name(saved.getName())
                .email(saved.getEmail())
                .phoneNumber(saved.getPhoneNumber())
                .profileImageUrl(saved.getProfileImageUrl())
                .build();
    }

    /**
     * Set or update the user's personal monthly budget.
     */
    @Transactional
    public BigDecimal updateBudget(Long userId, BigDecimal budget) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        user.setMonthlyBudget(budget);
        userRepository.save(user);
        log.info("Monthly budget updated for userId={}: ₹{}", userId, budget);
        return budget;
    }
}
