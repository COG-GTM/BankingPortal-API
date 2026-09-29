package com.webapp.bankingportal.scheduler;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.webapp.bankingportal.service.RecurringTransferService;

import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "recurring-transfer.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class RecurringTransferScheduler {

    private final RecurringTransferService recurringTransferService;
    private final Clock clock;

    @Scheduled(cron = "${recurring-transfer.scheduler.cron:0 0 * * * *}")
    public void run() {
        processDueTransfers(LocalDate.now(clock));
    }

    /**
     * Each occurrence runs in its own transaction so one failing transfer does
     * not block the rest of the batch.
     *
     * @return the number of occurrences executed successfully.
     */
    public int processDueTransfers(LocalDate date) {
        val dueTransfers = recurringTransferService.findDueTransfers(date);
        int executed = 0;

        for (val due : dueTransfers) {
            try {
                recurringTransferService.executeOccurrence(due.id(), due.occurrenceDate(), date);
                executed++;
            } catch (RuntimeException e) {
                try {
                    recurringTransferService.recordFailedOccurrence(due.id(), due.occurrenceDate(), date,
                            e.getMessage());
                } catch (RuntimeException recordException) {
                    log.error("Failed to record failure for recurring transfer {}", due.id(), recordException);
                }
            }
        }

        if (!dueTransfers.isEmpty()) {
            log.info("Recurring transfer run for {}: {} due, {} executed", date, dueTransfers.size(), executed);
        }
        return executed;
    }

}
