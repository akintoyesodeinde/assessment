package com.example.test.repo;

import com.example.test.model.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletTransactionRepo extends JpaRepository<WalletTransaction, Long> {

    Optional<WalletTransaction> findByIdempotencyId(String idempotencyId);
}
