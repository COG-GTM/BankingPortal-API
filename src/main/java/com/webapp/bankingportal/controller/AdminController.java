package com.webapp.bankingportal.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.webapp.bankingportal.dto.RoleUpdateRequest;
import com.webapp.bankingportal.service.UserService;
import com.webapp.bankingportal.util.JsonUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    @GetMapping("/users")
    public ResponseEntity<String> getAllUsers() {
        return ResponseEntity.ok(JsonUtil.toJson(userService.getAllUsers()));
    }

    @PutMapping("/users/{accountNumber}/role")
    public ResponseEntity<String> updateUserRole(@PathVariable String accountNumber,
            @RequestBody RoleUpdateRequest roleUpdateRequest) {
        return ResponseEntity.ok(JsonUtil.toJson(
                userService.updateUserRole(accountNumber, roleUpdateRequest.role())));
    }

}
