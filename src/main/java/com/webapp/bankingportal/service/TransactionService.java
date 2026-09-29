package com.webapp.bankingportal.service;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.webapp.bankingportal.dto.PagedResponse;
import com.webapp.bankingportal.dto.TransactionDTO;

public interface TransactionService {

	List<TransactionDTO> getAllTransactionsByAccountNumber(String accountNumber, Sort sort);
	PagedResponse<TransactionDTO> getTransactionsByAccountNumber(String accountNumber, Pageable pageable);
	void sendBankStatementByEmail(String accountNumber, Sort sort);

}
