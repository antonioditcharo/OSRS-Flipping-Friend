package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TradeGapEvidenceTest {
    private OfferEvent frame(String id,long at,long seq,int filled,String episode) {
        return OfferEvent.builder("fixture",at,"BUYING").accountId(AccountIdentity.fromProfileKey("fixture"))
            .eventIdentity(id,"s",episode).authenticatedObservation(true).slot(7).sequence(seq)
            .item(389,"Raw manta ray").buying(true).price(910).quantities(5866,filled).spent(filled*910L).build();
    }
    @Test public void anOvertakenUnchangedFrameAndRecoveryStayItemScoped() {
        var history=List.of(frame("newer",103,1,1448,"episode"),frame("delayed",102,2,1448,"episode"),
            frame("baseline",104,3,1448,"episode"));
        var scopes=scope(history,Map.of("delayed",389,"baseline",389));
        assertEquals(Set.of(389),scopes.get("delayed"));assertEquals(Set.of(389),scopes.get("baseline"));
    }
    @Test public void regressingTimeWithDifferentCountersOrEpisodeRemainsUnbounded() {
        for(var changed:List.of(frame("gap",102,2,1449,"episode"),frame("gap",102,2,1448,"other"))) {
            var scopes=scope(List.of(frame("newer",103,1,1448,"episode"),changed,frame("baseline",104,3,1449,"episode")),Map.of("gap",389,"baseline",389));
            assertTrue(scopes.get("gap").isEmpty());assertTrue(scopes.get("baseline").isEmpty());
        }
    }
    private Map<String,Set<Integer>> scope(List<OfferEvent> history,Map<String,Integer> gaps) {
        return TradeGapEvidence.scope(history,gaps,AccountIdentity.fromProfileKey("fixture"));
    }
    private OfferEvent e(String id,String type,int item,long seq,String session) {
        var b=OfferEvent.builder("fixture",100+seq,type).accountId(AccountIdentity.fromProfileKey("fixture"))
            .eventIdentity(id,session,item==0?null:"offer-"+item).slot(0).sequence(seq);
        if (item>0) b.item(item,"Item "+item).buying(true).price(100).quantities(10,"BOUGHT".equals(type)?10:0)
            .spent("BOUGHT".equals(type)?1000:0);
        return b.build();
    }
    @Test public void aChangedItemScopesBothSidesOfTheMissingTransition() {
        var history=List.of(e("old","BUYING",561,1,"s"),e("change","BUYING",562,2,"s"),
            e("clear","EMPTY",0,3,"s"));
        var scope=scope(history,Map.of("change",562,"clear",0));
        assertEquals(Set.of(561,562),scope.get("change"));
        assertEquals(Set.of(561,562),scope.get("clear"));
    }
    @Test public void aWitnessedTerminalAndEmptySeparateLaterGapEpisodes() {
        var history=List.of(e("old","BUYING",561,1,"s"),e("done","BOUGHT",561,2,"s"),
            e("collected","EMPTY",0,3,"s"),e("new","BUYING",562,4,"s"),e("gap","EMPTY",0,5,"s"));
        assertEquals(Set.of(562),scope(history,Map.of("gap",0)).get("gap"));
    }
    @Test public void aRejectedEmptyCannotEraseContextForLaterRejectedEmptyEvents() {
        var history=List.of(e("old","BUYING",561,1,"s"),e("change","BUYING",562,2,"s"),
            e("terminal","BOUGHT",562,3,"s"),e("clear","EMPTY",0,4,"s"),e("again","EMPTY",0,5,"s"));
        var scopes=scope(history,Map.of("change",562,"terminal",562,"clear",0,"again",0));
        assertEquals(Set.of(561,562),scopes.get("clear"));
        assertEquals(Set.of(561,562),scopes.get("again"));
    }
    @Test public void sessionAndOrderingAmbiguityCannotBecomeItemProof() {
        for (OfferEvent gap:List.of(e("gap","EMPTY",0,2,"other"),e("gap","EMPTY",0,1,"s"))) {
            var scope=scope(List.of(e("old","BUYING",561,1,"s"),gap),Map.of("gap",0));
            assertTrue(scope.get("gap").isEmpty());
        }
    }
    @Test public void missingSourceAndUnseenPriorOfferRemainUnbounded() {
        assertTrue(scope(List.of(e("old","BUYING",561,1,"s")),Map.of("missing",0)).get("missing").isEmpty());
        assertTrue(scope(List.of(e("gap","EMPTY",0,1,"s")),Map.of("gap",0)).get("gap").isEmpty());
    }
    @Test public void aForeignAccountCannotContributeToScopeProof() {
        var foreign=e("gap","EMPTY",0,2,"s").withAccountId(AccountIdentity.fromProfileKey("other"));
        assertTrue(scope(List.of(e("old","BUYING",561,1,"s"),foreign),Map.of("gap",0)).get("gap").isEmpty());
    }
    @Test public void missingStateCannotBreakTheReadOnlyScopeView() {
        var malformed=OfferEvent.builder("fixture",101,null).accountId(AccountIdentity.fromProfileKey("fixture"))
            .eventIdentity("old","s","offer").slot(0).sequence(1).item(561,"Item").buying(true)
            .price(100).quantities(10,0).build();
        assertTrue(scope(List.of(malformed,e("gap","EMPTY",0,2,"s")),Map.of("gap",0)).get("gap").isEmpty());
    }
}
