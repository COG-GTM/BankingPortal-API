package com.webapp.bankingportal;

import jakarta.validation.ConstraintViolationException;
import lombok.val;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.webapp.bankingportal.exception.InsufficientBalanceException;
import com.webapp.bankingportal.exception.InvalidAmountException;
import com.webapp.bankingportal.exception.InvalidPinException;
import com.webapp.bankingportal.exception.NotFoundException;
import com.webapp.bankingportal.exception.PinLockedException;
import com.webapp.bankingportal.exception.UnauthorizedException;
import com.webapp.bankingportal.repository.AccountRepository;

public class AccountServiceTests extends BaseTest {

    @Autowired
    AccountRepository accountRepository;

    @Test
    public void test_create_account_with_valid_user() {
        val user = createUser();
        userRepository.save(user);

        val account = accountService.createAccount(user);

        Assertions.assertNotNull(account);
        Assertions.assertNotNull(account.getAccountNumber());
        Assertions.assertEquals(user, account.getUser());
        Assertions.assertEquals(0.0, account.getBalance());
    }

    @Test
    public void test_create_account_with_null_user() {
        Assertions.assertThrows(ConstraintViolationException.class, () -> accountService.createAccount(null));
    }

    @Test
    public void test_create_pin_with_valid_details() {
        val accountDetails = createAccount();

        val pin = getRandomPin();

        accountService.createPin(accountDetails.get("accountNumber"), accountDetails.get("password"), pin);

        val account = accountRepository
                .findByAccountNumber(accountDetails.get("accountNumber"));

        Assertions.assertTrue(passwordEncoder.matches(pin, account.getPin()));
    }

    @Test
    public void test_create_pin_with_invalid_account_number() {
        Assertions.assertThrows(NotFoundException.class, () -> {
            accountService.createPin(getRandomAccountNumber(), getRandomPassword(), getRandomPin());
        });
    }

    @Test
    public void test_create_pin_with_invalid_password() {
        val accountNumber = createAccount()
                .get("accountNumber");

        Assertions.assertThrows(UnauthorizedException.class, () -> {
            accountService.createPin(accountNumber, getRandomPassword(), getRandomPin());
        });
    }

    @Test
    public void test_create_pin_with_existing_pin() {
        val accountDetails = createAccountWithPin(passwordEncoder, userRepository, accountService);

        Assertions.assertThrows(UnauthorizedException.class, () -> {
            accountService.createPin(accountDetails.get("accountNumber"), accountDetails.get("password"),
                    getRandomPin());
        });

    }

    @Test
    public void test_create_pin_with_missing_or_empty_pin() {
        val accountDetails = createAccount();

        Assertions.assertThrows(InvalidPinException.class, () -> {
            accountService.createPin(accountDetails.get("accountNumber"), accountDetails.get("password"), null);
        });

        Assertions.assertThrows(InvalidPinException.class, () -> {
            accountService.createPin(accountDetails.get("accountNumber"), accountDetails.get("password"), "");
        });
    }

    @Test
    public void test_create_pin_with_invalid_format() {
        val accountDetails = createAccount();

        // Short pin
        Assertions.assertThrows(InvalidPinException.class, () -> {
            accountService.createPin(accountDetails.get("accountNumber"), accountDetails.get("password"),
                    faker.number().digits(3));
        });

        // Long pin
        Assertions.assertThrows(InvalidPinException.class, () -> {
            accountService.createPin(accountDetails.get("accountNumber"), accountDetails.get("password"),
                    faker.number().digits(5));
        });

        // Invalid format
        Assertions.assertThrows(InvalidPinException.class, () -> {
            accountService.createPin(accountDetails.get("accountNumber"), accountDetails.get("password"),
                    getRandomPassword().substring(0, 4));
        });
    }

    @Test
    public void test_update_pin_with_valid_details() {
        val accountDetails = createAccountWithPin(passwordEncoder, userRepository, accountService);

        val newPin = getRandomPin();

        accountService.updatePin(accountDetails.get("accountNumber"), accountDetails.get("pin"),
                accountDetails.get("password"), newPin);

        val account = accountRepository
                .findByAccountNumber(accountDetails.get("accountNumber"));

        Assertions.assertTrue(passwordEncoder.matches(newPin, account.getPin()));
    }

    @Test
    public void test_update_pin_with_invalid_account_number() {
        Assertions.assertThrows(NotFoundException.class, () -> {
            accountService.updatePin(getRandomAccountNumber(), getRandomPin(), getRandomPassword(), getRandomPin());
        });
    }

    @Test
    public void test_update_pin_with_incorrect_password() {
        val accountDetails = createAccountWithPin(passwordEncoder, userRepository, accountService);

        Assertions.assertThrows(UnauthorizedException.class, () -> {
            accountService.updatePin(accountDetails.get("accountNumber"), accountDetails.get("pin"),
                    getRandomPassword(), getRandomPin());
        });
    }

    @Test
    public void test_update_pin_with_missing_or_empty_password() {
        val accountDetails = createAccountWithPin(passwordEncoder, userRepository, accountService);

        Assertions.assertThrows(UnauthorizedException.class, () -> {
            accountService.updatePin(accountDetails.get("accountNumber"), accountDetails.get("pin"), null,
                    getRandomPin());
        });

        Assertions.assertThrows(UnauthorizedException.class, () -> {
            accountService.updatePin(accountDetails.get("accountNumber"), accountDetails.get("pin"), "",
                    getRandomPin());
        });
    }

    @Test
    public void test_update_pin_with_incorrect_pin() {
        val accountDetails = createAccountWithPin(passwordEncoder, userRepository, accountService);

        Assertions.assertThrows(UnauthorizedException.class, () -> {
            accountService.updatePin(accountDetails.get("accountNumber"), getRandomPin(),
                    accountDetails.get("password"), getRandomPin());
        });
    }

    @Test
    public void test_update_pin_for_account_with_no_pin() {
        val accountDetails = createAccount();

        Assertions.assertThrows(UnauthorizedException.class, () -> {
            accountService.updatePin(accountDetails.get("accountNumber"), getRandomPin(),
                    accountDetails.get("password"), getRandomPin());
        });
    }

    @Test
    public void test_update_pin_with_missing_or_empty_old_pin() {
        val accountDetails = createAccountWithPin(passwordEncoder, userRepository, accountService);

        Assertions.assertThrows(UnauthorizedException.class, () -> {
            accountService.updatePin(accountDetails.get("accountNumber"), null, accountDetails.get("password"),
                    getRandomPin());
        });

        Assertions.assertThrows(UnauthorizedException.class, () -> {
            accountService.updatePin(accountDetails.get("accountNumber"), "", accountDetails.get("password"),
                    getRandomPin());
        });
    }

    @Test
    public void test_update_pin_with_missing_or_empty_new_pin() {
        val accountDetails = createAccountWithPin(passwordEncoder, userRepository, accountService);

        Assertions.assertThrows(InvalidPinException.class, () -> {
            accountService.updatePin(accountDetails.get("accountNumber"), accountDetails.get("pin"),
                    accountDetails.get("password"), null);
        });

        Assertions.assertThrows(InvalidPinException.class, () -> {
            accountService.updatePin(accountDetails.get("accountNumber"), accountDetails.get("pin"),
                    accountDetails.get("password"), "");
        });
    }

    @Test
    public void test_deposit_cash_with_valid_details() {
        val balance = 1000.0;
        val accountDetails = createAccountWithInitialBalance(balance);

        val account = accountRepository
                .findByAccountNumber(accountDetails.get("accountNumber"));

        Assertions.assertEquals(balance, account.getBalance(), 0.01);
    }

    @Test
    public void test_deposit_cash_with_invalid_account_number() {
        Assertions.assertThrows(NotFoundException.class, () -> {
            accountService.cashDeposit(getRandomAccountNumber(), getRandomPin(), 50.0);
        });
    }

    @Test
    public void test_deposit_cash_with_invalid_pin() {
        val accountDetails = createAccountWithPin(passwordEncoder, userRepository, accountService);

        Assertions.assertThrows(UnauthorizedException.class, () -> {
            accountService.cashDeposit(accountDetails.get("accountNumber"), getRandomPin(), 50.0);
        });
    }

    @Test
    public void test_deposit_invalid_amount() {
        val accountDetails = createAccountWithPin(passwordEncoder, userRepository, accountService);

        // Negative amount
        Assertions.assertThrows(InvalidAmountException.class, () -> {
            accountService.cashDeposit(accountDetails.get("accountNumber"), accountDetails.get("pin"), -50.0);
        });

        // Zero amount
        Assertions.assertThrows(InvalidAmountException.class, () -> {
            accountService.cashDeposit(accountDetails.get("accountNumber"), accountDetails.get("pin"), 0.0);
        });

        // Amount not in multiples of 100
        Assertions.assertThrows(InvalidAmountException.class, () -> {
            accountService.cashDeposit(accountDetails.get("accountNumber"), accountDetails.get("pin"), 50.0);
        });

        // Amount greater than 100,000
        Assertions.assertThrows(InvalidAmountException.class, () -> {
            accountService.cashDeposit(accountDetails.get("accountNumber"), accountDetails.get("pin"), 100001.0);
        });
    }

    @Test
    public void test_withdraw_cash_with_valid_details() {
        val balance = 1000.0;
        val accountDetails = createAccountWithInitialBalance(balance);

        val withdrawalAmount = 500.0;
        accountService.cashWithdrawal(accountDetails.get("accountNumber"), accountDetails.get("pin"), withdrawalAmount);

        val account = accountRepository
                .findByAccountNumber(accountDetails.get("accountNumber"));

        Assertions.assertEquals(balance - withdrawalAmount, account.getBalance(), 0.01);
    }

    @Test
    public void test_withdraw_insufficient_balance() {
        val accountDetails = createAccountWithInitialBalance(500.0);

        Assertions.assertThrows(InsufficientBalanceException.class, () -> {
            accountService.cashWithdrawal(accountDetails.get("accountNumber"), accountDetails.get("pin"), 1000.0);
        });
    }

    @Test
    public void test_transfer_funds_with_valid_accounts() {
        val sourceAccountBalance = 1000.0;
        val sourceAccountDetails = createAccountWithInitialBalance(sourceAccountBalance);

        val targetAccountBalance = 500.0;
        val targetAccountDetails = createAccountWithInitialBalance(targetAccountBalance);

        val transferAmount = 200;
        accountService.fundTransfer(sourceAccountDetails.get("accountNumber"),
                targetAccountDetails.get("accountNumber"), sourceAccountDetails.get("pin"), transferAmount);

        val sourceAccount = accountRepository
                .findByAccountNumber(sourceAccountDetails.get("accountNumber"));
        val targetAccount = accountRepository
                .findByAccountNumber(targetAccountDetails.get("accountNumber"));

        Assertions.assertEquals(sourceAccountBalance - transferAmount, sourceAccount.getBalance(), 0.01);

        Assertions.assertEquals(targetAccountBalance + transferAmount, targetAccount.getBalance(), 0.01);
    }

    @Test
    public void test_transfer_non_existent_target_account() {
        val accountDetails = createAccountWithInitialBalance(500.0);

        Assertions.assertThrows(NotFoundException.class, () -> {
            accountService.fundTransfer(accountDetails.get("accountNumber"), getRandomAccountNumber(),
                    accountDetails.get("pin"), 1000.0);
        });
    }

    @Test
    public void test_transfer_funds_insufficient_balance() {
        val sourceAccountDetails = createAccountWithInitialBalance(500.0);

        val targetAccountDetails = createAccount();

        Assertions.assertThrows(InsufficientBalanceException.class, () -> {
            accountService.fundTransfer(sourceAccountDetails.get("accountNumber"),
                    targetAccountDetails.get("accountNumber"), sourceAccountDetails.get("pin"), 1000.0);
        });
    }

    private static String getWrongPin(String pin) {
        String wrongPin;
        do {
            wrongPin = getRandomPin();
        } while (wrongPin.equals(pin));
        return wrongPin;
    }

    private void failPinAttempts(String accountNumber, String pin, int attempts) {
        for (int i = 0; i < attempts; i++) {
            Assertions.assertThrows(UnauthorizedException.class,
                    () -> accountService.cashDeposit(accountNumber, getWrongPin(pin), 100.0));
        }
    }

    @Test
    public void test_pin_locked_after_max_failed_attempts() {
        val accountDetails = createAccountWithPin(passwordEncoder, userRepository, accountService);
        val accountNumber = accountDetails.get("accountNumber");
        val pin = accountDetails.get("pin");

        failPinAttempts(accountNumber, pin, 2);
        Assertions.assertEquals(2, accountRepository.findByAccountNumber(accountNumber).getFailedPinAttempts());

        Assertions.assertThrows(PinLockedException.class,
                () -> accountService.cashDeposit(accountNumber, getWrongPin(pin), 100.0));

        val account = accountRepository.findByAccountNumber(accountNumber);
        Assertions.assertNotNull(account.getPinLockedUntil());
        Assertions.assertTrue(account.getPinLockedUntil().after(new Date()));
        Assertions.assertEquals(0.0, account.getBalance());
    }

    @Test
    public void test_locked_pin_rejects_correct_pin_on_all_pin_operations() {
        val accountDetails = createAccountWithInitialBalance(1000.0);
        val accountNumber = accountDetails.get("accountNumber");
        val pin = accountDetails.get("pin");
        val targetAccountNumber = createAccount().get("accountNumber");

        failPinAttempts(accountNumber, pin, 2);
        Assertions.assertThrows(PinLockedException.class,
                () -> accountService.cashWithdrawal(accountNumber, getWrongPin(pin), 100.0));

        Assertions.assertThrows(PinLockedException.class,
                () -> accountService.cashDeposit(accountNumber, pin, 100.0));
        Assertions.assertThrows(PinLockedException.class,
                () -> accountService.cashWithdrawal(accountNumber, pin, 100.0));
        Assertions.assertThrows(PinLockedException.class,
                () -> accountService.fundTransfer(accountNumber, targetAccountNumber, pin, 100.0));
        Assertions.assertThrows(PinLockedException.class,
                () -> accountService.updatePin(accountNumber, pin, accountDetails.get("password"), getRandomPin()));

        Assertions.assertEquals(1000.0, accountRepository.findByAccountNumber(accountNumber).getBalance());
    }

    @Test
    public void test_successful_pin_resets_failed_attempt_counter() {
        val accountDetails = createAccountWithPin(passwordEncoder, userRepository, accountService);
        val accountNumber = accountDetails.get("accountNumber");
        val pin = accountDetails.get("pin");

        failPinAttempts(accountNumber, pin, 2);
        accountService.cashDeposit(accountNumber, pin, 100.0);

        val account = accountRepository.findByAccountNumber(accountNumber);
        Assertions.assertEquals(0, account.getFailedPinAttempts());
        Assertions.assertNull(account.getPinLockedUntil());

        // Counter restarted, so two more failures must not lock the PIN
        failPinAttempts(accountNumber, pin, 2);
        Assertions.assertNull(accountRepository.findByAccountNumber(accountNumber).getPinLockedUntil());
        accountService.cashDeposit(accountNumber, pin, 100.0);
        Assertions.assertEquals(200.0, accountRepository.findByAccountNumber(accountNumber).getBalance());
    }

    @Test
    public void test_empty_pin_does_not_count_as_failed_attempt() {
        val accountDetails = createAccountWithPin(passwordEncoder, userRepository, accountService);
        val accountNumber = accountDetails.get("accountNumber");

        for (int i = 0; i < 5; i++) {
            Assertions.assertThrows(UnauthorizedException.class,
                    () -> accountService.cashDeposit(accountNumber, "", 100.0));
        }

        val account = accountRepository.findByAccountNumber(accountNumber);
        Assertions.assertEquals(0, account.getFailedPinAttempts());
        Assertions.assertNull(account.getPinLockedUntil());
    }

    @Test
    public void test_unlock_pin_with_valid_password() {
        val accountDetails = createAccountWithPin(passwordEncoder, userRepository, accountService);
        val accountNumber = accountDetails.get("accountNumber");
        val pin = accountDetails.get("pin");

        failPinAttempts(accountNumber, pin, 2);
        Assertions.assertThrows(PinLockedException.class,
                () -> accountService.cashDeposit(accountNumber, getWrongPin(pin), 100.0));

        accountService.unlockPin(accountNumber, accountDetails.get("password"));

        val account = accountRepository.findByAccountNumber(accountNumber);
        Assertions.assertNull(account.getPinLockedUntil());
        Assertions.assertEquals(0, account.getFailedPinAttempts());

        accountService.cashDeposit(accountNumber, pin, 100.0);
        Assertions.assertEquals(100.0, accountRepository.findByAccountNumber(accountNumber).getBalance());
    }

    @Test
    public void test_unlock_pin_with_invalid_password_keeps_lock() {
        val accountDetails = createAccountWithPin(passwordEncoder, userRepository, accountService);
        val accountNumber = accountDetails.get("accountNumber");
        val pin = accountDetails.get("pin");

        failPinAttempts(accountNumber, pin, 2);
        Assertions.assertThrows(PinLockedException.class,
                () -> accountService.cashDeposit(accountNumber, getWrongPin(pin), 100.0));

        Assertions.assertThrows(UnauthorizedException.class,
                () -> accountService.unlockPin(accountNumber, getRandomPassword()));
        Assertions.assertThrows(UnauthorizedException.class,
                () -> accountService.unlockPin(accountNumber, ""));

        Assertions.assertNotNull(accountRepository.findByAccountNumber(accountNumber).getPinLockedUntil());
        Assertions.assertThrows(PinLockedException.class,
                () -> accountService.cashDeposit(accountNumber, pin, 100.0));
    }

    @Test
    public void test_unlock_pin_for_account_without_pin() {
        val accountDetails = createAccount();

        Assertions.assertThrows(UnauthorizedException.class,
                () -> accountService.unlockPin(accountDetails.get("accountNumber"), accountDetails.get("password")));
    }

    @Test
    public void test_unlock_pin_with_invalid_account_number() {
        Assertions.assertThrows(NotFoundException.class,
                () -> accountService.unlockPin(getRandomAccountNumber(), getRandomPassword()));
    }

    @Test
    public void test_pin_lock_expires_automatically() {
        val accountDetails = createAccountWithPin(passwordEncoder, userRepository, accountService);
        val accountNumber = accountDetails.get("accountNumber");
        val pin = accountDetails.get("pin");

        failPinAttempts(accountNumber, pin, 2);
        Assertions.assertThrows(PinLockedException.class,
                () -> accountService.cashDeposit(accountNumber, getWrongPin(pin), 100.0));

        val account = accountRepository.findByAccountNumber(accountNumber);
        account.setPinLockedUntil(new Date(System.currentTimeMillis() - 1000));
        accountRepository.save(account);

        accountService.cashDeposit(accountNumber, pin, 100.0);

        val unlockedAccount = accountRepository.findByAccountNumber(accountNumber);
        Assertions.assertNull(unlockedAccount.getPinLockedUntil());
        Assertions.assertEquals(0, unlockedAccount.getFailedPinAttempts());
        Assertions.assertEquals(100.0, unlockedAccount.getBalance());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void test_failed_pin_attempts_persist_when_transaction_fails() {
        val accountDetails = createAccountWithPin(passwordEncoder, userRepository, accountService);
        val accountNumber = accountDetails.get("accountNumber");
        val pin = accountDetails.get("pin");

        failPinAttempts(accountNumber, pin, 2);
        Assertions.assertEquals(2, accountRepository.findByAccountNumber(accountNumber).getFailedPinAttempts());

        Assertions.assertThrows(PinLockedException.class,
                () -> accountService.fundTransfer(accountNumber, getRandomAccountNumber(), getWrongPin(pin), 100.0));
        Assertions.assertNotNull(accountRepository.findByAccountNumber(accountNumber).getPinLockedUntil());
    }

}
