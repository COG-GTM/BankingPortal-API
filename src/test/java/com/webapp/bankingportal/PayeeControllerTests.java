package com.webapp.bankingportal;

import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.jayway.jsonpath.JsonPath;
import com.webapp.bankingportal.dto.FundTransferRequest;
import com.webapp.bankingportal.dto.PayeeRequest;
import com.webapp.bankingportal.dto.PayeeVerificationRequest;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.JsonUtil;

import lombok.val;

public class PayeeControllerTests extends BaseTest {

    @Autowired
    private AccountRepository accountRepository;

    private ResultActions postPayee(String token, String accountNumber, String nickname) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders
                .post("/api/payees")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(new PayeeRequest(accountNumber, nickname))));
    }

    private Long addPayee(String token, String accountNumber) throws Exception {
        val response = postPayee(token, accountNumber, faker.name().firstName())
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private ResultActions verifyPayee(String token, Long payeeId, String pin) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders
                .post("/api/payees/" + payeeId + "/verify")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(new PayeeVerificationRequest(pin))));
    }

    private ResultActions transfer(Map<String, String> userDetails, FundTransferRequest request) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders
                .post("/api/account/fund-transfer")
                .header("Authorization", "Bearer " + userDetails.get("token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(request)));
    }

    @Test
    public void test_add_payee_with_valid_data() throws Exception {
        val userDetails = createAndLoginUser();
        val payeeDetails = createAndLoginUser();

        postPayee(userDetails.get("token"), payeeDetails.get("accountNumber"), "  Landlord  ")
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").isNumber())
                .andExpect(MockMvcResultMatchers.jsonPath("$.nickname").value("Landlord"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.accountNumber")
                        .value(payeeDetails.get("accountNumber")))
                .andExpect(MockMvcResultMatchers.jsonPath("$.accountHolderName").value(payeeDetails.get("name")))
                .andExpect(MockMvcResultMatchers.jsonPath("$.verified").value(false));
    }

    @Test
    public void test_add_payee_with_own_account() throws Exception {
        val userDetails = createAndLoginUser();

        postPayee(userDetails.get("token"), userDetails.get("accountNumber"), "Me")
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content().string(ApiMessages.PAYEE_SELF_ERROR.getMessage()));
    }

    @Test
    public void test_add_payee_with_nonexistent_account() throws Exception {
        val userDetails = createAndLoginUser();

        postPayee(userDetails.get("token"), getRandomAccountNumber(), "Ghost")
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.content().string(ApiMessages.ACCOUNT_NOT_FOUND.getMessage()));
    }

    @Test
    public void test_add_payee_with_missing_account_number() throws Exception {
        val userDetails = createAndLoginUser();

        postPayee(userDetails.get("token"), null, "Nobody")
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.PAYEE_ACCOUNT_NUMBER_EMPTY_ERROR.getMessage()));
    }

    @Test
    public void test_add_payee_with_invalid_nickname() throws Exception {
        val userDetails = createAndLoginUser();
        val payeeAccountNumber = createAndLoginUser().get("accountNumber");

        postPayee(userDetails.get("token"), payeeAccountNumber, " ")
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.PAYEE_NICKNAME_EMPTY_ERROR.getMessage()));

        postPayee(userDetails.get("token"), payeeAccountNumber, "x".repeat(51))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.PAYEE_NICKNAME_TOO_LONG_ERROR.getMessage()));
    }

    @Test
    public void test_add_duplicate_payee() throws Exception {
        val userDetails = createAndLoginUser();
        val payeeAccountNumber = createAndLoginUser().get("accountNumber");
        addPayee(userDetails.get("token"), payeeAccountNumber);

        postPayee(userDetails.get("token"), payeeAccountNumber, "Again")
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.PAYEE_ALREADY_EXISTS_ERROR.getMessage()));
    }

    @Test
    public void test_list_payees_returns_only_own_payees() throws Exception {
        val userDetails = createAndLoginUser();
        val otherUserDetails = createAndLoginUser();
        val firstPayee = createAndLoginUser().get("accountNumber");
        val secondPayee = createAndLoginUser().get("accountNumber");

        addPayee(userDetails.get("token"), firstPayee);
        addPayee(userDetails.get("token"), secondPayee);
        addPayee(otherUserDetails.get("token"), firstPayee);

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/payees")
                .header("Authorization", "Bearer " + userDetails.get("token")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$", Matchers.hasSize(2)))
                .andExpect(MockMvcResultMatchers.jsonPath("$[*].accountNumber",
                        Matchers.containsInAnyOrder(firstPayee, secondPayee)));
    }

    @Test
    public void test_list_payees_unauthorized_access() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/payees"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    public void test_verify_payee_with_valid_pin() throws Exception {
        val userDetails = createAndLoginUserWithPin();
        val payeeId = addPayee(userDetails.get("token"), createAndLoginUser().get("accountNumber"));

        verifyPayee(userDetails.get("token"), payeeId, userDetails.get("pin"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.verified").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.verifiedAt").isNotEmpty());

        verifyPayee(userDetails.get("token"), payeeId, userDetails.get("pin"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.PAYEE_ALREADY_VERIFIED_ERROR.getMessage()));
    }

    @Test
    public void test_verify_payee_with_invalid_pin() throws Exception {
        val userDetails = createAndLoginUserWithPin();
        val payeeId = addPayee(userDetails.get("token"), createAndLoginUser().get("accountNumber"));

        String wrongPin;
        do {
            wrongPin = getRandomPin();
        } while (wrongPin.equals(userDetails.get("pin")));

        verifyPayee(userDetails.get("token"), payeeId, wrongPin)
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.content().string(ApiMessages.PIN_INVALID_ERROR.getMessage()));
    }

    @Test
    public void test_verify_payee_owned_by_another_user() throws Exception {
        val ownerDetails = createAndLoginUserWithPin();
        val intruderDetails = createAndLoginUserWithPin();
        val payeeId = addPayee(ownerDetails.get("token"), createAndLoginUser().get("accountNumber"));

        verifyPayee(intruderDetails.get("token"), payeeId, intruderDetails.get("pin"))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.content().string(ApiMessages.PAYEE_NOT_FOUND.getMessage()));
    }

    @Test
    public void test_remove_payee() throws Exception {
        val userDetails = createAndLoginUser();
        val payeeId = addPayee(userDetails.get("token"), createAndLoginUser().get("accountNumber"));

        mockMvc.perform(MockMvcRequestBuilders
                .delete("/api/payees/" + payeeId)
                .header("Authorization", "Bearer " + userDetails.get("token")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(ApiMessages.PAYEE_REMOVED_SUCCESS.getMessage()));

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/payees")
                .header("Authorization", "Bearer " + userDetails.get("token")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$", Matchers.hasSize(0)));

        mockMvc.perform(MockMvcRequestBuilders
                .delete("/api/payees/" + payeeId)
                .header("Authorization", "Bearer " + userDetails.get("token")))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    public void test_remove_payee_owned_by_another_user() throws Exception {
        val ownerDetails = createAndLoginUser();
        val intruderDetails = createAndLoginUser();
        val payeeId = addPayee(ownerDetails.get("token"), createAndLoginUser().get("accountNumber"));

        mockMvc.perform(MockMvcRequestBuilders
                .delete("/api/payees/" + payeeId)
                .header("Authorization", "Bearer " + intruderDetails.get("token")))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.content().string(ApiMessages.PAYEE_NOT_FOUND.getMessage()));
    }

    @Test
    public void test_fund_transfer_to_verified_payee() throws Exception {
        val amount = 500.0;
        val userDetails = createAndLoginUserWithInitialBalance(amount);
        val payeeAccountNumber = createAndLoginUser().get("accountNumber");
        val payeeId = addPayee(userDetails.get("token"), payeeAccountNumber);
        verifyPayee(userDetails.get("token"), payeeId, userDetails.get("pin"))
                .andExpect(MockMvcResultMatchers.status().isOk());

        transfer(userDetails, new FundTransferRequest(null, null, 200.0, userDetails.get("pin"), payeeId))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(ApiMessages.CASH_TRANSFER_SUCCESS.getMessage()));

        Assertions.assertEquals(300.0,
                accountRepository.findByAccountNumber(userDetails.get("accountNumber")).getBalance());
        Assertions.assertEquals(200.0, accountRepository.findByAccountNumber(payeeAccountNumber).getBalance());
    }

    @Test
    public void test_fund_transfer_to_verified_payee_with_matching_target_account() throws Exception {
        val amount = 100.0;
        val userDetails = createAndLoginUserWithInitialBalance(amount);
        val payeeAccountNumber = createAndLoginUser().get("accountNumber");
        val payeeId = addPayee(userDetails.get("token"), payeeAccountNumber);
        verifyPayee(userDetails.get("token"), payeeId, userDetails.get("pin"));

        transfer(userDetails,
                new FundTransferRequest(null, payeeAccountNumber, amount, userDetails.get("pin"), payeeId))
                .andExpect(MockMvcResultMatchers.status().isOk());

        Assertions.assertEquals(amount, accountRepository.findByAccountNumber(payeeAccountNumber).getBalance());
    }

    @Test
    public void test_fund_transfer_to_unverified_payee() throws Exception {
        val amount = 100.0;
        val userDetails = createAndLoginUserWithInitialBalance(amount);
        val payeeAccountNumber = createAndLoginUser().get("accountNumber");
        val payeeId = addPayee(userDetails.get("token"), payeeAccountNumber);

        transfer(userDetails, new FundTransferRequest(null, null, amount, userDetails.get("pin"), payeeId))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.PAYEE_NOT_VERIFIED_ERROR.getMessage()));

        Assertions.assertEquals(0.0, accountRepository.findByAccountNumber(payeeAccountNumber).getBalance());
    }

    @Test
    public void test_fund_transfer_to_payee_with_mismatched_target_account() throws Exception {
        val amount = 100.0;
        val userDetails = createAndLoginUserWithInitialBalance(amount);
        val payeeId = addPayee(userDetails.get("token"), createAndLoginUser().get("accountNumber"));
        verifyPayee(userDetails.get("token"), payeeId, userDetails.get("pin"));
        val otherAccountNumber = createAndLoginUser().get("accountNumber");

        transfer(userDetails,
                new FundTransferRequest(null, otherAccountNumber, amount, userDetails.get("pin"), payeeId))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.PAYEE_TARGET_MISMATCH_ERROR.getMessage()));
    }

    @Test
    public void test_fund_transfer_to_payee_owned_by_another_user() throws Exception {
        val amount = 100.0;
        val ownerDetails = createAndLoginUserWithPin();
        val payeeId = addPayee(ownerDetails.get("token"), createAndLoginUser().get("accountNumber"));
        verifyPayee(ownerDetails.get("token"), payeeId, ownerDetails.get("pin"));
        val intruderDetails = createAndLoginUserWithInitialBalance(amount);

        transfer(intruderDetails, new FundTransferRequest(null, null, amount, intruderDetails.get("pin"), payeeId))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.content().string(ApiMessages.PAYEE_NOT_FOUND.getMessage()));
    }

}
