package com.webapp.bankingportal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.webapp.bankingportal.dto.UpdateUserRequest;
import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.User;
import com.webapp.bankingportal.mapper.UserMapperImpl;

import lombok.val;

public class UserMapperTests {

    private final UserMapperImpl userMapper = new UserMapperImpl();

    @Test
    public void test_update_user_does_not_touch_id_account_or_password() {
        val account = new Account();
        account.setId(10L);
        account.setAccountNumber("abc123");
        account.setBalance(50.0);

        val target = new User();
        target.setId(7L);
        target.setPassword("encoded-password");
        target.setAccount(account);

        val request = new UpdateUserRequest("New Name", "new@example.com", "US", "+12025550123",
                "New Address", "plain-password");

        userMapper.updateUser(request, target);

        Assertions.assertEquals(7L, target.getId());
        Assertions.assertSame(account, target.getAccount());
        Assertions.assertEquals(50.0, target.getAccount().getBalance());
        Assertions.assertEquals("encoded-password", target.getPassword());
        Assertions.assertEquals("New Name", target.getName());
        Assertions.assertEquals("new@example.com", target.getEmail());
        Assertions.assertEquals("US", target.getCountryCode());
        Assertions.assertEquals("+12025550123", target.getPhoneNumber());
        Assertions.assertEquals("New Address", target.getAddress());
    }

    @Test
    public void test_to_user_does_not_set_id_or_account() {
        val request = new UpdateUserRequest("Name", "a@example.com", "US", "+12025550123", "Addr", "pw");

        val user = userMapper.toUser(request);

        Assertions.assertNull(user.getId());
        Assertions.assertNull(user.getAccount());
        Assertions.assertEquals("pw", user.getPassword());
    }
}
