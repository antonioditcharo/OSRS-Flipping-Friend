package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TradeReviewIndexTest {
    final String account=AccountIdentity.fromProfileKey("review-prefix");
    OfferEvent source(String id,String episode,String type,int sequence,long at) {
        var b=OfferEvent.builder("fixture",at,type).accountId(account).authenticatedObservation(true)
            .eventIdentity(id,"session",episode).slot(0).sequence(sequence);
        if(!"EMPTY".equals(type))b.item(561,"Nature rune").price(120).quantities(2,"SOLD".equals(type)?2:0)
            .spent("SOLD".equals(type)?240:0).firstSeenAt(10);
        return b.build();
    }
    List<OfferEvent> history(){return List.of(source("open","sale","SELLING",1,10),source("sold","sale","SOLD",2,20),source("clear",null,"EMPTY",3,30));}
    TradeReviewRead.Row row(){return new TradeReviewRead.Row(1,"sold","sale",561,false);}
    @Test public void changingFinancialCursorOrLiveHoldingsDoesNotRescanTheCanonicalPrefix() {
        var index=new TradeReviewRead.Index();var history=history();var rows=List.of(row());
        assertEquals(TradeReviewSnapshot.ELIGIBLE,index.read(account,40,1,rows,history,Set.of(),Set.of(),Set.of()).states.get("sold"));
        assertEquals(3,index.processedCount());
        assertEquals(TradeReviewSnapshot.OPEN,index.read(account,41,2,rows,history,Set.of(),Set.of(561),Set.of("sold")).states.get("sold"));
        assertEquals(TradeReviewSnapshot.ARCHIVED,index.read(account,42,3,rows,history,Set.of(),Set.of(),Set.of("sold")).states.get("sold"));
        assertEquals(3,index.processedCount());
        rows.get(0).purchaseRecorded=true;
        assertEquals(TradeReviewSnapshot.RETAINED,index.read(account,43,3,rows,history,Set.of(),Set.of(),Set.of("sold")).states.get("sold"));
        assertEquals(6,index.processedCount());
    }
    @Test public void validatedAppendReadsOnlyTheNewTailAndRejectedOrReplacedHistoryWithdrawsClosure() {
        var index=new TradeReviewRead.Index();var old=history();var rows=List.of(row());
        index.read(account,40,1,rows,old,Set.of(),Set.of(),Set.of());
        var all=new ArrayList<>(old);all.add(source("new-open","other","SELLING",4,40));
        List<OfferEvent> tailOnly=new AbstractList<>() {
            public int size(){return all.size();}
            public OfferEvent get(int i){if(i<old.size())throw new AssertionError("Historical prefix rescanned");return all.get(i);}
        };
        index.appendCanonical(old,tailOnly);
        assertEquals(TradeReviewSnapshot.ELIGIBLE,index.read(account,50,2,rows,tailOnly,Set.of(),Set.of(),Set.of()).states.get("sold"));
        assertEquals(4,index.processedCount());
        assertEquals(TradeReviewSnapshot.OPEN,index.read(account,50,2,rows,all,Set.of("clear"),Set.of(),Set.of()).states.get("sold"));
        assertEquals(TradeReviewSnapshot.OPEN,new TradeReviewRead.Index().read(account,50,2,rows,all,Set.of("clear"),Set.of(),Set.of()).states.get("sold"));
    }
    @Test public void foreignAppendsWithdrawDisplayClosureAndCannotReuseAnotherAccountsIndex() {
        var index=new TradeReviewRead.Index();var old=history();var rows=List.of(row());
        index.read(account,40,1,rows,old,Set.of(),Set.of(),Set.of());
        var all=new ArrayList<>(old);
        all.add(OfferEvent.builder("fixture",50,"SELLING").accountId(AccountIdentity.fromProfileKey("foreign"))
            .authenticatedObservation(true).eventIdentity("foreign","foreign-session","foreign-offer").slot(1)
            .sequence(1).item(562,"Chaos rune").price(100).quantities(1,0).spent(0).firstSeenAt(50).build());
        index.appendCanonical(old,all);
        assertEquals(TradeReviewSnapshot.OPEN,index.read(account,60,2,rows,all,Set.of(),Set.of(),Set.of()).states.get("sold"));
    }
    @Test public void futureSourcesAndClockReversalAreReclassifiedWithoutInventingClosure() {
        var index=new TradeReviewRead.Index();var rows=List.of(row());
        var history=List.of(source("open","sale","SELLING",1,10),source("sold","sale","SOLD",2,100),source("clear",null,"EMPTY",3,150));
        assertEquals(TradeReviewSnapshot.OPEN,index.read(account,120,1,rows,history,Set.of(),Set.of(),Set.of()).states.get("sold"));
        assertEquals(TradeReviewSnapshot.ELIGIBLE,index.read(account,160,1,rows,history,Set.of(),Set.of(),Set.of()).states.get("sold"));
        long processed=index.processedCount();index.read(account,161,1,rows,history,Set.of(),Set.of(),Set.of());assertEquals(processed,index.processedCount());
        assertEquals(TradeReviewSnapshot.OPEN,index.read(account,120,1,rows,history,Set.of(),Set.of(),Set.of()).states.get("sold"));
    }
}
