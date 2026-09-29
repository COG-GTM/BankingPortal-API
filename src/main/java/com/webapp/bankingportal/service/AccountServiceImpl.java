package com.webapp.bankingportal.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.webapp.bankingportal.dto.BulkFundTransferResponse;
import com.webapp.bankingportal.dto.BulkTransferItem;
import com.webapp.bankingportal.dto.BulkTransferItemResult;
import com.webapp.bankingportal.dto.BulkTransferItemResult.Status;
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

    static final int MAX_BULK_TRANSFER_SIZE = 50;

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

    @Transactional
    @Override
    public void fundTransfer(String sourceAccountNumber, String targetAccountNumber, String pin, double amount) {
        validatePin(sourceAccountNumber, pin);

        val sourceAccount = accountRepository.findByAccountNumber(sourceAccountNumber);
        val targetAccount = validateTransfer(sourceAccount, targetAccountNumber, amount, sourceAccount.getBalance());
        executeTransfer(sourceAccount, targetAccount, amount);
    }

    /**
     * Processes transfers in request order against a running balance. Items that
     * fail business validation are reported and never touch any balance, so in
     * non-atomic mode the successful items are committed and the failed ones are
     * no-ops. In atomic mode every item is validated before any is executed, and
     * nothing is applied if a single item fails. Unexpected errors propagate and
     * roll back the whole batch.
     */
    @Transactional
    @Override
    public BulkFundTransferResponse bulkFundTransfer(String sourceAccountNumber, String pin,
            List<BulkTransferItem> transfers, boolean atomic) {
        validatePin(sourceAccountNumber, pin);
        validateBatchSize(transfers);

        val sourceAccount = accountRepository.findByAccountNumber(sourceAccountNumber);
        val results = new ArrayList<BulkTransferItemResult>(transfers.size());
        val validatedTargets = new ArrayList<Account>(transfers.size());
        var availableBalance = sourceAccount.getBalance();
        var anyFailed = false;

        for (int index = 0; index < transfers.size(); index++) {
            val item = transfers.get(index);
            try {
                if (item == null) {
                    throw new FundTransferException(ApiMessages.BULK_TRANSFER_ITEM_NULL_ERROR.getMessage());
                }

                val targetAccount = validateTransfer(sourceAccount, item.targetAccountNumber(), item.amount(),
                        availableBalance);
                availableBalance -= item.amount();

                if (atomic) {
                    validatedTargets.add(targetAccount);
                    results.add(null);
                } else {
                    val transaction = executeTransfer(sourceAccount, targetAccount, item.amount());
                    results.add(itemResult(index, item, Status.SUCCESS,
                            ApiMessages.BULK_TRANSFER_SUCCESS.getMessage(), transaction.getId()));
                }
            } catch (FundTransferException | NotFoundException | InvalidAmountException
                    | InsufficientBalanceException e) {
                anyFailed = true;
                validatedTargets.add(null);
                results.add(itemResult(index, item, Status.FAILED, e.getMessage(), null));
            }
        }

        if (atomic) {
            for (int index = 0; index < transfers.size(); index++) {
                if (results.get(index) != null) {
                    continue;
                }

                val item = transfers.get(index);
                if (anyFailed) {
                    results.set(index, itemResult(index, item, Status.SKIPPED,
                            ApiMessages.BULK_TRANSFER_SKIPPED.getMessage(), null));
                } else {
                    val transaction = executeTransfer(sourceAccount, validatedTargets.get(index), item.amount());
                    results.set(index, itemResult(index, item, Status.SUCCESS,
                            ApiMessages.BULK_TRANSFER_SUCCESS.getMessage(), transaction.getId()));
                }
            }
        }

        val response = BulkFundTransferResponse.of(atomic, results);
        log.info("Bulk transfer from account {}: status={}, succeeded={}, failed={}, skipped={}",
                sourceAccountNumber, response.status(), response.succeeded(), response.failed(), response.skipped());
        return response;
    }

    private void validateBatchSize(List<BulkTransferItem> transfers) {
        if (transfers == null || transfers.isEmpty()) {
            throw new FundTransferException(ApiMessages.BULK_TRANSFER_EMPTY_ERROR.getMessage());
        }

        if (transfers.size() > MAX_BULK_TRANSFER_SIZE) {
            throw new FundTransferException(
                    String.format(ApiMessages.BULK_TRANSFER_SIZE_EXCEEDED_ERROR.getMessage(), MAX_BULK_TRANSFER_SIZE));
        }
    }

    private static BulkTransferItemResult itemResult(int index, BulkTransferItem item, Status status,
            String message, Long transactionId) {
        return new BulkTransferItemResult(
                index,
                item == null ? null : item.targetAccountNumber(),
                item == null ? null : item.amount(),
                status,
                message,
                transactionId);
    }

    private Account validateTransfer(Account sourceAccount, String targetAccountNumber, double amount,
            double availableBalance) {
        validateAmount(amount);

        if (sourceAccount.getAccountNumber().equals(targetAccountNumber)) {
            throw new FundTransferException(ApiMessages.CASH_TRANSFER_SAME_ACCOUNT_ERROR.getMessage());
        }

        val targetAccount = accountRepository.findByAccountNumber(targetAccountNumber);
        if (targetAccount == null) {
            throw new NotFoundException(ApiMessages.ACCOUNT_NOT_FOUND.getMessage());
        }

        if (availableBalance < amount) {
            throw new InsufficientBalanceException(ApiMessages.BALANCE_INSUFFICIENT_ERROR.getMessage());
        }

        return targetAccount;
    }

    private Transaction executeTransfer(Account sourceAccount, Account targetAccount, double amount) {
        sourceAccount.setBalance(sourceAccount.getBalance() - amount);
        accountRepository.save(sourceAccount);

        targetAccount.setBalance(targetAccount.getBalance() + amount);
        accountRepository.save(targetAccount);

        val transaction = new Transaction();
        transaction.setAmount(amount);
        transaction.setTransactionType(TransactionType.CASH_TRANSFER);
        transaction.setTransactionDate(new Date());
        transaction.setSourceAccount(sourceAccount);
        transaction.setTargetAccount(targetAccount);
        return transactionRepository.save(transaction);
    }

}
