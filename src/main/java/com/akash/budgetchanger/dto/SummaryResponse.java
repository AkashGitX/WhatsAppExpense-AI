package com.akash.budgetchanger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Unified analytics response shaped for Chart.js consumption.
 *
 * Front-end usage:
 *   - categoryChart:     feed directly into a Chart.js Doughnut/Pie chart
 *   - monthlyTrendChart: feed directly into a Chart.js Line/Bar chart
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SummaryResponse {

    // ── Scalar KPIs ───────────────────────────────────────────────────────────

    /** Total spending in the current calendar month. */
    private BigDecimal monthlySpending;

    /** Total spending in the current Mon–Sun week. */
    private BigDecimal weeklySpending;

    /** Total spending today. */
    private BigDecimal dailySpending;

    /**
     * Remaining budget = monthly budget limit – monthly spending.
     * Clamped to 0 — never goes negative.
     * Check {@link #overBudget} to detect over-budget state.
     */
    private BigDecimal remainingBudget;

    /** Configured monthly budget limit (from application.properties / user setting). */
    private BigDecimal monthlyBudgetLimit;

    /**
     * True when the user has NOT yet set a personal monthly budget.
     * When true, remainingBudget and overBudget are meaningless.
     */
    private boolean budgetNotSet;

    /**
     * True when the user has exceeded their monthly budget.
     * Computed as: monthlySpending > monthlyBudgetLimit
     */
    private boolean overBudget;

    /**
     * Amount spent over budget (positive when overBudget = true, 0 otherwise).
     * Useful for "You are ₹X over budget!" messages on the dashboard.
     */
    private BigDecimal overBudgetAmount;

    /** Total number of expenses recorded this month (uses COUNT query — no object loading). */
    private long totalExpensesThisMonth;

    // ── Chart.js – Doughnut / Pie (category breakdown) ────────────────────────

    /**
     * Category breakdown shaped for Chart.js:
     * {
     *   "labels":          ["Food", "Transport", ...],
     *   "data":            [5000, 3000, ...],
     *   "backgroundColor": ["#FF6384", "#36A2EB", ...]
     * }
     */
    private ChartData categoryChart;

    // ── Chart.js – Line / Bar (monthly trend) ─────────────────────────────────

    /**
     * Last-6-months spending trend shaped for Chart.js:
     * {
     *   "labels": ["Nov 2025", "Dec 2025", ..., "Apr 2026"],
     *   "data":   [10000, 12000, ..., 9000]
     * }
     */
    private ChartData monthlyTrendChart;

    // ── Raw breakdown (for tables / tooltips) ─────────────────────────────────

    /** category → total amount (all-time). */
    private Map<String, BigDecimal> categoryBreakdown;

    // ── Inner type ────────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChartData {
        private List<String>     labels;
        private List<BigDecimal> data;
        private List<String>     backgroundColor;
    }
}
