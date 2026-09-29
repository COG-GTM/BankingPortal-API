package com.webapp.bankingportal.service;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.Transaction;
import com.webapp.bankingportal.entity.TransactionType;
import com.webapp.bankingportal.entity.User;
import com.webapp.bankingportal.exception.FundTransferException;
import com.webapp.bankingportal.exception.InsufficientBalanceException;
import com.webapp.bankingportal.exception.InvalidAmountException;
import com.webapp.bankingportal.exception.InvalidPinException;
import com.webapp.bankingportal.exception.NotFoundException;
import com.webapp.bankingportal.exception.UnauthorizedException;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.TransactionRepository;
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

    private Account getAccountForUpdate(String accountNumber) {
        val account = accountRepository.findByAccountNumberForUpdate(accountNumber);
        if (account == null) {
            throw new NotFoundException(ApiMessages.ACCOUNT_NOT_FOUND.getMessage());
        }

        return account;
    }

    /**
     * Locks the given accounts in a globally consistent order (by account number)
     * so that concurrent transfers in opposite directions cannot deadlock.
     * Accounts that do not exist map to {@code null}.
     */
    private Map<String, Account> getAccountsForUpdate(String... accountNumbers) {
        val accounts = new HashMap<String, Account>();
        Arrays.stream(accountNumbers)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .forEach(accountNumber -> accounts.put(accountNumber,
                        accountRepository.findByAccountNumberForUpdate(accountNumber)));
        return accounts;
    }

    private void validatePin(Account account, String pin) {
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

    private void validatePassword(Account account, String password) {
        if (password == null || password.isEmpty()) {
            throw new UnauthorizedException(ApiMessages.PASSWORD_EMPTY_ERROR.getMessage());
        }

        if (!passwordEncoder.matches(password, account.getUser().getPassword())) {
            throw new UnauthorizedException(ApiMessages.PASSWORD_INVALID_ERROR.getMessage());
        }
    }

    private void validateNewPin(String pin) {
        if (pin == null || pin.isEmpty()) {
            throw new InvalidPinException(ApiMessages.PIN_EMPTY_ERROR.getMessage());
        }

        if (!pin.matches("[0-9]{4}")) {
            throw new InvalidPinException(ApiMessages.PIN_FORMAT_INVALID_ERROR.getMessage());
        }
    }

    @Transactional
    @Override
    public void createPin(String accountNumber, String password, String pin) {
        val account = getAccountForUpdate(accountNumber);
        validatePassword(account, password);

        if (account.getPin() != null) {
            throw new UnauthorizedException(ApiMessages.PIN_ALREADY_EXISTS.getMessage());
        }

        validateNewPin(pin);

        account.setPin(passwordEncoder.encode(pin));
        accountRepository.save(account);
    }

    @Transactional
    @Override
    public void updatePin(String accountNumber, String oldPin, String password, String newPin) {
        log.info("Updating PIN for account: {}", accountNumber);

        val account = getAccountForUpdate(accountNumber);
        validatePassword(account, password);
        validatePin(account, oldPin);
        validateNewPin(newPin);

        account.setPin(passwordEncoder.encode(newPin));
        accountRepository.save(account);
    }

    private void validateAmount(double amount) {
        if (amount <= 0) {
            throw new InvalidAmountException(ApiMessages.AMOUNT_NEGATIVE_ERROR.getMessage());
        }

        if (amount % 100 != 0) {
            throw new InvalidAmountException(ApiMessages.AMOUNT_NOT_MULTIPLE_OF_100_ERROR.getMessage());
        }

        if (amount > 100000) {
            throw new InvalidAmountException(ApiMessages.AMOUNT_EXCEED_100_000_ERROR.getMessage());
        }
    }

    private void recordTransaction(double amount, TransactionType type, Account source, Account target) {
        val transaction = new Transaction();
        transaction.setAmount(amount);
        transaction.setTransactionType(type);
        transaction.setTransactionDate(new Date());
        transaction.setSourceAccount(source);
        transaction.setTargetAccount(target);
        transactionRepository.save(transaction);
    }

    @Transactional
    @Override
    public void cashDeposit(String accountNumber, String pin, double amount) {
        val account = getAccountForUpdate(accountNumber);
        validatePin(account, pin);
        validateAmount(amount);

        account.setBalance(account.getBalance() + amount);
        accountRepository.save(account);

        recordTransaction(amount, TransactionType.CASH_DEPOSIT, account, null);
    }

    @Transactional
    @Override
    public void cashWithdrawal(String accountNumber, String pin, double amount) {
        val account = getAccountForUpdate(accountNumber);
        validatePin(account, pin);
        validateAmount(amount);

        if (account.getBalance() < amount) {
            throw new InsufficientBalanceException(ApiMessages.BALANCE_INSUFFICIENT_ERROR.getMessage());
        }

        account.setBalance(account.getBalance() - amount);
        accountRepository.save(account);

        recordTransaction(amount, TransactionType.CASH_WITHDRAWAL, account, null);
    }

    @Transactional
    @Override
    public void fundTransfer(String sourceAccountNumber, String targetAccountNumber, String pin, double amount) {
        val accounts = getAccountsForUpdate(sourceAccountNumber, targetAccountNumber);

        val sourceAccount = accounts.get(sourceAccountNumber);
        if (sourceAccount == null) {
            throw new NotFoundException(ApiMessages.ACCOUNT_NOT_FOUND.getMessage());
        }

        validatePin(sourceAccount, pin);
        validateAmount(amount);

        if (sourceAccountNumber.equals(targetAccountNumber)) {
            throw new FundTransferException(ApiMessages.CASH_TRANSFER_SAME_ACCOUNT_ERROR.getMessage());
        }

        val targetAccount = accounts.get(targetAccountNumber);
        if (targetAccount == null) {
            throw new NotFoundException(ApiMessages.ACCOUNT_NOT_FOUND.getMessage());
        }

        if (sourceAccount.getBalance() < amount) {
            throw new InsufficientBalanceException(ApiMessages.BALANCE_INSUFFICIENT_ERROR.getMessage());
        }

        sourceAccount.setBalance(sourceAccount.getBalance() - amount);
        targetAccount.setBalance(targetAccount.getBalance() + amount);
        accountRepository.save(sourceAccount);
        accountRepository.save(targetAccount);

        recordTransaction(amount, TransactionType.CASH_TRANSFER, sourceAccount, targetAccount);
    }

}
