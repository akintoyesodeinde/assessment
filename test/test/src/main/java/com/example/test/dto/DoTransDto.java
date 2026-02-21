package com.example.test.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class DoTransDto {
    @NotBlank(message = "fromAccount is required")
    @Pattern(regexp = "^WAL[0-9A-Z]{12}$", message = "fromAccount format is invalid")
    private String fromAccount;

    @NotBlank(message = "toAccount is required")
    @Pattern(regexp = "^WAL[0-9A-Z]{12}$", message = "toAccount format is invalid")
    private String toAccount;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
    private BigDecimal amount;

    @NotBlank(message = "idempotencyId is required")
    @Pattern(regexp = "^[A-Za-z0-9_-]{8,64}$", message = "idempotencyId must be 8-64 chars using letters, numbers, '-' or '_'")
    private String idempotencyId;
}
