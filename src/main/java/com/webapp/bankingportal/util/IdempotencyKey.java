package com.webapp.bankingportal.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import lombok.val;

public final class IdempotencyKey {

    private IdempotencyKey() {
    }

    /**
     * Builds the idempotency cache key for the logged in user's request. Every
     * field the endpoint acts on is part of the key, so a cached response is only
     * replayed for an identical request (including PIN), and a client-supplied
     * {@code Idempotency-Key} header narrows it further. Fields are digested with
     * SHA-256 so secrets are not kept in the cache and collisions are negligible.
     */
    public static String of(String endpoint, String clientKey, Object... fields) {
        try {
            val digest = MessageDigest.getInstance("SHA-256");
            update(digest, clientKey);
            for (val field : fields) {
                update(digest, field);
            }
            return LoggedinUser.getAccountNumber() + ':' + endpoint + ':' + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void update(MessageDigest digest, Object value) {
        if (value == null) {
            digest.update((byte) 0);
            return;
        }
        val bytes = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
        digest.update((byte) 1);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
