package com.webapp.bankingportal.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.webapp.bankingportal.dto.PayeeRequest;
import com.webapp.bankingportal.dto.PayeeResponse;
import com.webapp.bankingportal.dto.PayeeVerificationRequest;
import com.webapp.bankingportal.service.PayeeService;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.LoggedinUser;

import lombok.RequiredArgsConstructor;
import lombok.val;

@RestController
@RequestMapping("/api/payees")
@RequiredArgsConstructor
public class PayeeController {

    private final PayeeService payeeService;

    @PostMapping
    public ResponseEntity<PayeeResponse> addPayee(@RequestBody PayeeRequest payeeRequest) {
        val payee = payeeService.addPayee(
                LoggedinUser.getAccountNumber(),
                payeeRequest.accountNumber(),
                payeeRequest.nickname());

        return ResponseEntity.status(HttpStatus.CREATED).body(payee);
    }

    @GetMapping
    public ResponseEntity<List<PayeeResponse>> getPayees() {
        return ResponseEntity.ok(payeeService.getPayees(LoggedinUser.getAccountNumber()));
    }

    @PostMapping("/{payeeId}/verify")
    public ResponseEntity<PayeeResponse> verifyPayee(@PathVariable Long payeeId,
            @RequestBody PayeeVerificationRequest verificationRequest) {
        return ResponseEntity.ok(payeeService.verifyPayee(
                LoggedinUser.getAccountNumber(),
                payeeId,
                verificationRequest.pin()));
    }

    @DeleteMapping("/{payeeId}")
    public ResponseEntity<String> removePayee(@PathVariable Long payeeId) {
        payeeService.removePayee(LoggedinUser.getAccountNumber(), payeeId);
        return ResponseEntity.ok(ApiMessages.PAYEE_REMOVED_SUCCESS.getMessage());
    }

}
