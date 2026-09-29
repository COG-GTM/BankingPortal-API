package com.webapp.bankingportal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.webapp.bankingportal.entity.Payee;

@Repository
public interface PayeeRepository extends JpaRepository<Payee, Long> {

    List<Payee> findAllByOwnerAccountAccountNumberOrderByCreatedAtAsc(String ownerAccountNumber);

    Optional<Payee> findByIdAndOwnerAccountAccountNumber(Long id, String ownerAccountNumber);

    boolean existsByOwnerAccountAccountNumberAndPayeeAccountAccountNumber(String ownerAccountNumber,
            String payeeAccountNumber);
}
