package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;

final class OfferEventAcknowledgement
{
	private final boolean accepted;
	private final boolean duplicate;
	private final String eventId;
	private final String sessionId;
	private final long sequence;

	private OfferEventAcknowledgement(boolean duplicate, String eventId, String sessionId, long sequence)
	{
		this.accepted = true;
		this.duplicate = duplicate;
		this.eventId = eventId;
		this.sessionId = sessionId;
		this.sequence = sequence;
	}

	static OfferEventAcknowledgement of(OfferEvent event, SqliteStore.EventAcceptance acceptance)
	{
		if (event == null) throw new IllegalArgumentException("event is required");
		if (acceptance == null) throw new IllegalArgumentException("acceptance is required");
		return new OfferEventAcknowledgement(acceptance == SqliteStore.EventAcceptance.DUPLICATE,
			event.getEventId(), event.getSessionId(), event.getSequence());
	}

	boolean isAccepted() { return accepted; }
	boolean isDuplicate() { return duplicate; }
	String getEventId() { return eventId; }
	String getSessionId() { return sessionId; }
	long getSequence() { return sequence; }
}
