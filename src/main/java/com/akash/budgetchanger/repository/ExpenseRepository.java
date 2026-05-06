package com.akash.budgetchanger.repository;

import com.akash.budgetchanger.entity.Expense;
import com.akash.budgetchanger.entity.ExpenseSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    // ── Basic lookups (non-paginated) ─────────────────────────────────────────

    List<Expense> findByUserIdOrderByDateDesc(Long userId);

    List<Expense> findByUserIdAndDateBetweenOrderByDateDesc(
            Long userId, LocalDate startDate, LocalDate endDate);

    List<Expense> findByUserIdAndCategory(Long userId, String category);

    List<Expense> findByUserIdAndSource(Long userId, ExpenseSource source);

    /** All expenses on or after startDate sorted ascending — used for trend chart and AI context. */
    List<Expense> findByUserIdAndDateGreaterThanEqualOrderByDateAsc(
            Long userId, LocalDate startDate);

    // ── Paginated lookup ──────────────────────────────────────────────────────

    /**
     * Returns a page of expenses for a user, most recent first.
     * Use Spring Data's Pageable: {@code PageRequest.of(0, 50, Sort.by("date").descending())}
     */
    Page<Expense> findByUserIdOrderByDateDesc(Long userId, Pageable pageable);

    // ── Aggregations ──────────────────────────────────────────────────────────

    @Query("""
           SELECT COALESCE(SUM(e.amount), 0) FROM Expense e
           WHERE e.user.id = :userId
             AND YEAR(e.date) = :year
             AND MONTH(e.date) = :month
           """)
    BigDecimal sumAmountByUserIdAndMonth(
            @Param("userId") Long userId,
            @Param("year")   int year,
            @Param("month")  int month);

    @Query("""
           SELECT COALESCE(SUM(e.amount), 0) FROM Expense e
           WHERE e.user.id = :userId
             AND e.date BETWEEN :startDate AND :endDate
           """)
    BigDecimal sumAmountByUserIdAndDateBetween(
            @Param("userId")    Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate")   LocalDate endDate);

    /**
     * Efficient row count for a date range — avoids loading Expense objects.
     * Used by AnalyticsService to populate totalExpensesThisMonth.
     */
    @Query("""
           SELECT COUNT(e) FROM Expense e
           WHERE e.user.id = :userId
             AND e.date BETWEEN :startDate AND :endDate
           """)
    long countByUserIdAndDateBetween(
            @Param("userId")    Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate")   LocalDate endDate);

    @Query("""
           SELECT e.category, COALESCE(SUM(e.amount), 0)
           FROM Expense e
           WHERE e.user.id = :userId
           GROUP BY e.category
           ORDER BY SUM(e.amount) DESC
           """)
    List<Object[]> findCategoryBreakdownByUserId(@Param("userId") Long userId);
}
