package com.akash.budgetchanger.dto;

import com.akash.budgetchanger.entity.Expense;
import com.akash.budgetchanger.entity.ExpenseSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseResponse {

    private Long id;
    private BigDecimal amount;
    private String category;
    private String note;
    private LocalDate date;
    private ExpenseSource source;
    private LocalDateTime createdAt;
    private Long userId;
    private String userName;

    public static ExpenseResponse from(Expense expense) {
        return ExpenseResponse.builder()
                .id(expense.getId())
                .amount(expense.getAmount())
                .category(expense.getCategory())
                .note(expense.getNote())
                .date(expense.getDate())
                .source(expense.getSource())
                .createdAt(expense.getCreatedAt())
                .userId(expense.getUser().getId())
                .userName(expense.getUser().getName())
                .build();
    }
}
