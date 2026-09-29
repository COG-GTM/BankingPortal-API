package com.webapp.bankingportal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.webapp.bankingportal.entity.OtpInfo;

@Repository
public interface OtpInfoRepository extends JpaRepository<OtpInfo, Long> {

    OtpInfo findByAccountNumber(String accountNumber);

    /**
     * Atomically records a verification attempt if the OTP still has attempts left.
     *
     * @return 1 if an attempt was reserved, 0 if the attempt limit is reached or the OTP no longer exists
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OtpInfo o SET o.failedAttempts = o.failedAttempts + 1 "
            + "WHERE o.id = :id AND o.failedAttempts < :maxAttempts")
    int reserveVerificationAttempt(@Param("id") Long id, @Param("maxAttempts") int maxAttempts);

    /**
     * Atomically consumes an OTP so that it can be used at most once.
     *
     * @return 1 if this call consumed the OTP, 0 if it was already consumed
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM OtpInfo o WHERE o.id = :id")
    int consumeById(@Param("id") Long id);
}
