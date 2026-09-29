package com.webapp.bankingportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import com.webapp.bankingportal.util.PaginationUtil;

import lombok.val;

public class PaginationUtilTests {

    @Test
    public void test_default_sort_is_transaction_date_desc_with_id_tiebreaker() {
        val sort = PaginationUtil.transactionSort(null);

        assertEquals(Sort.by(Sort.Direction.DESC, "transactionDate").and(Sort.by(Sort.Direction.DESC, "id")), sort);
        assertEquals("transactionDate,desc", PaginationUtil.describe(sort));
    }

    @Test
    public void test_sort_without_direction_defaults_to_desc() {
        assertEquals("amount,desc", PaginationUtil.describe(PaginationUtil.transactionSort("amount")));
    }

    @Test
    public void test_sort_direction_is_case_insensitive() {
        assertEquals("amount,asc", PaginationUtil.describe(PaginationUtil.transactionSort(" amount , ASC ")));
    }

    @Test
    public void test_sort_by_id_has_no_duplicate_tiebreaker() {
        assertEquals(Sort.by(Sort.Direction.ASC, "id"), PaginationUtil.transactionSort("id,asc"));
    }

    @Test
    public void test_invalid_sort_expressions_are_rejected() {
        assertThrows(IllegalArgumentException.class, () -> PaginationUtil.transactionSort("unknown"));
        assertThrows(IllegalArgumentException.class, () -> PaginationUtil.transactionSort("amount,up"));
        assertThrows(IllegalArgumentException.class, () -> PaginationUtil.transactionSort("amount,asc,extra"));
    }

    @Test
    public void test_pageable_bounds() {
        val pageable = PaginationUtil.transactionPageable(2, PaginationUtil.MAX_PAGE_SIZE, "amount,asc");
        assertEquals(2, pageable.getPageNumber());
        assertEquals(PaginationUtil.MAX_PAGE_SIZE, pageable.getPageSize());

        assertThrows(IllegalArgumentException.class, () -> PaginationUtil.transactionPageable(-1, 10, null));
        assertThrows(IllegalArgumentException.class, () -> PaginationUtil.transactionPageable(0, 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> PaginationUtil.transactionPageable(0, PaginationUtil.MAX_PAGE_SIZE + 1, null));
    }

}
