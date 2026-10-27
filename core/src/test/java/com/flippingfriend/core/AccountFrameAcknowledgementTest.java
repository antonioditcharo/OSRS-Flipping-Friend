package com.flippingfriend.core;

import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

public class AccountFrameAcknowledgementTest {
    private final Gson json=new Gson();
    private AccountSnapshot snapshot() {
        var raw=new com.google.gson.JsonObject();raw.addProperty("accountId",AccountIdentity.fromProfileKey("frame"));
        raw.addProperty("correlationId","frame-correlation");raw.addProperty("observedAt",1800000000L);raw.addProperty("automaticTradeScope",true);
        var sources=new com.google.gson.JsonObject();for(int i=0;i<8;i++)sources.addProperty(Integer.toString(i),"source-"+i);raw.add("offerReadSet",sources);
        return json.fromJson(raw,AccountSnapshot.class);
    }
    @Test public void aVerifiedReceiptMatchesOnlyItsExactAccountFrameAndReadSet() {
        var frame=snapshot();var ack=AccountFrameAcknowledgement.of(frame,true);assertTrue(ack.matches(frame));assertTrue(ack.isCurrentFrameVerified());
        for(String key:new String[]{"accountId","correlationId","observedAt","schemaVersion","accepted","offerReadSet"}) {
            var raw=json.toJsonTree(ack).getAsJsonObject();raw.remove(key);
            assertFalse("Missing "+key+" cannot count as a verified frame",json.fromJson(raw,AccountFrameAcknowledgement.class).matches(frame));
        }
        var raw=json.toJsonTree(ack).getAsJsonObject();raw.addProperty("schemaVersion",2);
        assertFalse(json.fromJson(raw,AccountFrameAcknowledgement.class).matches(frame));
        raw=json.toJsonTree(ack).getAsJsonObject();raw.getAsJsonObject("offerReadSet").addProperty("1","source-0");
        assertFalse(json.fromJson(raw,AccountFrameAcknowledgement.class).matches(frame));
        assertFalse(json.fromJson("{\"accepted\":true}",AccountFrameAcknowledgement.class).matches(frame));
    }
    @Test public void acceptedUnverifiedPublicationDoesNotClaimCurrentStateOrAcknowledgeOffers() {
        var frame=snapshot();var ack=AccountFrameAcknowledgement.of(frame,false);assertTrue(ack.matches(frame));assertFalse(ack.isCurrentFrameVerified());
        String wire=json.toJson(ack);assertFalse(wire.contains("sequence"));assertFalse(wire.contains("duplicate"));assertFalse(wire.contains("sessionId"));
    }
}
