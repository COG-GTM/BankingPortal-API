package com.webapp.bankingportal.scheduler;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.webapp.bankingportal.service.RecurringTransferService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "recurring-transfer.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class RecurringTransferScheduler {

    private final RecurringTransferService recurringTransferService;
    private final Clock clock;

    @Scheduled(cron = "${recurring-transfer.scheduler.cron:0 0 1 * * *}")
    public void runDueTransfers() {
        int processed = recurringTransferService.executeDueTransfers(LocalDate.now(clock));
        log.info("Recurring transfer run complete: {} schedule(s) processed", processed);
    }
}
