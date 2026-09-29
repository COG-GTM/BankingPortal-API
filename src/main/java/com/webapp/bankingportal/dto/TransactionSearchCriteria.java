package com.webapp.bankingportal.dto;

import java.time.LocalDate;
import java.util.List;

import com.webapp.bankingportal.entity.TransactionType;

public record TransactionSearchCriteria(
        LocalDate startDate,
        LocalDate endDate,
        List<TransactionType> transactionTypes,
        Double minAmount,
        Double maxAmount) {
}
