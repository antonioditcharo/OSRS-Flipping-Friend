package com.flippingfriend.model;

import com.flippingfriend.*;
import com.flippingfriend.session.*;
import java.lang.reflect.*;
import org.junit.*;
import static org.junit.Assert.*;

public class SalePriceStabilityTest {
    final long now=1800000000;
    SuggestionEngine engine()throws Exception {
        Constructor<?> constructor=SuggestionEngine.class.getDeclaredConstructors()[0];
        return (SuggestionEngine)constructor.newInstance(new Object[constructor.getParameterCount()]);
    }
    int price(SuggestionEngine engine,boolean buy,int fresh,long at)throws Exception {
        Method method=SuggestionEngine.class.getDeclaredMethod("advisedPrice",int.class,boolean.class,int.class,long.class);
        method.setAccessible(true);return (int)method.invoke(engine,561,buy,fresh,at);
    }
    @Test public void retainedSellPriceMustFitTheFinalPolicyDriftBudget()throws Exception {
        var engine=engine();assertEquals(1000,price(engine,false,1000,now-10));int shown=price(engine,false,1010,now);
        var horizon=TradingHorizon.of(RiskProfile.MODERATE,CheckInterval.FIVE_MINUTES);
        assertTrue("Price smoothing must not strand a valid acquired sale at final authorization",
            Math.abs((long)shown-1010)<horizon.outbidDeadband(1010));
        assertEquals(1010,shown);
    }
    @Test public void smallNoiseIsStableAndBuysKeepTheirExistingBudget()throws Exception {
        var engine=engine();assertEquals(1000,price(engine,false,1000,now));assertEquals(1000,price(engine,false,1004,now+1));
        assertEquals(1000,price(engine,true,1000,now));assertEquals(1000,price(engine,true,1010,now+1));
        assertEquals(1004,price(engine,false,1004,now+600));
    }
    @Test public void accountChangeAndBackwardClockCannotRetainAnotherObservation()throws Exception {
        var engine=engine();engine.setObservedAccount("first");price(engine,false,1000,now);
        engine.setObservedAccount("second");assertEquals(1004,price(engine,false,1004,now+1));
        assertEquals(1003,price(engine,false,1003,now-1));
    }
}
