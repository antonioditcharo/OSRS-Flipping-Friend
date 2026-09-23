package com.flippingfriend.companion;

final class OfferEventAcknowledgementResponse
{
        private boolean accepted;
        private boolean duplicate;
        private String eventId;
        private String sessionId;
        private long sequence;
        boolean isAccepted() { return accepted; }
        boolean isDuplicate() { return duplicate; }
        String getEventId() { return eventId; }
        String getSessionId() { return sessionId; }
        long getSequence() { return sequence; }
}
