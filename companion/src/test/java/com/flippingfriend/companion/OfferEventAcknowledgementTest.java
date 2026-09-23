package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OfferEventAcknowledgementTest
{
	private final Gson gson = new Gson();

	@Test public void acknowledgesNewCanonicalEvent()
	{
		OfferEvent event = OfferEvent.builder("trace", 1, "OBSERVED")
			.eventIdentity("event-1", "session-1", "offer-1").sequence(7).build();
		OfferEventAcknowledgement ack = OfferEventAcknowledgement.of(event, SqliteStore.EventAcceptance.NEW);
		assertTrue(ack.isAccepted()); assertFalse(ack.isDuplicate());
		assertEquals("event-1", ack.getEventId()); assertEquals("session-1", ack.getSessionId()); assertEquals(7, ack.getSequence());
	}

	@Test public void acknowledgesDuplicateCanonicalEventSuccessfully()
	{
		OfferEvent event = OfferEvent.builder("trace", 1, "OBSERVED").eventId("event-1").sequence(8).build();
		OfferEventAcknowledgement ack = OfferEventAcknowledgement.of(event, SqliteStore.EventAcceptance.DUPLICATE);
		assertTrue(ack.isAccepted()); assertTrue(ack.isDuplicate()); assertEquals(8, ack.getSequence());
	}

	@Test public void legacyAcknowledgementKeepsIdentityAbsent()
	{
		OfferEventAcknowledgement ack = OfferEventAcknowledgement.of(
			OfferEvent.builder("trace", 1, "EMPTY").build(), SqliteStore.EventAcceptance.NEW);
		assertTrue(ack.isAccepted()); assertFalse(ack.isDuplicate()); assertNull(ack.getEventId()); assertNull(ack.getSessionId());
	}

	@Test public void serializesTheServerContract()
	{
		OfferEvent event = OfferEvent.builder("trace", 1, "OBSERVED").eventIdentity("event-2", "session-2", "offer-2").sequence(9).build();
		String json = gson.toJson(OfferEventAcknowledgement.of(event, SqliteStore.EventAcceptance.DUPLICATE));
		assertTrue(json.contains("\"accepted\":true")); assertTrue(json.contains("\"duplicate\":true"));
		assertTrue(json.contains("\"eventId\":\"event-2\"")); assertTrue(json.contains("\"sessionId\":\"session-2\""));
		assertTrue(json.contains("\"sequence\":9"));
	}
}
