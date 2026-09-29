package com.webapp.bankingportal.service;

import java.util.List;

import com.webapp.bankingportal.dto.TransactionDTO;
import com.webapp.bankingportal.entity.Account;

public interface TransactionService {

	List<TransactionDTO> getAllTransactionsByAccountNumber(String accountNumber);
	void sendBankStatementByEmail(String accountNumber);
	void validateDailyTransferLimit(Account sourceAccount, double amount);

}
