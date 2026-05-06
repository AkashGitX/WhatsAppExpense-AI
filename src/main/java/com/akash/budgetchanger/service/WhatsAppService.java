package com.akash.budgetchanger.service;

import com.akash.budgetchanger.config.TwilioConfig;
import com.akash.budgetchanger.dto.ExpenseAiResponse;
import com.akash.budgetchanger.entity.Expense;
import com.akash.budgetchanger.entity.User;
import com.akash.budgetchanger.repository.UserRepository;
import com.akash.budgetchanger.util.PhoneNormalizer;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppService {

    private final UserRepository userRepository;
    private final TwilioConfig   twilioConfig;
    private final AIService      aiService;
    private final ExpenseService expenseService;

    /**
     * Full pipeline STEP-5 through STEP-8 (STEP-9 is logged by the controller).
     *
     * STEP-5 │ Phone normalization + DB user lookup
     * STEP-6 │ AI expense extraction (sub-steps 6.1–6.9 logged inside AIService)
     * STEP-7 │ Validation gate  — is AI result valid?
     * STEP-8 │ Database save
     *
     * @param from       raw Twilio "From" field, e.g. "whatsapp:+918101834270"
     * @param body       text of the incoming WhatsApp message
     * @param messageSid Twilio MessageSid (for log correlation)
     * @return reply text — wrapped in TwiML and logged as STEP-9 by the controller
     */
    public String handleIncomingMessage(String from, String body, String messageSid) {

        // ╔══════════════════════════════════════════════════════════════════════╗
        // ║ STEP-5 │ PHONE NORMALIZATION + USER MATCH                            ║
        // ╚══════════════════════════════════════════════════════════════════════╝
        log.info("STEP-5 │ Phone normalization");
        log.info("STEP-5.1 │ raw Twilio 'From' : '{}'", from);

        String phoneNumber = normalizePhoneNumber(from);

        log.info("STEP-5.2 │ normalized        : '{}'", phoneNumber);
        log.info("STEP-5.3 │ DB lookup — SELECT * FROM users WHERE phone_number = '{}'", phoneNumber);

        Optional<User> userOpt = userRepository.findByPhoneNumber(phoneNumber);

        if (userOpt.isEmpty()) {
            log.error("STEP-5.4 ✗ User NOT FOUND — phone='{}' has no matching row in users table", phoneNumber);
            log.error("  ┌─ DIAGNOSIS ─────────────────────────────────────────────────");
            log.error("  │  Raw from Twilio : '{}'", from);
            log.error("  │  After normalize : '{}'", phoneNumber);
            log.error("  │  Check DB        : SELECT id, name, phone_number FROM users;");
            log.error("  │  Likely cause    : user registered with a different number");
            log.error("  │                   OR registered without linking WhatsApp.");
            log.error("  └─────────────────────────────────────────────────────────────");
            return "❌ You are not registered on BudgetBot AI.\n\n" +
                   "Please visit the website to create an account and link your WhatsApp number.\n\n" +
                   "⚠️ Make sure to register with the SAME phone number you use on WhatsApp.";
        }

        User user = userOpt.get();
        log.info("STEP-5.4 ✓ User FOUND — id={}, name='{}', email='{}'",
                user.getId(), user.getName(), user.getEmail());

        // ╔══════════════════════════════════════════════════════════════════════╗
        // ║ STEP-6 │ AI PARSING (sub-steps 6.1–6.9 logged inside AIService)      ║
        // ╚══════════════════════════════════════════════════════════════════════╝
        log.info("STEP-6 │ Invoking AI service — sid='{}', message='{}'", messageSid, body);
        ExpenseAiResponse aiResult;
        try {
            aiResult = aiService.convertMessageToExpense(body);
        } catch (Exception e) {
            log.error("STEP-6 ✗ AI threw unexpected exception for userId={}: {}", user.getId(), e.getMessage(), e);
            return buildErrorReply(user.getName(),
                    "Our AI had an unexpected error. Please try again in a moment.");
        }

        // ╔══════════════════════════════════════════════════════════════════════╗
        // ║ STEP-7 │ VALIDATION GATE                                              ║
        // ╚══════════════════════════════════════════════════════════════════════╝
        if (!aiResult.isValid()) {
            log.warn("STEP-7 ✗ VALIDATION FAILED — AI result invalid");
            log.warn("  Reason  : '{}'", aiResult.getErrorReason());
            log.warn("  Message : '{}'", body);
            log.warn("  → No DB save will occur for sid='{}'", messageSid);
            return buildNonExpenseReply(user.getName(), body, aiResult.getErrorReason());
        }

        log.info("STEP-7 ✓ VALIDATION PASSED — amount={}, category='{}', date='{}', note='{}'",
                aiResult.getAmount(), aiResult.getCategory(), aiResult.getDate(), aiResult.getNote());

        // ╔══════════════════════════════════════════════════════════════════════╗
        // ║ STEP-8 │ DATABASE SAVE                                                ║
        // ╚══════════════════════════════════════════════════════════════════════╝
        log.info("STEP-8 │ Attempting DB save — userId={}, amount={}, category='{}', date='{}'",
                user.getId(), aiResult.getAmount(), aiResult.getCategory(), aiResult.getDate());

        Expense saved;
        try {
            saved = expenseService.saveFromWhatsApp(user, aiResult);
            log.info("STEP-8 ✓ Saved successfully — id={}, userId={}, amount={}, category='{}', date='{}'",
                    saved.getId(), user.getId(), saved.getAmount(), saved.getCategory(), saved.getDate());
        } catch (Exception e) {
            log.error("STEP-8 ✗ DB SAVE FAILED for userId={}: {}", user.getId(), e.getMessage(), e);
            return buildErrorReply(user.getName(),
                    "Expense was detected but could not be saved. Please try again.");
        }

        return buildSuccessReply(user.getName(), saved);
    }

    /**
     * Backward-compatible overload (called from tests or other code without messageSid).
     */
    public String handleIncomingMessage(String from, String body) {
        return handleIncomingMessage(from, body, "NO-SID");
    }

    // ── Reply builders ────────────────────────────────────────────────────────

    private String buildSuccessReply(String name, Expense expense) {
        return String.format(
                "✅ Expense saved, %s!\n\n" +
                "💰 Amount:   %s\n" +
                "📂 Category: %s\n" +
                "📝 Note:     %s\n" +
                "📅 Date:     %s\n\n" +
                "Track all your expenses on the BudgetBot dashboard.",
                name,
                expense.getAmount().toPlainString(),
                expense.getCategory(),
                expense.getNote() != null ? expense.getNote() : "-",
                expense.getDate()
        );
    }

    private String buildNonExpenseReply(String name, String originalMessage, String reason) {
        return String.format(
                "Hi %s! I couldn't detect an expense in your message.\n\n" +
                "Try sending something like:\n" +
                "• \"Spent 200 on groceries\"\n" +
                "• \"Paid 150 for transport today\"\n" +
                "• \"1500 on shopping\"\n\n" +
                "Reason: %s",
                name, reason
        );
    }

    private String buildErrorReply(String name, String reason) {
        return String.format(
                "Hi %s! Something went wrong while processing your message.\n%s",
                name, reason
        );
    }

    // ── Twilio outbound sender ────────────────────────────────────────────────

    public void sendMessage(String toPhone, String messageText) {
        if (twilioConfig.getAccountSid().startsWith("placeholder")) {
            log.warn("Twilio not configured — skipping outbound to {}: {}", toPhone, messageText);
            return;
        }
        try {
            String to   = toPhone.startsWith("whatsapp:") ? toPhone : "whatsapp:" + toPhone;
            String from = "whatsapp:" + twilioConfig.getWhatsappNumber();

            Message msg = Message.creator(
                    new PhoneNumber(to),
                    new PhoneNumber(from),
                    messageText
            ).create();

            log.info("WhatsApp message sent — SID: {}, to: {}", msg.getSid(), toPhone);
        } catch (Exception e) {
            log.error("Failed to send WhatsApp message to {}: {}", toPhone, e.getMessage(), e);
        }
    }

    // ── Phone normalizer ──────────────────────────────────────────────────────

    /**
     * Delegates to PhoneNormalizer.normalize() — all normalization logic lives there
     * so AuthService and UserService can reuse the same rules without circular deps.
     *
     * Handles every format Twilio may send:
     *   whatsapp:+918101834270  →  +918101834270
     *   whatsapp:918101834270   →  +918101834270
     *   8101834270              →  +918101834270
     *   +918101834270           →  +918101834270 (no-op)
     */
    public String normalizePhoneNumber(String from) {
        String result = PhoneNormalizer.normalize(from);
        return result != null ? result : "";
    }
}
