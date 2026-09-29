package com.webapp.bankingportal.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scheduled-transfer.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledTransferScheduler {

    private final ScheduledTransferService scheduledTransferService;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${scheduled-transfer.scheduler.interval-ms:60000}")
    public void executeDueTransfers() {
        val completed = scheduledTransferService.executeDueTransfers(LocalDateTime.now(clock));
        if (completed > 0) {
            log.info("Executed {} scheduled transfer(s)", completed);
        }
    }

}
