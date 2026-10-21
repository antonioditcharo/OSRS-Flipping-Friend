package com.flippingfriend.session;

import com.flippingfriend.data.TestStorage;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SaleContinuationAcceptanceTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final long now=java.time.Instant.now().getEpochSecond();
    @Test public void aRetainedHintCanAcceptItsFirstSourceAfterLaterFillsAndSurviveCollectionAndRestart()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"sale-upgrade");
        var tracker=mock(OfferTracker.class);
        var sale=new TrackedOffer(0,561,false,150,10,now-2400);sale.startEpisodeIdentity();sale.setState("SELLING");
        when(tracker.getOffers()).thenReturn(List.of(sale));when(tracker.getOffer(0)).thenReturn(sale);
        var legacy=new OfferContinuationStore(storage,"sell-relist-intent.json",false){};
        assertTrue(legacy.adviseOffer(sale,storage.accountId(),true,tracker,now-1200,0));
        sale.setQuantityFilled(2);sale.setSpent(300);sale.observeFillAt(now-600);
        var accepted=new SellRelistIntent(storage);
        assertTrue(accepted.advise(sale,storage.accountId(),true,tracker,"accepted-after-fill"));
        accepted.observe(tracker,storage.accountId());
        sale.setQuantityFilled(3);sale.setSpent(450);sale.setState("CANCELLED_SELL");sale.setCollected(true);
        when(tracker.getOffers()).thenReturn(List.of());when(tracker.getOffer(0)).thenReturn(null);
        when(tracker.getSettledOffer(0)).thenReturn(sale);
        var request=new SellRelistIntent(storage).collectedRequest(tracker,storage.accountId());
        assertNotNull("Upgrading the accepted source must not erase the original continuation",request);
        assertEquals(7,request.getQuantity());assertTrue(request.requiresManagedCost());
        assertEquals("accepted-after-fill",request.getSaleRepriceSource());assertEquals(now-600,request.getProgressAt());
        var document=storage.gson().fromJson(Files.readString(storage.accountDir().resolve("sell-relist-intent.json")),com.google.gson.JsonObject.class);
        var retained=document.getAsJsonArray("continuations").get(0).getAsJsonObject();
        assertEquals(now-1200,retained.get("createdAt").getAsLong());
        assertTrue(retained.get("saleRepriceAcceptedAt").getAsLong()>=now);
        when(tracker.getSettledOffer(0)).thenReturn(null);
        assertEquals(7,new SellRelistIntent(storage).collectedRequest(tracker,storage.accountId()).getQuantity());
    }
    @Test public void failedUpgradeKeepsTheCommittedHintAndRetriesAfterAccountSwitch()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"sale-upgrade");var tracker=mock(OfferTracker.class);
        var sale=new TrackedOffer(0,561,false,150,10,now-2400);sale.startEpisodeIdentity();sale.setState("SELLING");
        when(tracker.getOffers()).thenReturn(List.of(sale));when(tracker.getOffer(0)).thenReturn(sale);
        var legacy=new OfferContinuationStore(storage,"sell-relist-intent.json",false){};
        String originalAccount=storage.accountId();assertTrue(legacy.adviseOffer(sale,originalAccount,true,tracker,now-1200,0));
        Path path=storage.accountDir().resolve("sell-relist-intent.json"),blocked=path.resolveSibling(path.getFileName()+".tmp");
        String before=Files.readString(path);Files.createDirectory(blocked);
        sale.setQuantityFilled(2);sale.setSpent(300);sale.observeFillAt(now-600);var book=new SellRelistIntent(storage);
        assertFalse(book.advise(sale,originalAccount,true,tracker,"accepted-later"));assertEquals(before,Files.readString(path));
        var field=com.flippingfriend.data.PluginStorage.class.getDeclaredField("configManager");field.setAccessible(true);
        var config=(net.runelite.client.config.ConfigManager)field.get(storage);when(config.getRSProfileKey()).thenReturn("other");
        assertFalse(book.advise(sale,originalAccount,true,tracker,"foreign-attempt"));
        when(config.getRSProfileKey()).thenReturn("sale-upgrade");Files.delete(blocked);
        assertTrue(book.advise(sale,originalAccount,true,tracker,"accepted-later"));
        assertTrue(Files.readString(path).contains("accepted-later"));assertFalse(Files.readString(path).contains("foreign-attempt"));
        assertTrue(storage.gson().fromJson(Files.readString(path),com.google.gson.JsonObject.class)
            .getAsJsonArray("continuations").get(0).getAsJsonObject().get("saleRepriceAcceptedAt").getAsLong()>=now);
    }
    @Test public void futureProgressAndHistoricallyInvalidBindingsArePreservedWithoutManufacturedRepair()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"sale-upgrade");var tracker=mock(OfferTracker.class);
        var sale=new TrackedOffer(0,561,false,150,10,now-2400);sale.startEpisodeIdentity();sale.setState("SELLING");
        when(tracker.getOffers()).thenReturn(List.of(sale));when(tracker.getOffer(0)).thenReturn(sale);
        var legacy=new OfferContinuationStore(storage,"sell-relist-intent.json",false){};
        assertTrue(legacy.adviseOffer(sale,storage.accountId(),true,tracker,now-1200,0));
        Path path=storage.accountDir().resolve("sell-relist-intent.json");String before=Files.readString(path);
        sale.observeFillAt(now+86400);assertFalse(new SellRelistIntent(storage).advise(sale,storage.accountId(),true,tracker,"future"));
        assertEquals(before,Files.readString(path));
        var document=storage.gson().fromJson(before,com.google.gson.JsonObject.class);
        var retained=document.getAsJsonArray("continuations").get(0).getAsJsonObject();
        retained.addProperty("saleRepriceSource","unproven-old-binding");retained.addProperty("saleRepriceProgressAt",now-600);
        retained.remove("saleRepriceAcceptedAt");String invalid=document.toString();Files.writeString(path,invalid);
        assertNull(new SellRelistIntent(storage).collectedRequest(tracker,storage.accountId()));assertEquals(invalid,Files.readString(path));
    }
    @Test public void compatibleLegacyBindingsKeepTheirOriginalSourceWithoutAnAcceptanceBackfill()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"sale-upgrade");var tracker=mock(OfferTracker.class);
        var sale=new TrackedOffer(0,561,false,150,10,now-2400);sale.startEpisodeIdentity();sale.setState("SELLING");
        when(tracker.getOffers()).thenReturn(List.of(sale));when(tracker.getOffer(0)).thenReturn(sale);
        var original=new SellRelistIntent(storage);assertTrue(original.advise(sale,storage.accountId(),true,tracker,"old-source"));
        Path path=storage.accountDir().resolve("sell-relist-intent.json");
        var doc=storage.gson().fromJson(Files.readString(path),com.google.gson.JsonObject.class);
        doc.getAsJsonArray("continuations").get(0).getAsJsonObject().remove("saleRepriceAcceptedAt");Files.writeString(path,doc.toString());
        sale.setState("CANCELLED_SELL");sale.setQuantityFilled(3);sale.setSpent(450);sale.setCollected(true);
        when(tracker.getOffers()).thenReturn(List.of());when(tracker.getSettledOffer(0)).thenReturn(sale);
        var remaining=new SellRelistIntent(storage).collectedRequest(tracker,storage.accountId());
        assertNotNull(remaining);assertEquals(7,remaining.getQuantity());assertEquals("old-source",remaining.getSaleRepriceSource());
        var written=storage.gson().fromJson(Files.readString(path),com.google.gson.JsonObject.class)
            .getAsJsonArray("continuations").get(0).getAsJsonObject();
        assertEquals("Missing legacy time stays absent/zero rather than becoming today's acceptance",0,written.get("saleRepriceAcceptedAt").getAsLong());
    }
}
