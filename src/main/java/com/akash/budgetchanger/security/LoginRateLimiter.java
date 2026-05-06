package com.akash.budgetchanger.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory rate limiter for the login endpoint.
 *
 * Rules:
 *   - Max 5 failed login attempts per IP address within a 60-second window.
 *   - After 5 failures the IP is locked out for 60 seconds.
 *   - A successful login resets the counter for that IP.
 *
 * Note: In a multi-instance deployment replace this with a Redis-backed
 *       solution. For a single-instance app this is production-safe.
 */
@Slf4j
@Component
public class LoginRateLimiter {

    private static final int  MAX_FAILURES  = 5;
    private static final long LOCKOUT_MS    = 60_000L; // 60 seconds

    private final Map<String, AtomicInteger> failureCounts = new ConcurrentHashMap<>();
    private final Map<String, Long>          lockoutUntil  = new ConcurrentHashMap<>();

    /**
     * Returns true if the given IP is allowed to attempt a login.
     * Returns false if the IP is currently locked out.
     */
    public boolean isAllowed(String ip) {
        Long until = lockoutUntil.get(ip);
        if (until != null) {
            if (System.currentTimeMillis() < until) {
                long remaining = (until - System.currentTimeMillis()) / 1000;
                log.warn("Login blocked for IP {} — {} second(s) remaining in lockout", ip, remaining);
                return false;
            }
            // Lockout expired — clear state
            lockoutUntil.remove(ip);
            failureCounts.remove(ip);
        }
        return true;
    }

    /**
     * Record a failed login attempt.  Locks out the IP after MAX_FAILURES.
     */
    public void recordFailure(String ip) {
        AtomicInteger count = failureCounts.computeIfAbsent(ip, k -> new AtomicInteger(0));
        int failures = count.incrementAndGet();
        log.debug("Failed login attempt #{} for IP {}", failures, ip);
        if (failures >= MAX_FAILURES) {
            lockoutUntil.put(ip, System.currentTimeMillis() + LOCKOUT_MS);
            failureCounts.remove(ip);
            log.warn("IP {} locked out for {} seconds after {} failed attempts", ip, LOCKOUT_MS / 1000, MAX_FAILURES);
        }
    }

    /** Reset failure counter on successful login. */
    public void resetFailures(String ip) {
        failureCounts.remove(ip);
        lockoutUntil.remove(ip);
    }
}
