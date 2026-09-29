package com.webapp.bankingportal;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.webapp.bankingportal.dto.FundTransferRequest;
import com.webapp.bankingportal.dto.TransferIssue;
import com.webapp.bankingportal.dto.TransferPreviewResponse;
import com.webapp.bankingportal.entity.TransactionType;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.TransactionRepository;
import com.webapp.bankingportal.type.TransactionRuleViolation;
import com.webapp.bankingportal.type.TransferWarning;
import com.webapp.bankingportal.util.JsonUtil;

import lombok.val;

@TestPropertySource(properties = {
        "banking.transfer.fee.flat=2.50",
        "banking.transfer.fee.percentage=0.5",
        "banking.transfer.warning.low-balance-threshold=1000",
})
public class TransferPreviewTests extends BaseTest {

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    TransactionRepository transactionRepository;

    private double balanceOf(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).getBalance();
    }

    private int transactionCount(String accountNumber) {
        return transactionRepository
                .findBySourceAccount_AccountNumberOrTargetAccount_AccountNumber(accountNumber, accountNumber)
                .size();
    }

    @Test
    public void test_preview_reports_fee_and_resulting_balance() {
        val source = createAccountWithInitialBalance(10_000);
        val target = createAccount();

        val preview = accountService.previewFundTransfer(source.get("accountNumber"), target.get("accountNumber"),
                2_000);

        // 2000 * 0.5% = 10.00, + 2.50 flat
        Assertions.assertEquals(12.50, preview.fee());
        Assertions.assertEquals(2_012.50, preview.totalDebit());
        Assertions.assertEquals(10_000, preview.currentBalance());
        Assertions.assertEquals(7_987.50, preview.resultingBalance());
        Assertions.assertTrue(preview.executable());
        Assertions.assertTrue(preview.errors().isEmpty());
        Assertions.assertTrue(preview.warnings().isEmpty());
    }

    @Test
    public void test_preview_does_not_modify_state() {
        val source = createAccountWithInitialBalance(10_000);
        val target = createAccountWithInitialBalance(500);
        val sourceTransactions = transactionCount(source.get("accountNumber"));
        val targetTransactions = transactionCount(target.get("accountNumber"));

        accountService.previewFundTransfer(source.get("accountNumber"), target.get("accountNumber"), 2_000);

        Assertions.assertEquals(10_000, balanceOf(source.get("accountNumber")));
        Assertions.assertEquals(500, balanceOf(target.get("accountNumber")));
        Assertions.assertEquals(sourceTransactions, transactionCount(source.get("accountNumber")));
        Assertions.assertEquals(targetTransactions, transactionCount(target.get("accountNumber")));
    }

    @Test
    public void test_execute_matches_preview() {
        val source = createAccountWithInitialBalance(10_000);
        val target = createAccountWithInitialBalance(500);

        val preview = accountService.previewFundTransfer(source.get("accountNumber"), target.get("accountNumber"),
                2_000);
        accountService.fundTransfer(source.get("accountNumber"), target.get("accountNumber"), source.get("pin"),
                2_000);

        Assertions.assertEquals(preview.resultingBalance(), balanceOf(source.get("accountNumber")));
        Assertions.assertEquals(500 + preview.amount(), balanceOf(target.get("accountNumber")));

        val feeTransactions = transactionRepository
                .findBySourceAccount_AccountNumberOrTargetAccount_AccountNumber(source.get("accountNumber"),
                        source.get("accountNumber"))
                .stream()
                .filter(t -> t.getTransactionType() == TransactionType.TRANSFER_FEE)
                .toList();
        Assertions.assertEquals(1, feeTransactions.size());
        Assertions.assertEquals(preview.fee(), feeTransactions.get(0).getAmount());
    }

    @Test
    public void test_fee_counts_towards_insufficient_balance() {
        val source = createAccountWithInitialBalance(2_000);
        val target = createAccount();

        // Amount alone is covered by the balance, but amount + fee is not.
        val preview = accountService.previewFundTransfer(source.get("accountNumber"), target.get("accountNumber"),
                2_000);

        Assertions.assertFalse(preview.executable());
        Assertions.assertEquals(TransferIssue.of(TransactionRuleViolation.INSUFFICIENT_BALANCE),
                preview.errors().get(0));
        assertExecuteAgreesWithPreview(source.get("accountNumber"), target.get("accountNumber"), source.get("pin"),
                2_000, preview);
    }

    @Test
    public void test_execute_rejects_exactly_what_preview_flags() {
        val source = createAccountWithInitialBalance(5_000);
        val sourceNumber = source.get("accountNumber");
        val pin = source.get("pin");
        val targetNumber = createAccount().get("accountNumber");

        Supplier<String> unknownAccount = BaseTest::getRandomAccountNumber;

        Object[][] scenarios = {
                { targetNumber, 0.0, TransactionRuleViolation.AMOUNT_NOT_POSITIVE },
                { targetNumber, -200.0, TransactionRuleViolation.AMOUNT_NOT_POSITIVE },
                { targetNumber, 150.0, TransactionRuleViolation.AMOUNT_NOT_MULTIPLE_OF_100 },
                { targetNumber, 100_100.0, TransactionRuleViolation.AMOUNT_EXCEEDS_TRANSACTION_LIMIT },
                { sourceNumber, 100.0, TransactionRuleViolation.SAME_ACCOUNT },
                { unknownAccount.get(), 100.0, TransactionRuleViolation.TARGET_ACCOUNT_NOT_FOUND },
                { targetNumber, 10_000.0, TransactionRuleViolation.INSUFFICIENT_BALANCE },
        };

        for (val scenario : scenarios) {
            val target = (String) scenario[0];
            val amount = (double) scenario[1];
            val expected = (TransactionRuleViolation) scenario[2];

            val preview = accountService.previewFundTransfer(sourceNumber, target, amount);

            Assertions.assertFalse(preview.executable(), expected.name());
            Assertions.assertEquals(expected.name(), preview.errors().get(0).code());
            assertExecuteAgreesWithPreview(sourceNumber, target, pin, amount, preview);
        }

        Assertions.assertEquals(5_000, balanceOf(sourceNumber));
    }

    @Test
    public void test_preview_lists_all_blocking_issues() {
        val source = createAccountWithInitialBalance(100);
        val preview = accountService.previewFundTransfer(source.get("accountNumber"), getRandomAccountNumber(),
                150);

        Assertions.assertEquals(
                List.of(
                        TransactionRuleViolation.AMOUNT_NOT_MULTIPLE_OF_100.name(),
                        TransactionRuleViolation.TARGET_ACCOUNT_NOT_FOUND.name(),
                        TransactionRuleViolation.INSUFFICIENT_BALANCE.name()),
                preview.errors().stream().map(TransferIssue::code).toList());
    }

    @Test
    public void test_preview_warns_when_approaching_transaction_limit() {
        val source = createAccountWithInitialBalance(100_000);
        deposit(source, 100_000);
        val target = createAccount();

        val preview = accountService.previewFundTransfer(source.get("accountNumber"), target.get("accountNumber"),
                90_000);

        Assertions.assertTrue(preview.executable());
        Assertions.assertEquals(
                List.of(TransferIssue.of(TransferWarning.APPROACHING_TRANSACTION_LIMIT)),
                preview.warnings());
    }

    @Test
    public void test_preview_warns_on_low_resulting_balance() {
        val source = createAccountWithInitialBalance(1_500);
        val target = createAccount();

        val preview = accountService.previewFundTransfer(source.get("accountNumber"), target.get("accountNumber"),
                1_000);

        Assertions.assertTrue(preview.executable());
        Assertions.assertEquals(
                List.of(TransferIssue.of(TransferWarning.LOW_RESULTING_BALANCE)),
                preview.warnings());
    }

    @Test
    public void test_preview_endpoint_returns_preview() throws Exception {
        val userDetails = createAndLoginUserWithInitialBalance(10_000);
        val target = createAndLoginUser();

        val request = new FundTransferRequest(userDetails.get("accountNumber"), target.get("accountNumber"),
                2_000, null);

        mockMvc.perform(MockMvcRequestBuilders
                .post("/api/account/fund-transfer/preview")
                .header("Authorization", "Bearer " + userDetails.get("token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(request)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.fee").value(12.5))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalDebit").value(2012.5))
                .andExpect(MockMvcResultMatchers.jsonPath("$.resultingBalance").value(7987.5))
                .andExpect(MockMvcResultMatchers.jsonPath("$.executable").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.errors").isEmpty());

        Assertions.assertEquals(10_000, balanceOf(userDetails.get("accountNumber")));
    }

    @Test
    public void test_preview_endpoint_reports_errors_with_ok_status() throws Exception {
        val userDetails = createAndLoginUserWithInitialBalance(100);

        val request = new FundTransferRequest(userDetails.get("accountNumber"), userDetails.get("accountNumber"),
                100, null);

        mockMvc.perform(MockMvcRequestBuilders
                .post("/api/account/fund-transfer/preview")
                .header("Authorization", "Bearer " + userDetails.get("token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(request)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.executable").value(false))
                .andExpect(MockMvcResultMatchers.jsonPath("$.errors[0].code")
                        .value(TransactionRuleViolation.SAME_ACCOUNT.name()));
    }

    @Test
    public void test_preview_endpoint_requires_authentication() throws Exception {
        val request = new FundTransferRequest(getRandomAccountNumber(), getRandomAccountNumber(), 100, null);

        mockMvc.perform(MockMvcRequestBuilders
                .post("/api/account/fund-transfer/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(request)))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    private void deposit(Map<String, String> account, double amount) {
        accountService.cashDeposit(account.get("accountNumber"), account.get("pin"), amount);
    }

    private void assertExecuteAgreesWithPreview(String source, String target, String pin, double amount,
            TransferPreviewResponse preview) {
        val expected = preview.errors().get(0);
        val thrown = Assertions.assertThrows(RuntimeException.class,
                () -> accountService.fundTransfer(source, target, pin, amount));
        Assertions.assertEquals(TransactionRuleViolation.valueOf(expected.code()).toException().getClass(),
                thrown.getClass());
        Assertions.assertEquals(expected.message(), thrown.getMessage());
    }
}
