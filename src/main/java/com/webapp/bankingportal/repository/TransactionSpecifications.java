package com.webapp.bankingportal.repository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;

import org.springframework.data.jpa.domain.Specification;

import com.webapp.bankingportal.dto.TransactionSearchCriteria;
import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.Transaction;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.val;

public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    /**
     * Builds a specification matching transactions where the given account is
     * either the source or the target, narrowed by the optional criteria.
     * Date bounds are inclusive calendar days in the server's time zone.
     */
    public static Specification<Transaction> forAccount(String accountNumber, TransactionSearchCriteria criteria) {
        return (root, query, cb) -> {
            val source = root.<Transaction, Account>join("sourceAccount", JoinType.LEFT);
            val target = root.<Transaction, Account>join("targetAccount", JoinType.LEFT);

            val predicates = new ArrayList<Predicate>();
            predicates.add(cb.or(
                    cb.equal(source.get("accountNumber"), accountNumber),
                    cb.equal(target.get("accountNumber"), accountNumber)));

            if (criteria.startDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.<Date>get("transactionDate"), startOfDay(criteria.startDate())));
            }
            if (criteria.endDate() != null) {
                predicates.add(cb.lessThan(
                        root.<Date>get("transactionDate"), startOfDay(criteria.endDate().plusDays(1))));
            }
            if (criteria.transactionTypes() != null && !criteria.transactionTypes().isEmpty()) {
                predicates.add(root.get("transactionType").in(criteria.transactionTypes()));
            }
            if (criteria.minAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Double>get("amount"), criteria.minAmount()));
            }
            if (criteria.maxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Double>get("amount"), criteria.maxAmount()));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Date startOfDay(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

}
