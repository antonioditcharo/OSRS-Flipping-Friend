package com.flippingfriend.session;

import com.flippingfriend.*;
import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.flippingfriend.model.*;
import java.time.Instant;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TradeRecoveryScannerTest {
    final long now=1800000000;
    final String account=AccountIdentity.fromProfileKey("coverage");
    AccountLedgerSnapshot ledger(List<PositionProjection> positions){return new AccountLedgerSnapshot(account,now,0,0,0,0,0,0,0,0,List.of()).withTradeEvidence(positions,List.of(),Map.of());}
    TradeRecoverySnapshot scan(AccountLedgerSnapshot ledger,List<TrackedOffer> offers,Map<Integer,Integer> inventory,List<TradeRecoveryItem> workflows) {
        var state=new AccountState(true,true,false,100000000,0,0,8-offers.size(),8,0,false,inventory,inventory,Map.of());
        return TradeRecoveryScanner.scan(account,now,ledger,state,offers,MarketSnapshot.empty(),Map.of(),Map.of(),workflows,null,
            TradingHorizon.of(RiskProfile.MODERATE,CheckInterval.FIVE_MINUTES),new TaxCalculator(),0);
    }
    @Test public void allEightSlotsRemainCoveredWhenCollectionWinsAndPricesAreMissing() {
        var offers=new ArrayList<TrackedOffer>();
        for(int slot=0;slot<8;slot++) {
            var offer=new TrackedOffer(slot,561+slot,slot%2==0,100,10,now-3600);
            offer.setState(slot==0?"BOUGHT":slot%2==0?"BUYING":"SELLING");if(slot==0)offer.setQuantityFilled(10);
            offers.add(offer);
        }
        var snapshot=scan(ledger(List.of()),offers,Map.of(),List.of());
        assertEquals(8,snapshot.items.size());assertTrue(snapshot.evidenceCurrent);
        assertEquals(8,snapshot.items.stream().map(r->r.identity).distinct().count());
        assertEquals(1,snapshot.items.stream().filter(r->r.state==TradeRecoveryItem.State.AWAITING_COLLECTION).count());
        assertEquals(7,snapshot.items.stream().filter(r->r.state==TradeRecoveryItem.State.AWAITING_EVIDENCE).count());
        try{snapshot.items.clear();fail();}catch(UnsupportedOperationException expected){}
    }
    @Test public void onlyCanonicalRemainingAcquisitionsAreCoveredAndPersonalExtrasAreNotEnrolled() {
        var p=PositionProjection.ready(561,"Rune",3,301,now-600,now,"acquisition","episode");
        var sale=new TrackedOffer(0,561,false,150,2,now-300);sale.setState("SELLING");
        var snapshot=scan(ledger(List.of(p)),List.of(sale),Map.of(561,100,999,2000),List.of());
        assertEquals(2,snapshot.items.size());
        var stock=snapshot.items.stream().filter(r->r.kind==TradeRecoveryItem.Kind.STOCK).findFirst().orElseThrow();
        assertEquals(1,stock.quantity);assertEquals("acquisition",stock.identity);
        assertFalse(snapshot.items.stream().anyMatch(r->r.itemId==999));
    }
    @Test public void unseenStockAndUnknownWorkflowRemainVisibleWithoutDisposalOrPermission() {
        var stock=PositionProjection.ready(561,"Rune",3,301,now-600,now,"acquisition","episode");
        var unknown=new TradeRecoveryItem("interrupted",TradeRecoveryItem.Kind.CONTINUATION,TradeRecoveryItem.State.AWAITING_EVIDENCE,
            562,"Rune",2,0,120,false,now-600,now+30,"Unreadable workflow","Retry original evidence");
        var snapshot=scan(ledger(List.of(stock)),List.of(),Map.of(),List.of(unknown));
        assertEquals(2,snapshot.items.size());assertTrue(snapshot.items.stream().anyMatch(r->r.state==TradeRecoveryItem.State.AWAITING_ACCESS && r.quantity==3));
        assertTrue(snapshot.items.contains(unknown));
        var unavailable=scan(null,List.of(),Map.of(561,3),List.of(unknown));assertFalse(unavailable.evidenceCurrent);
        assertFalse(unavailable.items.stream().anyMatch(r->r.kind==TradeRecoveryItem.Kind.STOCK));
        assertTrue(unavailable.items.contains(unknown));
    }
    @Test public void freshBuyProgressCannotBecomeADeadlineCancellationAtAnySupportedCadence() {
        for(var cadence:List.of(CheckInterval.FIVE_MINUTES,CheckInterval.THIRTY_MINUTES,CheckInterval.TWO_HOURS,CheckInterval.EIGHT_HOURS)) {
            var offer=new TrackedOffer(0,561,true,1000,10,now-3600);offer.setState("BUYING");offer.observeFillAt(now-1);
            offer.setBuyMaintenanceDeadlineAt(now-60);
            var price=new LatestPrice(1500,now,1100,now);
            assertFalse(BuyRepricePricing.overdue(offer.getFirstSeen(),offer.getLastFillObservedAt(),offer.getBuyMaintenanceDeadlineAt(),price,
                TradingHorizon.of(RiskProfile.MODERATE,cadence),now));
        }
    }
}
