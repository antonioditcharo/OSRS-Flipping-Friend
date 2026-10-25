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
            char[] id = new char[67];id[0]='r';id[1]='s';id[2]='-';
            String hex="0123456789abcdef";
            for(int index=0;index<digest.length;index++) {
                id[3+index*2]=hex.charAt((digest[index]>>>4)&15);
                id[4+index*2]=hex.charAt(digest[index]&15);
            }
            return new String(id);
        }
        catch (NoSuchAlgorithmException impossible)
        {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public static String require(String accountId)
    {
        if (accountId == null || accountId.length()!=67 || !accountId.startsWith("rs-"))
            throw new IllegalArgumentException("valid account identity is required");
        for(int index=3;index<67;index++) {
            char part=accountId.charAt(index);
            if(!(part>='0' && part<='9' || part>='a' && part<='f'))
                throw new IllegalArgumentException("valid account identity is required");
        }
        return accountId;
    }
}
