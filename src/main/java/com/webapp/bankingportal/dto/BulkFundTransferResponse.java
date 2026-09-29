package com.webapp.bankingportal.dto;

import java.util.List;

public record BulkFundTransferResponse(
        BatchStatus status,
        boolean atomic,
        int total,
        int succeeded,
        int failed,
        int skipped,
        double totalAmountTransferred,
        List<BulkTransferItemResult> results) {

    public enum BatchStatus {
        COMPLETED,
        PARTIALLY_COMPLETED,
        FAILED
    }

    public static BulkFundTransferResponse of(boolean atomic, List<BulkTransferItemResult> results) {
        int succeeded = 0;
        int failed = 0;
        int skipped = 0;
        double totalAmountTransferred = 0;

        for (BulkTransferItemResult result : results) {
            switch (result.status()) {
                case SUCCESS -> {
                    succeeded++;
                    totalAmountTransferred += result.amount();
                }
                case FAILED -> failed++;
                case SKIPPED -> skipped++;
            }
        }

        BatchStatus status;
        if (succeeded == results.size()) {
            status = BatchStatus.COMPLETED;
        } else if (succeeded == 0) {
            status = BatchStatus.FAILED;
        } else {
            status = BatchStatus.PARTIALLY_COMPLETED;
        }

        return new BulkFundTransferResponse(status, atomic, results.size(), succeeded, failed, skipped,
                totalAmountTransferred, List.copyOf(results));
    }

}
