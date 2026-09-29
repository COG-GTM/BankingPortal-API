package com.webapp.bankingportal.entity;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

@Entity
@Data
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "owner_account_id", "payee_account_id" }))
public class Payee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "owner_account_id", nullable = false)
    private Account ownerAccount;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "payee_account_id", nullable = false)
    private Account payeeAccount;

    @NotEmpty
    @Column(length = 50)
    private String nickname;

    private boolean verified;

    private Date createdAt;

    private Date verifiedAt;

}
