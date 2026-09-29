package com.webapp.bankingportal.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import com.webapp.bankingportal.repository.AccountRepository;
import org.springframework.stereotype.Service;

import com.webapp.bankingportal.config.TransferLimitProperties;
import com.webapp.bankingportal.dto.TransactionDTO;
import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.TransactionType;
import com.webapp.bankingportal.exception.DailyTransferLimitExceededException;
import com.webapp.bankingportal.mapper.TransactionMapper;
import com.webapp.bankingportal.repository.TransactionRepository;
import com.webapp.bankingportal.util.ApiMessages;

import lombok.RequiredArgsConstructor;
import lombok.val;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final EmailService emailService;
    private final AccountRepository accountRepository;
    private final TransferLimitProperties transferLimitProperties;
    private final Clock clock;

    @Override
    public List<TransactionDTO> getAllTransactionsByAccountNumber(String accountNumber) {
        val transactions = transactionRepository
                .findBySourceAccount_AccountNumberOrTargetAccount_AccountNumber(accountNumber, accountNumber);

        val transactionDTOs = transactions.parallelStream()
                .map(transactionMapper::toDto)
                .sorted((t1, t2) -> t2.getTransactionDate().compareTo(t1.getTransactionDate()))
                .collect(Collectors.toList());

        return transactionDTOs;
    }
    public void sendBankStatementByEmail(String accountNumber) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Account number must not be null or empty");
        }
        List<TransactionDTO> transactions = getAllTransactionsByAccountNumber(accountNumber);

        StringBuilder sb = new StringBuilder();
        sb.append("Bank Statement for Account: ").append(accountNumber).append("\n\n");

        for(TransactionDTO txn : transactions) {
            sb.append("Date: ").append(txn.getTransactionDate())
                    .append(", Type: ").append(txn.getTransactionType())
                    .append(", Amount: ").append(txn.getAmount())
                    .append("\n");
        }

        val account = accountRepository.findByAccountNumber(accountNumber);
        if (account == null || account.getUser() == null) {
            // Optionally log or handle the error here
            return;
        }
        String email = account.getUser().getEmail();
        emailService.sendEmail(email, "Your Bank Statement", sb.toString());
    }

    @Override
    public void validateDailyTransferLimit(Account sourceAccount, double amount) {
        val dailyLimit = getDailyTransferLimit(sourceAccount);
        val transferredToday = getAmountTransferredToday(sourceAccount.getAccountNumber());

        if (transferredToday + amount > dailyLimit) {
            val remaining = Math.max(0, dailyLimit - transferredToday);
            throw new DailyTransferLimitExceededException(String.format(
                    ApiMessages.DAILY_TRANSFER_LIMIT_EXCEEDED_ERROR.getMessage(), dailyLimit, remaining));
        }
    }

    private double getDailyTransferLimit(Account account) {
        val accountLimit = account.getDailyTransferLimit();
        return accountLimit != null ? accountLimit : transferLimitProperties.getDailyLimit();
    }

    private double getAmountTransferredToday(String accountNumber) {
        val zone = transferLimitProperties.getZone();
        val today = LocalDate.now(clock.withZone(zone));
        val startOfDay = Date.from(today.atStartOfDay(zone).toInstant());
        val startOfNextDay = Date.from(today.plusDays(1).atStartOfDay(zone).toInstant());

        return transactionRepository.sumAmountBySourceAccountAndTypeBetween(
                accountNumber, TransactionType.CASH_TRANSFER, startOfDay, startOfNextDay);
    }

}
