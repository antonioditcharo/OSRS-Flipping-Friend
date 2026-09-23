package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class IdempotentOfferAcceptanceTest
{
	private static final int ITEM = 561;
	private static final int LIMIT = 18_000;
	@Test public void duplicateCanonicalDeliveryAppliesEffectsOnlyOnce() throws Exception
	{
		Path db = Files.createTempDirectory("idempotent-offer").resolve("companion.db");
		try (SqliteStore store = new SqliteStore(db); CompanionService service = new CompanionService(new Gson(), store))
		{
			OfferEvent event = settledBuy("event-1");
			assertEquals(SqliteStore.EventAcceptance.NEW, service.offer(event));
			assertEquals(SqliteStore.EventAcceptance.DUPLICATE, service.offer(event));
			assertEquals(1, store.recentOfferEvents(0).size()); assertEquals(1, store.executionStats().get(ITEM).observed);
			assertEquals(Integer.valueOf(LIMIT - 9_000), remaining(service));
		}
	}
	@Test public void legacyDeliveriesRemainAppendOnly() throws Exception
	{
		Path db = Files.createTempDirectory("legacy-offer").resolve("companion.db");
		try (SqliteStore store = new SqliteStore(db); CompanionService service = new CompanionService(new Gson(), store))
		{
			OfferEvent event = settledBuy(null);
			assertEquals(SqliteStore.EventAcceptance.NEW, service.offer(event));
			assertEquals(SqliteStore.EventAcceptance.NEW, service.offer(event));
			assertEquals(2, store.recentOfferEvents(0).size());
		}
	}
	private static OfferEvent settledBuy(String eventId)
	{
		long now=Instant.now().getEpochSecond(); return OfferEvent.builder("trace",now,"BOUGHT").eventId(eventId).slot(0)
			.item(ITEM,"Nature rune").buying(true).price(100).quantities(9_000,9_000).spent(900_000).firstSeenAt(now).build();
	}
	private static Integer remaining(CompanionService service) throws Exception
	{
		java.lang.reflect.Field f=CompanionService.class.getDeclaredField("buyLimits"); f.setAccessible(true);
		BuyLimitLedger ledger=(BuyLimitLedger)f.get(service); java.util.Map<Integer,MarketIngestionService.Item> m=new java.util.HashMap<>();
		m.put(ITEM,new MarketIngestionService.Item(ITEM,"Nature rune",LIMIT)); return ledger.remaining(m).get(ITEM);
	}
}
