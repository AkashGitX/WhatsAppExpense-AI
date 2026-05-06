package com.akash.budgetchanger.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request body for PUT /users/budget
 * Sets or updates the authenticated user's personal monthly budget.
 */
@Data
public class BudgetRequest {

    @NotNull(message = "Budget amount is required.")
    @DecimalMin(value = "0.0", inclusive = false, message = "Budget must be greater than zero.")
    private BigDecimal budget;
}
