package com.webapp.bankingportal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.Token;

@Repository
public interface TokenRepository extends JpaRepository<Token, Long> {

    Token findByToken(String token);

    Token[] findAllByAccount(Account account);

    void deleteByToken(String token);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Token t where t.account = :account")
    int deleteAllByAccount(@Param("account") Account account);
}
