package com.example.test.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class TransferResponse {
    private String idempotencyId;
    private String fromAccount;
    private String toAccount;
    private BigDecimal amount;
    private BigDecimal fromBalance;
    private BigDecimal toBalance;
}
