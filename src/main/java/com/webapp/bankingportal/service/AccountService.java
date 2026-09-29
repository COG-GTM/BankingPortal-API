package com.webapp.bankingportal.service;

import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.User;

public interface AccountService {

	public Account createAccount(User user);
	public boolean isPinCreated(String accountNumber) ;
	public void createPin(String accountNumber, String password, String pin) ;
	public void updatePin(String accountNumber, String oldPIN, String password, String newPIN);
	public void cashDeposit(String accountNumber, String pin, double amount);
	public void cashWithdrawal(String accountNumber, String pin, double amount);
	public void fundTransfer(String sourceAccountNumber, String targetAccountNumber, String pin, double amount);

	/**
	 * Runs the PIN, amount and target-account checks of a fund transfer without
	 * moving any money. Balance is not checked.
	 */
	public void validateFundTransfer(String sourceAccountNumber, String targetAccountNumber, String pin, double amount);

	/**
	 * Transfers funds on behalf of a schedule the account holder already
	 * authorized with their PIN. Must not be exposed to request handlers.
	 */
	public void executeScheduledFundTransfer(String sourceAccountNumber, String targetAccountNumber, double amount);
	
	
}
