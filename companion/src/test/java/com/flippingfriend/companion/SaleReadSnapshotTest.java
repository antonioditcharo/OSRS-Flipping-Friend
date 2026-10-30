package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class SaleReadSnapshotTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final String account=AccountIdentity.fromProfileKey("sale-read-snapshot");
    final Gson gson=new Gson();
    long sequence;
    OfferEvent event(String type,int filled,long spent,int price) {
        var b=OfferEvent.builder("fixture",1000+sequence,type).accountId(account).authenticatedObservation(true)
            .eventIdentity("source-"+(++sequence),"session",type.equals("EMPTY")?null:type.equals("BUYING")||type.equals("BOUGHT")?"buy":"sell")
            .sequence(sequence).slot(0);
        if(!type.equals("EMPTY"))b.item(561,"Nature rune").buying(type.equals("BUYING")||type.equals("BOUGHT"))
            .quantities(3,filled).spent(spent).price(price).firstSeenAt(1000);
        return b.build();
    }
    void accept(SqliteStore store,OfferEvent event)throws Exception{store.recordAndProjectOffer(event,gson.toJson(event),true);}
    @Test public void financialAndCurrentOfferReadsUseTheSamePrefixDuringConcurrentProgress()throws Exception {
        Path db=folder.newFolder().toPath().resolve("sale.db");
        try(var writer=new SqliteStore(db)) {
            accept(writer,event("EMPTY",0,0,0));accept(writer,event("BUYING",0,0,100));
            accept(writer,event("BOUGHT",3,300,100));accept(writer,event("EMPTY",0,0,0));
            var open=event("SELLING",0,0,150);accept(writer,open);
            var progress=event("SELLING",1,150,150);var changed=new AtomicBoolean();
            try(var reader=new SqliteStore(db,stage->{if("SALE_PLANNING_READ".equals(stage) && changed.compareAndSet(false,true))accept(writer,progress);})) {
                var first=reader.saleDecisionProjections(account,1010,561,null);
                assertTrue(changed.get());
                assertEquals(open.getEventId(),first.planning.currentEvents.get(0).getEventId());
                assertEquals(3,first.ledger.getTradingPositions().get(0).getQuantity());assertEquals(0,first.ledger.getSales());
                var second=reader.saleDecisionProjections(account,1011,561,null);
                assertEquals(progress.getEventId(),second.planning.currentEvents.get(0).getEventId());
                assertEquals(2,second.ledger.getTradingPositions().get(0).getQuantity());assertEquals(1,second.ledger.getSales());
                int count=writer.positionEventCount();reader.saleProjections(account,1012);
                assertEquals("Sale verification never books an execution",count,writer.positionEventCount());
            }
        }
    }
    @Test public void failedReadRollsBackAndLeavesTheNextFreshReadUsable()throws Exception {
        Path db=folder.newFolder().toPath().resolve("failure.db");var failOnce=new AtomicBoolean(true);
        try(var store=new SqliteStore(db,stage->{if("SALE_PLANNING_READ".equals(stage) && failOnce.getAndSet(false))throw new IllegalStateException("fixture read failure");})) {
            accept(store,event("EMPTY",0,0,0));
            try{store.saleProjections(account,1010);fail("Expected read failure");}catch(IllegalStateException expected){assertEquals("fixture read failure",expected.getMessage());}
            assertTrue(store.saleProjections(account,1011).ledger.isValid());assertEquals(0,store.positionEventCount());
        }
    }
}
