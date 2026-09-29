package com.webapp.bankingportal.service;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.webapp.bankingportal.dto.PagedResponse;
import com.webapp.bankingportal.dto.TransactionDTO;
import com.webapp.bankingportal.exception.NotFoundException;
import com.webapp.bankingportal.mapper.TransactionMapper;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.TransactionRepository;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.PaginationUtil;

import lombok.RequiredArgsConstructor;
import lombok.val;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final EmailService emailService;
    private final AccountRepository accountRepository;

    @Override
    public PagedResponse<TransactionDTO> getTransactionsByAccountNumber(String accountNumber, Pageable pageable) {
        val page = transactionRepository
                .findBySourceAccount_AccountNumberOrTargetAccount_AccountNumber(accountNumber, accountNumber, pageable);

        return PagedResponse.from(page, transactionMapper::toDto, PaginationUtil.describe(pageable.getSort()));
    }

    @Override
    public void sendBankStatementByEmail(String accountNumber, Sort sort) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Account number must not be null or empty");
        }

        val account = accountRepository.findByAccountNumber(accountNumber);
        if (account == null || account.getUser() == null) {
            throw new NotFoundException(ApiMessages.ACCOUNT_NOT_FOUND.getMessage());
        }

        val transactions = getAllTransactionsByAccountNumber(accountNumber, sort);

        val sb = new StringBuilder();
        sb.append("Bank Statement for Account: ").append(accountNumber).append("\n\n");

        for (TransactionDTO txn : transactions) {
            sb.append("Date: ").append(txn.getTransactionDate())
                    .append(", Type: ").append(txn.getTransactionType())
                    .append(", Amount: ").append(txn.getAmount())
                    .append("\n");
        }

        emailService.sendEmail(account.getUser().getEmail(), "Your Bank Statement", sb.toString());
    }

    @Override
    public List<TransactionDTO> getAllTransactionsByAccountNumber(String accountNumber, Sort sort) {
        return transactionRepository
                .findBySourceAccount_AccountNumberOrTargetAccount_AccountNumber(accountNumber, accountNumber, sort)
                .stream()
                .map(transactionMapper::toDto)
                .toList();
    }

}
