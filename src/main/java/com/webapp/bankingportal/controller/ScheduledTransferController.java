package com.webapp.bankingportal.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.webapp.bankingportal.dto.ScheduledTransferRequest;
import com.webapp.bankingportal.dto.ScheduledTransferResponse;
import com.webapp.bankingportal.service.ScheduledTransferService;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.JsonUtil;
import com.webapp.bankingportal.util.LoggedinUser;

import lombok.RequiredArgsConstructor;
import lombok.val;

@RestController
@RequestMapping("/api/account/scheduled-transfers")
@RequiredArgsConstructor
public class ScheduledTransferController {

    private final ScheduledTransferService scheduledTransferService;

    @PostMapping
    public ResponseEntity<String> scheduleTransfer(@RequestBody ScheduledTransferRequest request) {
        val transfer = scheduledTransferService.scheduleTransfer(LoggedinUser.getAccountNumber(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(JsonUtil.toJson(ScheduledTransferResponse.from(transfer)));
    }

    @GetMapping
    public ResponseEntity<String> getScheduledTransfers() {
        val transfers = scheduledTransferService.getScheduledTransfers(LoggedinUser.getAccountNumber())
                .stream()
                .map(ScheduledTransferResponse::from)
                .toList();
        return ResponseEntity.ok(JsonUtil.toJson(transfers));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> cancelScheduledTransfer(@PathVariable Long id) {
        scheduledTransferService.cancelScheduledTransfer(LoggedinUser.getAccountNumber(), id);
        return ResponseEntity.ok(ApiMessages.SCHEDULED_TRANSFER_CANCEL_SUCCESS.getMessage());
    }

}
