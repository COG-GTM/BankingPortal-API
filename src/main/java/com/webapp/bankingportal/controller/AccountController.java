package com.webapp.bankingportal.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.cache.annotation.Cacheable;
import com.webapp.bankingportal.dto.AmountRequest;
import com.webapp.bankingportal.dto.FundTransferRequest;
import com.webapp.bankingportal.dto.PinRequest;
import com.webapp.bankingportal.dto.PinUpdateRequest;
import com.webapp.bankingportal.service.AccountService;
import com.webapp.bankingportal.service.IdempotencyService;
import com.webapp.bankingportal.service.TransactionService;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.JsonUtil;
import com.webapp.bankingportal.util.LoggedinUser;

import lombok.RequiredArgsConstructor;
import lombok.val;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final IdempotencyService idempotencyService;

    @GetMapping("/pin/check")
    public ResponseEntity<String> checkAccountPIN() {
        val isPINValid = accountService.isPinCreated(LoggedinUser.getAccountNumber());
        val response = isPINValid ? ApiMessages.PIN_CREATED.getMessage()
                : ApiMessages.PIN_NOT_CREATED.getMessage();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/pin/create")
    @Cacheable(value = "idempotency", key = "T(com.webapp.bankingportal.util.LoggedinUser).getAccountNumber() + ':' + '/api/account/pin/create' + ':' + (#pinRequest).hashCode()")
    public ResponseEntity<String> createPIN(@RequestBody PinRequest pinRequest) {
        accountService.createPin(
                LoggedinUser.getAccountNumber(),
                pinRequest.password(),
                pinRequest.pin());

        return ResponseEntity.ok(ApiMessages.PIN_CREATION_SUCCESS.getMessage());
    }

    @PostMapping("/pin/update")
    @Cacheable(value = "idempotency", key = "T(com.webapp.bankingportal.util.LoggedinUser).getAccountNumber() + ':' + '/api/account/pin/update' + ':' + (#pinUpdateRequest).hashCode()")
    public ResponseEntity<String> updatePIN(@RequestBody PinUpdateRequest pinUpdateRequest) {
        accountService.updatePin(
                LoggedinUser.getAccountNumber(),
                pinUpdateRequest.oldPin(),
                pinUpdateRequest.password(),
                pinUpdateRequest.newPin());

        return ResponseEntity.ok(ApiMessages.PIN_UPDATE_SUCCESS.getMessage());
    }

    @PostMapping("/deposit")
    public ResponseEntity<String> cashDeposit(
            @RequestHeader(value = IdempotencyService.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestBody AmountRequest amountRequest) {
        val accountNumber = LoggedinUser.getAccountNumber();
        return idempotencyService.execute(accountNumber, "/api/account/deposit", idempotencyKey, amountRequest, () -> {
            accountService.cashDeposit(accountNumber, amountRequest.pin(), amountRequest.amount());
            return ResponseEntity.ok(ApiMessages.CASH_DEPOSIT_SUCCESS.getMessage());
        });
    }

    @PostMapping("/withdraw")
    public ResponseEntity<String> cashWithdrawal(
            @RequestHeader(value = IdempotencyService.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestBody AmountRequest amountRequest) {
        val accountNumber = LoggedinUser.getAccountNumber();
        return idempotencyService.execute(accountNumber, "/api/account/withdraw", idempotencyKey, amountRequest, () -> {
            accountService.cashWithdrawal(accountNumber, amountRequest.pin(), amountRequest.amount());
            return ResponseEntity.ok(ApiMessages.CASH_WITHDRAWAL_SUCCESS.getMessage());
        });
    }

    @PostMapping("/fund-transfer")
    public ResponseEntity<String> fundTransfer(
            @RequestHeader(value = IdempotencyService.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestBody FundTransferRequest fundTransferRequest) {
        val accountNumber = LoggedinUser.getAccountNumber();
        return idempotencyService.execute(accountNumber, "/api/account/fund-transfer", idempotencyKey,
                fundTransferRequest, () -> {
                    accountService.fundTransfer(
                            accountNumber,
                            fundTransferRequest.targetAccountNumber(),
                            fundTransferRequest.pin(),
                            fundTransferRequest.amount());
                    return ResponseEntity.ok(ApiMessages.CASH_TRANSFER_SUCCESS.getMessage());
                });
    }

    @GetMapping("/transactions")
    public ResponseEntity<String> getAllTransactionsByAccountNumber() {
        val transactions = transactionService
                .getAllTransactionsByAccountNumber(LoggedinUser.getAccountNumber());
        return ResponseEntity.ok(JsonUtil.toJson(transactions));
    }
    @GetMapping("/send-statement")
    public ResponseEntity<String> sendBankStatement() {
        String accountNumber = LoggedinUser.getAccountNumber(); // Get logged-in user account
        transactionService.sendBankStatementByEmail(accountNumber);
        return ResponseEntity.ok("{\"message\": \"Bank statement sent to your email.\"}");
    }

}
