package com.webapp.bankingportal.dto;

import java.util.List;

public record BalanceAuditTrailResponse(
        List<BalanceAuditLogResponse> entries,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
