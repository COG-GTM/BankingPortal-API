package com.webapp.bankingportal.entity;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Opaque, single-use refresh token. Only the SHA-256 hash of the token value
 * is stored. Every token issued from one login shares a {@code familyId}, so
 * the whole chain can be revoked when a rotated token is presented again.
 */
@Entity
@Table(indexes = @Index(name = "idx_refresh_token_family", columnList = "familyId"))
@NoArgsConstructor
@Data
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotEmpty
    @Column(unique = true, nullable = false, length = 64)
    private String tokenHash;

    @NotEmpty
    @Column(nullable = false, length = 36)
    private String familyId;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "account_id")
    private Account account;

    @NotNull
    private Date createdAt = new Date();

    @NotNull
    private Date expiresAt;

    private Date usedAt;

    private Date revokedAt;

    public RefreshToken(String tokenHash, String familyId, Account account, Date expiresAt) {
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.account = account;
        this.expiresAt = expiresAt;
    }

}
