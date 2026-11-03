package com.webapp.bankingportal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.webapp.bankingportal.entity.OtpInfo;

@Repository
public interface OtpInfoRepository extends JpaRepository<OtpInfo, Long> {

    OtpInfo findByAccountNumberAndOtp(String accountNumber, String otp);

    OtpInfo findByAccountNumber(String accountNumber);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE OtpInfo o SET o.verificationAttempts = o.verificationAttempts + 1 "
            + "WHERE o.id = :id AND o.verificationAttempts < :maxAttempts")
    int incrementVerificationAttempts(@Param("id") Long id, @Param("maxAttempts") int maxAttempts);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM OtpInfo o WHERE o.id = :id")
    int deleteOtpInfoById(@Param("id") Long id);
}
