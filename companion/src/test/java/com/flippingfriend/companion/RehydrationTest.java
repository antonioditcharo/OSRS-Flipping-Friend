package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Restarting the companion must not reset state the <em>game</em> is still enforcing.
 * <p>
 * Buy-limit windows are the case that bites: they live in memory but describe a four-hour reality
 * that continues regardless of whether this process is running. A companion that forgets them comes
 * back believing every allowance is untouched and recommends purchases the game will refuse — and
 * the refusal is silent, showing up only as an offer that stops filling part-way with a slot tied up
 * and half a position to unwind.
 */
public class RehydrationTest
{
	private static final int ITEM = 561;
	private static final int LIMIT = 12_000;

	private Path directory;
	private Gson gson;

	@Before
	public void setUp() throws Exception
	{
		directory = Files.createTempDirectory("flipping-friend-rehydrate");
		gson = new Gson();
	}

	private SqliteStore open() throws Exception
	{
		return new SqliteStore(directory.resolve("test.db"));
	}

	private static OfferEvent buy(int filled, Instant at)
	{
		return OfferEvent.builder("c", at.getEpochSecond(), "BOUGHT")
			.slot(0).item(ITEM, "Nature rune").buying(true).price(100)
			.quantities(filled, filled).spent(100L * filled).firstSeenAt(at.getEpochSecond())
			.build();
	}

	@Test
	public void buyLimitsSurviveARestart() throws Exception
	{
		SqliteStore first = open();
		CompanionService before = new CompanionService(gson, first);
		before.offer(buy(9_000, Instant.now()));
		before.close();
		first.close();

		// A brand new process against the same database.
		SqliteStore second = open();
		CompanionService after = new CompanionService(gson, second);
		after.rehydrate();

		assertEquals("the game is still enforcing this window, so the ledger must know about it",
			Integer.valueOf(LIMIT - 9_000), remaining(after));
		after.close();
		second.close();
	}

	@Test
	public void windowsOlderThanFourHoursAreNotResurrected() throws Exception
	{
		SqliteStore first = open();
		CompanionService before = new CompanionService(gson, first);
		before.offer(buy(9_000, Instant.now().minus(Duration.ofHours(5))));
		before.close();
		first.close();

		SqliteStore second = open();
		CompanionService after = new CompanionService(gson, second);
		after.rehydrate();

		assertEquals("that allowance came back hours ago", Integer.valueOf(LIMIT), remaining(after));
		after.close();
		second.close();
	}

	@Test
	public void replayDoesNotDoubleCountExecutionStatistics() throws Exception
	{
		// execution_stat is already durable. Re-recording settled offers on every restart would
		// inflate the sample by however many times the companion has been restarted, which is worse
		// than having no sample: the model would be confidently calibrated against a fiction.
		SqliteStore first = open();
		CompanionService before = new CompanionService(gson, first);
		before.offer(buy(9_000, Instant.now()));
		assertEquals(1, first.executionStats().get(ITEM).observed);
		before.close();
		first.close();

		SqliteStore second = open();
		CompanionService after = new CompanionService(gson, second);
		after.rehydrate();

		assertEquals("one offer happened, and restarting did not make it two",
			1, second.executionStats().get(ITEM).observed);
		after.close();
		second.close();
	}

	private static Integer remaining(CompanionService service) throws Exception
	{
		java.lang.reflect.Field field = CompanionService.class.getDeclaredField("buyLimits");
		field.setAccessible(true);
		BuyLimitLedger ledger = (BuyLimitLedger) field.get(service);

		java.util.Map<Integer, MarketIngestionService.Item> mapping = new java.util.HashMap<>();
		mapping.put(ITEM, new MarketIngestionService.Item(ITEM, "Nature rune", LIMIT));
		return ledger.remaining(mapping).get(ITEM);
	}

	@Test public void durableLimitsAndOffersDoNotDependOnReplayingUnassignedLegacyPayloads() throws Exception {
		try(var first=open()) {
			var before=new CompanionService(gson,first);
			try{before.offer(buy(9_000,Instant.now()));}finally{before.close();}
			// Legacy rows without source IDs are not canonical execution evidence. Replaying
			// an unused malformed row must not strand already durable offer/limit state.
			first.recordEvent(Instant.now().getEpochSecond(),"unused-legacy","BUYING","{");
		}
		try(var second=open()) {
			var after=new CompanionService(gson,second);
			try {
				after.rehydrate();
				assertEquals(Integer.valueOf(LIMIT-9_000),remaining(after));
				assertEquals(9_000,second.offerProjections().get(0).getFilledQuantity());
				assertEquals(1,second.executionStats().get(ITEM).observed);
			}finally{after.close();}
		}
	}
	@Test public void missingDurableStateStillRestoresLegacyLimitsWithoutBookingFinancialEffects() throws Exception {
		try(var store=open()) {
			var event=buy(9_000,Instant.now());
			store.recordEvent(event.getObservedAt(),"legacy","BOUGHT",gson.toJson(event));
			var after=new CompanionService(gson,store);
			try {
				after.rehydrate();
				assertEquals(Integer.valueOf(LIMIT-9_000),remaining(after));
				assertEquals(0,store.positionEventCount());
				assertEquals(0,store.buyLimitProjections().size());
				assertEquals(0,store.executionStats().size());
			}finally{after.close();}
		}
	}
}
