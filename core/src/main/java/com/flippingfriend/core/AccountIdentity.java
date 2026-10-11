package com.flippingfriend.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Stable opaque scope derived from the same RuneLite profile that owns local records. */
public final class AccountIdentity
{
    public static final String HEADER = "X-Flipping-Friend-Account";
    public static final String PROTOCOL_HEADER = "X-Flipping-Friend-Protocol";
    public static final String PROTOCOL = "accounts-v1";

    private AccountIdentity() { }

    public static String fromProfileKey(String profileKey)
    {
        if (profileKey == null || profileKey.isBlank())
            throw new IllegalArgumentException("account profile is required");
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(profileKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder id = new StringBuilder("rs-");
            for (byte part : digest) id.append(String.format("%02x", part & 0xff));
            return id.toString();
        }
        catch (NoSuchAlgorithmException impossible)
        {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public static String require(String accountId)
    {
        if (accountId == null || !accountId.matches("rs-[0-9a-f]{64}"))
            throw new IllegalArgumentException("valid account identity is required");
        return accountId;
    }
}
