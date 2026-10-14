package com.flippingfriend.model;
import com.flippingfriend.data.*;
import java.time.Instant;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;
public class ManualSalePriceTest {
    private final Instant now=Instant.ofEpochSecond(1700000000L);
    private MarketSnapshot market(long seconds) {
        return new MarketSnapshot(Map.of(561,new ItemMetadata(561,"Nature rune",false,25000,1)),
            Map.of(561,new LatestPrice(120,seconds,100,seconds)),Map.of(),Map.of(),now);
    }
    @Test public void quoteOnlyReflectsSelectedQuantityAndNeverInventsProfit() {
        var quote=ManualSalePrice.quote(561,100,6100,market(now.getEpochSecond()),now);
        assertNotNull(quote); assertEquals(100,quote.getQuantity()); assertEquals(119,quote.getPrice());
        assertFalse(quote.isProfitKnown()); assertEquals(0,quote.getBreakEvenPrice());
    }
    @Test public void staleFutureMissingAndExcessQuantityWithdrawQuotes() {
        assertNull(ManualSalePrice.quote(561,100,6100,market(now.getEpochSecond()-301),now));
        assertNull(ManualSalePrice.quote(561,100,6100,market(now.getEpochSecond()+1),now));
        assertNull(ManualSalePrice.quote(561,6101,6100,market(now.getEpochSecond()),now));
        assertNull(ManualSalePrice.quote(2,100,6100,market(now.getEpochSecond()),now));
        assertNull(ManualSalePrice.quote(561,0,6100,market(now.getEpochSecond()),now));
    }
}
