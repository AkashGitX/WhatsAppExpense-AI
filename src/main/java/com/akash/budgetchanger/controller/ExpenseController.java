package com.akash.budgetchanger.controller;

import com.akash.budgetchanger.dto.ApiResponse;
import com.akash.budgetchanger.dto.ExpenseRequest;
import com.akash.budgetchanger.dto.ExpenseResponse;
import com.akash.budgetchanger.dto.SummaryResponse;
import com.akash.budgetchanger.security.SecurityUtils;
import com.akash.budgetchanger.service.AnalyticsService;
import com.akash.budgetchanger.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for expense management and analytics.
 *
 * Authentication: All endpoints require a valid JWT in the Authorization header.
 * The userId is extracted from the JWT by {@link SecurityUtils}
 * — no userId query parameter is accepted or needed.
 */
@Slf4j
@RestController
@RequestMapping("/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService   expenseService;
    private final AnalyticsService analyticsService;

    // ── POST /expenses ────────────────────────────────────────────────────────

    /**
     * Manually create a new expense entry (web dashboard form).
     * userId is extracted from the JWT — no query param needed.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ExpenseResponse>> createExpense(
            @Valid @RequestBody ExpenseRequest request) {

        Long userId = SecurityUtils.getCurrentUserId();
        log.info("POST /expenses — userId={}, amount={}, category={}",
                userId, request.getAmount(), request.getCategory());

        try {
            ExpenseResponse saved = expenseService.saveManual(userId, request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Expense added successfully.", saved));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.fail(e.getMessage()));
        }
    }

    // ── GET /expenses ─────────────────────────────────────────────────────────

    /**
     * List expenses for the authenticated user, most recent first.
     *
     * Optional filters:
     *   ?category=Food          — filter by category
     *   ?page=0&size=50         — pagination (default: page 0, size 100)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ExpenseResponse>>> getExpenses(
            @RequestParam(required = false)        String category,
            @RequestParam(defaultValue = "0")      int    page,
            @RequestParam(defaultValue = "100")    int    size) {

        Long userId = SecurityUtils.getCurrentUserId();
        log.info("GET /expenses — userId={}, category={}, page={}, size={}", userId, category, page, size);

        List<ExpenseResponse> expenses = (category != null && !category.isBlank())
                ? expenseService.getExpensesByCategory(userId, category)
                : expenseService.getExpensesPaged(userId, page, size);

        return ResponseEntity.ok(ApiResponse.success("Expenses retrieved successfully.", expenses));
    }

    // ── GET /expenses/summary ─────────────────────────────────────────────────

    /**
     * Full analytics summary including Chart.js–ready data structures,
     * KPI values, over-budget flag, and category breakdown.
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<SummaryResponse>> getSummary() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("GET /expenses/summary — userId={}", userId);

        try {
            SummaryResponse summary = analyticsService.getSummary(userId);
            return ResponseEntity.ok(ApiResponse.success("Analytics summary retrieved.", summary));
        } catch (Exception e) {
            log.error("Analytics failed for userId={}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.fail("Could not calculate analytics. Please try again."));
        }
    }
}
