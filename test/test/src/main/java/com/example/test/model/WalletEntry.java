package com.example.test.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "wallet_entries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_entry_txn_account_type",
                columnNames = {"transaction_id", "account_id", "entry_type"}
        ),
        indexes = {
                @Index(name = "idx_entry_account_created_at", columnList = "account_id,created_at"),
                @Index(name = "idx_entry_txn", columnList = "transaction_id")
        }
)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WalletEntry implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, foreignKey = @ForeignKey(name = "fk_entry_txn"))
    private WalletTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, foreignKey = @ForeignKey(name = "fk_entry_account"))
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 16)
    private EntryType entryType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false, updatable = false, unique = true, length = 32)
    private String entryReference;
}
