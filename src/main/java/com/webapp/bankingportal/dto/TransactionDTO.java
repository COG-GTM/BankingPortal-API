package com.webapp.bankingportal.dto;

import java.util.Date;

import com.webapp.bankingportal.entity.Transaction;
import com.webapp.bankingportal.entity.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.val;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A single account transaction")
public class TransactionDTO {

    @Schema(example = "42")
    private Long id;

    @Schema(example = "1000.0")
    private double amount;

    @Schema(example = "CASH_TRANSFER")
    private TransactionType transactionType;

    @Schema(description = "Transaction time in epoch milliseconds", type = "integer", format = "int64",
            example = "1727600000000")
    private Date transactionDate;

    @Schema(description = "Account that initiated the transaction", example = "a1c3d9")
    private String sourceAccountNumber;

    @Schema(description = "Credited account for transfers; `N/A` for deposits and withdrawals", example = "b7e2f1")
    private String targetAccountNumber;

    public TransactionDTO(Transaction transaction) {
        this.id = transaction.getId();
        this.amount = transaction.getAmount();
        this.transactionType = transaction.getTransactionType();
        this.transactionDate = transaction.getTransactionDate();
        this.sourceAccountNumber = transaction.getSourceAccount().getAccountNumber();

        val targetAccount = transaction.getTargetAccount();
        var targetAccountNumber = "N/A";
        if (targetAccount != null) {
            targetAccountNumber = targetAccount.getAccountNumber();
        }

        this.targetAccountNumber = targetAccountNumber;
    }

}
