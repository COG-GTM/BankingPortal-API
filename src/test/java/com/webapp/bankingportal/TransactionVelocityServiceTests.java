package com.webapp.bankingportal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.webapp.bankingportal.config.VelocityCheckProperties;
import com.webapp.bankingportal.dto.VelocityViolation;
import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.ReviewStatus;
import com.webapp.bankingportal.entity.Transaction;
import com.webapp.bankingportal.entity.TransactionReviewFlag;
import com.webapp.bankingportal.entity.TransactionType;
import com.webapp.bankingportal.entity.VelocityRule;
import com.webapp.bankingportal.repository.TransactionRepository;
import com.webapp.bankingportal.repository.TransactionReviewFlagRepository;
import com.webapp.bankingportal.service.TransactionVelocityServiceImpl;

import lombok.val;

@ExtendWith(MockitoExtension.class)
public class TransactionVelocityServiceTests {

    private static final String ACCOUNT = "abc123";
    private static final Instant NOW = Instant.parse("2026-03-15T10:30:00Z");

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionReviewFlagRepository reviewFlagRepository;

    private VelocityCheckProperties properties;
    private TransactionVelocityServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new VelocityCheckProperties();
        properties.setPerTransactionAmountThreshold(50_000);
        properties.setDailyAmountThreshold(200_000);
        properties.setDailyCountThreshold(5);
        properties.setZoneId("UTC");
        service = new TransactionVelocityServiceImpl(transactionRepository, reviewFlagRepository, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void givenPriorTransfersToday(Double totalAmount, long count) {
        when(transactionRepository.sumAmountBySourceAccountAndTypeInRange(eq(ACCOUNT),
                eq(TransactionType.CASH_TRANSFER), any(Date.class), any(Date.class))).thenReturn(totalAmount);
        when(transactionRepository.countBySourceAccountAndTypeInRange(eq(ACCOUNT),
                eq(TransactionType.CASH_TRANSFER), any(Date.class), any(Date.class))).thenReturn(count);
    }

    private static List<VelocityRule> rules(List<VelocityViolation> violations) {
        return violations.stream().map(VelocityViolation::rule).toList();
    }

    @Test
    public void test_transfer_below_all_thresholds_is_not_flagged() {
        givenPriorTransfersToday(null, 0);

        Assertions.assertTrue(service.evaluateTransfer(ACCOUNT, 1_000).isEmpty());
    }

    @Test
    public void test_transfer_at_per_transaction_threshold_is_not_flagged() {
        givenPriorTransfersToday(null, 0);

        Assertions.assertTrue(service.evaluateTransfer(ACCOUNT, 50_000).isEmpty());
    }

    @Test
    public void test_transfer_above_per_transaction_threshold_is_flagged() {
        givenPriorTransfersToday(null, 0);

        val violations = service.evaluateTransfer(ACCOUNT, 50_100);

        Assertions.assertEquals(List.of(VelocityRule.SINGLE_TRANSFER_AMOUNT), rules(violations));
        Assertions.assertTrue(violations.get(0).detail().contains("50100.00"));
    }

    @Test
    public void test_daily_total_reaching_threshold_is_not_flagged() {
        givenPriorTransfersToday(150_000.0, 3);

        Assertions.assertTrue(service.evaluateTransfer(ACCOUNT, 50_000).isEmpty());
    }

    @Test
    public void test_daily_total_exceeding_threshold_is_flagged() {
        givenPriorTransfersToday(150_000.0, 3);

        val violations = service.evaluateTransfer(ACCOUNT, 50_100);

        Assertions.assertEquals(
                List.of(VelocityRule.SINGLE_TRANSFER_AMOUNT, VelocityRule.DAILY_TRANSFER_AMOUNT),
                rules(violations));
    }

    @Test
    public void test_many_small_transfers_exceeding_daily_total_is_flagged() {
        givenPriorTransfersToday(199_900.0, 4);

        val violations = service.evaluateTransfer(ACCOUNT, 200);

        Assertions.assertEquals(List.of(VelocityRule.DAILY_TRANSFER_AMOUNT), rules(violations));
    }

    @Test
    public void test_daily_count_reaching_threshold_is_not_flagged() {
        givenPriorTransfersToday(400.0, 4);

        Assertions.assertTrue(service.evaluateTransfer(ACCOUNT, 100).isEmpty());
    }

    @Test
    public void test_daily_count_exceeding_threshold_is_flagged() {
        givenPriorTransfersToday(500.0, 5);

        val violations = service.evaluateTransfer(ACCOUNT, 100);

        Assertions.assertEquals(List.of(VelocityRule.DAILY_TRANSFER_COUNT), rules(violations));
        Assertions.assertTrue(violations.get(0).detail().contains("6"));
    }

    @Test
    public void test_all_rules_can_trigger_together() {
        givenPriorTransfersToday(180_000.0, 5);

        val violations = service.evaluateTransfer(ACCOUNT, 60_000);

        Assertions.assertEquals(List.of(VelocityRule.SINGLE_TRANSFER_AMOUNT,
                VelocityRule.DAILY_TRANSFER_AMOUNT, VelocityRule.DAILY_TRANSFER_COUNT), rules(violations));
    }

    @Test
    public void test_custom_thresholds_are_respected() {
        properties.setPerTransactionAmountThreshold(1_000);
        properties.setDailyAmountThreshold(2_000);
        properties.setDailyCountThreshold(1);
        givenPriorTransfersToday(1_000.0, 1);

        val violations = service.evaluateTransfer(ACCOUNT, 1_100);

        Assertions.assertEquals(List.of(VelocityRule.SINGLE_TRANSFER_AMOUNT,
                VelocityRule.DAILY_TRANSFER_AMOUNT, VelocityRule.DAILY_TRANSFER_COUNT), rules(violations));
    }

    @Test
    public void test_daily_window_is_calendar_day_in_utc_by_default() {
        givenPriorTransfersToday(null, 0);

        service.evaluateTransfer(ACCOUNT, 100);

        val from = ArgumentCaptor.forClass(Date.class);
        val to = ArgumentCaptor.forClass(Date.class);
        verify(transactionRepository).sumAmountBySourceAccountAndTypeInRange(eq(ACCOUNT),
                eq(TransactionType.CASH_TRANSFER), from.capture(), to.capture());
        Assertions.assertEquals(Instant.parse("2026-03-15T00:00:00Z"), from.getValue().toInstant());
        Assertions.assertEquals(Instant.parse("2026-03-16T00:00:00Z"), to.getValue().toInstant());
    }

    @Test
    public void test_daily_window_uses_configured_zone() {
        properties.setZoneId("Asia/Kolkata");
        givenPriorTransfersToday(null, 0);

        service.evaluateTransfer(ACCOUNT, 100);

        val from = ArgumentCaptor.forClass(Date.class);
        val to = ArgumentCaptor.forClass(Date.class);
        verify(transactionRepository).countBySourceAccountAndTypeInRange(eq(ACCOUNT),
                eq(TransactionType.CASH_TRANSFER), from.capture(), to.capture());
        Assertions.assertEquals(Instant.parse("2026-03-14T18:30:00Z"), from.getValue().toInstant());
        Assertions.assertEquals(Instant.parse("2026-03-15T18:30:00Z"), to.getValue().toInstant());
    }

    @Test
    public void test_disabled_checks_never_flag() {
        properties.setEnabled(false);

        Assertions.assertTrue(service.evaluateTransfer(ACCOUNT, 100_000).isEmpty());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void test_flag_for_review_persists_one_pending_flag_per_violation() {
        val account = new Account();
        account.setAccountNumber(ACCOUNT);
        val transaction = new Transaction();
        transaction.setId(42L);
        transaction.setSourceAccount(account);
        when(reviewFlagRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        val flags = service.flagForReview(transaction, List.of(
                new VelocityViolation(VelocityRule.SINGLE_TRANSFER_AMOUNT, "big"),
                new VelocityViolation(VelocityRule.DAILY_TRANSFER_COUNT, "many")));

        val captor = ArgumentCaptor.forClass(List.class);
        verify(reviewFlagRepository).saveAll(captor.capture());
        Assertions.assertEquals(flags, captor.getValue());
        Assertions.assertEquals(2, flags.size());
        for (TransactionReviewFlag flag : flags) {
            Assertions.assertSame(transaction, flag.getTransaction());
            Assertions.assertEquals(ReviewStatus.PENDING, flag.getStatus());
            Assertions.assertEquals(NOW, flag.getFlaggedAt().toInstant());
        }
        Assertions.assertEquals(VelocityRule.SINGLE_TRANSFER_AMOUNT, flags.get(0).getRule());
        Assertions.assertEquals("big", flags.get(0).getDetail());
        Assertions.assertEquals(VelocityRule.DAILY_TRANSFER_COUNT, flags.get(1).getRule());
    }

    @Test
    public void test_flag_for_review_with_no_violations_saves_nothing() {
        Assertions.assertTrue(service.flagForReview(new Transaction(), List.of()).isEmpty());
        verify(reviewFlagRepository, never()).saveAll(anyList());
    }

}
