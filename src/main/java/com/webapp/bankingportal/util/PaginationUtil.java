package com.webapp.bankingportal.util;

import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import lombok.val;

public final class PaginationUtil {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final String DEFAULT_TRANSACTION_SORT = "transactionDate,desc";

    private static final Map<String, String> TRANSACTION_SORT_FIELDS = Map.of(
            "transactionDate", "transactionDate",
            "amount", "amount",
            "transactionType", "transactionType",
            "id", "id");

    private static final String ALLOWED_TRANSACTION_SORT_FIELDS = String.join(", ",
            TRANSACTION_SORT_FIELDS.keySet().stream().sorted().toList());

    private PaginationUtil() {
    }

    public static Pageable transactionPageable(int page, int size, String sort) {
        if (page < 0) {
            throw new IllegalArgumentException(ApiMessages.PAGE_NUMBER_INVALID_ERROR.getMessage());
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    String.format(ApiMessages.PAGE_SIZE_INVALID_ERROR.getMessage(), MAX_PAGE_SIZE));
        }

        return PageRequest.of(page, size, transactionSort(sort));
    }

    /**
     * Parses a sort expression of the form {@code field} or {@code field,direction}
     * and appends {@code id} as a tie-breaker so page boundaries are stable.
     */
    public static Sort transactionSort(String sort) {
        val expression = sort == null || sort.isBlank() ? DEFAULT_TRANSACTION_SORT : sort.trim();
        val parts = expression.split(",", -1);
        if (parts.length > 2) {
            throw new IllegalArgumentException(
                    String.format(ApiMessages.SORT_FIELD_INVALID_ERROR.getMessage(), expression,
                            ALLOWED_TRANSACTION_SORT_FIELDS));
        }

        val field = TRANSACTION_SORT_FIELDS.get(parts[0].trim());
        if (field == null) {
            throw new IllegalArgumentException(
                    String.format(ApiMessages.SORT_FIELD_INVALID_ERROR.getMessage(), parts[0].trim(),
                            ALLOWED_TRANSACTION_SORT_FIELDS));
        }

        var direction = Sort.Direction.DESC;
        if (parts.length == 2) {
            direction = switch (parts[1].trim().toLowerCase(Locale.ROOT)) {
                case "asc" -> Sort.Direction.ASC;
                case "desc" -> Sort.Direction.DESC;
                default -> throw new IllegalArgumentException(
                        String.format(ApiMessages.SORT_DIRECTION_INVALID_ERROR.getMessage(), parts[1].trim()));
            };
        }

        val primary = Sort.by(direction, field);
        return "id".equals(field) ? primary : primary.and(Sort.by(direction, "id"));
    }

    public static String describe(Sort sort) {
        val order = sort.iterator().next();
        return order.getProperty() + "," + order.getDirection().name().toLowerCase(Locale.ROOT);
    }

}
