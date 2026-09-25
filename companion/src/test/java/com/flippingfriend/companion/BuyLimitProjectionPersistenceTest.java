package com.flippingfriend.companion;
import com.flippingfriend.core.*;import java.nio.file.*;import org.junit.Test;import static org.junit.Assert.*;
public class BuyLimitProjectionPersistenceTest{
 @Test public void acquisitionDuplicateAndRestartAreDurable()throws Exception{Path db=Files.createTempDirectory("limit-projection").resolve("db");try(SqliteStore s=new SqliteStore(db)){s.recordAndProjectOffer(event("a",0,1),"a");s.recordAndProjectOffer(event("b",4,2),"b");assertEquals(4,s.buyLimitProjections().get(0).getUsedQuantity());assertEquals(1,s.buyLimitEventCount());assertEquals(SqliteStore.EventAcceptance.DUPLICATE,s.recordAndProjectOffer(event("b",4,2),"dup").acceptance);assertEquals(1,s.buyLimitEventCount());}try(SqliteStore s=new SqliteStore(db)){assertEquals(4,s.buyLimitProjections().get(0).getUsedQuantity());}}
 static OfferEvent event(String id,int filled,long seq){return OfferEvent.builder("t",100+seq,filled<10?"BUYING":"BOUGHT").eventIdentity(id,"s","o").slot(1).item(561,"Nature rune").buying(true).price(100).quantities(10,filled).spent(filled*100L).sequence(seq).build();}
}
