package com.webapp.bankingportal.service;

import java.util.List;
import java.util.stream.Collectors;

import com.webapp.bankingportal.repository.AccountRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.webapp.bankingportal.dto.PagedResponse;
import com.webapp.bankingportal.dto.TransactionDTO;
import com.webapp.bankingportal.dto.TransactionSearchCriteria;
import com.webapp.bankingportal.mapper.TransactionMapper;
import com.webapp.bankingportal.repository.TransactionRepository;
import com.webapp.bankingportal.repository.TransactionSpecifications;
import com.webapp.bankingportal.util.ApiMessages;

import lombok.RequiredArgsConstructor;
import lombok.val;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final List<String> SORTABLE_FIELDS = List.of("transactionDate", "amount");

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final EmailService emailService;
    private final AccountRepository accountRepository;

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
    public PagedResponse<TransactionDTO> searchTransactions(String accountNumber, TransactionSearchCriteria criteria,
            int page, int size, String sortBy, String sortDirection) {
        validateSearch(criteria, page, size, sortBy);

        val direction = Sort.Direction.fromString(sortDirection);
        val sort = Sort.by(direction, sortBy).and(Sort.by(direction, "id"));
        val pageable = PageRequest.of(page, size, sort);

        val result = transactionRepository
                .findAll(TransactionSpecifications.forAccount(accountNumber, criteria), pageable)
                .map(transactionMapper::toDto);

        return PagedResponse.from(result);
    }

    private void validateSearch(TransactionSearchCriteria criteria, int page, int size, String sortBy) {
        if (page < 0) {
            throw new IllegalArgumentException(ApiMessages.TRANSACTION_SEARCH_PAGE_ERROR.getMessage());
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    String.format(ApiMessages.TRANSACTION_SEARCH_SIZE_ERROR.getMessage(), MAX_PAGE_SIZE));
        }
        if (!SORTABLE_FIELDS.contains(sortBy)) {
            throw new IllegalArgumentException(String.format(ApiMessages.TRANSACTION_SEARCH_SORT_ERROR.getMessage(),
                    String.join(", ", SORTABLE_FIELDS)));
        }
        if (criteria.startDate() != null && criteria.endDate() != null
                && criteria.startDate().isAfter(criteria.endDate())) {
            throw new IllegalArgumentException(ApiMessages.TRANSACTION_SEARCH_DATE_RANGE_ERROR.getMessage());
        }
        if ((criteria.minAmount() != null && criteria.minAmount() < 0)
                || (criteria.maxAmount() != null && criteria.maxAmount() < 0)) {
            throw new IllegalArgumentException(ApiMessages.TRANSACTION_SEARCH_AMOUNT_NEGATIVE_ERROR.getMessage());
        }
        if (criteria.minAmount() != null && criteria.maxAmount() != null
                && criteria.minAmount() > criteria.maxAmount()) {
            throw new IllegalArgumentException(ApiMessages.TRANSACTION_SEARCH_AMOUNT_RANGE_ERROR.getMessage());
        }
    }

}
