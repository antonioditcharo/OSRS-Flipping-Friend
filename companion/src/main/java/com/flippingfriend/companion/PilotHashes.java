package com.flippingfriend.companion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class PilotHashes {
    static final String GENESIS="0".repeat(64);
    static String sha(String value) {
        try {
            var result=new StringBuilder();
            for(byte b:MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)))
                result.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            return result.toString();
        }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
