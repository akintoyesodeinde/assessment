package com.example.test.service;

import com.example.test.dto.CreateUserRequest;
import com.example.test.dto.CreateUserResponse;
import com.example.test.dto.DoTransDto;
import com.example.test.dto.FundWalletRequest;
import com.example.test.dto.RegisterRequest;
import com.example.test.dto.TransferResponse;
import com.example.test.dto.WalletEntryView;
import com.example.test.dto.WebTransferRequest;
import com.example.test.exception.BadRequestException;
import com.example.test.exception.ConflictException;
import com.example.test.exception.InsufficientFundsException;
import com.example.test.exception.ResourceNotFoundException;
import com.example.test.model.Account;
import com.example.test.model.EntryType;
import com.example.test.model.TransactionStatus;
import com.example.test.model.User;
import com.example.test.model.WalletEntry;
import com.example.test.model.WalletBalance;
import com.example.test.model.WalletTransaction;
import com.example.test.repo.AccountRepo;
import com.example.test.repo.UserRepo;
import com.example.test.repo.WalletEntryRepo;
import com.example.test.repo.WalletBalanceRepo;
import com.example.test.repo.WalletTransactionRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DoService implements ServiceCall{

    private static final Pattern IDEMPOTENCY_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");
    private static final String TREASURY_USER_EMAIL = "treasury@wallet.local";
    private static final BigDecimal TREASURY_INITIAL_BALANCE = new BigDecimal("1000000000.00");

    private final UserRepo userRepo;
    private final AccountRepo accountRepo;
    private final WalletBalanceRepo walletBalanceRepo;
    private final WalletTransactionRepo walletTransactionRepo;
    private final WalletEntryRepo walletEntryRepo;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public CreateUserResponse createUserAndAccount(CreateUserRequest request) {
        String normalizedEmail = normalizeEmail(request == null ? null : request.getEmail());
        if (userRepo.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("User already exists with this email");
        }

        User savedUser = userRepo.save(new User(
                null,
                normalizedEmail,
                passwordEncoder.encode(UUID.randomUUID().toString())
        ));
        return createWalletForExistingUser(savedUser);
    }

    @Override
    @Transactional
    public Long registerUser(RegisterRequest request) {
        if (request == null) {
            throw new BadRequestException("Registration payload is required");
        }
        String normalizedEmail = normalizeEmail(request.getEmail());
        if (request.getPassword() == null || request.getPassword().trim().length() < 6) {
            throw new BadRequestException("Password must have at least 6 characters");
        }
        if (userRepo.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("User already exists with this email");
        }
        User savedUser = userRepo.save(new User(
                null,
                normalizedEmail,
                passwordEncoder.encode(request.getPassword().trim())
        ));
        log.info("Registered user={}", savedUser.getId());
        return savedUser.getId();
    }

    @Override
    @Transactional
    public CreateUserResponse createWalletForUser(String email) {
        User user = findUserByEmail(email);
        return createWalletForExistingUser(user);
    }

    @Override
    @Transactional(readOnly = true)
    public CreateUserResponse getWalletForUser(String email) {
        User user = findUserByEmail(email);
        WalletBalance wallet = walletBalanceRepo.findByAccount_User_EmailIgnoreCase(user.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Wallet does not exist for this user"));
        return new CreateUserResponse(
                user.getId(),
                user.getEmail(),
                wallet.getAccount().getAccountNumber(),
                wallet.getAmount()
        );
    }

    @Override
    @Transactional
    public TransferResponse doTransferFromUserWallet(String email, WebTransferRequest request) {
        if (request == null) {
            throw new BadRequestException("Transfer payload is required");
        }
        BigDecimal amount = normalizeAmount(request.getAmount());
        CreateUserResponse wallet = getWalletForUser(email);
        return doIntraTransfer(
                new DoTransDto(
                        wallet.getAccountNumber(),
                        request.getToAccount(),
                        amount,
                        generateClientSafeIdempotencyId("WEBTR")
                )
        );
    }

    @Override
    @Transactional
    public TransferResponse fundWalletForUser(String email, FundWalletRequest request) {
        if (request == null) {
            throw new BadRequestException("Funding payload is required");
        }
        BigDecimal amount = normalizeAmount(request.getAmount());
        CreateUserResponse userWallet = getWalletForUser(email);
        WalletBalance treasuryWallet = getOrCreateTreasuryWallet();
        String treasuryAccount = treasuryWallet.getAccount().getAccountNumber();
        if (treasuryAccount.equals(userWallet.getAccountNumber())) {
            throw new ConflictException("Treasury account cannot fund itself");
        }
        return doIntraTransfer(
                new DoTransDto(
                        treasuryAccount,
                        userWallet.getAccountNumber(),
                        amount,
                        generateClientSafeIdempotencyId("TOPUP")
                )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<WalletEntryView> listEntriesForUser(String email) {
        CreateUserResponse wallet = getWalletForUser(email);
        return walletEntryRepo.findTop20ByAccount_AccountNumberOrderByCreatedAtDesc(wallet.getAccountNumber())
                .stream()
                .map(entry -> new WalletEntryView(
                        entry.getTransaction().getReference(),
                        entry.getEntryType(),
                        entry.getAmount(),
                        entry.getBalanceAfter(),
                        entry.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public TransferResponse doIntraTransfer(DoTransDto request) {
        if (request == null) {
            throw new BadRequestException("Transfer request cannot be null");
        }

        String fromAccount = normalizeAccountNumber(request.getFromAccount(), "fromAccount");
        String toAccount = normalizeAccountNumber(request.getToAccount(), "toAccount");
        BigDecimal amount = normalizeAmount(request.getAmount());
        String idempotencyId = normalizeIdempotencyId(request.getIdempotencyId());

        if (fromAccount.equals(toAccount)) {
            throw new BadRequestException("Source and destination accounts must be different");
        }

        WalletTransaction existingTransaction = walletTransactionRepo.findByIdempotencyId(idempotencyId).orElse(null);
        if (existingTransaction != null) {
            return buildExistingIdempotentResponse(existingTransaction, fromAccount, toAccount, amount, idempotencyId);
        }

        try {
            WalletBalance fromWallet;
            WalletBalance toWallet;
            if (fromAccount.compareTo(toAccount) < 0) {
                WalletBalance firstLocked = lockWalletByAccountNumber(fromAccount);
                WalletBalance secondLocked = lockWalletByAccountNumber(toAccount);
                fromWallet = firstLocked;
                toWallet = secondLocked;
            } else {
                WalletBalance firstLocked = lockWalletByAccountNumber(toAccount);
                WalletBalance secondLocked = lockWalletByAccountNumber(fromAccount);
                fromWallet = secondLocked;
                toWallet = firstLocked;
            }

            WalletTransaction existingAfterLock = walletTransactionRepo.findByIdempotencyId(idempotencyId).orElse(null);
            if (existingAfterLock != null) {
                return buildExistingIdempotentResponse(existingAfterLock, fromAccount, toAccount, amount, idempotencyId);
            }

            if (fromWallet.getAmount().compareTo(amount) < 0) {
                throw new InsufficientFundsException("Insufficient balance in source wallet");
            }

            BigDecimal fromBalanceAfter = fromWallet.getAmount().subtract(amount);
            BigDecimal toBalanceAfter = toWallet.getAmount().add(amount);

            WalletTransaction transaction = reserveTransaction(
                    idempotencyId,
                    amount,
                    fromWallet.getAccount(),
                    toWallet.getAccount(),
                    fromBalanceAfter,
                    toBalanceAfter
            );

            fromWallet.setAmount(fromBalanceAfter);
            toWallet.setAmount(toBalanceAfter);
            walletBalanceRepo.save(fromWallet);
            walletBalanceRepo.save(toWallet);

            persistBalancedEntries(
                    transaction,
                    fromWallet.getAccount(),
                    toWallet.getAccount(),
                    amount,
                    fromBalanceAfter,
                    toBalanceAfter
            );

            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setCompletedAt(Instant.now());
            walletTransactionRepo.save(transaction);

            log.info("Transfer completed: {} -> {} amount={}", fromAccount, toAccount, amount);
            return new TransferResponse(
                    idempotencyId,
                    fromAccount,
                    toAccount,
                    amount,
                    fromBalanceAfter,
                    toBalanceAfter
            );
        } catch (BadRequestException | InsufficientFundsException | ResourceNotFoundException ex) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.warn("Transfer rejected for idempotencyId={}: {}", idempotencyId, ex.getMessage());
            throw ex;
        } catch (ConflictException ex) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.warn("Transfer conflict for idempotencyId={}: {}", idempotencyId, ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Transfer failed for idempotencyId={}", idempotencyId, ex);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            throw ex;
        }
    }

    private TransferResponse buildExistingIdempotentResponse(
            WalletTransaction existingTransaction,
            String fromAccount,
            String toAccount,
            BigDecimal amount,
            String idempotencyId
    ) {
        String existingFrom = existingTransaction.getFromAccount().getAccountNumber();
        String existingTo = existingTransaction.getToAccount().getAccountNumber();
        BigDecimal existingAmount = existingTransaction.getAmount();
        if (existingTransaction.getStatus() != TransactionStatus.COMPLETED) {
            throw new ConflictException("Transfer is already being processed for this idempotencyId");
        }
        if (!existingFrom.equals(fromAccount) || !existingTo.equals(toAccount) || existingAmount.compareTo(amount) != 0) {
            throw new ConflictException("Idempotency ID already used with a different transfer payload");
        }
        return new TransferResponse(
                idempotencyId,
                existingFrom,
                existingTo,
                existingAmount,
                existingTransaction.getFromBalanceAfter(),
                existingTransaction.getToBalanceAfter()
        );
    }

    private CreateUserResponse createWalletForExistingUser(User user) {
        if (accountRepo.findByUser_EmailIgnoreCase(user.getEmail()).isPresent()) {
            throw new ConflictException("Wallet already exists for this user");
        }
        String accountNumber = generateUniqueAccountNumber();
        Account account = accountRepo.save(new Account(null, accountNumber, user));
        WalletBalance wallet = walletBalanceRepo.save(new WalletBalance(null, BigDecimal.ZERO, account, 0L));
        log.info("Created wallet for user={}, account={}", user.getId(), accountNumber);
        return new CreateUserResponse(user.getId(), user.getEmail(), accountNumber, wallet.getAmount());
    }

    private WalletBalance getOrCreateTreasuryWallet() {
        User treasuryUser = userRepo.findByEmailIgnoreCase(TREASURY_USER_EMAIL)
                .orElseGet(() -> userRepo.save(new User(
                        null,
                        TREASURY_USER_EMAIL,
                        passwordEncoder.encode(UUID.randomUUID().toString())
                )));

        Account treasuryAccount = accountRepo.findByUser_EmailIgnoreCase(TREASURY_USER_EMAIL)
                .orElseGet(() -> accountRepo.save(new Account(
                        null,
                        generateUniqueAccountNumber(),
                        treasuryUser
                )));

        return walletBalanceRepo.findByAccount_AccountNumber(treasuryAccount.getAccountNumber())
                .orElseGet(() -> walletBalanceRepo.save(new WalletBalance(
                        null,
                        TREASURY_INITIAL_BALANCE,
                        treasuryAccount,
                        0L
                )));
    }

    private User findUserByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        return userRepo.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private WalletBalance lockWalletByAccountNumber(String accountNumber) {
        return walletBalanceRepo.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found for account: " + accountNumber));
    }

    private String normalizeEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new BadRequestException("Email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeAccountNumber(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value.trim();
    }

    private String normalizeIdempotencyId(String idempotencyId) {
        if (idempotencyId == null || idempotencyId.trim().isEmpty()) {
            throw new BadRequestException("idempotencyId is required");
        }
        String normalized = idempotencyId.trim();
        if (!IDEMPOTENCY_ID_PATTERN.matcher(normalized).matches()) {
            throw new BadRequestException("idempotencyId must be 8-64 chars using letters, numbers, '-' or '_'");
        }
        return normalized;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            throw new BadRequestException("Transfer amount is required");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Transfer amount must be greater than zero");
        }
        if (amount.scale() > 2) {
            throw new BadRequestException("Transfer amount can have at most 2 decimal places");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private WalletTransaction reserveTransaction(
            String idempotencyId,
            BigDecimal amount,
            Account fromAccount,
            Account toAccount,
            BigDecimal fromBalanceAfter,
            BigDecimal toBalanceAfter
    ) {
        WalletTransaction transaction = new WalletTransaction();
        transaction.setReference(generateTransactionReference());
        transaction.setIdempotencyId(idempotencyId);
        transaction.setAmount(amount);
        transaction.setFromAccount(fromAccount);
        transaction.setToAccount(toAccount);
        transaction.setFromBalanceAfter(fromBalanceAfter);
        transaction.setToBalanceAfter(toBalanceAfter);
        transaction.setStatus(TransactionStatus.PENDING);

        try {
            return walletTransactionRepo.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException ex) {
            WalletTransaction concurrent = walletTransactionRepo.findByIdempotencyId(idempotencyId).orElse(null);
            if (concurrent != null) {
                throw new ConflictException("idempotencyId is already in use");
            }
            throw ex;
        }
    }

    private void persistBalancedEntries(
            WalletTransaction transaction,
            Account fromAccount,
            Account toAccount,
            BigDecimal amount,
            BigDecimal fromBalanceAfter,
            BigDecimal toBalanceAfter
    ) {
        WalletEntry debitEntry = new WalletEntry(
                null,
                transaction,
                fromAccount,
                EntryType.DEBIT,
                amount,
                fromBalanceAfter,
                Instant.now(),
                generateEntryReference()
        );
        WalletEntry creditEntry = new WalletEntry(
                null,
                transaction,
                toAccount,
                EntryType.CREDIT,
                amount,
                toBalanceAfter,
                Instant.now(),
                generateEntryReference()
        );

        if (debitEntry.getAmount().compareTo(creditEntry.getAmount()) != 0) {
            throw new IllegalStateException("Ledger entries are not balanced");
        }
        walletEntryRepo.save(debitEntry);
        walletEntryRepo.save(creditEntry);
    }

    private String generateUniqueAccountNumber() {
        String accountNumber;
        do {
            accountNumber = "WAL" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        } while (accountRepo.existsByAccountNumber(accountNumber));
        return accountNumber;
    }

    private String generateTransactionReference() {
        return "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
    }

    private String generateEntryReference() {
        return "ENT" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
    }

    private String generateClientSafeIdempotencyId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
