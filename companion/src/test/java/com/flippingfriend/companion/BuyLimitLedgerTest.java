package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

/**
 * The four-hour buy allowance is anchored to the first purchase, not sliding. Getting that wrong
 * makes the planner suggest purchases the game will silently refuse, which strands a slot with no
 * error message.
 */
public class BuyLimitLedgerTest
{
	private static final int ITEM = 561;
	private static final int LIMIT = 10_000;

	private static Map<Integer, MarketIngestionService.Item> mapping()
	{
		Map<Integer, MarketIngestionService.Item> mapping = new HashMap<>();
		mapping.put(ITEM, new MarketIngestionService.Item(ITEM, "Nature rune", LIMIT));
		return mapping;
	}

	private static OfferEvent buy(int filled, Instant at)
	{
		return OfferEvent.builder("c", at.getEpochSecond(), "FILL")
			.slot(0).item(ITEM, "Nature rune").buying(true).price(100)
			.quantities(LIMIT, filled).spent(100L * filled).build();
	}

	/** A distinct offer instance: its own slot, and its own moment of first appearing. */
	private static OfferEvent buy(int filled, Instant at, int slot, long firstSeenAt)
	{
		return OfferEvent.builder("c", at.getEpochSecond(), "FILL")
			.slot(slot).firstSeenAt(firstSeenAt).item(ITEM, "Nature rune").buying(true).price(100)
			.quantities(LIMIT, filled).spent(100L * filled).build();
	}

	@Test
	public void twoSeparateOffersBothConsumeTheAllowance()
	{
		// The other half of the running-total rule, and the half that was missing. Keyed by item
		// alone, a high-water mark cannot tell two 3,000-unit purchases apart from one announced
		// twice, so it recorded 3,000 where 6,000 of the allowance had gone. The next offer then
		// goes over the limit and stops filling part-way with no error -- the exact failure the
		// ledger exists to prevent.
		BuyLimitLedger ledger = new BuyLimitLedger();
		Instant now = Instant.now();

		ledger.apply(buy(3_000, now, 0, now.getEpochSecond() - 600));
		ledger.apply(buy(3_000, now, 1, now.getEpochSecond() - 60));

		assertEquals(Integer.valueOf(LIMIT - 6_000), ledger.remaining(mapping()).get(ITEM));
	}

	@Test
	public void twoOffersTheClientNeverSawAppearAreStillTwoPurchases()
	{
		// firstSeenAt is 0 whenever the plugin did not watch the offer appear -- a login replay, or
		// the plugin being enabled mid-offer. Both offers then keyed to "slot:0" and the high-water
		// merge kept only the larger, recording 3,000 where 6,000 of the allowance had gone.
		BuyLimitLedger ledger = new BuyLimitLedger();
		Instant now = Instant.now();

		ledger.apply(buy(3_000, now.minusSeconds(600), 0, 0));
		ledger.apply(buy(3_000, now, 1, 0));

		assertEquals(Integer.valueOf(LIMIT - 6_000), ledger.remaining(mapping()).get(ITEM));
	}

	@Test
	public void aFillLostInTransitIsCorrectedByThePluginsLedger()
	{
		// Offer events are fire-and-forget over loopback: one that lands while this process is
		// restarting never arrives, and nothing ever asks again. The plugin's ledger is durable, so it
		// comes with every account snapshot and is used as a floor.
		BuyLimitLedger ledger = new BuyLimitLedger();
		Instant now = Instant.now();
		ledger.apply(buy(1_000, now, 0, now.getEpochSecond() - 600));

		Map<Integer, Integer> pluginKnows = new HashMap<>();
		pluginKnows.put(ITEM, 4_000);
		ledger.reconcile(pluginKnows);

		assertEquals("the allowance the plugin saw spent must be respected",
			Integer.valueOf(LIMIT - 4_000), ledger.remaining(mapping()).get(ITEM));
	}

	@Test
	public void reconcilingTwiceDoesNotSpendTheAllowanceTwice()
	{
		// The snapshot arrives every cycle, so the correction has to be idempotent -- adding it as a
		// delta each time would eat the whole allowance within minutes.
		BuyLimitLedger ledger = new BuyLimitLedger();
		Map<Integer, Integer> pluginKnows = new HashMap<>();
		pluginKnows.put(ITEM, 3_000);

		for (int i = 0; i < 20; i++)
		{
			ledger.reconcile(pluginKnows);
		}

		assertEquals(Integer.valueOf(LIMIT - 3_000), ledger.remaining(mapping()).get(ITEM));
	}

	@Test
	public void reconcilingNeverLowersWhatThisLedgerAlreadySaw()
	{
		// A floor, not a ceiling. If this process has seen more than the plugin has booked yet, the
		// higher figure stands: over-ordering is the expensive mistake.
		BuyLimitLedger ledger = new BuyLimitLedger();
		Instant now = Instant.now();
		ledger.apply(buy(5_000, now, 0, now.getEpochSecond() - 600));

		Map<Integer, Integer> pluginKnows = new HashMap<>();
		pluginKnows.put(ITEM, 1_000);
		ledger.reconcile(pluginKnows);

		assertEquals(Integer.valueOf(LIMIT - 5_000), ledger.remaining(mapping()).get(ITEM));
	}

	@Test
	public void oneOfferReusingASlotIsStillItsOwnPurchase()
	{
		// A collected offer frees its slot immediately, so the slot number alone repeats. What
		// separates the new offer from the old one is when it first appeared.
		BuyLimitLedger ledger = new BuyLimitLedger();
		Instant now = Instant.now();

		ledger.apply(buy(2_000, now, 3, now.getEpochSecond() - 900));
		ledger.apply(buy(2_500, now, 3, now.getEpochSecond() - 120));

		assertEquals(Integer.valueOf(LIMIT - 4_500), ledger.remaining(mapping()).get(ITEM));
	}

	@Test
	public void countsFillsAgainstTheAllowance()
	{
		BuyLimitLedger ledger = new BuyLimitLedger();
		ledger.apply(buy(3_000, Instant.now()));

		assertEquals(Integer.valueOf(LIMIT - 3_000), ledger.remaining(mapping()).get(ITEM));
	}

	@Test
	public void treatsRepeatedEventsAsARunningTotalNotADelta()
	{
		// The plugin reports the offer's running total, and may replay it on reconnect. Summing
		// those would double-count and lock the item out for four hours for no reason.
		BuyLimitLedger ledger = new BuyLimitLedger();
		Instant now = Instant.now();
		ledger.apply(buy(2_000, now));
		ledger.apply(buy(2_000, now));
		ledger.apply(buy(3_500, now));

		assertEquals(Integer.valueOf(LIMIT - 3_500), ledger.remaining(mapping()).get(ITEM));
	}

	@Test
	public void theWholeAllowanceReturnsFourHoursAfterTheFirstPurchase()
	{
		BuyLimitLedger ledger = new BuyLimitLedger();
		ledger.apply(buy(LIMIT, Instant.now().minusSeconds(4 * 3600 + 60)));

		assertEquals("an expired window frees the full limit",
			Integer.valueOf(LIMIT), ledger.remaining(mapping()).get(ITEM));
		assertNull(ledger.resetsAt(ITEM));
	}

	@Test
	public void reportsWhenTheAllowanceComesBack()
	{
		BuyLimitLedger ledger = new BuyLimitLedger();
		Instant now = Instant.now();
		ledger.apply(buy(500, now));

		Instant resets = ledger.resetsAt(ITEM);
		assertNotNull("a live window must report its reset time for slot rotation", resets);
		assertEquals(now.plus(BuyLimitLedger.WINDOW).getEpochSecond(), resets.getEpochSecond());
	}

	@Test
	public void ignoresSellsAndEmptyFills()
	{
		BuyLimitLedger ledger = new BuyLimitLedger();
		Instant now = Instant.now();
		ledger.apply(OfferEvent.builder("c", now.getEpochSecond(), "FILL")
			.slot(0).item(ITEM, "Nature rune").buying(false).price(100)
			.quantities(LIMIT, 5_000).spent(500_000L).build());
		ledger.apply(buy(0, now));

		assertEquals("selling does not consume a buy allowance",
			Integer.valueOf(LIMIT), ledger.remaining(mapping()).get(ITEM));
		assertEquals(0, ledger.trackedItems());
	}

	@Test
	public void neverReportsNegativeRemaining()
	{
		BuyLimitLedger ledger = new BuyLimitLedger();
		ledger.apply(buy(LIMIT + 5_000, Instant.now()));

		assertEquals(Integer.valueOf(0), ledger.remaining(mapping()).get(ITEM));
	}
}
