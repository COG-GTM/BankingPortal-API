package com.webapp.bankingportal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.User;
import com.webapp.bankingportal.exception.NotFoundException;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.UserRepository;
import com.webapp.bankingportal.util.ApiMessages;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplUnitTests {

    private static final String ACCOUNT_NUMBER = "abc123";

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountRepository accountRepository;

    private DashboardServiceImpl dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardServiceImpl(userRepository, accountRepository);
    }

    private static Account account() {
        var user = new User();
        user.setName("Jane Doe");
        user.setEmail("jane@example.com");

        var account = new Account();
        account.setAccountNumber(ACCOUNT_NUMBER);
        account.setBalance(2500);
        account.setUser(user);
        user.setAccount(account);
        return account;
    }

    @Test
    void getUserDetails_mapsUserAndAccountFields() {
        var account = account();
        when(userRepository.findByAccountAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.of(account.getUser()));

        var response = dashboardService.getUserDetails(ACCOUNT_NUMBER);

        assertEquals("Jane Doe", response.getName());
        assertEquals("jane@example.com", response.getEmail());
        assertEquals(ACCOUNT_NUMBER, response.getAccountNumber());
        assertEquals(account.getIfscCode(), response.getIfscCode());
        assertEquals(account.getBranch(), response.getBranch());
        assertEquals(account.getAccountType(), response.getAccountType());
    }

    @Test
    void getUserDetails_throwsWhenUserMissing() {
        when(userRepository.findByAccountAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.empty());

        var ex = assertThrows(NotFoundException.class, () -> dashboardService.getUserDetails(ACCOUNT_NUMBER));
        assertEquals(String.format(ApiMessages.USER_NOT_FOUND_BY_ACCOUNT.getMessage(), ACCOUNT_NUMBER),
                ex.getMessage());
    }

    @Test
    void getAccountDetails_mapsAccountFields() {
        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(account());

        var response = dashboardService.getAccountDetails(ACCOUNT_NUMBER);

        assertEquals(ACCOUNT_NUMBER, response.getAccountNumber());
        assertEquals(2500, response.getBalance());
    }

    @Test
    void getAccountDetails_throwsWhenAccountMissing() {
        assertThrows(NotFoundException.class, () -> dashboardService.getAccountDetails(ACCOUNT_NUMBER));
    }
}
