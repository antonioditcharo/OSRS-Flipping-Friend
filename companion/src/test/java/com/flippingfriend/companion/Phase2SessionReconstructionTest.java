package com.flippingfriend.companion;
import com.flippingfriend.core.*;
import java.nio.file.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class Phase2SessionReconstructionTest
{
 @Test public void completeBuyToSellSessionReconstructsAcrossRestarts() throws Exception
 {
  Path db=Files.createTempDirectory("phase2-session").resolve("db");
  try(SqliteStore s=new SqliteStore(db)){accept(s,buy("b0","BUYING",0,0,1));accept(s,buy("b4","BUYING",4,4000,2));}
  try(SqliteStore s=new SqliteStore(db)){accept(s,buy("b7","BUYING",7,7300,3));accept(s,buy("b10","BOUGHT",10,10600,4));assertEquals(SqliteStore.EventAcceptance.DUPLICATE,accept(s,buy("b10","BOUGHT",10,10600,4)).acceptance);accept(s,empty("clear-buy",5));accept(s,sell("s0","SELLING",0,0,6));accept(s,sell("s4","SELLING",4,5000,7));}
  try(SqliteStore s=new SqliteStore(db)){accept(s,sell("s10","SOLD",10,12000,8));assertEquals(SqliteStore.EventAcceptance.DUPLICATE,accept(s,sell("s10","SOLD",10,12000,8)).acceptance);accept(s,empty("clear-sell",9));}
  try(SqliteStore s=new SqliteStore(db)){assertEquals(OfferLifecycleState.EMPTY,s.offerProjections().get(0).getState());assertTrue(s.positionProjections().isEmpty());long[] totals=s.positionEventTotals();assertArrayEquals(new long[]{10,10600,10,10600},totals);assertEquals(10,s.buyLimitProjections().get(0).getUsedQuantity());assertEquals(5,s.positionEventCount());assertEquals(3,s.buyLimitEventCount());}
 }
 private static OfferEvent buy(String id,String state,int filled,long spent,long seq){return event(id,state,true,filled,spent,seq,"buy");}
 private static OfferEvent sell(String id,String state,int filled,long spent,long seq){return event(id,state,false,filled,spent,seq,"sell");}
 private static OfferEvent event(String id,String state,boolean buying,int filled,long spent,long seq,String identity){return OfferEvent.builder("trace",100+seq,state).eventIdentity(id,"session",identity).slot(2).item(4151,"Abyssal whip").buying(buying).price(1000).quantities(10,filled).spent(spent).sequence(seq).build();}
 private static OfferEvent empty(String id,long seq){return OfferEvent.builder("trace",100+seq,"EMPTY").eventIdentity(id,"session",null).slot(2).sequence(seq).build();}
 private static SqliteStore.ProjectedEventAcceptance accept(SqliteStore store,OfferEvent event)throws Exception {return store.recordAndProjectOffer(event,new com.google.gson.Gson().toJson(event));}
}
