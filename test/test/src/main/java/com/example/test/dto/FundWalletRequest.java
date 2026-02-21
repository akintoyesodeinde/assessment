package com.example.test.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FundWalletRequest {
    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
    private BigDecimal amount;
}
