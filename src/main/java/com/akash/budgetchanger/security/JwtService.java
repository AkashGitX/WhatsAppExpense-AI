package com.akash.budgetchanger.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Arrays;
import java.util.Date;

/**
 * Generates and validates HS256 JWT tokens.
 * Subject = userId (Long, stored as a String in the "sub" claim).
 */
@Slf4j
@Component
public class JwtService {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    // ── Public API ────────────────────────────────────────────────────────────

    /** Create a signed JWT for the given userId. */
    public String generateToken(Long userId) {
        return Jwts.builder()
                .setSubject(userId.toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /** Extract userId from a valid token. Throws if the token is invalid/expired. */
    public Long extractUserId(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return Long.parseLong(claims.getSubject());
    }

    /** Returns true only when the token signature is valid and it has not expired. */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(signingKey()).build().parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("JWT unsupported: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("JWT malformed: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT empty/null: {}", e.getMessage());
        } catch (JwtException e) {
            log.warn("JWT invalid: {}", e.getMessage());
        }
        return false;
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /**
     * Build an HMAC-SHA256 key from the configured secret.
     * Pads to 32 bytes (256 bits) if the configured secret is shorter.
     */
    private Key signingKey() {
        byte[] raw = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            raw = Arrays.copyOf(raw, 32);
        }
        return Keys.hmacShaKeyFor(raw);
    }
}
