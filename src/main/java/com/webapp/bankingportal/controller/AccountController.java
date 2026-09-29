package com.webapp.bankingportal.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.cache.annotation.Cacheable;
import com.webapp.bankingportal.dto.AmountRequest;
import com.webapp.bankingportal.dto.FundTransferRequest;
import com.webapp.bankingportal.dto.PinRequest;
import com.webapp.bankingportal.dto.PinUpdateRequest;
import com.webapp.bankingportal.dto.TransactionDTO;
import com.webapp.bankingportal.service.AccountService;
import com.webapp.bankingportal.service.TransactionService;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.JsonUtil;
import com.webapp.bankingportal.util.LoggedinUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import lombok.val;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
@Tag(name = "Account", description = "PIN management, cash operations, fund transfers and transaction history")
public class AccountController {

    private static final String IDEMPOTENCY_NOTE = " Responses are cached per account and request body, so replaying an"
            + " identical request returns the cached response without executing the operation again.";
    private static final String TEXT = "text/plain";
    private static final String JSON = "application/json";

    private final AccountService accountService;
    private final TransactionService transactionService;

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

    @Operation(summary = "Deposit cash", description = "Deposits cash into the authenticated user's account and records a"
            + " CASH_DEPOSIT transaction. The account is taken from the JWT; `accountNumber` in the body is ignored."
            + " Amount must be > 0, a multiple of 100 and at most 100,000." + IDEMPOTENCY_NOTE)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = JSON,
            schema = @Schema(implementation = AmountRequest.class),
            examples = @ExampleObject(name = "deposit", description = "Deposit 500",
                    value = "{\"pin\": \"1234\", \"amount\": 500}")))
    @ApiResponse(responseCode = "200", description = "Cash deposited", content = @Content(mediaType = JSON,
            examples = @ExampleObject(value = "{\"msg\": \"Cash deposited successfully\"}")))
    @ApiResponse(responseCode = "400", description = "Invalid amount", content = @Content(mediaType = TEXT,
            examples = {
                    @ExampleObject(name = "notPositive", value = "Amount must be greater than 0"),
                    @ExampleObject(name = "notMultipleOf100", value = "Amount must be in multiples of 100"),
                    @ExampleObject(name = "tooLarge", value = "Amount cannot be greater than 100,000") }))
    @ApiResponse(responseCode = "401", ref = "#/components/responses/PinOrAuthError")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/AccountNotFound")
    @PostMapping("/deposit")
    @Cacheable(value = "idempotency", key = "T(com.webapp.bankingportal.util.LoggedinUser).getAccountNumber() + ':' + '/api/account/deposit' + ':' + (#amountRequest).hashCode()")
    public ResponseEntity<String> cashDeposit(@RequestBody AmountRequest amountRequest) {
        accountService.cashDeposit(
                LoggedinUser.getAccountNumber(),
                amountRequest.pin(),
                amountRequest.amount());

        return ResponseEntity.ok(ApiMessages.CASH_DEPOSIT_SUCCESS.getMessage());
    }

    @Operation(summary = "Withdraw cash", description = "Withdraws cash from the authenticated user's account and records a"
            + " CASH_WITHDRAWAL transaction. The account is taken from the JWT; `accountNumber` in the body is ignored."
            + " Amount must be > 0, a multiple of 100, at most 100,000 and not exceed the balance." + IDEMPOTENCY_NOTE)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = JSON,
            schema = @Schema(implementation = AmountRequest.class),
            examples = @ExampleObject(name = "withdraw", description = "Withdraw 200",
                    value = "{\"pin\": \"1234\", \"amount\": 200}")))
    @ApiResponse(responseCode = "200", description = "Cash withdrawn", content = @Content(mediaType = JSON,
            examples = @ExampleObject(value = "{\"msg\": \"Cash withdrawn successfully\"}")))
    @ApiResponse(responseCode = "400", description = "Invalid amount or insufficient balance",
            content = @Content(mediaType = TEXT, examples = {
                    @ExampleObject(name = "insufficientBalance", value = "Insufficient balance"),
                    @ExampleObject(name = "notPositive", value = "Amount must be greater than 0"),
                    @ExampleObject(name = "notMultipleOf100", value = "Amount must be in multiples of 100"),
                    @ExampleObject(name = "tooLarge", value = "Amount cannot be greater than 100,000") }))
    @ApiResponse(responseCode = "401", ref = "#/components/responses/PinOrAuthError")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/AccountNotFound")
    @PostMapping("/withdraw")
    @Cacheable(value = "idempotency", key = "T(com.webapp.bankingportal.util.LoggedinUser).getAccountNumber() + ':' + '/api/account/withdraw' + ':' + (#amountRequest).hashCode()")
    public ResponseEntity<String> cashWithdrawal(@RequestBody AmountRequest amountRequest) {
        accountService.cashWithdrawal(
                LoggedinUser.getAccountNumber(),
                amountRequest.pin(),
                amountRequest.amount());

        return ResponseEntity.ok(ApiMessages.CASH_WITHDRAWAL_SUCCESS.getMessage());
    }

    @Operation(summary = "Transfer funds", description = "Transfers funds from the authenticated user's account to"
            + " `targetAccountNumber` and records a CASH_TRANSFER transaction. The source account is taken from the JWT;"
            + " `sourceAccountNumber` in the body is ignored. Amount must be > 0, a multiple of 100, at most 100,000"
            + " and not exceed the source balance." + IDEMPOTENCY_NOTE)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = JSON,
            schema = @Schema(implementation = FundTransferRequest.class),
            examples = @ExampleObject(name = "transfer",
                    description = "Transfer 1,000 to account b7e2f1", value = "{\"targetAccountNumber\": \"b7e2f1\", \"amount\": 1000, \"pin\": \"1234\"}")))
    @ApiResponse(responseCode = "200", description = "Funds transferred", content = @Content(mediaType = JSON,
            examples = @ExampleObject(value = "{\"msg\": \"Fund transferred successfully\"}")))
    @ApiResponse(responseCode = "400", description = "Invalid amount, insufficient balance or same source and target",
            content = @Content(mediaType = TEXT, examples = {
                    @ExampleObject(name = "sameAccount", value = "Source and target account cannot be the same"),
                    @ExampleObject(name = "insufficientBalance", value = "Insufficient balance"),
                    @ExampleObject(name = "notPositive", value = "Amount must be greater than 0"),
                    @ExampleObject(name = "notMultipleOf100", value = "Amount must be in multiples of 100"),
                    @ExampleObject(name = "tooLarge", value = "Amount cannot be greater than 100,000") }))
    @ApiResponse(responseCode = "401", ref = "#/components/responses/PinOrAuthError")
    @ApiResponse(responseCode = "404", description = "Source or target account does not exist",
            content = @Content(mediaType = TEXT, examples = @ExampleObject(value = "Account does not exist")))
    @PostMapping("/fund-transfer")
    @Cacheable(value = "idempotency", key = "T(com.webapp.bankingportal.util.LoggedinUser).getAccountNumber() + ':' + '/api/account/fund-transfer' + ':' + (#fundTransferRequest).hashCode()")
    public ResponseEntity<String> fundTransfer(@RequestBody FundTransferRequest fundTransferRequest) {
        accountService.fundTransfer(
                LoggedinUser.getAccountNumber(),
                fundTransferRequest.targetAccountNumber(),
                fundTransferRequest.pin(),
                fundTransferRequest.amount());

        return ResponseEntity.ok(ApiMessages.CASH_TRANSFER_SUCCESS.getMessage());
    }

    @Operation(summary = "List transactions", description = "Returns every transaction where the authenticated user's"
            + " account is the source or the target, newest first. `targetAccountNumber` is `N/A` for deposits and"
            + " withdrawals; `transactionDate` is epoch milliseconds.")
    @ApiResponse(responseCode = "200", description = "Transaction history", content = @Content(mediaType = JSON,
            array = @ArraySchema(schema = @Schema(implementation = TransactionDTO.class)),
            examples = @ExampleObject(value = """
                    [
                      {"id": 42, "amount": 1000.0, "transactionType": "CASH_TRANSFER",
                       "transactionDate": 1727600000000, "sourceAccountNumber": "a1c3d9", "targetAccountNumber": "b7e2f1"},
                      {"id": 41, "amount": 500.0, "transactionType": "CASH_DEPOSIT",
                       "transactionDate": 1727590000000, "sourceAccountNumber": "a1c3d9", "targetAccountNumber": "N/A"}
                    ]""")))
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    @GetMapping("/transactions")
    public ResponseEntity<String> getAllTransactionsByAccountNumber() {
        val transactions = transactionService
                .getAllTransactionsByAccountNumber(LoggedinUser.getAccountNumber());
        return ResponseEntity.ok(JsonUtil.toJson(transactions));
    }

    @Operation(summary = "Email bank statement", description = "Emails a plain-text statement of all transactions for"
            + " the authenticated user's account to the account holder's registered email address.")
    @ApiResponse(responseCode = "200", description = "Statement sent", content = @Content(mediaType = JSON,
            examples = @ExampleObject(value = "{\"message\": \"Bank statement sent to your email.\"}")))
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    @ApiResponse(responseCode = "500", description = "Statement could not be generated or sent",
            content = @Content(mediaType = JSON, examples = @ExampleObject(
                    value = "{\"message\": \"An unexpected error occurred: Mail server connection failed\"}")))
    @GetMapping("/send-statement")
    public ResponseEntity<String> sendBankStatement() {
        String accountNumber = LoggedinUser.getAccountNumber(); // Get logged-in user account
        transactionService.sendBankStatementByEmail(accountNumber);
        return ResponseEntity.ok("{\"message\": \"Bank statement sent to your email.\"}");
    }

}
