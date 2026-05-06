package com.akash.budgetchanger.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Prints a clear configuration status table at startup.
 * Shows which integrations are ready vs missing — without leaking secret values.
 *
 * Check the application log right after "Started BudgetBotApplication" to see this output.
 */
@Slf4j
@Component
public class StartupConfigValidator {

    @Value("${spring.ai.openai.api-key:sk-placeholder}")
    private String openAiKey;

    @Value("${twilio.account.sid:placeholder-sid}")
    private String twilioSid;

    @Value("${twilio.auth.token:placeholder-token}")
    private String twilioToken;

    @Value("${twilio.whatsapp.number:+14155238886}")
    private String twilioNumber;

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    @PostConstruct
    public void validate() {
        log.info("┌─────────────────────────────────────────────────────┐");
        log.info("│          BudgetBot AI — Configuration Status        │");
        log.info("├───────────────────────┬─────────────────────────────┤");
        log.info("│ OpenAI API Key        │ {}  │", statusOf(openAiKey, "sk-placeholder"));
        log.info("│ Twilio Account SID    │ {}  │", statusOf(twilioSid, "placeholder-sid"));
        log.info("│ Twilio Auth Token     │ {}  │", statusOf(twilioToken, "placeholder-token"));
        log.info("│ Twilio WhatsApp #     │ {}  │", padded(twilioNumber));
        log.info("│ JWT Secret            │ {}  │", statusOfJwt());
        log.info("└───────────────────────┴─────────────────────────────┘");

        if (isPlaceholder(openAiKey, "sk-placeholder")) {
            log.warn("⚠  OPENAI_API_KEY is not set — AI expense extraction is DISABLED.");
            log.warn("   Set the OPENAI_API_KEY secret in Replit Secrets to enable AI features.");
        }
        if (isPlaceholder(twilioSid, "placeholder-sid") || isPlaceholder(twilioToken, "placeholder-token")) {
            log.warn("⚠  Twilio credentials are not set — WhatsApp sending is DISABLED.");
            log.warn("   Set TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN in Replit Secrets.");
        }
    }

    private String statusOf(String value, String placeholder) {
        if (isPlaceholder(value, placeholder)) {
            return padded("❌ NOT SET (using placeholder)");
        }
        // Show only the first 6 chars of the actual value for a quick sanity check
        String preview = value.length() > 6 ? value.substring(0, 6) + "…" : value;
        return padded("✅ SET (" + preview + ")           ");
    }

    private String statusOfJwt() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            return padded("⚠  using default (set JWT_SECRET)");
        }
        return padded("✅ SET (" + jwtSecret.length() + " chars)             ");
    }

    private boolean isPlaceholder(String value, String placeholder) {
        return value == null || value.isBlank() || value.startsWith(placeholder);
    }

    private String padded(String s) {
        // Pad or truncate to exactly 27 chars for alignment
        if (s.length() >= 27) return s.substring(0, 27);
        return s + " ".repeat(27 - s.length());
    }
}
