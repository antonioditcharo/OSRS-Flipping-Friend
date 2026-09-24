package com.flippingfriend.companion;
import com.flippingfriend.core.*;
import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class PositionProjectionPersistenceTest{
 @Test public void acquisitionDisposalDuplicateAndRestartAreDurable() throws Exception{
  Path db=Files.createTempDirectory("position-projection").resolve("test.db");
  try(SqliteStore s=new SqliteStore(db)){
   s.recordAndProjectOffer(event("b1","BUYING",true,0,0,1,"buy"),"b1");
   s.recordAndProjectOffer(event("b2","BOUGHT",true,10,10000,2,"buy"),"b2");
   assertPosition(s,10,10000); assertEquals(1,s.positionEventCount());
   assertEquals(SqliteStore.EventAcceptance.DUPLICATE,s.recordAndProjectOffer(event("b2","BOUGHT",true,10,10000,2,"buy"),"dup").acceptance);
   assertEquals(1,s.positionEventCount());
   s.recordAndProjectOffer(empty("c1",3),"clear");
   s.recordAndProjectOffer(event("s1","SELLING",false,0,0,4,"sell"),"s1");
   s.recordAndProjectOffer(event("s2","SELLING",false,4,5000,5,"sell"),"s2");
   assertPosition(s,6,6000); assertEquals(2,s.positionEventCount());
  }
  try(SqliteStore s=new SqliteStore(db)){assertPosition(s,6,6000);}
 }
 @Test public void disposalWithoutPositionFailsClosed() throws Exception{
  Path db=Files.createTempDirectory("position-reconcile").resolve("test.db");
  try(SqliteStore s=new SqliteStore(db)){
   s.recordAndProjectOffer(event("s1","SELLING",false,0,0,1,"sell"),"s1");
   SqliteStore.ProjectedEventAcceptance r=s.recordAndProjectOffer(event("s2","SOLD",false,10,12000,2,"sell"),"s2");
   assertEquals(PositionConsistencyState.RECONCILE,r.positionProjection.getConsistencyState());
   assertEquals(0,r.positionProjection.getQuantity());
  }
 }
 private static void assertPosition(SqliteStore s,int q,long c)throws Exception{List<PositionProjection> p=s.positionProjections();assertEquals(1,p.size());assertEquals(q,p.get(0).getQuantity());assertEquals(c,p.get(0).getTotalCost());}
 private static OfferEvent event(String id,String state,boolean buying,int filled,long spent,long seq,String identity){return OfferEvent.builder("trace",100+seq,state).eventIdentity(id,"session",identity).slot(2).item(4151,"Abyssal whip").buying(buying).price(1000).quantities(10,filled).spent(spent).sequence(seq).build();}
 private static OfferEvent empty(String id,long seq){return OfferEvent.builder("trace",100+seq,"EMPTY").eventIdentity(id,"session",null).slot(2).sequence(seq).build();}
}
