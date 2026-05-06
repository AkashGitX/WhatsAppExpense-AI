package com.akash.budgetchanger.controller;

import com.akash.budgetchanger.config.TwilioConfig;
import com.akash.budgetchanger.service.WhatsAppService;
import com.twilio.security.RequestValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Handles incoming WhatsApp messages forwarded by Twilio.
 *
 * Pipeline (steps logged here):
 *   STEP-1  Webhook receipt  — every field logged
 *   STEP-2  Duplicate SID    — LRU cache, 200 entries
 *   STEP-3  Timer started    — captures t0, warns if total > 5 s
 *   STEP-4  Signature check  — HMAC validation or skip flag
 *   STEP-5..8 delegated to WhatsAppService
 *   STEP-9  Twilio response  — TwiML + HTTP 200 + TOTAL_PROCESSING_TIME
 */
@Slf4j
@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final WhatsAppService whatsAppService;
    private final TwilioConfig    twilioConfig;

    @Value("${twilio.skip-validation:false}")
    private boolean skipValidation;

    @Value("${app.public-url:}")
    private String publicUrl;

    // ── Duplicate-SID protection ───────────────────────────────────────────────
    private static final int MAX_SEEN_SIDS = 200;
    private final Map<String, Boolean> seenMessageSids = Collections.synchronizedMap(
            new LinkedHashMap<>(MAX_SEEN_SIDS, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_SEEN_SIDS;
                }
            }
    );

    /**
     * POST /webhook/whatsapp
     * Twilio sends incoming WhatsApp messages as application/x-www-form-urlencoded.
     */
    @PostMapping(
            value = "/whatsapp",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<String> receiveWhatsAppMessage(
            HttpServletRequest servletRequest,
            @RequestHeader(value = "X-Twilio-Signature", required = false, defaultValue = "") String twilioSignature,
            @RequestParam(value = "From",       required = false, defaultValue = "") String from,
            @RequestParam(value = "To",         required = false, defaultValue = "") String to,
            @RequestParam(value = "Body",       required = false, defaultValue = "") String body,
            @RequestParam(value = "MessageSid", required = false, defaultValue = "") String messageSid,
            @RequestParam Map<String, String> allParams
    ) {
        // ╔══════════════════════════════════════════════════════════════════════╗
        // ║ STEP-1 │ WEBHOOK RECEIPT — full request dump                         ║
        // ╚══════════════════════════════════════════════════════════════════════╝
        log.info("╔══════════════════════════════════════════════════════╗");
        log.info("║  STEP-1 │ Twilio Webhook — Incoming Message          ║");
        log.info("╠══════════════════════════════════════════════════════╣");
        log.info("║  Timestamp  : {}", Instant.now());
        log.info("║  MessageSid : {}", messageSid);
        log.info("║  From       : {}", from);
        log.info("║  To         : {}", to);
        log.info("║  Body       : '{}'", body);
        log.info("║  Remote IP  : {}", servletRequest.getRemoteAddr());
        log.info("║  Request URL: {}", servletRequest.getRequestURL());
        log.info("╠════════════════════ HEADERS ══════════════════════════╣");
        Collections.list(servletRequest.getHeaderNames()).forEach(h ->
            log.info("║  {} : {}", h, servletRequest.getHeader(h)));
        log.info("╠════════════════════ PARAMS ═══════════════════════════╣");
        allParams.entrySet().stream()
            .filter(e -> !e.getKey().equalsIgnoreCase("Body"))
            .forEach(e -> log.info("║  {} = {}", e.getKey(), e.getValue()));
        log.info("╚══════════════════════════════════════════════════════╝");

        // ╔══════════════════════════════════════════════════════════════════════╗
        // ║ STEP-2 │ DUPLICATE / RETRY DETECTION                                 ║
        // ╚══════════════════════════════════════════════════════════════════════╝
        if (!messageSid.isBlank()) {
            Boolean alreadySeen = seenMessageSids.putIfAbsent(messageSid, Boolean.TRUE);
            if (alreadySeen != null) {
                log.warn("STEP-2 ⚠ DUPLICATE MessageSid='{}' — Twilio retry detected. SKIPPING to prevent double-save.", messageSid);
                return ResponseEntity.ok(buildTwiml(""));
            }
            log.info("STEP-2 ✓ NEW MessageSid='{}' — no duplicate in cache", messageSid);
        } else {
            log.warn("STEP-2 ⚠ No MessageSid present — deduplication skipped (direct test?)");
        }

        // ╔══════════════════════════════════════════════════════════════════════╗
        // ║ STEP-3 │ RESPONSE-TIME TRACKING — timer starts here                  ║
        // ║         Twilio requires HTTP 200 within 15 s or it will retry.        ║
        // ║         We warn if total processing exceeds 5 s.                      ║
        // ╚══════════════════════════════════════════════════════════════════════╝
        final long t0 = System.currentTimeMillis();
        log.info("STEP-3 │ Response timer started — t0={} ms", t0);

        // ╔══════════════════════════════════════════════════════════════════════╗
        // ║ STEP-4 │ TWILIO SIGNATURE VALIDATION                                 ║
        // ╚══════════════════════════════════════════════════════════════════════╝
        if (skipValidation) {
            log.warn("STEP-4 ⚠ Signature validation SKIPPED (twilio.skip-validation=true)");
        } else if (isTwilioConfigured()) {
            String[] validationUrl = new String[1];
            boolean valid = validateTwilioSignature(servletRequest, twilioSignature, allParams, validationUrl);
            if (!valid) {
                long elapsed = System.currentTimeMillis() - t0;
                log.error("STEP-4 ✗ Signature INVALID — returning HTTP 403  [{}ms]", elapsed);
                log.error("  Received X-Twilio-Signature : '{}'", twilioSignature);
                log.error("  Raw request URL             : {}", servletRequest.getRequestURL());
                log.error("  URL used for validation     : {}", validationUrl[0]);
                log.error("  ─────────────────────────────────────────────────────────");
                log.error("  FIX: Twilio sandbox URL must be EXACTLY:");
                log.error("       {}/webhook/whatsapp", publicUrl);
                log.error("  ─────────────────────────────────────────────────────────");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("");
            }
            log.info("STEP-4 ✓ Signature VALID — URL used: {}", validationUrl[0]);
        } else {
            log.info("STEP-4 ⚠ Twilio credentials not configured — signature check skipped");
        }

        // ── Reject blank body (image / audio / sticker) ───────────────────────
        if (body.isBlank()) {
            long elapsed = System.currentTimeMillis() - t0;
            log.warn("STEP-4b ⚠ Empty body (image/audio/sticker?) — sending prompt  [{}ms]", elapsed);
            return respondAndTime(buildTwiml(
                    "Please send a text message describing your expense. " +
                    "Example: \"Spent 500 on groceries\""), t0);
        }

        // ╔══════════════════════════════════════════════════════════════════════╗
        // ║ STEP-5 → STEP-8 delegated to WhatsAppService                         ║
        // ╚══════════════════════════════════════════════════════════════════════╝
        String replyText;
        try {
            replyText = whatsAppService.handleIncomingMessage(from, body, messageSid);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - t0;
            log.error("STEP-5+ ✗ Unhandled error in WhatsAppService: {}  [{}ms]", e.getMessage(), elapsed, e);
            replyText = "Sorry, something went wrong processing your message. Please try again.";
        }

        // ╔══════════════════════════════════════════════════════════════════════╗
        // ║ STEP-9 │ TWILIO RESPONSE — TwiML + HTTP status + processing time      ║
        // ╚══════════════════════════════════════════════════════════════════════╝
        return respondAndTime(buildTwiml(replyText), t0);
    }

    /**
     * GET /webhook/whatsapp — health check / connectivity test.
     */
    @GetMapping("/whatsapp")
    public ResponseEntity<String> healthCheck() {
        log.info("Webhook health check hit");
        return ResponseEntity.ok(
                "BudgetBot AI WhatsApp Webhook is active.\n" +
                "Public URL: " + publicUrl + "/webhook/whatsapp\n" +
                "Signature validation: " + (skipValidation ? "DISABLED (debug)" : "ENABLED")
        );
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Logs STEP-9 with full TwiML, HTTP 200, and total processing time.
     * Warns loudly if processing took more than 5 000 ms (Twilio retry risk).
     */
    private ResponseEntity<String> respondAndTime(String twiml, long t0) {
        long elapsed = System.currentTimeMillis() - t0;
        log.info("STEP-9 ✓ HTTP 200 — TwiML sent");
        log.info("  TwiML       : {}", twiml);
        if (elapsed > 5_000) {
            log.warn("STEP-9 ⚠⚠ TOTAL_PROCESSING_TIME = {} ms  ← EXCEEDS 5 000 ms — Twilio RETRY RISK!", elapsed);
        } else {
            log.info("STEP-9 │ TOTAL_PROCESSING_TIME = {} ms  ✓ within Twilio threshold", elapsed);
        }
        return ResponseEntity.ok(twiml);
    }

    private boolean validateTwilioSignature(HttpServletRequest request,
                                            String signature,
                                            Map<String, String> params,
                                            String[] outValidationUrl) {
        try {
            RequestValidator validator = new RequestValidator(twilioConfig.getAuthToken());

            String url       = request.getRequestURL().toString();
            String urlSource = "X-Forwarded headers";

            if (url.contains("localhost") && !publicUrl.isBlank()) {
                url       = publicUrl + "/webhook/whatsapp";
                urlSource = "app.public-url fallback";
            }

            if (outValidationUrl != null) outValidationUrl[0] = url;

            log.info("── STEP-4 Signature Validation ───────────────────────────────");
            log.info("  URL source   : {}", urlSource);
            log.info("  URL for HMAC : {}", url);
            log.info("  X-Twilio-Sig : '{}'", signature);
            log.info("  Param count  : {}", params.size());

            Map<String, String> sortedParams = new TreeMap<>(params);
            boolean valid = validator.validate(url, sortedParams, signature);
            log.info("  Result       : {}", valid ? "✓ PASS" : "✗ FAIL");
            log.info("─────────────────────────────────────────────────────────────");
            return valid;

        } catch (Exception e) {
            if (outValidationUrl != null && outValidationUrl[0] == null)
                outValidationUrl[0] = "(error before URL was set)";
            log.error("Signature validation threw exception: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean isTwilioConfigured() {
        return !twilioConfig.getAccountSid().startsWith("placeholder") &&
               !twilioConfig.getAuthToken().startsWith("placeholder");
    }

    private String buildTwiml(String message) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
               "<Response>" +
               "<Message>" + escapeXml(message) + "</Message>" +
               "</Response>";
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&",  "&amp;")
                   .replace("<",  "&lt;")
                   .replace(">",  "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'",  "&apos;");
    }
}
