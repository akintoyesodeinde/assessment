package com.example.test.repo;

import com.example.test.model.WalletBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WalletBalanceRepo extends JpaRepository<WalletBalance,Long> {

    Optional<WalletBalance> findByAccount_AccountNumber(String accountNumber);

    Optional<WalletBalance> findByAccount_User_EmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WalletBalance w where w.account.accountNumber = :accountNumber")
    Optional<WalletBalance> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);
}
