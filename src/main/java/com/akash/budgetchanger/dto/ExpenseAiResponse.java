package com.akash.budgetchanger.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * DTO representing the structured expense data extracted by Spring AI from a
 * natural-language WhatsApp message.
 *
 * The {@link FlexibleBigDecimalDeserializer} on {@code amount} handles the case
 * where OpenAI returns the amount as a JSON string ("500") instead of a number (500).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExpenseAiResponse {

    /**
     * Accepts both numeric (500) and string ("500") JSON values from the AI.
     * Returns {@link BigDecimal#ZERO} for blank/unparseable values.
     */
    @JsonDeserialize(using = FlexibleBigDecimalDeserializer.class)
    private BigDecimal amount;

    private String category;
    private String note;

    /** ISO-8601 date string, e.g. "2026-04-25" */
    private String date;

    /** True when AI returned a valid, parseable expense object. */
    private boolean valid;

    /** Human-readable reason when valid = false. */
    private String errorReason;

    public static ExpenseAiResponse invalid(String reason) {
        return ExpenseAiResponse.builder()
                .valid(false)
                .errorReason(reason)
                .build();
    }

    // ── Custom deserializer ───────────────────────────────────────────────────

    /**
     * Handles both numeric and string representations of the amount field.
     *
     * OpenAI sometimes returns {@code "amount": "500"} (a JSON string) instead of
     * {@code "amount": 500} (a JSON number).  This deserializer converts both forms
     * to a {@link BigDecimal}, stripping currency symbols if present.
     */
    public static class FlexibleBigDecimalDeserializer extends JsonDeserializer<BigDecimal> {

        @Override
        public BigDecimal deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            String raw = p.getText();
            if (raw == null || raw.isBlank()) {
                return BigDecimal.ZERO;
            }
            // Strip common currency symbols (₹, $, €, £, ¥) and whitespace
            String cleaned = raw.trim().replaceAll("[₹$€£¥,]", "");
            try {
                return new BigDecimal(cleaned);
            } catch (NumberFormatException e) {
                return BigDecimal.ZERO;
            }
        }
    }
}
