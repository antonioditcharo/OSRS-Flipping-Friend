package com.flippingfriend.companion;

import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

public class OfferEventAcknowledgementResponseTest
{
        @Test public void readsContractShapes()
        {
                Gson gson = new Gson();
                OfferEventAcknowledgementResponse fresh = gson.fromJson("{\"accepted\":true,\"duplicate\":false,\"eventId\":\"e\",\"sessionId\":\"s\",\"sequence\":7}", OfferEventAcknowledgementResponse.class);
                assertTrue(fresh.isAccepted()); assertFalse(fresh.isDuplicate()); assertEquals("e", fresh.getEventId()); assertEquals("s", fresh.getSessionId()); assertEquals(7, fresh.getSequence());
                OfferEventAcknowledgementResponse duplicate = gson.fromJson("{\"accepted\":true,\"duplicate\":true,\"eventId\":\"e\",\"sessionId\":\"s\",\"sequence\":8}", OfferEventAcknowledgementResponse.class);
                assertTrue(duplicate.isDuplicate());
                OfferEventAcknowledgementResponse rejected = gson.fromJson("{\"accepted\":false}", OfferEventAcknowledgementResponse.class);
                assertFalse(rejected.isAccepted()); assertNull(rejected.getEventId()); assertNull(rejected.getSessionId());
        }
}
