package com.akash.budgetchanger.config;

import com.twilio.Twilio;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Getter
@Configuration
public class TwilioConfig {

    @Value("${twilio.account.sid}")
    private String accountSid;

    @Value("${twilio.auth.token}")
    private String authToken;

    @Value("${twilio.whatsapp.number}")
    private String whatsappNumber;

    @PostConstruct
    public void initTwilio() {
        if (accountSid.startsWith("placeholder") || authToken.startsWith("placeholder")) {
            log.warn("Twilio credentials are placeholders — WhatsApp sending is disabled. " +
                     "Set TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN env vars to enable.");
            return;
        }
        Twilio.init(accountSid, authToken);
        log.info("Twilio initialized. WhatsApp number: {}", whatsappNumber);
    }
}
