package com.akash.budgetchanger.util;

/**
 * Deterministic phone number normalizer.
 *
 * Handles every format Twilio or a web user may supply:
 *
 *   whatsapp:+918101834270  →  +918101834270
 *   whatsapp:918101834270   →  +918101834270
 *   +918101834270           →  +918101834270  (no-op)
 *   918101834270            →  +918101834270
 *   8101834270              →  +918101834270  (10-digit Indian)
 *   +91 81018 34270         →  +918101834270  (spaces stripped)
 *   +1 415 523 8886         →  +14155238886   (non-Indian — kept as-is)
 *
 * All DB writes and lookups must go through normalize() so the stored
 * format is always identical to the lookup key.
 */
public final class PhoneNormalizer {

    private PhoneNormalizer() {}

    /**
     * Normalize a raw phone string to E.164 format.
     *
     * @param raw any phone string (may include "whatsapp:", spaces, dashes, etc.)
     * @return normalized E.164 string, or null if input is null
     */
    public static String normalize(String raw) {
        if (raw == null) return null;

        // ── Step 1: strip "whatsapp:" prefix ──────────────────────────────────
        String n = raw.replace("whatsapp:", "").trim();

        // ── Step 2: remove every character that is not a digit or '+' ─────────
        n = n.replaceAll("[^0-9+]", "");

        if (n.isEmpty()) return raw.trim();  // un-parseable — return cleaned input

        // ── Step 3: normalise to E.164 ─────────────────────────────────────────

        // Already correct E.164 with country code +91
        if (n.startsWith("+91") && n.length() == 13) return n;

        // +91 with wrong length — still starts with +91, trust it
        if (n.startsWith("+91")) return n;

        // Any other +XX international number — keep as-is
        if (n.startsWith("+")) return n;

        // 91XXXXXXXXXX (12 digits, no +)
        if (n.startsWith("91") && n.length() == 12) return "+" + n;

        // 10-digit Indian number (no country code at all)
        if (n.length() == 10) return "+91" + n;

        // Fallback: prepend + if it looks like a full international number
        return "+" + n;
    }

    /**
     * Returns true if the raw string looks like it contains a phone number
     * that can be normalized (has at least 10 digits).
     */
    public static boolean isValid(String raw) {
        if (raw == null || raw.isBlank()) return false;
        String digitsOnly = raw.replaceAll("[^0-9]", "");
        return digitsOnly.length() >= 10;
    }
}
