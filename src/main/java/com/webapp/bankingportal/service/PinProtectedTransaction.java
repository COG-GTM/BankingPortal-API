package com.webapp.bankingportal.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.transaction.annotation.Transactional;

import com.webapp.bankingportal.exception.FundTransferException;
import com.webapp.bankingportal.exception.IncorrectPinException;
import com.webapp.bankingportal.exception.InsufficientBalanceException;
import com.webapp.bankingportal.exception.InvalidAmountException;
import com.webapp.bankingportal.exception.InvalidPinException;
import com.webapp.bankingportal.exception.NotFoundException;
import com.webapp.bankingportal.exception.PinLockedException;

/**
 * Transaction for PIN-protected operations. The listed exceptions are only thrown
 * before any business write, so committing on them persists the PIN attempt
 * counter (failure or reset) without committing a partial operation.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Transactional(noRollbackFor = {
        IncorrectPinException.class,
        PinLockedException.class,
        InvalidAmountException.class,
        InsufficientBalanceException.class,
        FundTransferException.class,
        NotFoundException.class,
        InvalidPinException.class })
public @interface PinProtectedTransaction {
}
