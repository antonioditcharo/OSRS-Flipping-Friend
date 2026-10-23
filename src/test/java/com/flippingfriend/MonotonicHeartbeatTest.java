package com.flippingfriend;

import com.flippingfriend.session.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real heartbeat scheduling, with a valid negative local timer origin and no game/network input. */
public class MonotonicHeartbeatTest {
    @Test public void heartbeatStartsImmediatelyAndKeepsIndependentRefreshAndPersistenceIntervals()throws Exception {
        var f=new AdviceDeliveryBoundaryTest.Fixture();var clock=new AtomicLong(-100_000);
        f.set("heartbeatClock",(java.util.function.LongSupplier)clock::get);f.set("exemptionsResolved",true);
        var skips=mock(SkipList.class);f.set("skipList",skips);
        var config=(FlippingFriendConfig)field(f.plugin,"config");when(config.refreshSeconds()).thenReturn(30);
        f.plugin.tick();assertEquals(2,f.clientQueue.size());verify(skips,times(1)).save();
        f.plugin.tick();assertEquals("An unchanged timer cannot repeat either refresh",2,f.clientQueue.size());verify(skips,times(1)).save();
        clock.addAndGet(1001);f.plugin.tick();assertEquals(3,f.clientQueue.size());verify(skips,times(1)).save();
        assertFalse(((AtomicBoolean)field(f.plugin,"engineRefreshRequested")).get());
        clock.set(-69_999);f.plugin.tick();assertTrue("Guidance becomes due independently of account refresh",((AtomicBoolean)field(f.plugin,"engineRefreshRequested")).get());
        verify(skips,times(1)).save();clock.set(-39_999);f.plugin.tick();verify(skips,times(2)).save();
    }
    private Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
}
