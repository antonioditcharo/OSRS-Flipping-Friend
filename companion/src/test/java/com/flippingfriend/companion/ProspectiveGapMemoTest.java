package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProspectiveGapMemoTest {
    private static final class Counted extends AbstractList<OfferEvent> {
        final List<OfferEvent> values;int reads;
        Counted(List<OfferEvent> values){this.values=List.copyOf(values);}
        public int size(){return values.size();}
        public OfferEvent get(int index){reads++;return values.get(index);}
    }
    @Test public void unchangedProofAvoidsHistoricalWorkButNewSourcesScopesCostsAndUnknownsAreRevalidated() {
        String account=AccountIdentity.fromProfileKey("memo");long at=20000;
        var sources=new ArrayList<OfferEvent>();var receipt=new HashMap<Integer,String>();
        for(int slot=0;slot<8;slot++) {
            String id="opening-"+slot;receipt.put(slot,id);
            sources.add(OfferEvent.builder("fixture",at,"EMPTY").accountId(account).eventIdentity(id,"session",null)
                .slot(slot).sequence(slot+1).authenticatedObservation(true).build());
        }
        sources.add(buy(account,"buy",at+1,9,10));
        var snapshot=new AccountSnapshot("fixture",at,100000000,0,8,8,true,false,0).withAccountId(account).withOfferReadSet(receipt);
        var read=new SqliteStore.PlanningProjections(List.of(),List.of());read.prospectiveGapMemo=new ProspectiveGapExposure.Memo();
        read.riskEvidence=new RiskLedgerEvidence(new RiskWindow(snapshot,"session",0,100000000));
        read.riskEvidence.newReconciliationItems.add(561);read.riskEvidence.openingCosts.put(561,500L);
        var initial=new Counted(sources);read.history=initial;
        var first=ProspectiveGapExposure.calculate(read);assertEquals(Long.valueOf(1500),first.get(561));int work=initial.reads;
        for(int i=0;i<100;i++)assertSame(first,ProspectiveGapExposure.calculate(read));
        assertEquals("Retained proof must not walk the account's history on every report/health/action",work,initial.reads);
        sources.add(OfferEvent.builder("fixture",at+2,"EMPTY").accountId(account).eventIdentity("clear","session",null)
            .slot(0).sequence(10).authenticatedObservation(true).build());
        sources.add(buy(account,"new-buy",at+3,11,20));read.history=new Counted(sources);
        assertEquals(Long.valueOf(3500),ProspectiveGapExposure.calculate(read).get(561));assertEquals(Long.valueOf(1500),first.get(561));
        read.riskEvidence.openingCosts.put(561,700L);read.riskEvidence.newReconciliationItems.add(562);read.riskEvidence.openingCosts.put(562,40L);
        var revised=ProspectiveGapExposure.calculate(read);assertEquals(Long.valueOf(3700),revised.get(561));assertEquals(Long.valueOf(40),revised.get(562));
        read.riskEvidence.unbounded=true;assertNull(ProspectiveGapExposure.calculate(read));read.riskEvidence.unbounded=false;
        var changed=new ArrayList<>(sources);changed.set(0,sources.get(0).withAccountId(AccountIdentity.fromProfileKey("foreign")));
        read.history=new Counted(changed);assertNull("A replacement prefix cannot borrow a cached safe result",ProspectiveGapExposure.calculate(read));
        read.history=null;assertNull(ProspectiveGapExposure.calculate(read));
        read.history=new Counted(sources);assertNotNull(ProspectiveGapExposure.calculate(read));
        read.riskEvidence=new RiskLedgerEvidence(new RiskWindow(snapshot,"unproven-new-session",0,100000000));
        read.riskEvidence.newReconciliationItems.add(561);read.riskEvidence.openingCosts.put(561,500L);
        assertNull("A different risk window must prove its own opening sources",ProspectiveGapExposure.calculate(read));
    }
    private OfferEvent buy(String account,String id,long at,int sequence,int quantity) {
        return OfferEvent.builder("fixture",at,"BUYING").accountId(account).eventIdentity(id,"session",id)
            .slot(0).item(561,"Rune").buying(true).price(100).quantities(quantity,0).sequence(sequence).authenticatedObservation(true).build();
    }
}
