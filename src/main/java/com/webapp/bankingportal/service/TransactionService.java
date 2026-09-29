package com.webapp.bankingportal.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.webapp.bankingportal.dto.TransactionDTO;

public interface TransactionService {

	List<TransactionDTO> getAllTransactionsByAccountNumber(String accountNumber);
	Page<TransactionDTO> getTransactionHistory(String accountNumber, Pageable pageable);
	void sendBankStatementByEmail(String accountNumber);

}
