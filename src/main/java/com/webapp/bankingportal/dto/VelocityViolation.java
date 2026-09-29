package com.webapp.bankingportal.dto;

import com.webapp.bankingportal.entity.VelocityRule;

public record VelocityViolation(VelocityRule rule, String detail) {
}
