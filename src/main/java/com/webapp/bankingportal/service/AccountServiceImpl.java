package com.webapp.bankingportal.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.webapp.bankingportal.dto.TransferIssue;
import com.webapp.bankingportal.dto.TransferPreviewResponse;
import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.Transaction;
import com.webapp.bankingportal.entity.TransactionType;
import com.webapp.bankingportal.entity.User;
import com.webapp.bankingportal.exception.InsufficientBalanceException;
import com.webapp.bankingportal.exception.InvalidPinException;
import com.webapp.bankingportal.exception.NotFoundException;
import com.webapp.bankingportal.exception.UnauthorizedException;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.TransactionRepository;
import com.webapp.bankingportal.type.TransactionRuleViolation;
import com.webapp.bankingportal.type.TransferWarning;
import com.webapp.bankingportal.util.ApiMessages;

import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {
@Autowired
    private final AccountRepository accountRepository;
    @Autowired
    private final PasswordEncoder passwordEncoder;
    @Autowired
    private final TransactionRepository transactionRepository;
    private final TransferPolicy transferPolicy;

    @Override
    public Account createAccount(User user) {
        val account = new Account();
        account.setAccountNumber(generateUniqueAccountNumber());
        account.setBalance(0.0);
        account.setUser(user);
        return accountRepository.save(account);
    }

    @Override
    public boolean isPinCreated(String accountNumber) {
        val account = accountRepository.findByAccountNumber(accountNumber);
        if (account == null) {
            throw new NotFoundException(ApiMessages.ACCOUNT_NOT_FOUND.getMessage());
        }

        return account.getPin() != null;
    }

    private String generateUniqueAccountNumber() {
        String accountNumber;
        do {
            // Generate a UUID as the account number
            accountNumber = UUID.randomUUID().toString().replaceAll("-", "").substring(0, 6);
        } while (accountRepository.findByAccountNumber(accountNumber) != null);

        return accountNumber;
    }

    private void validatePin(String accountNumber, String pin) {
        val account = accountRepository.findByAccountNumber(accountNumber);
        if (account == null) {
            throw new NotFoundException(ApiMessages.ACCOUNT_NOT_FOUND.getMessage());
        }

        if (account.getPin() == null) {
            throw new UnauthorizedException(ApiMessages.PIN_NOT_CREATED.getMessage());
        }

        if (pin == null || pin.isEmpty()) {
            throw new UnauthorizedException(ApiMessages.PIN_EMPTY_ERROR.getMessage());
        }

        if (!passwordEncoder.matches(pin, account.getPin())) {
            throw new UnauthorizedException(ApiMessages.PIN_INVALID_ERROR.getMessage());
        }
    }

    private void validatePassword(String accountNumber, String password) {
        val account = accountRepository.findByAccountNumber(accountNumber);
        if (account == null) {
            throw new NotFoundException(ApiMessages.ACCOUNT_NOT_FOUND.getMessage());
        }

        if (password == null || password.isEmpty()) {
            throw new UnauthorizedException(ApiMessages.PASSWORD_EMPTY_ERROR.getMessage());
        }

        if (!passwordEncoder.matches(password, account.getUser().getPassword())) {
            throw new UnauthorizedException(ApiMessages.PASSWORD_INVALID_ERROR.getMessage());
        }
    }

    @Override
    public void createPin(String accountNumber, String password, String pin) {
        validatePassword(accountNumber, password);

        val account = accountRepository.findByAccountNumber(accountNumber);
        if (account.getPin() != null) {
            throw new UnauthorizedException(ApiMessages.PIN_ALREADY_EXISTS.getMessage());
        }

        if (pin == null || pin.isEmpty()) {
            throw new InvalidPinException(ApiMessages.PIN_EMPTY_ERROR.getMessage());
        }

        if (!pin.matches("[0-9]{4}")) {
            throw new InvalidPinException(ApiMessages.PIN_FORMAT_INVALID_ERROR.getMessage());
        }

        account.setPin(passwordEncoder.encode(pin));
        accountRepository.save(account);
    }

    @Override
    public void updatePin(String accountNumber, String oldPin, String password, String newPin) {
        log.info("Updating PIN for account: {}", accountNumber);

        validatePassword(accountNumber, password);
        validatePin(accountNumber, oldPin);

        val account = accountRepository.findByAccountNumber(accountNumber);

        if (newPin == null || newPin.isEmpty()) {
            throw new InvalidPinException(ApiMessages.PIN_EMPTY_ERROR.getMessage());
        }

        if (!newPin.matches("[0-9]{4}")) {
            throw new InvalidPinException(ApiMessages.PIN_FORMAT_INVALID_ERROR.getMessage());
        }

        account.setPin(passwordEncoder.encode(newPin));
        accountRepository.save(account);
    }

    private List<TransactionRuleViolation> amountViolations(double amount) {
        val violations = new ArrayList<TransactionRuleViolation>();
        if (amount <= 0) {
            violations.add(TransactionRuleViolation.AMOUNT_NOT_POSITIVE);
        }

        if (amount % TransferPolicy.AMOUNT_STEP != 0) {
            violations.add(TransactionRuleViolation.AMOUNT_NOT_MULTIPLE_OF_100);
        }

        if (amount > TransferPolicy.MAX_TRANSACTION_AMOUNT) {
            violations.add(TransactionRuleViolation.AMOUNT_EXCEEDS_TRANSACTION_LIMIT);
        }

        return violations;
    }

    private void validateAmount(double amount) {
        val violations = amountViolations(amount);
        if (!violations.isEmpty()) {
            throw violations.get(0).toException();
        }
    }

    @Transactional
    @Override
    public void cashDeposit(String accountNumber, String pin, double amount) {
        validatePin(accountNumber, pin);
        validateAmount(amount);

        val account = accountRepository.findByAccountNumber(accountNumber);
        val currentBalance = account.getBalance();
        val newBalance = currentBalance + amount;
        account.setBalance(newBalance);
        accountRepository.save(account);

        val transaction = new Transaction();
        transaction.setAmount(amount);
        transaction.setTransactionType(TransactionType.CASH_DEPOSIT);
        transaction.setTransactionDate(new Date());
        transaction.setSourceAccount(account);
        transactionRepository.save(transaction);
    }

    @Transactional
    @Override
    public void cashWithdrawal(String accountNumber, String pin, double amount) {
        validatePin(accountNumber, pin);
        validateAmount(amount);

        val account = accountRepository.findByAccountNumber(accountNumber);
        val currentBalance = account.getBalance();
        if (currentBalance < amount) {
            throw new InsufficientBalanceException(ApiMessages.BALANCE_INSUFFICIENT_ERROR.getMessage());
        }

        val newBalance = currentBalance - amount;
        account.setBalance(newBalance);
        accountRepository.save(account);

        val transaction = new Transaction();
        transaction.setAmount(amount);
        transaction.setTransactionType(TransactionType.CASH_WITHDRAWAL);
        transaction.setTransactionDate(new Date());
        transaction.setSourceAccount(account);
        transactionRepository.save(transaction);
    }

    private record TransferPlan(
            Account sourceAccount,
            Account targetAccount,
            List<TransactionRuleViolation> violations,
            TransferPreviewResponse preview) {
    }

    /**
     * Single source of truth for transfer rules, fees and resulting balances.
     * Both {@link #previewFundTransfer} and {@link #fundTransfer} go through this
     * method, so a preview always reflects what execution would do against the
     * same account state. Violations are ordered by precedence; execution
     * rejects the transfer with the first one.
     */
    private TransferPlan planTransfer(String sourceAccountNumber, String targetAccountNumber, double amount) {
        val sourceAccount = accountRepository.findByAccountNumber(sourceAccountNumber);
        if (sourceAccount == null) {
            throw new NotFoundException(ApiMessages.ACCOUNT_NOT_FOUND.getMessage());
        }

        val violations = amountViolations(amount);

        Account targetAccount = null;
        if (sourceAccountNumber.equals(targetAccountNumber)) {
            violations.add(TransactionRuleViolation.SAME_ACCOUNT);
        } else {
            targetAccount = accountRepository.findByAccountNumber(targetAccountNumber);
            if (targetAccount == null) {
                violations.add(TransactionRuleViolation.TARGET_ACCOUNT_NOT_FOUND);
            }
        }

        val fee = transferPolicy.calculateFee(amount);
        val totalDebit = BigDecimal.valueOf(amount).add(BigDecimal.valueOf(fee));
        val currentBalance = BigDecimal.valueOf(sourceAccount.getBalance());
        val resultingBalance = currentBalance.subtract(totalDebit);
        if (resultingBalance.signum() < 0) {
            violations.add(TransactionRuleViolation.INSUFFICIENT_BALANCE);
        }

        val warnings = new ArrayList<TransferWarning>();
        if (transferPolicy.isApproachingTransactionLimit(amount)) {
            warnings.add(TransferWarning.APPROACHING_TRANSACTION_LIMIT);
        }

        if (violations.isEmpty() && transferPolicy.isLowBalance(resultingBalance.doubleValue())) {
            warnings.add(TransferWarning.LOW_RESULTING_BALANCE);
        }

        val preview = new TransferPreviewResponse(
                sourceAccountNumber,
                targetAccountNumber,
                amount,
                fee,
                totalDebit.doubleValue(),
                sourceAccount.getBalance(),
                resultingBalance.doubleValue(),
                violations.isEmpty(),
                violations.stream().map(TransferIssue::of).toList(),
                warnings.stream().map(TransferIssue::of).toList());

        return new TransferPlan(sourceAccount, targetAccount, List.copyOf(violations), preview);
    }

    @Transactional(readOnly = true)
    @Override
    public TransferPreviewResponse previewFundTransfer(String sourceAccountNumber, String targetAccountNumber,
            double amount) {
        return planTransfer(sourceAccountNumber, targetAccountNumber, amount).preview();
    }

    @Transactional
    @Override
    public void fundTransfer(String sourceAccountNumber, String targetAccountNumber, String pin, double amount) {
        validatePin(sourceAccountNumber, pin);

        val plan = planTransfer(sourceAccountNumber, targetAccountNumber, amount);
        if (!plan.violations().isEmpty()) {
            throw plan.violations().get(0).toException();
        }

        val preview = plan.preview();
        val sourceAccount = plan.sourceAccount();
        val targetAccount = plan.targetAccount();

        sourceAccount.setBalance(preview.resultingBalance());
        accountRepository.save(sourceAccount);

        targetAccount.setBalance(targetAccount.getBalance() + preview.amount());
        accountRepository.save(targetAccount);

        val transactionDate = new Date();
        val transaction = new Transaction();
        transaction.setAmount(preview.amount());
        transaction.setTransactionType(TransactionType.CASH_TRANSFER);
        transaction.setTransactionDate(transactionDate);
        transaction.setSourceAccount(sourceAccount);
        transaction.setTargetAccount(targetAccount);
        transactionRepository.save(transaction);

        if (preview.fee() > 0) {
            val feeTransaction = new Transaction();
            feeTransaction.setAmount(preview.fee());
            feeTransaction.setTransactionType(TransactionType.TRANSFER_FEE);
            feeTransaction.setTransactionDate(transactionDate);
            feeTransaction.setSourceAccount(sourceAccount);
            transactionRepository.save(feeTransaction);
        }
    }

}
