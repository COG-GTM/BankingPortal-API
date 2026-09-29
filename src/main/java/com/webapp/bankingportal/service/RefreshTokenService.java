package com.webapp.bankingportal.service;

import com.webapp.bankingportal.dto.TokenPair;
import com.webapp.bankingportal.exception.InvalidTokenException;

public interface RefreshTokenService {

    /**
     * Issues an access token and a refresh token that starts a new token family.
     */
    public TokenPair issueTokenPair(String accountNumber) throws InvalidTokenException;

    /**
     * Exchanges a refresh token for a new token pair in the same family. The
     * presented token is consumed; presenting it again is treated as theft and
     * revokes the entire family, including its access tokens.
     */
    public TokenPair rotate(String refreshToken) throws InvalidTokenException;

    public void revokeFamily(String familyId);

    public void revokeFamilyOfAccessToken(String accessToken);
}
