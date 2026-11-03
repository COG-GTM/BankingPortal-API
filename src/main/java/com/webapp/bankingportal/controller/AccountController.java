package com.webapp.bankingportal.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.webapp.bankingportal.dto.AmountRequest;
import com.webapp.bankingportal.dto.FundTransferRequest;
import com.webapp.bankingportal.dto.PinRequest;
import com.webapp.bankingportal.dto.PinUpdateRequest;
import com.webapp.bankingportal.service.AccountService;
import com.webapp.bankingportal.service.IdempotencyGuard;
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

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final IdempotencyGuard idempotencyGuard;

    @GetMapping("/pin/check")
    public ResponseEntity<String> checkAccountPIN() {
        val isPINValid = accountService.isPinCreated(LoggedinUser.getAccountNumber());
        val response = isPINValid ? ApiMessages.PIN_CREATED.getMessage()
                : ApiMessages.PIN_NOT_CREATED.getMessage();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/pin/create")
    public ResponseEntity<String> createPIN(@RequestBody PinRequest pinRequest,
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey) {
        return idempotencyGuard.execute("/api/account/pin/create", idempotencyKey, () -> {
            accountService.createPin(
                    LoggedinUser.getAccountNumber(),
                    pinRequest.password(),
                    pinRequest.pin());
            return ResponseEntity.ok(ApiMessages.PIN_CREATION_SUCCESS.getMessage());
        }, pinRequest.password(), pinRequest.pin());
    }

    @PostMapping("/pin/update")
    public ResponseEntity<String> updatePIN(@RequestBody PinUpdateRequest pinUpdateRequest,
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey) {
        return idempotencyGuard.execute("/api/account/pin/update", idempotencyKey, () -> {
            accountService.updatePin(
                    LoggedinUser.getAccountNumber(),
                    pinUpdateRequest.oldPin(),
                    pinUpdateRequest.password(),
                    pinUpdateRequest.newPin());
            return ResponseEntity.ok(ApiMessages.PIN_UPDATE_SUCCESS.getMessage());
        }, pinUpdateRequest.oldPin(), pinUpdateRequest.password(), pinUpdateRequest.newPin());
    }

    @PostMapping("/deposit")
    public ResponseEntity<String> cashDeposit(@RequestBody AmountRequest amountRequest,
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey) {
        return idempotencyGuard.execute("/api/account/deposit", idempotencyKey, () -> {
            accountService.cashDeposit(
                    LoggedinUser.getAccountNumber(),
                    amountRequest.pin(),
                    amountRequest.amount());
            return ResponseEntity.ok(ApiMessages.CASH_DEPOSIT_SUCCESS.getMessage());
        }, amountRequest.pin(), amountRequest.amount());
    }

    @PostMapping("/withdraw")
    public ResponseEntity<String> cashWithdrawal(@RequestBody AmountRequest amountRequest,
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey) {
        return idempotencyGuard.execute("/api/account/withdraw", idempotencyKey, () -> {
            accountService.cashWithdrawal(
                    LoggedinUser.getAccountNumber(),
                    amountRequest.pin(),
                    amountRequest.amount());
            return ResponseEntity.ok(ApiMessages.CASH_WITHDRAWAL_SUCCESS.getMessage());
        }, amountRequest.pin(), amountRequest.amount());
    }

    @PostMapping("/fund-transfer")
    public ResponseEntity<String> fundTransfer(@RequestBody FundTransferRequest fundTransferRequest,
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey) {
        return idempotencyGuard.execute("/api/account/fund-transfer", idempotencyKey, () -> {
            accountService.fundTransfer(
                    LoggedinUser.getAccountNumber(),
                    fundTransferRequest.targetAccountNumber(),
                    fundTransferRequest.pin(),
                    fundTransferRequest.amount());
            return ResponseEntity.ok(ApiMessages.CASH_TRANSFER_SUCCESS.getMessage());
        }, fundTransferRequest.targetAccountNumber(), fundTransferRequest.pin(),
                fundTransferRequest.amount());
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
