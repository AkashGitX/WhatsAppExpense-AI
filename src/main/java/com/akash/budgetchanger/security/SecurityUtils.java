package com.akash.budgetchanger.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Convenience utility for controllers to get the currently authenticated userId
 * without coupling directly to the SecurityContextHolder.
 *
 * The userId is set as the Authentication principal by JwtFilter after
 * successful token validation.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * Returns the userId of the currently authenticated user.
     *
     * @throws IllegalStateException if no authenticated user is present
     *         (should not happen on endpoints protected by Spring Security)
     */
    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        throw new IllegalStateException(
                "No authenticated user found in SecurityContext. " +
                "Ensure the endpoint is protected by the JwtFilter.");
    }
}
