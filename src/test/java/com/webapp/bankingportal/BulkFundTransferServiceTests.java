package com.webapp.bankingportal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.webapp.bankingportal.dto.BulkFundTransferResponse.BatchStatus;
import com.webapp.bankingportal.dto.BulkTransferItem;
import com.webapp.bankingportal.dto.BulkTransferItemResult.Status;
import com.webapp.bankingportal.exception.FundTransferException;
import com.webapp.bankingportal.exception.UnauthorizedException;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.TransactionRepository;
import com.webapp.bankingportal.util.ApiMessages;

import lombok.val;

public class BulkFundTransferServiceTests extends BaseTest {

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    TransactionRepository transactionRepository;

    private double balanceOf(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).getBalance();
    }

    private int transactionCountOf(String accountNumber) {
        return transactionRepository
                .findBySourceAccount_AccountNumberOrTargetAccount_AccountNumber(accountNumber, accountNumber)
                .size();
    }

    @Test
    public void test_bulk_transfer_all_valid() {
        val source = createAccountWithInitialBalance(1000.0);
        val target1 = createAccount().get("accountNumber");
        val target2 = createAccount().get("accountNumber");

        val response = accountService.bulkFundTransfer(source.get("accountNumber"), source.get("pin"),
                List.of(new BulkTransferItem(target1, 300.0), new BulkTransferItem(target2, 200.0)), false);

        Assertions.assertEquals(BatchStatus.COMPLETED, response.status());
        Assertions.assertEquals(2, response.total());
        Assertions.assertEquals(2, response.succeeded());
        Assertions.assertEquals(0, response.failed());
        Assertions.assertEquals(500.0, response.totalAmountTransferred(), 0.01);
        response.results().forEach(result -> {
            Assertions.assertEquals(Status.SUCCESS, result.status());
            Assertions.assertNotNull(result.transactionId());
        });

        Assertions.assertEquals(500.0, balanceOf(source.get("accountNumber")), 0.01);
        Assertions.assertEquals(300.0, balanceOf(target1), 0.01);
        Assertions.assertEquals(200.0, balanceOf(target2), 0.01);
    }

    @Test
    public void test_bulk_transfer_partial_failure_applies_only_valid_items() {
        val source = createAccountWithInitialBalance(1000.0);
        val sourceAccountNumber = source.get("accountNumber");
        val target = createAccount().get("accountNumber");

        val transfers = Arrays.asList(
                new BulkTransferItem(target, 200.0),
                new BulkTransferItem(getRandomAccountNumber(), 100.0),
                new BulkTransferItem(target, 150.0),
                new BulkTransferItem(sourceAccountNumber, 100.0),
                null,
                new BulkTransferItem(target, 5000.0),
                new BulkTransferItem(target, 300.0));

        val response = accountService.bulkFundTransfer(sourceAccountNumber, source.get("pin"), transfers, false);

        Assertions.assertEquals(BatchStatus.PARTIALLY_COMPLETED, response.status());
        Assertions.assertEquals(7, response.total());
        Assertions.assertEquals(2, response.succeeded());
        Assertions.assertEquals(5, response.failed());
        Assertions.assertEquals(0, response.skipped());

        val results = response.results();
        for (int i = 0; i < results.size(); i++) {
            Assertions.assertEquals(i, results.get(i).index());
        }

        Assertions.assertEquals(Status.SUCCESS, results.get(0).status());
        Assertions.assertEquals(ApiMessages.ACCOUNT_NOT_FOUND.getMessage(), results.get(1).message());
        Assertions.assertEquals(ApiMessages.AMOUNT_NOT_MULTIPLE_OF_100_ERROR.getMessage(), results.get(2).message());
        Assertions.assertEquals(ApiMessages.CASH_TRANSFER_SAME_ACCOUNT_ERROR.getMessage(), results.get(3).message());
        Assertions.assertEquals(ApiMessages.BULK_TRANSFER_ITEM_NULL_ERROR.getMessage(), results.get(4).message());
        Assertions.assertEquals(ApiMessages.BALANCE_INSUFFICIENT_ERROR.getMessage(), results.get(5).message());
        Assertions.assertEquals(Status.SUCCESS, results.get(6).status());
        results.subList(1, 6).forEach(result -> {
            Assertions.assertEquals(Status.FAILED, result.status());
            Assertions.assertNull(result.transactionId());
        });

        Assertions.assertEquals(500.0, balanceOf(sourceAccountNumber), 0.01);
        Assertions.assertEquals(500.0, balanceOf(target), 0.01);
        Assertions.assertEquals(3, transactionCountOf(sourceAccountNumber));
    }

    @Test
    public void test_bulk_transfer_uses_running_balance() {
        val source = createAccountWithInitialBalance(500.0);
        val target = createAccount().get("accountNumber");

        val response = accountService.bulkFundTransfer(source.get("accountNumber"), source.get("pin"),
                List.of(new BulkTransferItem(target, 300.0),
                        new BulkTransferItem(target, 300.0),
                        new BulkTransferItem(target, 200.0)),
                false);

        Assertions.assertEquals(BatchStatus.PARTIALLY_COMPLETED, response.status());
        Assertions.assertEquals(Status.SUCCESS, response.results().get(0).status());
        Assertions.assertEquals(Status.FAILED, response.results().get(1).status());
        Assertions.assertEquals(ApiMessages.BALANCE_INSUFFICIENT_ERROR.getMessage(),
                response.results().get(1).message());
        Assertions.assertEquals(Status.SUCCESS, response.results().get(2).status());

        Assertions.assertEquals(0.0, balanceOf(source.get("accountNumber")), 0.01);
        Assertions.assertEquals(500.0, balanceOf(target), 0.01);
    }

    @Test
    public void test_bulk_transfer_all_items_fail() {
        val source = createAccountWithInitialBalance(100.0);
        val target = createAccount().get("accountNumber");

        val response = accountService.bulkFundTransfer(source.get("accountNumber"), source.get("pin"),
                List.of(new BulkTransferItem(target, 200.0), new BulkTransferItem(getRandomAccountNumber(), 100.0)),
                false);

        Assertions.assertEquals(BatchStatus.FAILED, response.status());
        Assertions.assertEquals(2, response.failed());
        Assertions.assertEquals(0.0, response.totalAmountTransferred(), 0.01);
        Assertions.assertEquals(100.0, balanceOf(source.get("accountNumber")), 0.01);
        Assertions.assertEquals(0.0, balanceOf(target), 0.01);
    }

    @Test
    public void test_atomic_bulk_transfer_all_valid() {
        val source = createAccountWithInitialBalance(1000.0);
        val target1 = createAccount().get("accountNumber");
        val target2 = createAccount().get("accountNumber");

        val response = accountService.bulkFundTransfer(source.get("accountNumber"), source.get("pin"),
                List.of(new BulkTransferItem(target1, 600.0), new BulkTransferItem(target2, 400.0)), true);

        Assertions.assertEquals(BatchStatus.COMPLETED, response.status());
        Assertions.assertTrue(response.atomic());
        Assertions.assertEquals(2, response.succeeded());
        response.results().forEach(result -> Assertions.assertNotNull(result.transactionId()));

        Assertions.assertEquals(0.0, balanceOf(source.get("accountNumber")), 0.01);
        Assertions.assertEquals(600.0, balanceOf(target1), 0.01);
        Assertions.assertEquals(400.0, balanceOf(target2), 0.01);
    }

    @Test
    public void test_atomic_bulk_transfer_with_one_failure_applies_nothing() {
        val source = createAccountWithInitialBalance(1000.0);
        val sourceAccountNumber = source.get("accountNumber");
        val target = createAccount().get("accountNumber");
        val transactionsBefore = transactionCountOf(sourceAccountNumber);

        val response = accountService.bulkFundTransfer(sourceAccountNumber, source.get("pin"),
                List.of(new BulkTransferItem(target, 200.0),
                        new BulkTransferItem(getRandomAccountNumber(), 100.0),
                        new BulkTransferItem(target, 300.0)),
                true);

        Assertions.assertEquals(BatchStatus.FAILED, response.status());
        Assertions.assertEquals(0, response.succeeded());
        Assertions.assertEquals(1, response.failed());
        Assertions.assertEquals(2, response.skipped());
        Assertions.assertEquals(Status.SKIPPED, response.results().get(0).status());
        Assertions.assertEquals(ApiMessages.BULK_TRANSFER_SKIPPED.getMessage(), response.results().get(0).message());
        Assertions.assertEquals(Status.FAILED, response.results().get(1).status());
        Assertions.assertEquals(ApiMessages.ACCOUNT_NOT_FOUND.getMessage(), response.results().get(1).message());
        Assertions.assertEquals(Status.SKIPPED, response.results().get(2).status());

        Assertions.assertEquals(1000.0, balanceOf(sourceAccountNumber), 0.01);
        Assertions.assertEquals(0.0, balanceOf(target), 0.01);
        Assertions.assertEquals(transactionsBefore, transactionCountOf(sourceAccountNumber));
    }

    @Test
    public void test_atomic_bulk_transfer_exceeding_balance_in_aggregate_applies_nothing() {
        val source = createAccountWithInitialBalance(500.0);
        val target = createAccount().get("accountNumber");

        val response = accountService.bulkFundTransfer(source.get("accountNumber"), source.get("pin"),
                List.of(new BulkTransferItem(target, 300.0), new BulkTransferItem(target, 300.0)), true);

        Assertions.assertEquals(BatchStatus.FAILED, response.status());
        Assertions.assertEquals(Status.SKIPPED, response.results().get(0).status());
        Assertions.assertEquals(Status.FAILED, response.results().get(1).status());
        Assertions.assertEquals(ApiMessages.BALANCE_INSUFFICIENT_ERROR.getMessage(),
                response.results().get(1).message());

        Assertions.assertEquals(500.0, balanceOf(source.get("accountNumber")), 0.01);
        Assertions.assertEquals(0.0, balanceOf(target), 0.01);
    }

    @Test
    public void test_bulk_transfer_with_invalid_pin_rejects_whole_batch() {
        val source = createAccountWithInitialBalance(1000.0);
        val target = createAccount().get("accountNumber");

        Assertions.assertThrows(UnauthorizedException.class, () -> accountService.bulkFundTransfer(
                source.get("accountNumber"), getRandomPin() + "0",
                List.of(new BulkTransferItem(target, 100.0)), false));

        Assertions.assertEquals(1000.0, balanceOf(source.get("accountNumber")), 0.01);
        Assertions.assertEquals(0.0, balanceOf(target), 0.01);
    }

    @Test
    public void test_bulk_transfer_with_empty_batch() {
        val source = createAccountWithInitialBalance(1000.0);

        val emptyError = Assertions.assertThrows(FundTransferException.class, () -> accountService
                .bulkFundTransfer(source.get("accountNumber"), source.get("pin"), Collections.emptyList(), false));
        Assertions.assertEquals(ApiMessages.BULK_TRANSFER_EMPTY_ERROR.getMessage(), emptyError.getMessage());

        val nullError = Assertions.assertThrows(FundTransferException.class, () -> accountService
                .bulkFundTransfer(source.get("accountNumber"), source.get("pin"), null, false));
        Assertions.assertEquals(ApiMessages.BULK_TRANSFER_EMPTY_ERROR.getMessage(), nullError.getMessage());
    }

    @Test
    public void test_bulk_transfer_exceeding_max_batch_size() {
        val source = createAccountWithInitialBalance(1000.0);
        val target = createAccount().get("accountNumber");

        val transfers = new ArrayList<BulkTransferItem>();
        for (int i = 0; i < 51; i++) {
            transfers.add(new BulkTransferItem(target, 100.0));
        }

        val error = Assertions.assertThrows(FundTransferException.class, () -> accountService
                .bulkFundTransfer(source.get("accountNumber"), source.get("pin"), transfers, false));
        Assertions.assertEquals(String.format(ApiMessages.BULK_TRANSFER_SIZE_EXCEEDED_ERROR.getMessage(), 50),
                error.getMessage());
        Assertions.assertEquals(1000.0, balanceOf(source.get("accountNumber")), 0.01);
    }

}
