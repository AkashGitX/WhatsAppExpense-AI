package com.akash.budgetchanger.service;

import com.akash.budgetchanger.dto.ExpenseAiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {

    private final ChatModel    chatModel;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.openai.api-key:sk-placeholder}")
    private String openAiApiKey;

    private static final List<String> VALID_CATEGORIES = List.of(
            "Food", "Transport", "Shopping", "Bills",
            "Entertainment", "Health", "Education", "Others"
    );

    // ── Prompt template ───────────────────────────────────────────────────────
    // %s slots (in order): dateContext block, message
    // The date context block is fully computed in Java so the AI never has to
    // guess what "Monday" or "yesterday" means — it gets the exact YYYY-MM-DD.
    private static final String EXTRACTION_PROMPT = """
            You are an expense extraction engine.
            Extract expense details from the Message below.
            Return ONLY valid JSON — no markdown, no explanation, no extra text.

            ── DATE REFERENCE (use these to resolve any day name or relative term) ──
            %s
            ─────────────────────────────────────────────────────────────────────────

            Message: "%s"

            Required output format:
            {
              "amount": <positive number, no currency symbols>,
              "category": "<exactly one of: Food, Transport, Shopping, Bills, Entertainment, Health, Education, Others>",
              "note": "<brief description, max 60 chars>",
              "date": "<YYYY-MM-DD, resolved from the DATE REFERENCE above>"
            }

            Rules:
            1. amount   : MUST be a positive number found in the message.
            2. category : MUST be exactly one of the listed values; choose the best match.
            3. note     : Short description of what was spent on.
            4. date     : Resolve relative terms (yesterday, Monday, last week, etc.) using
                          the DATE REFERENCE above. If no date is mentioned, use today's date.
            5. If the message contains NO monetary amount at all, return EXACTLY:
               {"error":"not_an_expense"}
            6. Do NOT copy values from any previous response — each call is fully independent.
            """;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Converts a natural-language WhatsApp message into a structured ExpenseAiResponse.
     *
     * Pipeline (logged as sub-steps of STEP-6):
     *   6.1  Build stateless prompt with computed date context
     *   6.2  Call OpenAI gpt-4o-mini
     *   6.3  Strip markdown / prefix noise
     *   6.4  Parse JSON
     *   6.5  Check {"error":"not_an_expense"}
     *   6.6  Validate amount > 0
     *   6.7  Validate / normalise category
     *   6.8  Validate / default date
     *   6.9  Fill note if blank
     *   → Regex fallback if any step above fails
     */
    public ExpenseAiResponse convertMessageToExpense(String message) {
        log.info("┌────────────────────────────────────────────────────────");
        log.info("│ STEP-6.1 │ AI Extraction — NEW STATELESS REQUEST");
        log.info("│  Input   : '{}'", message);
        log.info("│  Model   : gpt-4o-mini  |  History: NONE (single-turn)");
        log.info("└────────────────────────────────────────────────────────");

        if (isAiDisabled()) {
            log.error("STEP-6.1 ✗ OPENAI_API_KEY not configured — activating regex fallback");
            return regexFallback(message, "OpenAI API key not set");
        }

        try {
            // 6.1 — Build fully self-contained prompt with computed date context
            String dateContext = buildDateContext();
            String prompt      = String.format(EXTRACTION_PROMPT, dateContext, message);

            log.info("STEP-6.1 │ Date context injected into prompt:");
            log.info("{}", dateContext);

            // 6.2 — Call OpenAI
            log.info("STEP-6.2 │ Calling OpenAI...");
            ChatResponse response = chatModel.call(new Prompt(prompt));
            String rawContent     = response.getResult().getOutput().getContent();
            log.info("STEP-6.2 ✓ AI raw response: {}", rawContent);

            return validateAndParse(rawContent, message);

        } catch (Exception e) {
            log.error("STEP-6.2 ✗ OpenAI call FAILED: {} — activating regex fallback", e.getMessage(), e);
            return regexFallback(message, "AI service error: " + e.getMessage());
        }
    }

    public String answerSpendingQuery(String question, String context) {
        if (isAiDisabled()) {
            log.error("AI is disabled — cannot answer spending query. Set OPENAI_API_KEY.");
            return "AI is not configured. Please set the OPENAI_API_KEY environment variable to enable chat.";
        }

        log.info("──── AI Spending Query ──────────────────────────────────");
        log.info("  Question: '{}'", question);

        try {
            String prompt = String.format("""
                    You are a personal finance assistant. Answer the user's question based on their expense data.
                    
                    Expense summary:
                    %s
                    
                    User question: "%s"
                    
                    Provide a concise, helpful answer. Use currency amounts where relevant.
                    """, context, question);

            ChatResponse response = chatModel.call(new Prompt(prompt));
            String answer = response.getResult().getOutput().getContent();
            log.info("  Answer length: {} chars", answer.length());
            log.info("─────────────────────────────────────────────────────");
            return answer;

        } catch (Exception e) {
            log.error("AI query FAILED for question '{}': {}", question, e.getMessage(), e);
            return "Sorry, I couldn't process your question right now. Please try again later.";
        }
    }

    // ── Validation pipeline ───────────────────────────────────────────────────

    /**
     * STEP-6.3 through STEP-6.9 — strict validation of AI JSON response.
     */
    private ExpenseAiResponse validateAndParse(String rawContent, String originalMessage) {
        // 6.3 — Strip markdown code fences and any text before first {
        String json = rawContent.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("(?s)^```(?:json)?\\s*", "").replaceAll("(?s)```\\s*$", "").trim();
        }
        int firstBrace = json.indexOf('{');
        int lastBrace  = json.lastIndexOf('}');
        if (firstBrace > 0 && lastBrace > firstBrace) {
            log.warn("STEP-6.3 ⚠ AI returned text before JSON — extracting JSON block");
            json = json.substring(firstBrace, lastBrace + 1);
        }
        log.info("STEP-6.3 │ Cleaned JSON: {}", json);

        // 6.4 — Parse JSON
        ExpenseAiResponse parsed;
        try {
            parsed = objectMapper.readValue(json, ExpenseAiResponse.class);
            log.info("STEP-6.4 ✓ JSON parse OK");
        } catch (Exception e) {
            log.error("STEP-6.4 ✗ JSON parse FAILED: '{}' — activating regex fallback", e.getMessage());
            return regexFallback(originalMessage, "AI returned invalid JSON: " + e.getMessage());
        }

        // 6.5 — Check for explicit not-an-expense signal
        if (json.contains("\"error\"")) {
            log.warn("STEP-6.5 ✗ AI returned error signal — message is not an expense");
            return ExpenseAiResponse.invalid("Message does not contain expense information.");
        }
        log.info("STEP-6.5 ✓ No error signal — message is an expense");

        // 6.6 — Validate amount
        if (parsed.getAmount() == null || parsed.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            log.error("STEP-6.6 ✗ Amount is missing or zero (got: {}) — regex fallback", parsed.getAmount());
            return regexFallback(originalMessage, "AI returned zero/missing amount");
        }
        log.info("STEP-6.6 ✓ Amount: {}", parsed.getAmount());

        // 6.7 — Validate category
        String cat = parsed.getCategory() != null ? parsed.getCategory().trim() : "";
        if (!VALID_CATEGORIES.contains(cat)) {
            log.warn("STEP-6.7 ⚠ Unknown category '{}' — defaulting to Others", cat);
            parsed.setCategory("Others");
        }
        log.info("STEP-6.7 ✓ Category: {}", parsed.getCategory());

        // 6.8 — Validate date format
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        if (parsed.getDate() == null || parsed.getDate().isBlank()) {
            log.warn("STEP-6.8 ⚠ Date missing — defaulting to today: {}", today);
            parsed.setDate(today);
        } else {
            try {
                LocalDate.parse(parsed.getDate());
                log.info("STEP-6.8 ✓ Date: {}", parsed.getDate());
            } catch (Exception e) {
                log.warn("STEP-6.8 ⚠ Invalid date '{}' — defaulting to today: {}", parsed.getDate(), today);
                parsed.setDate(today);
            }
        }

        // 6.9 — Note fallback
        if (parsed.getNote() == null || parsed.getNote().isBlank()) {
            log.warn("STEP-6.9 ⚠ Note missing — using original message");
            parsed.setNote(originalMessage.length() > 60
                    ? originalMessage.substring(0, 60) : originalMessage);
        }
        log.info("STEP-6.9 ✓ Note: '{}'", parsed.getNote());

        parsed.setValid(true);
        log.info("STEP-6 ✓ ALL VALIDATION PASSED — expense ready to save");
        return parsed;
    }

    // ── Regex fallback ────────────────────────────────────────────────────────

    /**
     * When AI fails or returns garbage, extract the first number from the message.
     * Guarantees a save whenever any numeric amount is present.
     *
     * amount   = first integer or decimal found
     * category = "Others"
     * note     = full original message (truncated to 60 chars)
     * date     = today
     */
    private ExpenseAiResponse regexFallback(String message, String reason) {
        log.warn("STEP-6 ── REGEX FALLBACK activated");
        log.warn("  Reason        : {}", reason);
        log.warn("  Input message : '{}'", message);

        Pattern amountPattern = Pattern.compile("\\b(\\d{1,7}(?:\\.\\d{1,2})?)\\b");
        Matcher matcher = amountPattern.matcher(message);

        if (matcher.find()) {
            String matched = matcher.group(1);
            try {
                BigDecimal amount = new BigDecimal(matched);
                if (amount.compareTo(BigDecimal.ZERO) > 0) {
                    String note  = message.length() > 60 ? message.substring(0, 60) : message;
                    String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

                    log.info("STEP-6 ✓ Regex extracted: amount={}, category=Others, date={}", amount, today);
                    return ExpenseAiResponse.builder()
                            .amount(amount)
                            .category("Others")
                            .note(note)
                            .date(today)
                            .valid(true)
                            .build();
                }
            } catch (NumberFormatException ignored) {}
        }

        log.error("STEP-6 ✗ Regex fallback also failed — no numeric amount in: '{}'", message);
        return ExpenseAiResponse.invalid(
                "Could not find an expense amount in your message. " +
                "Try: \"Spent 500 on food\" or \"Netflix 649\"");
    }

    // ── Date context builder ──────────────────────────────────────────────────

    /**
     * Builds a human-readable date reference block for the AI prompt.
     *
     * Instead of asking the AI to compute what "Monday" means, we compute it in
     * Java and inject the exact YYYY-MM-DD dates for every day of the week.
     * This makes relative date resolution deterministic regardless of AI knowledge.
     *
     * Example output (when today is Sunday 2026-04-26):
     *
     *   Today (Sunday)    : 2026-04-26
     *   Yesterday         : 2026-04-25
     *   Last Saturday     : 2026-04-25
     *   Last Friday       : 2026-04-24
     *   Last Thursday     : 2026-04-23
     *   Last Wednesday    : 2026-04-22
     *   Last Tuesday      : 2026-04-21
     *   Last Monday       : 2026-04-20
     *   Last Sunday       : 2026-04-19
     */
    private String buildDateContext() {
        LocalDate today     = LocalDate.now();
        DayOfWeek todayDow  = today.getDayOfWeek();
        String    todayStr  = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String    todayName = todayDow.getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Today (%s) : %s%n", todayName, todayStr));
        sb.append(String.format("Yesterday  : %s%n", today.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)));

        for (DayOfWeek dow : DayOfWeek.values()) {
            int diff = todayDow.getValue() - dow.getValue();
            if (diff <= 0) diff += 7;
            LocalDate dayDate = today.minusDays(diff);
            String    dayName = dow.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            sb.append(String.format("Last %-12s : %s%n", dayName, dayDate.format(DateTimeFormatter.ISO_LOCAL_DATE)));
        }

        return sb.toString().trim();
    }

    private boolean isAiDisabled() {
        return openAiApiKey == null
                || openAiApiKey.isBlank()
                || openAiApiKey.startsWith("sk-placeholder");
    }
}
