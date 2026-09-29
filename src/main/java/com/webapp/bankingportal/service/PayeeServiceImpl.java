package com.webapp.bankingportal.service;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.webapp.bankingportal.dto.PayeeResponse;
import com.webapp.bankingportal.entity.Payee;
import com.webapp.bankingportal.exception.FundTransferException;
import com.webapp.bankingportal.exception.NotFoundException;
import com.webapp.bankingportal.exception.PayeeException;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.PayeeRepository;
import com.webapp.bankingportal.util.ApiMessages;

import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class PayeeServiceImpl implements PayeeService {

    private static final int MAX_NICKNAME_LENGTH = 50;

    private final PayeeRepository payeeRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;

    @Transactional
    @Override
    public PayeeResponse addPayee(String ownerAccountNumber, String payeeAccountNumber, String nickname) {
        if (payeeAccountNumber == null || payeeAccountNumber.isBlank()) {
            throw new PayeeException(ApiMessages.PAYEE_ACCOUNT_NUMBER_EMPTY_ERROR.getMessage());
        }

        if (nickname == null || nickname.isBlank()) {
            throw new PayeeException(ApiMessages.PAYEE_NICKNAME_EMPTY_ERROR.getMessage());
        }

        val trimmedNickname = nickname.trim();
        if (trimmedNickname.length() > MAX_NICKNAME_LENGTH) {
            throw new PayeeException(ApiMessages.PAYEE_NICKNAME_TOO_LONG_ERROR.getMessage());
        }

        val trimmedPayeeAccountNumber = payeeAccountNumber.trim();
        if (ownerAccountNumber.equals(trimmedPayeeAccountNumber)) {
            throw new PayeeException(ApiMessages.PAYEE_SELF_ERROR.getMessage());
        }

        val ownerAccount = accountRepository.findByAccountNumber(ownerAccountNumber);
        if (ownerAccount == null) {
            throw new NotFoundException(ApiMessages.ACCOUNT_NOT_FOUND.getMessage());
        }

        val payeeAccount = accountRepository.findByAccountNumber(trimmedPayeeAccountNumber);
        if (payeeAccount == null) {
            throw new NotFoundException(ApiMessages.ACCOUNT_NOT_FOUND.getMessage());
        }

        if (payeeRepository.existsByOwnerAccountAccountNumberAndPayeeAccountAccountNumber(
                ownerAccountNumber, trimmedPayeeAccountNumber)) {
            throw new PayeeException(ApiMessages.PAYEE_ALREADY_EXISTS_ERROR.getMessage());
        }

        val payee = new Payee();
        payee.setOwnerAccount(ownerAccount);
        payee.setPayeeAccount(payeeAccount);
        payee.setNickname(trimmedNickname);
        payee.setVerified(false);
        payee.setCreatedAt(new Date());

        log.info("Adding payee {} for account {}", trimmedPayeeAccountNumber, ownerAccountNumber);
        return PayeeResponse.from(payeeRepository.save(payee));
    }

    @Override
    public List<PayeeResponse> getPayees(String ownerAccountNumber) {
        return payeeRepository.findAllByOwnerAccountAccountNumberOrderByCreatedAtAsc(ownerAccountNumber)
                .stream()
                .map(PayeeResponse::from)
                .toList();
    }

    @Transactional
    @Override
    public PayeeResponse verifyPayee(String ownerAccountNumber, Long payeeId, String pin) {
        val payee = getOwnedPayee(ownerAccountNumber, payeeId);
        accountService.validatePin(ownerAccountNumber, pin);

        if (payee.isVerified()) {
            throw new PayeeException(ApiMessages.PAYEE_ALREADY_VERIFIED_ERROR.getMessage());
        }

        payee.setVerified(true);
        payee.setVerifiedAt(new Date());
        return PayeeResponse.from(payeeRepository.save(payee));
    }

    @Transactional
    @Override
    public void removePayee(String ownerAccountNumber, Long payeeId) {
        payeeRepository.delete(getOwnedPayee(ownerAccountNumber, payeeId));
    }

    @Override
    public String resolveTransferTarget(String ownerAccountNumber, Long payeeId, String targetAccountNumber) {
        if (payeeId == null) {
            return targetAccountNumber;
        }

        val payee = getOwnedPayee(ownerAccountNumber, payeeId);
        if (!payee.isVerified()) {
            throw new FundTransferException(ApiMessages.PAYEE_NOT_VERIFIED_ERROR.getMessage());
        }

        val payeeAccountNumber = payee.getPayeeAccount().getAccountNumber();
        if (targetAccountNumber != null && !targetAccountNumber.isBlank()
                && !targetAccountNumber.equals(payeeAccountNumber)) {
            throw new FundTransferException(ApiMessages.PAYEE_TARGET_MISMATCH_ERROR.getMessage());
        }

        return payeeAccountNumber;
    }

    private Payee getOwnedPayee(String ownerAccountNumber, Long payeeId) {
        if (payeeId == null) {
            throw new NotFoundException(ApiMessages.PAYEE_NOT_FOUND.getMessage());
        }

        return payeeRepository.findByIdAndOwnerAccountAccountNumber(payeeId, ownerAccountNumber)
                .orElseThrow(() -> new NotFoundException(ApiMessages.PAYEE_NOT_FOUND.getMessage()));
    }

}
