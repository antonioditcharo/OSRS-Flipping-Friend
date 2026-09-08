package com.flippingfriend.companion;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/**
 * Covers the buy-limit window being explained rather than merely enforced.
 *
 * <p>{@link BuyLimitLedger#resetsAt} has existed, with tests, and no production caller: the system
 * knew to the second when every item became available again and never told anyone. A spent limit
 * therefore read as permanent, and "spent" and "spent, back in 40 minutes" call for very different
 * responses from a player deciding whether to wait.
 */
public class BuyLimitRotationTest
{
	private static final int ITEM = 4151;
	private static final long T0 = 1_700_000_000L;

	private static SeriesSource history()
	{
		return (itemId, timestep) -> Collections.<Candle>emptyList();
	}

	private static Candle bar(int low, int high)
	{
		return new Candle(T0, high, low, 5_000, 5_000);
	}

	private static CandidateFactory.QuotedItem quoted(int id, int low, int high)
	{
		MarketIngestionService.Item item =
			new MarketIngestionService.Item(id, "Abyssal whip", 70, true, true);
		return new CandidateFactory.QuotedItem(item, new LatestPrice(high, T0, low, T0),
			bar(low, high), bar(low, high));
	}

	/** Spends the whole four-hour limit, so the next plan must turn the item away. */
	private static BuyLimitLedger spentLedger()
	{
		BuyLimitLedger ledger = new BuyLimitLedger();
		ledger.apply(OfferEvent.builder("t", T0, "BOUGHT")
			.item(ITEM, "Abyssal whip")
			.slot(1)
			.buying(true)
			.price(1_000)
			.quantities(70, 70)
			.firstSeenAt(T0)
			.build());
		return ledger;
	}

	/** Runs one planning pass and returns the veto recorded against the item, or null. */
	private static String vetoFor(CandidateFactory factory, Instant now)
	{
		Map<Integer, Integer> spent = new HashMap<>();
		spent.put(ITEM, 0);
		factory.build(Collections.singletonList(quoted(ITEM, 1_000, 1_100)), 1.0, spent,
			100_000_000L, now);
		return factory.lastVetoFor(ITEM);
	}

	@Test
	public void aSpentLimitSaysWhenItFrees()
	{
		CandidateFactory factory = new CandidateFactory(history());
		factory.setBuyLimitLedger(spentLedger());

		// One hour into a four-hour window: three hours left.
		String veto = vetoFor(factory, Instant.ofEpochSecond(T0 + 3600));

		assertTrue("the veto must fire: " + veto, veto != null && veto.contains("buy limit is spent"));
		assertTrue("and must say when it frees: " + veto, veto.contains("resets in"));
		assertTrue("in hours and minutes: " + veto, veto.contains("h "));
	}

	@Test
	public void aShortWaitIsReportedInMinutes()
	{
		CandidateFactory factory = new CandidateFactory(history());
		factory.setBuyLimitLedger(spentLedger());

		// Three and a half hours in: half an hour left.
		String veto = vetoFor(factory, Instant.ofEpochSecond(T0 + 3600 * 3 + 1800));

		assertTrue("a sub-hour wait must read in minutes: " + veto, veto.contains("m."));
		assertFalse("and must not claim hours: " + veto, veto.contains("h "));
	}

	/**
	 * Replay has no live ledger. The veto must still fire — the limit is real — it just cannot say
	 * when it lifts, and inventing a time would be worse than omitting one.
	 */
	@Test
	public void withoutALedgerTheVetoStillFiresButPromisesNothing()
	{
		CandidateFactory factory = new CandidateFactory(history());

		String veto = vetoFor(factory, Instant.ofEpochSecond(T0 + 3600));

		assertTrue("the limit still applies: " + veto,
			veto != null && veto.contains("buy limit is spent"));
		assertFalse("but no time can be claimed: " + veto, veto.contains("resets in"));
	}

	/** Once the window has elapsed there is no wait to report, and the item is tradeable again. */
	@Test
	public void anExpiredWindowReportsNoWait()
	{
		CandidateFactory factory = new CandidateFactory(history());
		factory.setBuyLimitLedger(spentLedger());

		String veto = vetoFor(factory, Instant.ofEpochSecond(T0 + 3600 * 5));

		assertFalse("an elapsed window must not advertise a wait: " + veto,
			veto != null && veto.contains("resets in"));
	}
}
