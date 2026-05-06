package com.akash.budgetchanger.service;

import com.akash.budgetchanger.dto.ChatMessageResponse;
import com.akash.budgetchanger.entity.ChatHistory;
import com.akash.budgetchanger.entity.Expense;
import com.akash.budgetchanger.entity.User;
import com.akash.budgetchanger.repository.ChatHistoryRepository;
import com.akash.budgetchanger.repository.ExpenseRepository;
import com.akash.budgetchanger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
    private static final int MAX_CONTEXT_TRANSACTIONS = 100;
    private static final int MAX_CONTEXT_NOTE_LENGTH = 80;

    private final ChatHistoryRepository chatHistoryRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final AIService aiService;

    /**
     * Process a user question, build expense context, call AI, persist result.
     *
     * @param userId   the authenticated user
     * @param question natural-language spending question
     * @return the question + AI answer pair
     */
    @Transactional
    public ChatMessageResponse askQuestion(Long userId, String question) {
        log.info("AI request started — userId={}, question='{}'", userId, question);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Collect last 60 days of expenses as context for the AI
        LocalDate since = LocalDate.now().minusDays(60);
        List<Expense> expenses = expenseRepository
                .findByUserIdAndDateGreaterThanEqualOrderByDateAsc(userId, since);

        String context = buildExpenseContext(user, expenses);
        log.info("AI chat context prepared — userId={}, expenseCount={}", userId, expenses.size());
        log.debug("AI context:\n{}", context);

        String answer;
        try {
            log.info("AI provider request dispatch — userId={}", userId);
            answer = aiService.answerSpendingQuery(question, context);
            log.info("AI response received — userId={}, answerLength={}", userId, answer != null ? answer.length() : 0);
        } catch (Exception ex) {
            log.error("AI request failed in ChatService — userId={}, exceptionType={}, message={}",
                    userId, ex.getClass().getName(), ex.getMessage(), ex);
            throw ex;
        }

        ChatHistory record = ChatHistory.builder()
                .user(user)
                .message(question)
                .response(answer)
                .build();
        ChatHistory saved = chatHistoryRepository.save(record);

        return ChatMessageResponse.builder()
                .id(saved.getId())
                .question(question)
                .answer(answer)
                .timestamp(saved.getTimestamp())
                .build();
    }

    /**
     * Return the 20 most recent chat exchanges for a user, newest-first.
     */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getHistory(Long userId) {
        return chatHistoryRepository.findTop20ByUserIdOrderByTimestampDesc(userId)
                .stream()
                .map(h -> ChatMessageResponse.builder()
                        .id(h.getId())
                        .question(h.getMessage())
                        .answer(h.getResponse())
                        .timestamp(h.getTimestamp())
                        .build())
                .collect(Collectors.toList());
    }

    // ── Context builder ───────────────────────────────────────────────────────

    /**
     * Convert recent expenses into a structured text block that the AI can reason over.
     * Includes summary KPIs and a line-by-line expense list.
     */
    private String buildExpenseContext(User user, List<Expense> expenses) {
        LocalDate today = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM dd, yyyy");

        BigDecimal total = expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Category totals
        Map<String, BigDecimal> catTotals = expenses.stream()
                .collect(Collectors.groupingBy(
                        Expense::getCategory,
                        Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)
                ));

        // This month's total
        BigDecimal monthlyTotal = expenses.stream()
                .filter(e -> e.getDate().getMonth() == today.getMonth()
                        && e.getDate().getYear() == today.getYear())
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        StringBuilder sb = new StringBuilder();
        sb.append("User Profile:\n");
        sb.append("  Name: ").append(user.getName()).append("\n");
        sb.append("  Today: ").append(today.format(fmt)).append("\n\n");

        sb.append("Expense Summary (last 60 days):\n");
        sb.append("  Total spent: ").append(total).append("\n");
        sb.append("  This month (").append(today.getMonth()).append("): ").append(monthlyTotal).append("\n\n");

        sb.append("Category Breakdown:\n");
        catTotals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(e -> sb.append("  - ").append(e.getKey())
                        .append(": ").append(e.getValue()).append("\n"));

        sb.append("\nDetailed Transactions (latest ")
                .append(Math.min(expenses.size(), MAX_CONTEXT_TRANSACTIONS))
                .append(" of ")
                .append(expenses.size())
                .append("):\n");
        expenses.stream()
                .sorted((a, b) -> b.getDate().compareTo(a.getDate()))
                .limit(MAX_CONTEXT_TRANSACTIONS)
                .forEach(e -> sb.append("  [").append(e.getDate().format(fmt))
                        .append("] ").append(e.getCategory())
                        .append(" — ").append(e.getAmount())
                        .append(e.getNote() != null ? " (\"" + truncateForContext(e.getNote()) + "\")" : "")
                        .append(" [").append(e.getSource()).append("]\n"));

        return sb.toString();
    }

    private String truncateForContext(String note) {
        if (note == null) {
            return "";
        }
        String normalized = note.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_CONTEXT_NOTE_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_CONTEXT_NOTE_LENGTH) + "...";
    }
}

