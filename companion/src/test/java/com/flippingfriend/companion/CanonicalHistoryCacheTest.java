package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Disposable source stores: warm reads must observe tails, foreign edits and rollbacks. */
public class CanonicalHistoryCacheTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final String account=AccountIdentity.fromProfileKey("prefix");final Gson gson=new Gson();
    OfferEvent event(String id,String state,int item,boolean buy,int fill,long seq) {
        return OfferEvent.builder("fixture",1700000000+seq,state).accountId(account).slot(0).sequence(seq)
            .eventIdentity(id,"session",item==0?null:"offer-"+item).item(item,"Item").buying(buy)
            .price(item==0?0:100).quantities(item==0?0:10,fill).spent(fill*100L).build();
    }
    void accept(SqliteStore s,OfferEvent e)throws Exception{s.recordAndProjectOffer(e,gson.toJson(e),true);}
    void seed(SqliteStore s)throws Exception {
        accept(s,event("open","BUYING",561,true,0,1));
        // Changing item creates a scoped gap, which needs the complete canonical prefix.
        accept(s,event("gap","BUYING",562,true,0,2));
    }
    @Test public void warmPrefixStillSeesNewGapSourcesAndNewExecutions()throws Exception {
        Path db=folder.newFolder().toPath().resolve("tail.db");
        try(var s=new SqliteStore(db)) {
            seed(s);var first=s.ledgerSnapshot(account);assertFalse(first.hasUnboundedReconciliation());
            accept(s,event("clear","EMPTY",0,false,0,3));
            var warm=s.ledgerSnapshot(account);assertEquals(2,warm.getReconciliationScope().size());
            try(var cold=SqliteStore.openReadOnly(db)) {
                var fresh=cold.ledgerSnapshot(account);assertEquals(fresh.getReconciliationScope(),warm.getReconciliationScope());
                assertEquals(gson.toJsonTree(fresh.getTradingPositions()),gson.toJsonTree(warm.getTradingPositions()));
            }
        }
    }
    @Test public void externalDeletionOrCorruptionCannotBorrowAPreviouslyValidPrefix()throws Exception {
        for(String mutation:new String[]{"DELETE FROM event_log WHERE event_id='gap'","UPDATE event_log SET payload='{}' WHERE event_id='open'"}) {
            Path db=folder.newFolder().toPath().resolve("external.db");
            try(var s=new SqliteStore(db)) {
                seed(s);assertFalse(s.ledgerSnapshot(account).hasUnboundedReconciliation());
                try(var other=DriverManager.getConnection("jdbc:sqlite:"+db);var sql=other.createStatement()){sql.execute(mutation);}
                assertTrue("External evidence changes must withdraw scope",s.ledgerSnapshot(account).hasUnboundedReconciliation());
            }
        }
    }
    @Test public void aRolledBackTailCannotSurviveToQualifyLaterReads()throws Exception {
        Path db=folder.newFolder().toPath().resolve("rollback.db");boolean[] fail={false};
        try(var s=new SqliteStore(db,point->{if(fail[0] && point.equals("POSITION_ACCOUNTING_APPLIED"))throw new IllegalStateException("fixture");})) {
            seed(s);s.ledgerSnapshot(account);fail[0]=true;
            try{accept(s,event("rolled-back","EMPTY",0,false,0,3));fail();}catch(IllegalStateException expected) {assertEquals("fixture",expected.getMessage());}
            fail[0]=false;assertEquals(1,s.ledgerSnapshot(account).getReconciliationScope().size());
            accept(s,event("real","EMPTY",0,false,0,3));
            var warm=s.ledgerSnapshot(account);assertFalse(warm.getReconciliationScope().containsKey("rolled-back"));
            assertTrue(warm.getReconciliationScope().containsKey("real"));
        }
    }
}
