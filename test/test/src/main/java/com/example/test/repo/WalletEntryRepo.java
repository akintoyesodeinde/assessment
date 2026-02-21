package com.example.test.repo;

import com.example.test.model.WalletEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletEntryRepo extends JpaRepository<WalletEntry, Long> {

    List<WalletEntry> findTop20ByAccount_AccountNumberOrderByCreatedAtDesc(String accountNumber);
}
