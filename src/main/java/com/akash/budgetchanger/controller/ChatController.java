package com.akash.budgetchanger.controller;

import com.akash.budgetchanger.dto.ApiResponse;
import com.akash.budgetchanger.dto.ChatMessageResponse;
import com.akash.budgetchanger.dto.ChatRequest;
import com.akash.budgetchanger.security.SecurityUtils;
import com.akash.budgetchanger.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for AI-powered expense Q&amp;A.
 *
 * Authentication: JWT required (userId extracted from token — no query param).
 */
@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * POST /chat/ask
     * Ask an AI question about your spending history.
     * Body: { "question": "How much did I spend on food this week?" }
     */
    @PostMapping("/ask")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> ask(
            @Valid @RequestBody ChatRequest request) {

        Long userId = SecurityUtils.getCurrentUserId();
        log.info("POST /chat/ask — userId={}", userId);
        ChatMessageResponse response = chatService.askQuestion(userId, request.getQuestion());
        return ResponseEntity.ok(ApiResponse.success("AI response generated.", response));
    }

    /**
     * GET /chat/history
     * Retrieve the 20 most recent chat exchanges, newest-first.
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> history() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("GET /chat/history — userId={}", userId);
        List<ChatMessageResponse> history = chatService.getHistory(userId);
        return ResponseEntity.ok(ApiResponse.success("Chat history retrieved.", history));
    }
}
