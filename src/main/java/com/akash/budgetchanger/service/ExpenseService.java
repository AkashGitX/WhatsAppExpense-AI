package com.akash.budgetchanger.service;

import com.akash.budgetchanger.dto.ExpenseAiResponse;
import com.akash.budgetchanger.dto.ExpenseRequest;
import com.akash.budgetchanger.dto.ExpenseResponse;
import com.akash.budgetchanger.entity.Expense;
import com.akash.budgetchanger.entity.ExpenseSource;
import com.akash.budgetchanger.entity.User;
import com.akash.budgetchanger.repository.ExpenseRepository;
import com.akash.budgetchanger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository    userRepository;

    // ── Write operations ──────────────────────────────────────────────────────

    /** Save an expense from an AI-parsed WhatsApp message. */
    @Transactional
    public Expense saveFromWhatsApp(User user, ExpenseAiResponse aiResult) {
        LocalDate date = parseDate(aiResult.getDate());

        Expense expense = Expense.builder()
                .user(user)
                .amount(aiResult.getAmount())
                .category(normalizeCategory(aiResult.getCategory()))
                .note(aiResult.getNote())
                .date(date)
                .source(ExpenseSource.WHATSAPP)
                .build();

        Expense saved = expenseRepository.save(expense);
        log.info("Saved WHATSAPP expense: id={}, userId={}, amount={}, category={}",
                saved.getId(), user.getId(), saved.getAmount(), saved.getCategory());
        return saved;
    }

    /** Save a manually entered expense from the web dashboard. */
    @Transactional
    public ExpenseResponse saveManual(Long userId, ExpenseRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        LocalDate date = parseDate(request.getDate());

        Expense expense = Expense.builder()
                .user(user)
                .amount(request.getAmount())
                .category(normalizeCategory(request.getCategory()))
                .note(request.getNote())
                .date(date)
                .source(ExpenseSource.WEB)
                .build();

        Expense saved = expenseRepository.save(expense);
        log.info("Saved WEB expense: id={}, userId={}, amount={}, category={}",
                saved.getId(), userId, saved.getAmount(), saved.getCategory());
        return ExpenseResponse.from(saved);
    }

    // ── Read operations ───────────────────────────────────────────────────────

    /**
     * Retrieve a page of expenses for a user, most recent first.
     *
     * @param userId user's ID
     * @param page   0-based page number (default 0)
     * @param size   items per page (default 100 — loads all for most users)
     */
    @Transactional(readOnly = true)
    public List<ExpenseResponse> getExpensesPaged(Long userId, int page, int size) {
        // Guard against unreasonable page sizes
        int safeSize = Math.min(size, 500);
        PageRequest pageable = PageRequest.of(page, safeSize, Sort.by("date").descending());
        Page<Expense> expensePage = expenseRepository.findByUserIdOrderByDateDesc(userId, pageable);
        return expensePage.getContent().stream()
                .map(ExpenseResponse::from)
                .collect(Collectors.toList());
    }

    /** Retrieve all expenses for a user, most recent first (no pagination). */
    @Transactional(readOnly = true)
    public List<ExpenseResponse> getExpenses(Long userId) {
        return expenseRepository.findByUserIdOrderByDateDesc(userId)
                .stream()
                .map(ExpenseResponse::from)
                .collect(Collectors.toList());
    }

    /** Retrieve expenses filtered by category (first-letter-capitalised match). */
    @Transactional(readOnly = true)
    public List<ExpenseResponse> getExpensesByCategory(Long userId, String category) {
        return expenseRepository.findByUserIdAndCategory(userId, normalizeCategory(category))
                .stream()
                .map(ExpenseResponse::from)
                .collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return LocalDate.now();
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            log.warn("Could not parse date '{}', defaulting to today", dateStr);
            return LocalDate.now();
        }
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) return "Others";
        String trimmed = category.trim();
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1).toLowerCase();
    }
}
