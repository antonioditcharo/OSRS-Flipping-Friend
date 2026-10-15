package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;

/** Durable prospective cursor in existing event_log metadata, never a reconstructed session. */
final class RiskWindow {
    static final String EVENT_TYPE="RISK_WINDOW_V1";
    private final String schemaVersion;
    final String accountId, windowId, sessionId;
    final long startedAt, positionCursor, startingCapital;
    private final Map<Integer,String> sourceReadSet;

    RiskWindow(AccountSnapshot snapshot,String session,long cursor,long capital) {
        schemaVersion="1";
        accountId=AccountIdentity.require(snapshot.getAccountId());windowId=UUID.randomUUID().toString();sessionId=session;
        startedAt=snapshot.getObservedAt();positionCursor=cursor;startingCapital=capital;
        sourceReadSet=Collections.unmodifiableMap(new HashMap<>(snapshot.getOfferReadSet()));
        if(!isValid())throw new IllegalArgumentException("invalid risk window");
    }
    boolean isValid() {
        if(!"1".equals(schemaVersion) || accountId==null || !accountId.matches("rs-[0-9a-f]{64}")
            || windowId==null || windowId.isBlank() || sessionId==null || sessionId.isBlank()
            || startedAt<=0 || positionCursor<0 || startingCapital<=0 || sourceReadSet==null || sourceReadSet.size()!=8
            || new HashSet<>(sourceReadSet.values()).size()!=8)return false;
        for(int slot=0;slot<8;slot++)if(sourceReadSet.get(slot)==null || sourceReadSet.get(slot).isBlank())return false;
        return true;
    }
    static String correlation(String session) {return "risk-window-v1:"+session;}
    String sourceForSlot(int slot) { return sourceReadSet.get(slot); }
}
