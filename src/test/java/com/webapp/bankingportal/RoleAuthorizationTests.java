package com.webapp.bankingportal;

import java.util.HashMap;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.webapp.bankingportal.dto.RoleUpdateRequest;
import com.webapp.bankingportal.entity.Role;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.JsonUtil;

import lombok.val;

public class RoleAuthorizationTests extends BaseTest {

    private HashMap<String, String> createAndLoginAdmin() throws Exception {
        val adminDetails = createAndLoginUser();
        setRole(adminDetails.get("accountNumber"), Role.ADMIN);
        return adminDetails;
    }

    private void setRole(String accountNumber, Role role) {
        val user = userRepository.findByAccountAccountNumber(accountNumber).get();
        user.setRole(role);
        userRepository.save(user);
    }

    private Role getRole(String accountNumber) {
        return userRepository.findByAccountAccountNumber(accountNumber).get().getRole();
    }

    private static String bearer(HashMap<String, String> userDetails) {
        return "Bearer " + userDetails.get("token");
    }

    @Test
    public void test_registered_user_defaults_to_customer_role() throws Exception {
        val customer = createAndLoginUser();

        Assertions.assertEquals(Role.CUSTOMER, getRole(customer.get("accountNumber")));

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/dashboard/user")
                .header("Authorization", bearer(customer)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.role").value(Role.CUSTOMER.name()));
    }

    @Test
    public void test_register_ignores_client_supplied_role() throws Exception {
        val user = createUser();
        val json = JsonUtil.toJson(user);
        val jsonWithRole = json.substring(0, json.length() - 1) + ",\"role\":\"ADMIN\"}";

        mockMvc.perform(MockMvcRequestBuilders
                .post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonWithRole))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.role").value(Role.CUSTOMER.name()));

        Assertions.assertEquals(Role.CUSTOMER, userRepository.findByEmail(user.getEmail()).get().getRole());
    }

    @Test
    public void test_update_user_cannot_escalate_role() throws Exception {
        val customer = createAndLoginUser();

        val updatedUser = createUser();
        updatedUser.setPassword(customer.get("password"));
        updatedUser.setPhoneNumber(getRandomPhoneNumber(customer.get("countryCode")));
        val json = JsonUtil.toJson(updatedUser);
        val jsonWithRole = json.substring(0, json.length() - 1) + ",\"role\":\"ADMIN\"}";

        mockMvc.perform(MockMvcRequestBuilders
                .post("/api/users/update")
                .header("Authorization", bearer(customer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonWithRole))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.role").value(Role.CUSTOMER.name()));

        Assertions.assertEquals(Role.CUSTOMER, getRole(customer.get("accountNumber")));
    }

    @Test
    public void test_customer_can_access_customer_endpoints() throws Exception {
        val customer = createAndLoginUser();

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/dashboard/user")
                .header("Authorization", bearer(customer)))
                .andExpect(MockMvcResultMatchers.status().isOk());

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/dashboard/account")
                .header("Authorization", bearer(customer)))
                .andExpect(MockMvcResultMatchers.status().isOk());

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/account/pin/check")
                .header("Authorization", bearer(customer)))
                .andExpect(MockMvcResultMatchers.status().isOk());

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/account/transactions")
                .header("Authorization", bearer(customer)))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    public void test_customer_cannot_list_users() throws Exception {
        val customer = createAndLoginUser();

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/admin/users")
                .header("Authorization", bearer(customer)))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.content().string(ApiMessages.ACCESS_DENIED.getMessage()));
    }

    @Test
    public void test_customer_cannot_change_roles() throws Exception {
        val customer = createAndLoginUser();

        mockMvc.perform(MockMvcRequestBuilders
                .put("/api/admin/users/" + customer.get("accountNumber") + "/role")
                .header("Authorization", bearer(customer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(new RoleUpdateRequest(Role.ADMIN))))
                .andExpect(MockMvcResultMatchers.status().isForbidden());

        Assertions.assertEquals(Role.CUSTOMER, getRole(customer.get("accountNumber")));
    }

    @Test
    public void test_customer_cannot_access_actuator() throws Exception {
        val customer = createAndLoginUser();

        mockMvc.perform(MockMvcRequestBuilders
                .get("/actuator/metrics")
                .header("Authorization", bearer(customer)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    public void test_admin_can_list_users() throws Exception {
        val admin = createAndLoginAdmin();
        val customer = createAndLoginUser();

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/admin/users")
                .header("Authorization", bearer(admin)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$[*].accountNumber",
                        Matchers.hasItems(admin.get("accountNumber"), customer.get("accountNumber"))));
    }

    @Test
    public void test_admin_can_change_roles() throws Exception {
        val admin = createAndLoginAdmin();
        val customer = createAndLoginUser();

        mockMvc.perform(MockMvcRequestBuilders
                .put("/api/admin/users/" + customer.get("accountNumber") + "/role")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(new RoleUpdateRequest(Role.ADMIN))))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.role").value(Role.ADMIN.name()));

        Assertions.assertEquals(Role.ADMIN, getRole(customer.get("accountNumber")));
    }

    @Test
    public void test_admin_change_role_requires_role() throws Exception {
        val admin = createAndLoginAdmin();
        val customer = createAndLoginUser();

        mockMvc.perform(MockMvcRequestBuilders
                .put("/api/admin/users/" + customer.get("accountNumber") + "/role")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content().string(ApiMessages.USER_ROLE_MISSING_ERROR.getMessage()));
    }

    @Test
    public void test_admin_cannot_access_customer_endpoints() throws Exception {
        val admin = createAndLoginAdmin();

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/dashboard/user")
                .header("Authorization", bearer(admin)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/account/pin/check")
                .header("Authorization", bearer(admin)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/account/transactions")
                .header("Authorization", bearer(admin)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    public void test_role_change_applies_to_existing_token() throws Exception {
        val user = createAndLoginUser();

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/admin/users")
                .header("Authorization", bearer(user)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());

        setRole(user.get("accountNumber"), Role.ADMIN);

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/admin/users")
                .header("Authorization", bearer(user)))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    public void test_both_roles_can_logout() throws Exception {
        val customer = createAndLoginUser();
        val admin = createAndLoginAdmin();

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/users/logout")
                .header("Authorization", bearer(customer)))
                .andExpect(MockMvcResultMatchers.status().isFound());

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/users/logout")
                .header("Authorization", bearer(admin)))
                .andExpect(MockMvcResultMatchers.status().isFound());
    }

    @Test
    public void test_unauthenticated_requests_are_rejected() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/users"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/dashboard/user"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());

        mockMvc.perform(MockMvcRequestBuilders.get("/actuator/metrics"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    public void test_actuator_health_is_public() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/actuator/health"))
                .andExpect(MockMvcResultMatchers.status().is(Matchers.not(Matchers.anyOf(
                        Matchers.is(401), Matchers.is(403)))));
    }

}
