package com.example.test;

import com.example.test.dto.CreateUserRequest;
import com.example.test.dto.CreateUserResponse;
import com.example.test.dto.DoTransDto;
import com.example.test.dto.FundWalletRequest;
import com.example.test.dto.TransferResponse;
import com.example.test.exception.BadRequestException;
import com.example.test.exception.InsufficientFundsException;
import com.example.test.model.WalletBalance;
import com.example.test.repo.WalletBalanceRepo;
import com.example.test.repo.WalletTransactionRepo;
import com.example.test.service.ServiceCall;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class TestApplicationTests {

	@Autowired
	private ServiceCall serviceCall;

	@Autowired
	private WalletBalanceRepo walletBalanceRepo;

	@Autowired
	private WalletTransactionRepo walletTransactionRepo;

	@Test
	void contextLoads() {
	}

	@Test
	void createUserAlsoCreatesAccountAndWallet() {
		CreateUserResponse created = serviceCall.createUserAndAccount(
				new CreateUserRequest(uniqueEmail("create-user"))
		);

		assertNotNull(created.getUserId());
		assertNotNull(created.getAccountNumber());
		assertEquals(0, created.getBalance().compareTo(BigDecimal.ZERO));

		Optional<WalletBalance> wallet = walletBalanceRepo.findByAccount_AccountNumber(created.getAccountNumber());
		assertTrue(wallet.isPresent());
		assertEquals(0, wallet.get().getAmount().compareTo(BigDecimal.ZERO));
	}

	@Test
	void transferMovesFundsBetweenWallets() {
		CreateUserResponse source = serviceCall.createUserAndAccount(new CreateUserRequest(uniqueEmail("source")));
		CreateUserResponse destination = serviceCall.createUserAndAccount(new CreateUserRequest(uniqueEmail("destination")));

		WalletBalance sourceWallet = walletBalanceRepo.findByAccount_AccountNumber(source.getAccountNumber()).orElseThrow();
		sourceWallet.setAmount(new BigDecimal("100.00"));
		walletBalanceRepo.save(sourceWallet);

		TransferResponse transfer = serviceCall.doIntraTransfer(
				new DoTransDto(source.getAccountNumber(), destination.getAccountNumber(), new BigDecimal("40.00"), uniqueIdempotency())
		);

		assertEquals(0, transfer.getAmount().compareTo(new BigDecimal("40.00")));
		assertEquals(0, transfer.getFromBalance().compareTo(new BigDecimal("60.00")));
		assertEquals(0, transfer.getToBalance().compareTo(new BigDecimal("40.00")));
	}

	@Test
	void transferFailsWhenInsufficientFundsAndKeepsBalances() {
		CreateUserResponse source = serviceCall.createUserAndAccount(new CreateUserRequest(uniqueEmail("source-low")));
		CreateUserResponse destination = serviceCall.createUserAndAccount(new CreateUserRequest(uniqueEmail("destination-low")));

		WalletBalance sourceWallet = walletBalanceRepo.findByAccount_AccountNumber(source.getAccountNumber()).orElseThrow();
		WalletBalance destinationWallet = walletBalanceRepo.findByAccount_AccountNumber(destination.getAccountNumber()).orElseThrow();
		sourceWallet.setAmount(new BigDecimal("10.00"));
		destinationWallet.setAmount(new BigDecimal("5.00"));
		walletBalanceRepo.save(sourceWallet);
		walletBalanceRepo.save(destinationWallet);

		assertThrows(
				InsufficientFundsException.class,
				() -> serviceCall.doIntraTransfer(new DoTransDto(source.getAccountNumber(), destination.getAccountNumber(), new BigDecimal("20.00"), uniqueIdempotency()))
		);

		WalletBalance sourceAfter = walletBalanceRepo.findByAccount_AccountNumber(source.getAccountNumber()).orElseThrow();
		WalletBalance destinationAfter = walletBalanceRepo.findByAccount_AccountNumber(destination.getAccountNumber()).orElseThrow();
		assertEquals(0, sourceAfter.getAmount().compareTo(new BigDecimal("10.00")));
		assertEquals(0, destinationAfter.getAmount().compareTo(new BigDecimal("5.00")));
	}

	@Test
	void transferFailsWhenFromAndToAreSame() {
		CreateUserResponse source = serviceCall.createUserAndAccount(new CreateUserRequest(uniqueEmail("same-account")));

		assertThrows(
				BadRequestException.class,
				() -> serviceCall.doIntraTransfer(
						new DoTransDto(source.getAccountNumber(), source.getAccountNumber(), new BigDecimal("1.00"), uniqueIdempotency())
				)
		);
	}

	@Test
	void duplicateIdempotencyIdDoesNotCreateDuplicateTransfer() {
		CreateUserResponse source = serviceCall.createUserAndAccount(new CreateUserRequest(uniqueEmail("source-idem")));
		CreateUserResponse destination = serviceCall.createUserAndAccount(new CreateUserRequest(uniqueEmail("destination-idem")));

		WalletBalance sourceWallet = walletBalanceRepo.findByAccount_AccountNumber(source.getAccountNumber()).orElseThrow();
		sourceWallet.setAmount(new BigDecimal("100.00"));
		walletBalanceRepo.save(sourceWallet);

		String idempotencyId = uniqueIdempotency();
		DoTransDto request = new DoTransDto(
				source.getAccountNumber(),
				destination.getAccountNumber(),
				new BigDecimal("25.00"),
				idempotencyId
		);

		TransferResponse first = serviceCall.doIntraTransfer(request);
		TransferResponse second = serviceCall.doIntraTransfer(request);

		assertEquals(0, first.getFromBalance().compareTo(new BigDecimal("75.00")));
		assertEquals(0, first.getToBalance().compareTo(new BigDecimal("25.00")));
		assertEquals(0, second.getFromBalance().compareTo(new BigDecimal("75.00")));
		assertEquals(0, second.getToBalance().compareTo(new BigDecimal("25.00")));
		assertEquals(1L, walletTransactionRepo.count());
	}

	@Test
	void invalidIdempotencyIdIsRejected() {
		CreateUserResponse source = serviceCall.createUserAndAccount(new CreateUserRequest(uniqueEmail("source-invalid-idem")));
		CreateUserResponse destination = serviceCall.createUserAndAccount(new CreateUserRequest(uniqueEmail("destination-invalid-idem")));

		assertThrows(
				BadRequestException.class,
				() -> serviceCall.doIntraTransfer(
						new DoTransDto(source.getAccountNumber(), destination.getAccountNumber(), new BigDecimal("1.00"), "bad")
				)
		);
	}

	@Test
	void fundWalletCreditsUserBalance() {
		CreateUserResponse userWallet = serviceCall.createUserAndAccount(new CreateUserRequest(uniqueEmail("fund-user")));

		TransferResponse fundResponse = serviceCall.fundWalletForUser(
				userWallet.getEmail(),
				new FundWalletRequest(new BigDecimal("75.00"))
		);

		assertEquals(0, fundResponse.getAmount().compareTo(new BigDecimal("75.00")));
		assertEquals(userWallet.getAccountNumber(), fundResponse.getToAccount());
		assertEquals(0, fundResponse.getToBalance().compareTo(new BigDecimal("75.00")));

		WalletBalance balanceAfterFunding = walletBalanceRepo.findByAccount_AccountNumber(userWallet.getAccountNumber()).orElseThrow();
		assertEquals(0, balanceAfterFunding.getAmount().compareTo(new BigDecimal("75.00")));
	}

	private String uniqueEmail(String prefix) {
		return prefix + "-" + UUID.randomUUID() + "@example.com";
	}

	private String uniqueIdempotency() {
		return "idem_" + UUID.randomUUID().toString().replace("-", "");
	}

}
