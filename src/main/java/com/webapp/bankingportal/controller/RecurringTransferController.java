package com.webapp.bankingportal.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.webapp.bankingportal.dto.RecurringTransferRequest;
import com.webapp.bankingportal.dto.RecurringTransferResponse;
import com.webapp.bankingportal.service.RecurringTransferService;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.JsonUtil;
import com.webapp.bankingportal.util.LoggedinUser;

import lombok.RequiredArgsConstructor;
import lombok.val;

@RestController
@RequestMapping("/api/account/recurring-transfers")
@RequiredArgsConstructor
public class RecurringTransferController {

    private final RecurringTransferService recurringTransferService;

    @PostMapping
    public ResponseEntity<String> createRecurringTransfer(@RequestBody RecurringTransferRequest request) {
        val transfer = recurringTransferService.createRecurringTransfer(LoggedinUser.getAccountNumber(), request);
        return ResponseEntity.ok(JsonUtil.toJson(RecurringTransferResponse.from(transfer)));
    }

    @GetMapping
    public ResponseEntity<String> getRecurringTransfers() {
        val transfers = recurringTransferService.getRecurringTransfers(LoggedinUser.getAccountNumber())
                .stream()
                .map(RecurringTransferResponse::from)
                .toList();
        return ResponseEntity.ok(JsonUtil.toJson(transfers));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> cancelRecurringTransfer(@PathVariable Long id) {
        recurringTransferService.cancelRecurringTransfer(LoggedinUser.getAccountNumber(), id);
        return ResponseEntity.ok(ApiMessages.RECURRING_TRANSFER_CANCEL_SUCCESS.getMessage());
    }
}
