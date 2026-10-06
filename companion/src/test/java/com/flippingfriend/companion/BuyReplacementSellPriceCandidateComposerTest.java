package com.flippingfriend.companion;
import java.util.List; import org.junit.Test; import static org.junit.Assert.*;
public class BuyReplacementSellPriceCandidateComposerTest {
 @Test public void generatesHighAnchoredBuyMajorSellMinorGrid(){BuyReplacementSellPriceCandidateSet r=new BuyReplacementSellPriceCandidateComposer().compose(strategy());assertNotNull(r);assertEquals(4,r.getCandidates().size());int[] p={510,509,507,504};for(int i=0;i<p.length;i++){BuyReplacementSellPriceCandidate c=r.getCandidates().get(i);assertEquals(1,c.getBuyOffsetIndex());assertEquals(i,c.getSellOffsetIndex());assertEquals(p[i],c.getSellPrice());assertEquals(5,c.getExactRemainderQuantity());assertEquals(2505,c.getTotalCost());}}
 @Test public void rawNonpositiveSpreadEvidenceIsNotFiltered(){BuyReplacementSellPriceCandidateSet r=new BuyReplacementSellPriceCandidateComposer().compose(strategy());assertEquals(4,r.getCandidates().size());assertTrue(r.getCandidates().get(3).getSellPrice()>0);}
 @Test public void collectionIsImmutableAndProvenancePreserved(){List<BuyReplacementSellPriceCandidate> c=new BuyReplacementSellPriceCandidateComposer().compose(strategy()).getCandidates();try{c.clear();fail("immutable");}catch(UnsupportedOperationException expected){}assertEquals(0.0,c.get(0).getSellOffset(),0);assertEquals(-0.012,c.get(3).getSellOffset(),0);}
 @Test public void unavailableInputFailsClosed(){assertNull(new BuyReplacementSellPriceCandidateComposer().compose(null));}
 @Test public void contractsCarryNoEconomicsSelectionOrAuthorization()throws Exception{String s=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/flippingfriend/companion/BuyReplacementSellPriceCandidateSet.java"));for(String x:new String[]{"profit","tax","margin","selected","preferred","PolicyDecision","OfferLifecycleAction","REPLACEMENT_AUTHORIZED"})assertFalse(s.contains(x));}
 static BuyReplacementSellPriceCandidateSet setForCompanionTests(){return new BuyReplacementSellPriceCandidateComposer().compose(strategy());}
 private static BuyReplacementSellPricingStrategyContext strategy(){return BuyReplacementSellPricingStrategyContextComposerTest.strategyForCompanionTests();}
}
