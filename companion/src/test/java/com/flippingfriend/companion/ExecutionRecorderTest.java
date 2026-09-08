package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The execution record is the only place real fill behaviour enters the system, so the cases that
 * matter are the two ways of corrupting a count: treating each partial fill as a separate offer, and
 * re-counting offers the game replays after a reconnect. Both inflate the sample with duplicates,
 * and a model calibrated against an inflated sample is confidently wrong rather than merely
 * uncertain.
 */
public class ExecutionRecorderTest
{
	private Path database;
	private SqliteStore store;
	private ExecutionRecorder recorder;

	@Before
	public void setUp() throws Exception
	{
		database = Files.createTempDirectory("flipping-friend-exec").resolve("test.db");
		store = new SqliteStore(database);
		recorder = new ExecutionRecorder(store);
	}

	@After
	public void tearDown() throws Exception
	{
		store.close();
	}

	private static OfferEvent.Builder offer(String type, int slot, long firstSeen, long observedAt)
	{
		return OfferEvent.builder("c", observedAt, type)
			.slot(slot)
			.item(4151, "Abyssal whip")
			.buying(true)
			.price(1_000_000)
			.firstSeenAt(firstSeen);
	}

	@Test
	public void aRestartDoesNotReCountOffersTheGameReplays() throws Exception
	{
		// The reconnect that matters is the one straight after the companion restarts. The memory of
		// what had already been counted used to live in this object, so it was empty at exactly the
		// moment the client re-announced every open slot -- and each settled offer went into
		// execution_stat a second time. That table is the sample both the completion posterior and
		// the learned durations are built on, so the duplicates do not just add noise, they make the
		// model confident about a history that never happened.
		assertTrue(recorder.record(offer("BOUGHT", 1, 100, 400).quantities(10, 10).build()));

		Map<Integer, SqliteStore.ExecutionStat> afterFirst = store.executionStats();
		assertEquals(1, afterFirst.get(4151).observed);

		// A new recorder over the same database is what a restart looks like.
		ExecutionRecorder afterRestart = new ExecutionRecorder(store);
		assertFalse("the replayed offer must not be counted again",
			afterRestart.record(offer("BOUGHT", 1, 100, 400).quantities(10, 10).build()));

		assertEquals("the sample must be unchanged by a restart",
			1, store.executionStats().get(4151).observed);

		// A genuinely different offer in the same slot still counts.
		assertTrue(afterRestart.record(offer("BOUGHT", 1, 900, 1200).quantities(10, 10).build()));
		assertEquals(2, store.executionStats().get(4151).observed);
	}

	@Test
	public void countsNothingUntilAnOfferSettles() throws Exception
	{
		assertFalse(recorder.record(offer("BUYING", 1, 100, 160).quantities(10, 3).build()));
		assertFalse(recorder.record(offer("BUYING", 1, 100, 220).quantities(10, 7).build()));

		assertTrue("an offer still in the queue has not told us anything yet",
			store.executionStats().isEmpty());
	}

	@Test
	public void recordsOneObservationPerOfferRatherThanPerFill() throws Exception
	{
		// A single offer that filled in three visible steps.
		recorder.record(offer("BUYING", 1, 100, 160).quantities(10, 3).build());
		recorder.record(offer("BUYING", 1, 100, 220).quantities(10, 7).build());
		assertTrue(recorder.record(offer("BOUGHT", 1, 100, 400).quantities(10, 10).build()));

		SqliteStore.ExecutionStat stat = store.executionStats().get(4151);
		assertEquals("one offer, not three", 1, stat.observed);
		assertEquals(1, stat.completed);
		assertEquals("300 seconds open", 5.0, stat.meanFillMinutes(), 1e-9);
	}

	@Test
	public void ignoresTheSameSettledOfferReplayedOnLogin() throws Exception
	{
		assertTrue(recorder.record(offer("BOUGHT", 1, 100, 400).quantities(10, 10).build()));
		assertFalse("the game re-announces finished offers on every reconnect",
			recorder.record(offer("BOUGHT", 1, 100, 400).quantities(10, 10).build()));

		assertEquals(1, store.executionStats().get(4151).observed);
	}

	@Test
	public void aReplacementOfferInTheSameSlotIsADifferentOffer() throws Exception
	{
		assertTrue(recorder.record(offer("BOUGHT", 1, 100, 400).quantities(10, 10).build()));
		assertTrue("a new offer necessarily starts later than the one it replaced",
			recorder.record(offer("BOUGHT", 1, 500, 800).quantities(10, 10).build()));

		assertEquals(2, store.executionStats().get(4151).observed);
	}

	@Test
	public void aCancelledOfferCountsAsObservedButNotAsAFillTime() throws Exception
	{
		// Cancelling after twenty minutes does not mean the item takes twenty minutes to buy; it
		// means it did not fill. Averaging that in would measure the player's patience, not the market.
		recorder.record(offer("CANCELLED_BUY", 1, 100, 1_300).quantities(10, 4).build());

		SqliteStore.ExecutionStat stat = store.executionStats().get(4151);
		assertEquals(1, stat.observed);
		assertEquals(0, stat.completed);
		assertEquals(0.0, stat.fillMinutes, 1e-9);
		assertEquals("a partial fill is still a failure to complete", 0.0, stat.completionRate(), 1e-9);
	}

	@Test
	public void accumulatesAcrossOffersIntoARealisedCompletionRate() throws Exception
	{
		recorder.record(offer("BOUGHT", 1, 100, 400).quantities(10, 10).build());
		recorder.record(offer("CANCELLED_BUY", 2, 100, 400).quantities(10, 2).build());
		recorder.record(offer("BOUGHT", 3, 100, 700).quantities(10, 10).build());

		SqliteStore.ExecutionStat stat = store.executionStats().get(4151);
		assertEquals(3, stat.observed);
		assertEquals(2, stat.completed);
		assertEquals(2.0 / 3.0, stat.completionRate(), 1e-9);
		// Five minutes and ten minutes over the two that completed.
		assertEquals(7.5, stat.meanFillMinutes(), 1e-9);
	}

	@Test
	public void separatesItemsFromEachOther() throws Exception
	{
		recorder.record(offer("BOUGHT", 1, 100, 400).quantities(10, 10).build());
		recorder.record(OfferEvent.builder("c", 400, "SOLD").slot(2).item(561, "Nature rune")
			.buying(false).firstSeenAt(100).quantities(500, 500).build());

		Map<Integer, SqliteStore.ExecutionStat> stats = store.executionStats();
		assertEquals(1, stats.get(4151).observed);
		assertEquals(1, stats.get(561).observed);
	}
}
