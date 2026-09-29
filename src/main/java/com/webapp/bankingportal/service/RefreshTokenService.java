package com.webapp.bankingportal.service;

import com.webapp.bankingportal.dto.RefreshTokenRotation;
import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.exception.InvalidTokenException;

public interface RefreshTokenService {

    /**
     * Issues a refresh token that starts a new token family.
     *
     * @return the raw refresh token; only its hash is stored
     */
    public String issue(Account account);

    /**
     * Revokes the presented refresh token and issues its successor in the same
     * family. Presenting an already-revoked token is treated as reuse: the whole
     * family and all of the account's access tokens are revoked.
     */
    public RefreshTokenRotation rotate(String rawToken) throws InvalidTokenException;

    /**
     * Revokes the family of the presented refresh token. Unknown tokens are ignored.
     */
    public void revoke(String rawToken);

    public void revokeAllForAccount(Account account);
}
