package com.webapp.bankingportal;

import java.time.LocalDateTime;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.jayway.jsonpath.JsonPath;
import com.webapp.bankingportal.util.ApiMessages;

import lombok.val;

public class ScheduledTransferControllerTests extends BaseTest {

    private static String requestBody(String targetAccountNumber, double amount, LocalDateTime scheduledAt,
            String pin) {
        return String.format(
                "{\"targetAccountNumber\":\"%s\",\"amount\":%s,\"scheduledAt\":\"%s\",\"pin\":\"%s\"}",
                targetAccountNumber, amount, scheduledAt, pin);
    }

    @Test
    public void test_schedule_list_and_cancel_transfer() throws Exception {
        val userDetails = createAndLoginUserWithPin();
        val target = createAccount();
        val token = "Bearer " + userDetails.get("token");

        val result = mockMvc.perform(MockMvcRequestBuilders
                .post("/api/account/scheduled-transfers")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(target.get("accountNumber"), 200.0, LocalDateTime.now().plusDays(1),
                        userDetails.get("pin"))))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("PENDING"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.sourceAccountNumber")
                        .value(userDetails.get("accountNumber")))
                .andExpect(MockMvcResultMatchers.jsonPath("$.targetAccountNumber")
                        .value(target.get("accountNumber")))
                .andReturn();

        Integer id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/account/scheduled-transfers")
                .header("Authorization", token))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].id").value(id));

        mockMvc.perform(MockMvcRequestBuilders
                .delete("/api/account/scheduled-transfers/" + id)
                .header("Authorization", token))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.SCHEDULED_TRANSFER_CANCEL_SUCCESS.getMessage()));

        mockMvc.perform(MockMvcRequestBuilders
                .delete("/api/account/scheduled-transfers/" + id)
                .header("Authorization", token))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.SCHEDULED_TRANSFER_NOT_PENDING_ERROR.getMessage()));
    }

    @Test
    public void test_schedule_transfer_in_past() throws Exception {
        val userDetails = createAndLoginUserWithPin();
        val target = createAccount();

        mockMvc.perform(MockMvcRequestBuilders
                .post("/api/account/scheduled-transfers")
                .header("Authorization", "Bearer " + userDetails.get("token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(target.get("accountNumber"), 200.0, LocalDateTime.now().minusDays(1),
                        userDetails.get("pin"))))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.SCHEDULED_TRANSFER_DATE_IN_PAST_ERROR.getMessage()));
    }

    @Test
    public void test_cancel_other_users_transfer_returns_not_found() throws Exception {
        val owner = createAndLoginUserWithPin();
        val other = createAndLoginUserWithPin();
        val target = createAccount();

        val result = mockMvc.perform(MockMvcRequestBuilders
                .post("/api/account/scheduled-transfers")
                .header("Authorization", "Bearer " + owner.get("token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(target.get("accountNumber"), 200.0, LocalDateTime.now().plusDays(1),
                        owner.get("pin"))))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andReturn();
        Integer id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(MockMvcRequestBuilders
                .delete("/api/account/scheduled-transfers/" + id)
                .header("Authorization", "Bearer " + other.get("token")))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.content()
                        .string(CoreMatchers.containsString(ApiMessages.SCHEDULED_TRANSFER_NOT_FOUND.getMessage())));
    }

    @Test
    public void test_schedule_transfer_without_authorization() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                .post("/api/account/scheduled-transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody("123456", 200.0, LocalDateTime.now().plusDays(1), "1234")))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

}
