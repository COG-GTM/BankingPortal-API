package com.webapp.bankingportal.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.webapp.bankingportal.dto.RecurringTransferRequest;
import com.webapp.bankingportal.dto.RecurringTransferResponse;
import com.webapp.bankingportal.service.RecurringTransferService;
import com.webapp.bankingportal.util.LoggedinUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/account/recurring-transfers")
@RequiredArgsConstructor
public class RecurringTransferController {

    private final RecurringTransferService recurringTransferService;

    @PostMapping
    public ResponseEntity<RecurringTransferResponse> createRecurringTransfer(
            @RequestBody RecurringTransferRequest request) {
        return ResponseEntity.ok(
                recurringTransferService.createRecurringTransfer(LoggedinUser.getAccountNumber(), request));
    }

    @GetMapping
    public ResponseEntity<List<RecurringTransferResponse>> getRecurringTransfers() {
        return ResponseEntity.ok(recurringTransferService.getRecurringTransfers(LoggedinUser.getAccountNumber()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecurringTransferResponse> getRecurringTransfer(@PathVariable Long id) {
        return ResponseEntity.ok(
                recurringTransferService.getRecurringTransfer(LoggedinUser.getAccountNumber(), id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<RecurringTransferResponse> cancelRecurringTransfer(@PathVariable Long id) {
        return ResponseEntity.ok(
                recurringTransferService.cancelRecurringTransfer(LoggedinUser.getAccountNumber(), id));
    }

}
