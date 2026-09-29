package com.webapp.bankingportal.entity;

import java.time.Instant;

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

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Opaque refresh token. Only the SHA-256 hash of the token is persisted.
 * Tokens issued from the same login share a {@code familyId}; each rotation
 * revokes the presented token and issues a successor in the same family.
 */
@Entity
@Table(name = "refresh_token", indexes = @Index(name = "idx_refresh_token_family", columnList = "familyId"))
@Getter
@Setter
@NoArgsConstructor
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
    private Instant createdAt;

    @NotNull
    private Instant expiresAt;

    private Instant revokedAt;

    @Column(length = 64)
    private String replacedByHash;

    public RefreshToken(String tokenHash, String familyId, Account account, Instant createdAt, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.account = account;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

}
