package com.webapp.bankingportal.service;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.webapp.bankingportal.dto.TransactionDTO;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.TransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.val;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTABLE_PROPERTIES = Set.of("id", "amount", "transactionType", "transactionDate");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("transactionDate"), Sort.Order.desc("id"));

    private final TransactionRepository transactionRepository;
    private final EmailService emailService;
    private final AccountRepository accountRepository;

    @Override
    public List<TransactionDTO> getAllTransactionsByAccountNumber(String accountNumber) {
        return accountRepository.findIdByAccountNumber(accountNumber)
                .map(accountId -> transactionRepository.findHistoryByAccountId(accountId, DEFAULT_SORT))
                .orElseGet(List::of);
    }

    @Override
    public Page<TransactionDTO> getTransactionHistory(String accountNumber, Pageable pageable) {
        val pageRequest = toPageRequest(pageable);
        return accountRepository.findIdByAccountNumber(accountNumber)
                .map(accountId -> transactionRepository.findHistoryByAccountId(accountId, pageRequest))
                .orElseGet(() -> Page.empty(pageRequest));
    }

    private static PageRequest toPageRequest(Pageable pageable) {
        var sort = pageable.getSort();
        for (val order : sort) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new IllegalArgumentException("Unsupported sort property: " + order.getProperty());
            }
        }

        if (sort.isUnsorted()) {
            sort = DEFAULT_SORT;
        } else if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by(Sort.Order.desc("id")));
        }

        if (pageable.isUnpaged()) {
            return PageRequest.of(0, MAX_PAGE_SIZE, sort);
        }

        return PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE), sort);
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

}
