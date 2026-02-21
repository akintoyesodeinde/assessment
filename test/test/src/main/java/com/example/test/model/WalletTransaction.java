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
        name = "wallet_transactions",
        indexes = {
                @Index(name = "idx_txn_created_at", columnList = "created_at"),
                @Index(name = "idx_txn_from_account", columnList = "from_account_id"),
                @Index(name = "idx_txn_to_account", columnList = "to_account_id")
        }
)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WalletTransaction implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String reference;

    @Column(nullable = false, unique = true, updatable = false, length = 64)
    private String idempotencyId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_account_id", nullable = false, foreignKey = @ForeignKey(name = "fk_txn_from_account"))
    private Account fromAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_account_id", nullable = false, foreignKey = @ForeignKey(name = "fk_txn_to_account"))
    private Account toAccount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal fromBalanceAfter;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal toBalanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
