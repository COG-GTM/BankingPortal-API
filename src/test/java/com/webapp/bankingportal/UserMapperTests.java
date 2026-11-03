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
    public void test_update_user_copies_only_profile_fields() {
        val account = new Account();
        account.setId(42L);
        account.setAccountNumber("abc123");
        account.setBalance(100);
        account.setPin("pin-hash");

        val target = new User();
        target.setId(7L);
        target.setPassword("password-hash");
        target.setAccount(account);
        account.setUser(target);

        val source = new UpdateUserRequest("New Name", "new@example.com", "US", "2025550123",
                "1 New Street", "plaintext-password");

        userMapper.updateUser(source, target);

        Assertions.assertEquals("New Name", target.getName());
        Assertions.assertEquals("new@example.com", target.getEmail());
        Assertions.assertEquals("US", target.getCountryCode());
        Assertions.assertEquals("2025550123", target.getPhoneNumber());
        Assertions.assertEquals("1 New Street", target.getAddress());

        Assertions.assertEquals(7L, target.getId());
        Assertions.assertEquals("password-hash", target.getPassword());
        Assertions.assertSame(account, target.getAccount());
        Assertions.assertEquals(42L, account.getId());
        Assertions.assertEquals("abc123", account.getAccountNumber());
        Assertions.assertEquals(100, account.getBalance());
        Assertions.assertEquals("pin-hash", account.getPin());
    }

    @Test
    public void test_update_user_ignores_null_fields() {
        val target = new User();
        target.setName("Old Name");
        target.setEmail("old@example.com");

        userMapper.updateUser(new UpdateUserRequest(null, null, null, null, null, null), target);

        Assertions.assertEquals("Old Name", target.getName());
        Assertions.assertEquals("old@example.com", target.getEmail());
    }
}
