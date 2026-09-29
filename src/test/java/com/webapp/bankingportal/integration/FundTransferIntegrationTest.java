package com.webapp.bankingportal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.jayway.jsonpath.JsonPath;
import com.webapp.bankingportal.dto.AmountRequest;
import com.webapp.bankingportal.dto.FundTransferRequest;
import com.webapp.bankingportal.dto.LoginRequest;
import com.webapp.bankingportal.dto.PinRequest;
import com.webapp.bankingportal.entity.Transaction;
import com.webapp.bankingportal.entity.TransactionType;
import com.webapp.bankingportal.exception.GeolocationException;
import com.webapp.bankingportal.exception.NotFoundException;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.TransactionRepository;
import com.webapp.bankingportal.service.AccountService;
import com.webapp.bankingportal.service.GeolocationService;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.JsonUtil;

import lombok.val;

/**
 * End-to-end fund transfer flow (register, login, create PIN, deposit,
 * transfer) over HTTP against a real MySQL instance started by Testcontainers.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.main.allow-circular-references=true",
        "jwt.secret=DxRVQlXF1Z39bJuUk1VoqfWS82pKJukRrt5pNl6ODhuSaztDny33Fw/NsBBafh1reMgxaG0qwgm88HJ4t2lteg==",
        "jwt.expiration=86400000",
        "geo.api.url=http://localhost",
        "geo.api.key=unused",
        "spring.mail.host=localhost"
})
class FundTransferIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    private static final String PASSWORD = "Str0ng!Passw0rd";
    private static final String UNKNOWN_ACCOUNT_NUMBER = "zzzzzz";
    private static final PhoneNumberUtil PHONE_NUMBER_UTIL = PhoneNumberUtil.getInstance();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountService accountService;

    @MockBean
    private GeolocationService geolocationService;

    private record Customer(String accountNumber, String token, String pin) {
    }

    @BeforeEach
    void stubGeolocation() {
        when(geolocationService.getGeolocation(anyString()))
                .thenReturn(CompletableFuture.failedFuture(new GeolocationException("disabled in tests")));
    }

    @Test
    void transferMovesFundsAndRecordsTransaction() throws Exception {
        val sender = onboardCustomerWithBalance(1000);
        val receiver = onboardCustomerWithBalance(0);

        transfer(sender, receiver.accountNumber(), 300)
                .andExpect(status().isOk())
                .andExpect(content().string(ApiMessages.CASH_TRANSFER_SUCCESS.getMessage()));

        assertThat(balanceOf(sender)).isEqualTo(700.0);
        assertThat(balanceOf(receiver)).isEqualTo(300.0);
        assertDashboardBalance(sender, 700.0);
        assertDashboardBalance(receiver, 300.0);

        val transfers = transfersFrom(sender);
        assertThat(transfers).hasSize(1);
        val recorded = transfers.get(0);
        assertThat(recorded.getAmount()).isEqualTo(300.0);
        assertThat(recorded.getTargetAccount().getAccountNumber()).isEqualTo(receiver.accountNumber());
        assertThat(recorded.getTransactionDate()).isNotNull();

        mockMvc.perform(get("/api/account/transactions").header("Authorization", bearer(receiver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].transactionType").value(TransactionType.CASH_TRANSFER.name()))
                .andExpect(jsonPath("$[0].sourceAccountNumber").value(sender.accountNumber()))
                .andExpect(jsonPath("$[0].targetAccountNumber").value(receiver.accountNumber()))
                .andExpect(jsonPath("$[0].amount").value(300.0));
    }

    @Test
    void transferOfEntireBalanceSucceeds() throws Exception {
        val sender = onboardCustomerWithBalance(500);
        val receiver = onboardCustomerWithBalance(0);

        transfer(sender, receiver.accountNumber(), 500).andExpect(status().isOk());

        assertThat(balanceOf(sender)).isZero();
        assertThat(balanceOf(receiver)).isEqualTo(500.0);
    }

    @Test
    void transferWithInsufficientFundsIsRejectedAndLeavesBalancesUntouched() throws Exception {
        val sender = onboardCustomerWithBalance(500);
        val receiver = onboardCustomerWithBalance(200);

        transfer(sender, receiver.accountNumber(), 600)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(ApiMessages.BALANCE_INSUFFICIENT_ERROR.getMessage()));

        assertThat(balanceOf(sender)).isEqualTo(500.0);
        assertThat(balanceOf(receiver)).isEqualTo(200.0);
        assertThat(transfersFrom(sender)).isEmpty();
        assertThat(transfersFrom(receiver)).isEmpty();
    }

    @Test
    void transferFromEmptyAccountIsRejected() throws Exception {
        val sender = onboardCustomerWithBalance(0);
        val receiver = onboardCustomerWithBalance(0);

        transfer(sender, receiver.accountNumber(), 100)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(ApiMessages.BALANCE_INSUFFICIENT_ERROR.getMessage()));

        assertThat(balanceOf(sender)).isZero();
        assertThat(balanceOf(receiver)).isZero();
        assertThat(transfersFrom(sender)).isEmpty();
    }

    @Test
    void transferToOwnAccountIsRejected() throws Exception {
        val customer = onboardCustomerWithBalance(1000);

        transfer(customer, customer.accountNumber(), 100)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(ApiMessages.CASH_TRANSFER_SAME_ACCOUNT_ERROR.getMessage()));

        assertThat(balanceOf(customer)).isEqualTo(1000.0);
        assertThat(transfersFrom(customer)).isEmpty();
    }

    @Test
    void transferToUnknownAccountIsRejected() throws Exception {
        val sender = onboardCustomerWithBalance(1000);
        assertThat(accountRepository.findByAccountNumber(UNKNOWN_ACCOUNT_NUMBER)).isNull();

        transfer(sender, UNKNOWN_ACCOUNT_NUMBER, 100)
                .andExpect(status().isNotFound())
                .andExpect(content().string(ApiMessages.ACCOUNT_NOT_FOUND.getMessage()));

        assertThat(balanceOf(sender)).isEqualTo(1000.0);
        assertThat(transfersFrom(sender)).isEmpty();
    }

    @Test
    void transferFromUnknownAccountIsRejected() throws Exception {
        val receiver = onboardCustomerWithBalance(0);

        assertThatThrownBy(() -> accountService.fundTransfer(
                UNKNOWN_ACCOUNT_NUMBER, receiver.accountNumber(), "1234", 100))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(ApiMessages.ACCOUNT_NOT_FOUND.getMessage());

        assertThat(balanceOf(receiver)).isZero();
        assertThat(transfersFrom(receiver)).isEmpty();
    }

    @Test
    void transferWithWrongPinIsRejected() throws Exception {
        val sender = onboardCustomerWithBalance(1000);
        val receiver = onboardCustomerWithBalance(0);
        val wrongPin = sender.pin().equals("0000") ? "1111" : "0000";

        transfer(new Customer(sender.accountNumber(), sender.token(), wrongPin), receiver.accountNumber(), 100)
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(ApiMessages.PIN_INVALID_ERROR.getMessage()));

        assertThat(balanceOf(sender)).isEqualTo(1000.0);
        assertThat(balanceOf(receiver)).isZero();
    }

    private ResultActions transfer(Customer sender, String targetAccountNumber, double amount) throws Exception {
        val request = new FundTransferRequest(sender.accountNumber(), targetAccountNumber, amount, sender.pin());
        return mockMvc.perform(post("/api/account/fund-transfer")
                .header("Authorization", bearer(sender))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(request)));
    }

    private Customer onboardCustomerWithBalance(double initialBalance) throws Exception {
        val accountNumber = register();
        val token = login(accountNumber);
        val pin = String.format("%04d", ThreadLocalRandom.current().nextInt(10_000));
        val customer = new Customer(accountNumber, token, pin);

        mockMvc.perform(post("/api/account/pin/create")
                .header("Authorization", bearer(customer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(new PinRequest(accountNumber, pin, PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(content().string(ApiMessages.PIN_CREATION_SUCCESS.getMessage()));

        if (initialBalance > 0) {
            mockMvc.perform(post("/api/account/deposit")
                    .header("Authorization", bearer(customer))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(JsonUtil.toJson(new AmountRequest(accountNumber, pin, initialBalance))))
                    .andExpect(status().isOk())
                    .andExpect(content().string(ApiMessages.CASH_DEPOSIT_SUCCESS.getMessage()));
        }

        assertThat(balanceOf(customer)).isEqualTo(initialBalance);
        return customer;
    }

    private String register() throws Exception {
        val unique = UUID.randomUUID().toString().substring(0, 8);
        val user = Map.of(
                "name", "Test User " + unique,
                "password", PASSWORD,
                "email", "user-" + unique + "@example.com",
                "address", "1 Integration Way",
                "countryCode", "US",
                "phoneNumber", randomUsPhoneNumber());

        val response = mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(response, "$.accountNumber");
    }

    private String login(String accountNumber) throws Exception {
        val response = mockMvc.perform(post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(new LoginRequest(accountNumber, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(response, "$.token");
    }

    private void assertDashboardBalance(Customer customer, double expected) throws Exception {
        mockMvc.perform(get("/api/dashboard/account").header("Authorization", bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value(customer.accountNumber()))
                .andExpect(jsonPath("$.balance").value(expected));
    }

    private double balanceOf(Customer customer) {
        return accountRepository.findByAccountNumber(customer.accountNumber()).getBalance();
    }

    private List<Transaction> transfersFrom(Customer customer) {
        return transactionRepository
                .findBySourceAccount_AccountNumberOrTargetAccount_AccountNumber(
                        customer.accountNumber(), customer.accountNumber())
                .stream()
                .filter(t -> t.getTransactionType() == TransactionType.CASH_TRANSFER)
                .toList();
    }

    private static String bearer(Customer customer) {
        return "Bearer " + customer.token();
    }

    private static String randomUsPhoneNumber() throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            val candidate = "+1" + (200 + ThreadLocalRandom.current().nextInt(800))
                    + (200 + ThreadLocalRandom.current().nextInt(800))
                    + String.format("%04d", ThreadLocalRandom.current().nextInt(10_000));
            if (PHONE_NUMBER_UTIL.isValidNumber(PHONE_NUMBER_UTIL.parse(candidate, "US"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a valid US phone number");
    }
}
