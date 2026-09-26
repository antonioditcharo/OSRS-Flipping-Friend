package com.flippingfriend.companion;
import com.flippingfriend.core.*;
import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class Phase2TransactionRollbackTest
{
 @Test public void everyCanonicalCheckpointRollsBackCompletely() throws Exception
 {
  for(String checkpoint:new String[]{"OFFER_EVENT_RECORDED","OFFER_PROJECTION_WRITTEN","POSITION_ACCOUNTING_APPLIED","BUY_LIMIT_ACCOUNTING_APPLIED"})
  {
   Path db=Files.createTempDirectory("rollback-offer").resolve("db");
   try(SqliteStore seed=new SqliteStore(db)){seed.recordAndProjectOffer(event("open","BUYING",0,0,1),"open");}
   try(SqliteStore failing=new SqliteStore(db,name->{if(checkpoint.equals(name))throw new Exception("injected "+name);})){try{failing.recordAndProjectOffer(event("partial","BUYING",4,4000,2),"partial");fail();}catch(Exception expected){assertTrue(expected.getMessage().contains("injected"));}}
   try(SqliteStore clean=new SqliteStore(db)){assertEquals(1,clean.eventCount("BUYING"));assertEquals(0,clean.positionEventCount());assertEquals(0,clean.buyLimitEventCount());assertEquals(0,clean.offerProjections().get(0).getFilledQuantity());clean.recordAndProjectOffer(event("partial","BUYING",4,4000,2),"partial");assertEquals(4,clean.positionProjections().get(0).getQuantity());assertEquals(4,clean.buyLimitProjections().get(0).getUsedQuantity());}
  }
 }
 @Test public void everySnapshotCheckpointRollsBackCompletely() throws Exception
 {
  for(String checkpoint:new String[]{"ACCOUNT_EVENT_RECORDED","SNAPSHOT_POSITIONS_RECONCILED","SNAPSHOT_BUY_LIMITS_RECONCILED"})
  {
   Path db=Files.createTempDirectory("rollback-snapshot").resolve("db"); AccountSnapshot a=account();
   try(SqliteStore failing=new SqliteStore(db,name->{if(checkpoint.equals(name))throw new Exception("injected "+name);})){try{failing.recordAndReconcileAccount(a,PositionStateView.from(a),"snapshot");fail();}catch(Exception expected){assertTrue(expected.getMessage().contains("injected"));}}
   try(SqliteStore clean=new SqliteStore(db)){assertEquals(0,clean.eventCount("ACCOUNT_STATE"));assertTrue(clean.positionProjections().isEmpty());assertEquals(0,clean.reconciliationEventCount());assertTrue(clean.buyLimitProjections().isEmpty());assertEquals(0,clean.buyLimitEventCount());clean.recordAndReconcileAccount(a,PositionStateView.from(a),"snapshot");assertEquals(1,clean.eventCount("ACCOUNT_STATE"));assertEquals(1,clean.positionProjections().size());assertEquals(1,clean.buyLimitProjections().size());}
  }
 }
 private static OfferEvent event(String id,String state,int filled,long spent,long seq){return OfferEvent.builder("trace",100+seq,state).eventIdentity(id,"session","offer").slot(1).item(4151,"Abyssal whip").buying(true).price(1000).quantities(10,filled).spent(spent).sequence(seq).build();}
 private static AccountSnapshot account(){Map<Integer,Integer> limits=new HashMap<>();limits.put(4151,4);return new AccountSnapshot("snapshot",200,0,0,8,8,true,true,0,null,null,0,limits,false,0,null,null,null,false,0,Collections.singletonList(new PositionSnapshot(4151,"Abyssal whip",4,4000,true,100,0,0,0)));}
}
