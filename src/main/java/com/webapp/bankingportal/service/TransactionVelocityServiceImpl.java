package com.webapp.bankingportal.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.webapp.bankingportal.config.VelocityCheckProperties;
import com.webapp.bankingportal.dto.VelocityViolation;
import com.webapp.bankingportal.entity.Transaction;
import com.webapp.bankingportal.entity.TransactionReviewFlag;
import com.webapp.bankingportal.entity.TransactionType;
import com.webapp.bankingportal.entity.VelocityRule;
import com.webapp.bankingportal.repository.TransactionRepository;
import com.webapp.bankingportal.repository.TransactionReviewFlagRepository;

import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionVelocityServiceImpl implements TransactionVelocityService {

    private final TransactionRepository transactionRepository;
    private final TransactionReviewFlagRepository reviewFlagRepository;
    private final VelocityCheckProperties properties;
    private final Clock clock;

    @Override
    public List<VelocityViolation> evaluateTransfer(String sourceAccountNumber, double amount) {
        if (!properties.isEnabled()) {
            return List.of();
        }

        val violations = new ArrayList<VelocityViolation>();

        if (amount > properties.getPerTransactionAmountThreshold()) {
            violations.add(new VelocityViolation(VelocityRule.SINGLE_TRANSFER_AMOUNT,
                    String.format("Transfer amount %.2f exceeds per-transaction threshold %.2f",
                            amount, properties.getPerTransactionAmountThreshold())));
        }

        val zone = ZoneId.of(properties.getZoneId());
        val dayStart = LocalDate.now(clock.withZone(zone)).atStartOfDay(zone);
        val from = Date.from(dayStart.toInstant());
        val to = Date.from(dayStart.plusDays(1).toInstant());

        val priorAmount = transactionRepository.sumAmountBySourceAccountAndTypeInRange(
                sourceAccountNumber, TransactionType.CASH_TRANSFER, from, to);
        val dailyAmount = (priorAmount == null ? 0.0 : priorAmount) + amount;
        if (dailyAmount > properties.getDailyAmountThreshold()) {
            violations.add(new VelocityViolation(VelocityRule.DAILY_TRANSFER_AMOUNT,
                    String.format("Daily transfer total %.2f exceeds daily threshold %.2f",
                            dailyAmount, properties.getDailyAmountThreshold())));
        }

        val dailyCount = transactionRepository.countBySourceAccountAndTypeInRange(
                sourceAccountNumber, TransactionType.CASH_TRANSFER, from, to) + 1;
        if (dailyCount > properties.getDailyCountThreshold()) {
            violations.add(new VelocityViolation(VelocityRule.DAILY_TRANSFER_COUNT,
                    String.format("Daily transfer count %d exceeds daily threshold %d",
                            dailyCount, properties.getDailyCountThreshold())));
        }

        return violations;
    }

    @Override
    public List<TransactionReviewFlag> flagForReview(Transaction transaction, List<VelocityViolation> violations) {
        if (violations.isEmpty()) {
            return List.of();
        }

        val flaggedAt = Date.from(clock.instant());
        val flags = violations.stream().map(violation -> {
            val flag = new TransactionReviewFlag();
            flag.setTransaction(transaction);
            flag.setRule(violation.rule());
            flag.setDetail(violation.detail());
            flag.setFlaggedAt(flaggedAt);
            return flag;
        }).toList();

        log.warn("Transaction {} from account {} flagged for review: {}", transaction.getId(),
                transaction.getSourceAccount().getAccountNumber(),
                violations.stream().map(VelocityViolation::rule).toList());

        return reviewFlagRepository.saveAll(flags);
    }

}
