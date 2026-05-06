package com.akash.budgetchanger.service;

import com.akash.budgetchanger.dto.SummaryResponse;
import com.akash.budgetchanger.entity.Expense;
import com.akash.budgetchanger.entity.User;
import com.akash.budgetchanger.repository.ExpenseRepository;
import com.akash.budgetchanger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository    userRepository;

    private static final List<String> CHART_COLORS = List.of(
            "#FF6384", "#36A2EB", "#FFCE56", "#4BC0C0",
            "#9966FF", "#FF9F40", "#C9CBCF", "#7CFC00"
    );

    // ── Public API ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SummaryResponse getSummary(Long userId) {
        LocalDate today = LocalDate.now();

        // Load user's personal monthly budget
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        BigDecimal userBudget = user.getMonthlyBudget();
        boolean    budgetNotSet = (userBudget == null || userBudget.compareTo(BigDecimal.ZERO) <= 0);
        BigDecimal effectiveBudget = budgetNotSet ? BigDecimal.ZERO : userBudget;

        BigDecimal monthly = getMonthlySpending(userId, today.getYear(), today.getMonthValue());
        BigDecimal weekly  = getWeeklySpending(userId, today);
        BigDecimal daily   = getDailySpending(userId, today);

        boolean    overBudget       = !budgetNotSet && monthly.compareTo(effectiveBudget) > 0;
        BigDecimal remaining        = budgetNotSet  ? BigDecimal.ZERO
                                    : overBudget    ? BigDecimal.ZERO
                                    : effectiveBudget.subtract(monthly);
        BigDecimal overBudgetAmount = overBudget
                                    ? monthly.subtract(effectiveBudget)
                                    : BigDecimal.ZERO;

        Map<String, BigDecimal> categoryMap  = getCategoryBreakdown(userId);
        SummaryResponse.ChartData catChart   = buildCategoryChart(categoryMap);

        List<Expense> recent = expenseRepository
                .findByUserIdAndDateGreaterThanEqualOrderByDateAsc(
                        userId, today.minusMonths(6).withDayOfMonth(1));
        SummaryResponse.ChartData trendChart = buildMonthlyTrendChart(recent, today);

        long countThisMonth = expenseRepository.countByUserIdAndDateBetween(
                userId, today.withDayOfMonth(1), today);

        return SummaryResponse.builder()
                .monthlySpending(monthly)
                .weeklySpending(weekly)
                .dailySpending(daily)
                .remainingBudget(remaining)
                .monthlyBudgetLimit(effectiveBudget)
                .budgetNotSet(budgetNotSet)
                .overBudget(overBudget)
                .overBudgetAmount(overBudgetAmount.setScale(2, RoundingMode.HALF_UP))
                .totalExpensesThisMonth(countThisMonth)
                .categoryChart(catChart)
                .monthlyTrendChart(trendChart)
                .categoryBreakdown(categoryMap)
                .build();
    }

    @Transactional(readOnly = true)
    public BigDecimal getMonthlySpending(Long userId, int year, int month) {
        BigDecimal result = expenseRepository.sumAmountByUserIdAndMonth(userId, year, month);
        return result != null ? result.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public BigDecimal getWeeklySpending(Long userId, LocalDate reference) {
        LocalDate monday = reference.with(DayOfWeek.MONDAY);
        LocalDate sunday = reference.with(DayOfWeek.SUNDAY);
        BigDecimal result = expenseRepository.sumAmountByUserIdAndDateBetween(userId, monday, sunday);
        return result != null ? result.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public BigDecimal getDailySpending(Long userId, LocalDate date) {
        BigDecimal result = expenseRepository.sumAmountByUserIdAndDateBetween(userId, date, date);
        return result != null ? result.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getCategoryBreakdown(Long userId) {
        List<Object[]> rows = expenseRepository.findCategoryBreakdownByUserId(userId);
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String     category = (String) row[0];
            BigDecimal amount   = ((BigDecimal) row[1]).setScale(2, RoundingMode.HALF_UP);
            result.put(category, amount);
        }
        return result;
    }

    // ── Chart builders ────────────────────────────────────────────────────────

    private SummaryResponse.ChartData buildCategoryChart(Map<String, BigDecimal> categoryMap) {
        List<String>     labels = new ArrayList<>(categoryMap.keySet());
        List<BigDecimal> data   = new ArrayList<>(categoryMap.values());
        List<String>     colors = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            colors.add(CHART_COLORS.get(i % CHART_COLORS.size()));
        }
        return SummaryResponse.ChartData.builder()
                .labels(labels).data(data).backgroundColor(colors).build();
    }

    private SummaryResponse.ChartData buildMonthlyTrendChart(
            List<Expense> expenses, LocalDate today) {

        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("MMM yyyy");
        Map<String, BigDecimal> monthTotals = new LinkedHashMap<>();

        for (int i = 5; i >= 0; i--) {
            LocalDate month = today.minusMonths(i).withDayOfMonth(1);
            monthTotals.put(month.format(labelFmt), BigDecimal.ZERO);
        }
        for (Expense e : expenses) {
            String slot = e.getDate().withDayOfMonth(1).format(labelFmt);
            if (monthTotals.containsKey(slot)) {
                monthTotals.merge(slot, e.getAmount(), BigDecimal::add);
            }
        }

        List<String>     labels = new ArrayList<>(monthTotals.keySet());
        List<BigDecimal> data   = monthTotals.values().stream()
                .map(v -> v.setScale(2, RoundingMode.HALF_UP))
                .collect(Collectors.toList());

        return SummaryResponse.ChartData.builder().labels(labels).data(data).build();
    }
}
