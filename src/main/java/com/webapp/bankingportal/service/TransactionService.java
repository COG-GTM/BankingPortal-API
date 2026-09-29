package com.webapp.bankingportal.service;

import java.util.List;

import com.webapp.bankingportal.dto.PagedResponse;
import com.webapp.bankingportal.dto.TransactionDTO;
import com.webapp.bankingportal.dto.TransactionSearchCriteria;

public interface TransactionService {

	List<TransactionDTO> getAllTransactionsByAccountNumber(String accountNumber);
	void sendBankStatementByEmail(String accountNumber);

	PagedResponse<TransactionDTO> searchTransactions(String accountNumber, TransactionSearchCriteria criteria,
			int page, int size, String sortBy, String sortDirection);

}
