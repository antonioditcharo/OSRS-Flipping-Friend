package com.flippingfriend.companion;
import com.flippingfriend.model.RiskAppetite;
import org.junit.Test;
import static org.junit.Assert.*;
public class BuyReplacementSellPricingStrategyContextComposerTest {
 @Test public void bindsBalancedSellOffsetsAndCurrentHighAnchor(){BuyReplacementSellPricingStrategyContext r=composer().compose(viable(),510,1000);assertNotNull(r);assertEquals("Balanced",r.getAppetiteName());assertArrayEquals(RiskAppetite.BALANCED.getSellOffsets(),r.getSellOffsets(),0);assertEquals(510,r.getCurrentHighPrice());assertEquals(5,r.getExactRemainderQuantity());assertEquals(1,r.getViableCandidates().size());}
 @Test public void sellOffsetsAreDefensivelyCopied(){BuyReplacementSellPricingStrategyContext r=composer().compose(viable(),510,1000);double[] x=r.getSellOffsets();x[0]=99;assertEquals(0,r.getSellOffsets()[0],0);try{r.getViableCandidates().clear();fail("immutable");}catch(UnsupportedOperationException expected){}}
 @Test public void unavailableAndInvalidAnchorsFailClosed(){assertNull(composer().compose(null,510,1000));assertNull(composer().compose(viable(),0,1000));assertNull(composer().compose(viable(),510,999));}
 @Test public void contractCarriesNoGenerationEconomicsSelectionOrAuthorization()throws Exception{String s=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/flippingfriend/companion/BuyReplacementSellPricingStrategyContext.java"));for(String x:new String[]{"PriceOffset","sellPrice","profit","tax","selected","PolicyDecision","OfferLifecycleAction","REPLACEMENT_AUTHORIZED"})assertFalse(s.contains(x));}
 static BuyReplacementSellPricingStrategyContext strategyForCompanionTests(){return composer().compose(viable(),510,1000);}
 static BuyReplacementSellPricingStrategyContext strategyForHighForCompanionTests(int high){return composer().compose(viable(),high,1000);}
 private static BuyReplacementSellPricingStrategyContextComposer composer(){return new BuyReplacementSellPricingStrategyContextComposer();}
 private static BuyReplacementCandidateFillViabilityAssessment viable(){return BuyReplacementCandidateFillViabilityEvaluatorTest.viableForCompanionTests();}
}
