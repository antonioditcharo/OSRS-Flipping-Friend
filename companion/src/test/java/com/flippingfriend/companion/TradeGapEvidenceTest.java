package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TradeGapEvidenceTest {
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
        var scope=TradeGapEvidence.scope(history,Map.of("change",562,"clear",0));
        assertEquals(Set.of(561,562),scope.get("change"));
        assertEquals(Set.of(561,562),scope.get("clear"));
    }
    @Test public void aWitnessedTerminalAndEmptySeparateLaterGapEpisodes() {
        var history=List.of(e("old","BUYING",561,1,"s"),e("done","BOUGHT",561,2,"s"),
            e("collected","EMPTY",0,3,"s"),e("new","BUYING",562,4,"s"),e("gap","EMPTY",0,5,"s"));
        assertEquals(Set.of(562),TradeGapEvidence.scope(history,Map.of("gap",0)).get("gap"));
    }
    @Test public void aRejectedEmptyCannotEraseContextForLaterRejectedEmptyEvents() {
        var history=List.of(e("old","BUYING",561,1,"s"),e("change","BUYING",562,2,"s"),
            e("terminal","BOUGHT",562,3,"s"),e("clear","EMPTY",0,4,"s"),e("again","EMPTY",0,5,"s"));
        var scopes=TradeGapEvidence.scope(history,Map.of("change",562,"terminal",562,"clear",0,"again",0));
        assertEquals(Set.of(561,562),scopes.get("clear"));
        assertEquals(Set.of(561,562),scopes.get("again"));
    }
    @Test public void sessionAndOrderingAmbiguityCannotBecomeItemProof() {
        for (OfferEvent gap:List.of(e("gap","EMPTY",0,2,"other"),e("gap","EMPTY",0,1,"s"))) {
            var scope=TradeGapEvidence.scope(List.of(e("old","BUYING",561,1,"s"),gap),Map.of("gap",0));
            assertTrue(scope.get("gap").isEmpty());
        }
    }
    @Test public void missingSourceAndUnseenPriorOfferRemainUnbounded() {
        assertTrue(TradeGapEvidence.scope(List.of(e("old","BUYING",561,1,"s")),Map.of("missing",0)).get("missing").isEmpty());
        assertTrue(TradeGapEvidence.scope(List.of(e("gap","EMPTY",0,1,"s")),Map.of("gap",0)).get("gap").isEmpty());
    }
    @Test public void aForeignAccountCannotContributeToScopeProof() {
        var foreign=e("gap","EMPTY",0,2,"s").withAccountId(AccountIdentity.fromProfileKey("other"));
        assertTrue(TradeGapEvidence.scope(List.of(e("old","BUYING",561,1,"s"),foreign),Map.of("gap",0)).get("gap").isEmpty());
    }
}
