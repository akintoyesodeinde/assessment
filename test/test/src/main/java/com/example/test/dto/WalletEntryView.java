package com.example.test.dto;

import com.example.test.model.EntryType;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@AllArgsConstructor
public class WalletEntryView {
    private String transactionReference;
    private EntryType entryType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private Instant createdAt;
}
