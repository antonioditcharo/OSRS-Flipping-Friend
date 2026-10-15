package com.flippingfriend.data;

import com.flippingfriend.session.*;
import com.flippingfriend.model.TaxCalculator;
import com.google.gson.Gson;
import java.time.Instant;
import java.util.*;
import net.runelite.client.config.ConfigManager;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** One real journal/storage instance across profile switches; only disposable JSON files. */
public class TradeRiskAccountIsolationTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    FlipRecord winningFlip() {
        return new FlipRecord(4151,"Abyssal whip",10,1000000,2000000,400000,9600000,
            100,200,1,9600000,"MODERATE");
    }
    @Test public void anotherAccountsProfitCannotDisarmTheCurrentAccountsDrawdown()throws Exception {
        var profiles=mock(ConfigManager.class);when(profiles.getRSProfileKey()).thenReturn("account-a");
        var storage=new PluginStorage(profiles,new Gson(),folder.newFolder().toPath());
        var journal=new TradeJournal(storage,null,new AccountMonitor(null,null,null));
        journal.load();journal.startSession();journal.record(winningFlip());
        when(profiles.getRSProfileKey()).thenReturn("account-b");journal.load();
        var positions=new PositionBook(null);positions.recordBuy(4151,"Abyssal whip",10,10000000,Instant.now());
        long now=Instant.now().getEpochSecond();
        var market=new MarketSnapshot(Map.of(4151,new ItemMetadata(4151,"Abyssal whip",true,70,0)),
            Map.of(4151,new LatestPrice(510000,now,500000,now)),Map.of(),Map.of(),Instant.now());
        assertTrue("Foreign profit must not hide a greater-than-4M loss",SessionRisk.markedDrawdown(journal.sessionStats(),positions,market,new TaxCalculator())>4000000);
        assertEquals(0,journal.sessionStats().getProfit());
    }
    @Test public void switchingBackRestoresOnlyThatAccountsLocalSession()throws Exception {
        var profiles=mock(ConfigManager.class);when(profiles.getRSProfileKey()).thenReturn("account-a");
        var storage=new PluginStorage(profiles,new Gson(),folder.newFolder().toPath());
        var journal=new TradeJournal(storage,null,new AccountMonitor(null,null,null));
        journal.load();journal.startSession();journal.record(winningFlip());
        when(profiles.getRSProfileKey()).thenReturn("account-b");journal.load();
        journal.record(new FlipRecord(561,"Nature rune",10,100000,50000,10000,-510000,100,200,1,100,"MODERATE"));
        assertEquals(-510000,journal.sessionStats().getProfit());
        when(profiles.getRSProfileKey()).thenReturn("account-a");journal.load();
        assertEquals(9600000,journal.sessionStats().getProfit());assertEquals(1,journal.getSessionFlips().size());
        when(profiles.getRSProfileKey()).thenReturn("account-b");journal.load();
        assertEquals(-510000,journal.sessionStats().getProfit());assertEquals(1,journal.getSessionFlips().size());
    }
}
