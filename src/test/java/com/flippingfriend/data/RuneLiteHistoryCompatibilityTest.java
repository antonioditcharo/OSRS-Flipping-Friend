package com.flippingfriend.data;

import com.flippingfriend.companion.CompanionClient;
import com.flippingfriend.core.GeHistorySnapshot;
import com.google.gson.Gson;
import java.nio.file.*;
import net.runelite.api.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.grandexchange.GrandExchangeConfig;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Runs the actual adapter with RuneLite's provided Gson, including asynchronous persistence. */
public class RuneLiteHistoryCompatibilityTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    @Test public void currentRuneLiteHistoryIsCapturedAndAcknowledgedWithItsProvidedGson()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"history-client");
        var configuration=mock(ConfigManager.class);when(configuration.getRSProfileKey()).thenReturn("history-client");
        long now=System.currentTimeMillis();String raw="[{\"b\":true,\"i\":561,\"q\":3,\"p\":101,\"t\":"+(now-1000)+"}]";
        when(configuration.getRSProfileConfiguration(GrandExchangeConfig.CONFIG_GROUP,"tradeHistory")).thenReturn(raw);
        var client=mock(Client.class);when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        var companion=mock(CompanionClient.class);when(companion.publishHistory(any())).thenReturn(true);
        var collector=new RuneLiteHistoryCollector(storage,configuration,client,companion,new Gson());collector.start();
        try {
            collector.capture(true);var sent=ArgumentCaptor.forClass(GeHistorySnapshot.class);
            verify(companion,timeout(3000)).publishHistory(sent.capture());
            assertEquals(storage.accountId(),sent.getValue().accountId);assertEquals(raw,sent.getValue().historyJson);
            assertEquals(303,sent.getValue().rows().get(0).grossLower());assertEquals(305,sent.getValue().rows().get(0).grossUpper());
            try(var files=Files.list(storage.accountDir().resolve("history-evidence"))) {
                assertEquals(1,files.filter(p->p.getFileName().toString().endsWith(".json")).count());
            }
            Path ack=storage.accountDir().resolve("history-evidence").resolve(sent.getValue().evidenceId()+".json.ack");
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
            while(!Files.exists(ack) && System.nanoTime()-deadline<0)Thread.sleep(10);
            assertTrue("The independent supporting-evidence receipt must also be durable",Files.exists(ack));
        }finally{collector.stop();}
    }
}
