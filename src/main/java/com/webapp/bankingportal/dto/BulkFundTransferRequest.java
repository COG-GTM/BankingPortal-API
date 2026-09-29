package com.webapp.bankingportal.dto;

import java.util.List;

/**
 * A batch of transfers from the logged-in account, authorized by a single PIN.
 *
 * @param atomic when true, either every transfer is applied or none are;
 *               when false (default), each transfer succeeds or fails on its own.
 */
public record BulkFundTransferRequest(String pin, List<BulkTransferItem> transfers, boolean atomic) {
}
