package com.webapp.bankingportal;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.webapp.bankingportal.entity.ReviewStatus;
import com.webapp.bankingportal.entity.TransactionReviewFlag;
import com.webapp.bankingportal.entity.VelocityRule;
import com.webapp.bankingportal.repository.TransactionRepository;
import com.webapp.bankingportal.repository.TransactionReviewFlagRepository;

import lombok.val;

public class TransferVelocityIntegrationTests extends BaseTest {

    @Autowired
    TransactionRepository transactionRepository;

    @Autowired
    TransactionReviewFlagRepository reviewFlagRepository;

    private List<VelocityRule> flaggedRulesFor(String sourceAccountNumber) {
        return reviewFlagRepository.findAll().stream()
                .filter(flag -> flag.getTransaction().getSourceAccount().getAccountNumber()
                        .equals(sourceAccountNumber))
                .map(TransactionReviewFlag::getRule)
                .toList();
    }

    @Test
    public void test_transfer_within_thresholds_is_not_flagged() {
        val source = createAccountWithInitialBalance(100_000);
        val target = createAccount();

        accountService.fundTransfer(source.get("accountNumber"), target.get("accountNumber"), source.get("pin"),
                50_000);

        Assertions.assertTrue(flaggedRulesFor(source.get("accountNumber")).isEmpty());
    }

    @Test
    public void test_large_transfer_completes_and_is_flagged_for_review() {
        val source = createAccountWithInitialBalance(100_000);
        val target = createAccount();

        accountService.fundTransfer(source.get("accountNumber"), target.get("accountNumber"), source.get("pin"),
                50_100);

        val flags = reviewFlagRepository.findByStatus(ReviewStatus.PENDING).stream()
                .filter(flag -> flag.getTransaction().getSourceAccount().getAccountNumber()
                        .equals(source.get("accountNumber")))
                .toList();
        Assertions.assertEquals(1, flags.size());
        Assertions.assertEquals(VelocityRule.SINGLE_TRANSFER_AMOUNT, flags.get(0).getRule());
        Assertions.assertEquals(50_100, flags.get(0).getTransaction().getAmount());
        Assertions.assertNotNull(flags.get(0).getTransaction().getId());
    }

    @Test
    public void test_sixth_transfer_of_the_day_is_flagged() {
        val source = createAccountWithInitialBalance(1_000);
        val target = createAccount();

        for (int i = 0; i < 5; i++) {
            accountService.fundTransfer(source.get("accountNumber"), target.get("accountNumber"),
                    source.get("pin"), 100);
        }
        Assertions.assertTrue(flaggedRulesFor(source.get("accountNumber")).isEmpty());

        accountService.fundTransfer(source.get("accountNumber"), target.get("accountNumber"), source.get("pin"),
                100);

        Assertions.assertEquals(List.of(VelocityRule.DAILY_TRANSFER_COUNT),
                flaggedRulesFor(source.get("accountNumber")));
    }

    @Test
    public void test_cumulative_daily_amount_above_threshold_is_flagged() {
        val source = createAccountWithInitialBalance(100_000);
        accountService.cashDeposit(source.get("accountNumber"), source.get("pin"), 100_000);
        accountService.cashDeposit(source.get("accountNumber"), source.get("pin"), 100_000);
        val target = createAccount();

        for (int i = 0; i < 4; i++) {
            accountService.fundTransfer(source.get("accountNumber"), target.get("accountNumber"),
                    source.get("pin"), 50_000);
        }
        Assertions.assertTrue(flaggedRulesFor(source.get("accountNumber")).isEmpty());

        accountService.fundTransfer(source.get("accountNumber"), target.get("accountNumber"), source.get("pin"),
                100);

        Assertions.assertEquals(List.of(VelocityRule.DAILY_TRANSFER_AMOUNT),
                flaggedRulesFor(source.get("accountNumber")));
    }

    @Test
    public void test_deposits_and_withdrawals_do_not_count_toward_transfer_velocity() {
        val source = createAccountWithInitialBalance(100_000);
        for (int i = 0; i < 6; i++) {
            accountService.cashWithdrawal(source.get("accountNumber"), source.get("pin"), 100);
        }
        val target = createAccount();

        accountService.fundTransfer(source.get("accountNumber"), target.get("accountNumber"), source.get("pin"),
                100);

        Assertions.assertTrue(flaggedRulesFor(source.get("accountNumber")).isEmpty());
    }

}
