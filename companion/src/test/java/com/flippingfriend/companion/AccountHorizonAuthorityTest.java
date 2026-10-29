package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.flippingfriend.data.Candle;
import com.flippingfriend.model.AccountHorizonPricing;
import java.util.List;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class AccountHorizonAuthorityTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    @Test public void offlineChallengerRetainsExactCurrentSourceInventoryQuantityAndCostAuthority()throws Exception {
        var f=new CompanionSalePolicyTest();f.folder=folder;long now=f.at+3600;
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("shadow-authority.db"))) {
            var account=f.setup(store);var read=store.saleProjections(f.id,now);
            String source=read.ledger.getTradingPositions().get(0).getSourceEventId();int effects=store.positionEventCount();
            SeriesSource history=new SeriesSource(){public List<Candle> series(int item,String step){return f.bars(now,120);}public boolean synchronousAnalysis(){return true;}};
            var shadow=new CompanionSalePolicy(true);
            var approved=shadow.decide(read.planning,read.ledger,account,f.market(now,120),history,561,120,2,source,false,-1,now);
            assertEquals(OfferLifecycleAction.PLACE_SELL,approved.getAction());
            assertEquals(CompanionSalePolicy.VERSION+"-"+AccountHorizonPricing.VERSION,approved.getPolicyVersion());
            assertEquals(OfferLifecycleAction.WAIT,shadow.decide(read.planning,read.ledger,account,f.market(now,120),history,561,120,4,source,false,-1,now).getAction());
            assertEquals(OfferLifecycleAction.WAIT,shadow.decide(read.planning,read.ledger,account,f.market(now,120),history,561,120,2,"other-source",false,-1,now).getAction());
            assertEquals(OfferLifecycleAction.WAIT,shadow.decide(read.planning,read.ledger,account,f.market(now,120),history,561,1,2,source,false,-1,now).getAction());
            assertEquals(OfferLifecycleAction.WAIT,shadow.decide(read.planning,read.ledger,account.withObservedInventory(java.util.Map.of(561,1)),f.market(now,120),history,561,120,2,source,false,-1,now).getAction());
            assertEquals(effects,store.positionEventCount());assertEquals(301,store.ledgerSnapshot(f.id,now).getTradingPositions().get(0).getTotalCost());
        }
    }
    @Test public void liveRequestCannotSelectUnqualifiedShadowPolicy()throws Exception {
        var f=new CompanionSalePolicyTest();f.folder=folder;long now=f.at+3600;
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("shadow-not-live.db"))) {
            var account=f.setup(store);var read=store.saleProjections(f.id,now);String source=read.ledger.getTradingPositions().get(0).getSourceEventId();
            var refused=new CompanionSalePolicy(true).decide(read.planning,read.ledger,account,f.market(now,120),(i,s)->f.bars(now,120),561,120,2,source,false,-1,now);
            assertEquals("SHADOW_REQUIRES_OFFLINE_ANALYSIS",refused.getReasonCode());
            var live=new CompanionSalePolicy().decide(read.planning,read.ledger,account,f.market(now,120),(i,s)->f.bars(now,120),561,120,2,source,false,-1,now);
            assertEquals(OfferLifecycleAction.PLACE_SELL,live.getAction());assertEquals(CompanionSalePolicy.VERSION,live.getPolicyVersion());
        }
    }
}
