package com.flippingfriend.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class AccountIdentityTest
{
    @Test public void optimizedIdentityKeepsTheOriginalUtf8HashAndExactLowercaseScopeAlphabet() {
        String expected="rs-4041f52c8c4a9dc317372a802b1d53cd3e8e5fbe6989faec3cf19349e9f5316f";
        assertEquals(expected,AccountIdentity.fromProfileKey("preserved-account-\u00df-\u754c"));
        assertEquals(expected,AccountIdentity.require(expected));
        for(String invalid:new String[]{expected+"\n",expected.substring(0,66),"rs-"+"0".repeat(63)+"A",
                "rs-"+"0".repeat(63)+"\uff10","RS-"+"0".repeat(64)}) {
            try{AccountIdentity.require(invalid);fail("Invalid identity accepted");}catch(IllegalArgumentException required){}
        }
    }
    @Test public void identityIsStableOpaqueAndDoesNotCollapseSanitizedNames()
    {
        String first = AccountIdentity.fromProfileKey("one/two");
        assertEquals(first, AccountIdentity.fromProfileKey("one/two"));
        assertNotEquals(first, AccountIdentity.fromProfileKey("one_two"));
        assertEquals(first, AccountIdentity.require(first));
        assertFalse(first.contains("one"));
    }

    @Test public void refusesMissingAndPathLikeScopes()
    {
        for (String invalid : new String[] {null, "", "../other", "rs-123", "default"})
        {
            try { AccountIdentity.require(invalid); fail("invalid scope accepted"); }
            catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void attachingScopePreservesOfferIdentityAndEconomicEvidence()
    {
        OfferEvent event = OfferEvent.builder("correlation", 10, "BUYING")
            .eventIdentity("event", "session", "offer").slot(2).item(561, "Nature rune")
            .buying(true).price(101).quantities(10, 3).spent(300).sequence(7).firstSeenAt(5)
            .recommendation("plan", 100, 10, 4, 2.5, 0.8).build();
        OfferEvent scoped = event.withAccountId(AccountIdentity.fromProfileKey("one"));
        assertEquals(event.getEventId(), scoped.getEventId());
        assertEquals(event.getSessionId(), scoped.getSessionId());
        assertEquals(event.getOfferIdentity(), scoped.getOfferIdentity());
        assertEquals(event.getSpent(), scoped.getSpent());
        assertEquals(event.getSequence(), scoped.getSequence());
        assertEquals(event.getPredictedCompletion(), scoped.getPredictedCompletion(), 0);
        assertNull(event.getAccountId());
    }
}
