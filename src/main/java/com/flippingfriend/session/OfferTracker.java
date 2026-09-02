package com.flippingfriend.session;

import com.flippingfriend.data.MarketSnapshot;
import com.flippingfriend.data.PluginStorage;
import com.flippingfriend.model.TaxCalculator;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Watches the eight Grand Exchange slots and turns raw offer updates into bookkeeping.
 * <p>
 * This is where a flip actually becomes a fact: a completed buy adds to the position book and the
 * buy-limit ledger, and a completed sell closes the position and writes a journal entry that the
 * calibrator will later learn from. Everything downstream depends on this being exactly right, so
 * fills are always applied as deltas against what has already been recorded, never as absolutes.
 */
@Singleton
public class OfferTracker
{
	private static final Logger log = LoggerFactory.getLogger(OfferTracker.class);

	private static final Type OFFER_LIST = new TypeToken<List<TrackedOffer>>()
	{
	}.getType();
	private static final String FILE_NAME = "offers.json";

	/**
	 * How long a finished offer is remembered so a replay of it can be recognised.
	 * <p>
	 * Long enough to cover a logout, a client crash and a restart; short enough that a slot reused
	 * for a genuinely identical trade the next day is treated as the new trade it is.
	 */
	private static final long SETTLED_RETENTION_SECONDS = 12 * 3600;

	/** The states in which an offer is finished and its journal entry is due. */
	private static boolean isSettledState(String state)
	{
		return "SOLD".equals(state) || "CANCELLED_SELL".equals(state)
			|| "BOUGHT".equals(state) || "CANCELLED_BUY".equals(state);
	}

	private final PluginStorage storage;
	private final PositionBook positions;
	private final BuyLimitTracker buyLimits;
	private final TradeJournal journal;
	private final TaxCalculator taxCalculator;
	private final TradePlans tradePlans;

	private final Map<Integer, TrackedOffer> bySlot = new ConcurrentHashMap<>();

	/**
	 * Offers that have left their slot, kept for a while so a replay cannot re-book them.
	 * <p>
	 * The game replays every slot on login, and {@code bySlot} used to simply drop an offer when it
	 * was collected -- which discarded the one piece of evidence that its fills had already been
	 * counted. The next announcement of that offer looked brand new, {@code delta} came out as the
	 * entire running total, and the whole sale was booked a second time. Kept out of {@code bySlot}
	 * rather than flagged inside it so that nothing reading live offers can see a ghost.
	 */
	private final Map<Integer, TrackedOffer> settled = new ConcurrentHashMap<>();

	private Path loadedFrom;
	private volatile Runnable changeListener;
	private volatile AbandonedBuyListener abandonedBuyListener;
	private volatile MarketSnapshot market = MarketSnapshot.empty();
	private volatile String riskProfileName = "";

	@Inject
	public OfferTracker(PluginStorage storage, PositionBook positions, BuyLimitTracker buyLimits,
		TradeJournal journal, TaxCalculator taxCalculator, TradePlans tradePlans)
	{
		this.storage = storage;
		this.positions = positions;
		this.buyLimits = buyLimits;
		this.journal = journal;
		this.taxCalculator = taxCalculator;
		this.tradePlans = tradePlans;
	}

	public void setChangeListener(Runnable listener)
	{
		this.changeListener = listener;
	}

	public void setMarket(MarketSnapshot market)
	{
		this.market = market;
	}

	public void setRiskProfileName(String riskProfileName)
	{
		this.riskProfileName = riskProfileName;
	}

	public synchronized void load()
	{
		Path file = storage.accountDir().resolve(FILE_NAME);
		if (file.equals(loadedFrom))
		{
			return;
		}

		bySlot.clear();
		settled.clear();
		long cutoff = Instant.now().getEpochSecond() - SETTLED_RETENTION_SECONDS;
		for (TrackedOffer offer : storage.<List<TrackedOffer>>readJson(file, OFFER_LIST, new ArrayList<>()))
		{
			if (offer.isCollected())
			{
				if (offer.getLastChanged() >= cutoff)
				{
					settled.put(offer.getSlot(), offer);
				}
			}
			else
			{
				bySlot.put(offer.getSlot(), offer);
			}
		}
		loadedFrom = file;
	}

	public synchronized void save()
	{
		if (!storage.hasAccount())
		{
			return;
		}
		Path file = storage.accountDir().resolve(FILE_NAME);
		// Settled offers go to disk too. They are the only thing standing between a login replay and
		// a second copy of every trade, so they have to survive the restart that causes the replay.
		List<TrackedOffer> all = new ArrayList<>(bySlot.values());
		all.addAll(settled.values());
		storage.writeJson(file, all, OFFER_LIST);
		loadedFrom = file;
	}

	public Collection<TrackedOffer> getOffers()
	{
		return bySlot.values();
	}

	/** Quantity of each item currently sitting unsold in an open sell offer. */
	public Map<Integer, Integer> listedForSale()
	{
		Map<Integer, Integer> listed = new java.util.HashMap<>();
		for (TrackedOffer offer : bySlot.values())
		{
			if (!offer.isBuying() && offer.getRemaining() > 0)
			{
				listed.merge(offer.getItemId(), offer.getRemaining(), Integer::sum);
			}
		}
		return listed;
	}

	/** Items with any offer currently occupying a slot, whether buying or selling. */
	public java.util.Set<Integer> itemsWithOpenOffers()
	{
		java.util.Set<Integer> items = new java.util.HashSet<>();
		for (TrackedOffer offer : bySlot.values())
		{
			String state = offer.getState();
			if ("BUYING".equals(state) || "SELLING".equals(state))
			{
				items.add(offer.getItemId());
			}
		}
		return items;
	}

	/**
	 * Items with a buy offer still working.
	 * <p>
	 * Distinct from {@link #itemsWithOpenOffers()}, which counts sells as well because its caller is
	 * asking "is this item already on the exchange". Here the question is whether the position is
	 * still growing, and only a buy makes it grow.
	 */
	public java.util.Set<Integer> itemsBeingBought()
	{
		java.util.Set<Integer> items = new java.util.HashSet<>();
		for (TrackedOffer offer : bySlot.values())
		{
			if (offer.isBuying() && "BUYING".equals(offer.getState()) && offer.getRemaining() > 0)
			{
				items.add(offer.getItemId());
			}
		}
		return items;
	}

	public TrackedOffer getOffer(int slot)
	{
		return bySlot.get(slot);
	}

	/**
	 * Applies one offer update from the game.
	 * <p>
	 * Must be called on the client thread, because {@link GrandExchangeOffer} reads live client
	 * state. Everything it produces is plain data, so downstream consumers are free of the client.
	 */
	public void onOfferChanged(int slot, GrandExchangeOffer offer)
	{
		if (offer == null)
		{
			return;
		}

		GrandExchangeOfferState state = offer.getState();
		Instant now = Instant.now();

		if (state == GrandExchangeOfferState.EMPTY)
		{
			// Collected, so the slot is free. The fills were booked when they happened, but the offer
			// is kept aside rather than dropped: discarding it discards the proof they were booked,
			// and the next replay of that slot would look like a brand new offer that had somehow
			// filled completely -- which is how one sale became three.
			TrackedOffer done = bySlot.remove(slot);
			if (done != null)
			{
				// Book anything that filled without ever being seen.
				//
				// This branch used to go straight to the journal, which is only correct if every
				// intermediate update was observed. When they were not -- the plugin restarted
				// mid-offer, or the login replay was discarded -- the outstanding fill was thrown away
				// here, and with it the buy's cost. A running total is exactly the shape that can be
				// settled up late, so it is.
				int outstanding = done.getQuantityFilled() - done.getRecordedQuantity();
				if (outstanding > 0)
				{
					log.warn("booking {} x {} that filled without being seen, before the slot is freed",
						outstanding, done.getItemName());
					applyFill(done, outstanding, null, now);   // null: settle up from the tracked totals
					done.setRecordedQuantity(done.getQuantityFilled());
				}
				writeJournalEntry(done, now);
				done.setCollected(true);
				settled.put(slot, done);
				pruneSettled(now);
				notifyChanged();
			}
			return;
		}

		boolean buying = state == GrandExchangeOfferState.BUYING
			|| state == GrandExchangeOfferState.BOUGHT
			|| state == GrandExchangeOfferState.CANCELLED_BUY;

		int itemId = offer.getItemId();
		int price = offer.getPrice();
		int total = offer.getTotalQuantity();
		int filled = offer.getQuantitySold();

		TrackedOffer tracked = bySlot.get(slot);
		if (tracked == null || !tracked.matches(itemId, buying, price, total))
		{
			TrackedOffer previous = settled.get(slot);
			// Refusing to resurrect a *collected* offer was tried here and is wrong: `settled` holds
			// precisely the collected ones, so that condition switches replay suppression off
			// altogether, and the login replay books every finished offer a second time. What remains
			// is the original rule, whose limitation is real but is the cheaper mistake: a brand-new
			// offer for the same item, side, price and size, in the same slot, whose very first
			// observed event is already fully filled, is read as a replay and its purchase is missed.
			// Reaching that state needs every intermediate update to have gone unseen, which is what
			// the pending-offer buffer in the plugin now prevents.
			if (previous != null && previous.matches(itemId, buying, price, total)
				&& filled >= previous.getRecordedQuantity())
			{
				// The same offer being announced again -- a login replay, or an event arriving after
				// the collect. It is already accounted for, so take back the record of that rather
				// than starting from zero and booking the whole thing a second time.
				//
				// The test is that it arrives already filled to at least what was booked. A genuinely
				// new offer placed into this slot starts at zero and climbs, so it fails this and is
				// treated as new. Only an identical offer that refilled to exactly the same point
				// before the plugin saw a single intermediate update would be mistaken for a replay,
				// and that errs towards recording one trade too few rather than a dozen too many.
				settled.remove(slot);
				tracked = previous;
				tracked.setCollected(false);
				bySlot.put(slot, tracked);
			}
			else
			{
				// A different offer now occupies this slot, so start fresh rather than carrying over
				// another trade's recorded quantity. Write out whatever the outgoing offer had
				// accrued first: if its collect was never seen -- an event missed across a login, say
				// -- this is the last chance to record the trade, and dropping the object would take
				// the fills with it.
				if (tracked != null)
				{
					writeJournalEntry(tracked, now);
				}
				settled.remove(slot);
				tracked = new TrackedOffer(slot, itemId, buying, price, total, now.getEpochSecond());
				tracked.setItemName(market.getItemName(itemId));
				bySlot.put(slot, tracked);
			}
		}

		int delta = filled - tracked.getRecordedQuantity();
		String previousState = tracked.getState();

		tracked.setQuantityFilled(filled);
		tracked.setSpent(offer.getSpent());
		tracked.setState(state.name());
		tracked.setLastChanged(now.getEpochSecond());

		// A buy that was running and is now cancelled: the player has decided something about this
		// item. Reported rather than acted on, because whether it counts as a rejection depends on
		// things this class has no business knowing -- notably whether the plugin itself asked for the
		// cancellation, which is not a rejection at all.
		if (state == GrandExchangeOfferState.CANCELLED_BUY
			&& GrandExchangeOfferState.BUYING.name().equals(previousState))
		{
			AbandonedBuyListener listener = abandonedBuyListener;
			if (listener != null)
			{
				listener.onBuyAbandoned(itemId, filled, total, tracked.minutesOpen(now.getEpochSecond()));
			}
		}

		if (delta > 0)
		{
			applyFill(tracked, delta, offer, now);
			tracked.setRecordedQuantity(filled);
		}

		// A finished offer is one flip, however many instalments the market filled it in.
		if (isSettledState(state.name()))
		{
			writeJournalEntry(tracked, now);
		}

		notifyChanged();
	}

	/**
	 * Writes the one journal entry this offer earned, if it has not already been written.
	 * <p>
	 * Everything about the sale has been accruing on the offer since its first fill, so this is
	 * arithmetic on totals rather than a fresh look at the position book -- which by now may well
	 * have been emptied by the very sale being recorded.
	 */
	private void writeJournalEntry(TrackedOffer tracked, Instant now)
	{
		if (tracked.isJournalled() || tracked.isBuying() || tracked.getPendingQuantity() <= 0)
		{
			return;
		}
		tracked.setJournalled(true);

		int quantity = tracked.getPendingQuantity();
		int backed = tracked.getPendingBackedQuantity();
		int unbacked = tracked.getPendingUnbackedQuantity();
		long costBasis = tracked.getPendingCostBasis();

		// Whatever the position book could not account for, priced from what this item recently cost.
		//
		// This used to return here, and the whole flip was dropped: 90 Rune nails sold for 70,200 gp
		// and left no trace in the journal at all, because the position that held their price had been
		// deleted before the sale settled. Booking the proceeds as pure profit is not the answer
		// either -- that is how a 28k flip once came out as 2.57m -- so the cost is reconstructed from
		// the ledger of what was actually paid, and the entry is marked as reconstructed so it can be
		// told apart from one measured end to end.
		boolean reconstructed = false;
		if (unbacked > 0)
		{
			// Claimed from the ledger, not priced from it. The ledger only vouches for units actually
			// bought and not already paid for by a position, so selling more than was ever bought
			// leaves the excess unpriced -- which is correct, and is the difference between recovering
			// a lost cost basis and inventing a profit.
			Position.Removal claim = positions.claimUnaccounted(tracked.getItemId(), unbacked);
			if (claim.getBackedQuantity() > 0)
			{
				costBasis += claim.getCostBasis();
				backed += claim.getBackedQuantity();
				reconstructed = true;
				log.warn("sold {} x {} with only {} priced by the book; {} more priced from what they "
						+ "recently cost, {} left unpriced",
					quantity, tracked.getItemName(), quantity - unbacked, claim.getBackedQuantity(),
					claim.getUnbackedQuantity());
			}
			else
			{
				log.warn("sold {} x {} and {} of them have no known cost anywhere; recording only the "
						+ "{} that do. This flip's profit is understated",
					quantity, tracked.getItemName(), unbacked, quantity - unbacked);
			}
		}

		if (backed <= 0)
		{
			// Nothing here can be priced at all, from the book or the ledger. Said loudly, because a
			// sale that reaches this point is money moved with no record of it anywhere.
			log.warn("SOLD {} x {} FOR {} gp WITH NO KNOWN COST -- this flip is missing from the "
					+ "journal entirely",
				quantity, tracked.getItemName(), (long) tracked.getPrice() * quantity);
			return;
		}

		long gross = tracked.getPendingGross();
		long tax = tracked.getPendingTax();

		if (backed < quantity && quantity > 0)
		{
			gross = (long) ((double) gross * backed / quantity);
			tax = (long) ((double) tax * backed / quantity);
		}
		long profit = gross - tax - costBasis;
		int averageCost = (int) (costBasis / backed);
		long openedAt = tracked.getPendingOpenedAt() > 0
			? tracked.getPendingOpenedAt() : tracked.getFirstSeen();

		FlipRecord record = new FlipRecord(tracked.getItemId(), tracked.getItemName(), backed,
			averageCost, tracked.getPrice(), tax, profit, openedAt, now.getEpochSecond(),
			tracked.getPendingPredictedMinutes(), tracked.getPendingPredictedProfit(),
			riskProfileName);
		record.setCostReconstructed(reconstructed);
		journal.record(record);
	}

	/** Forgets finished offers old enough that no replay could still be referring to them. */
	private void pruneSettled(Instant now)
	{
		long cutoff = now.getEpochSecond() - SETTLED_RETENTION_SECONDS;
		settled.values().removeIf(offer -> offer.getLastChanged() < cutoff);
	}

	private void applyFill(TrackedOffer tracked, int quantity, GrandExchangeOffer offer, Instant now)
	{
		int itemId = tracked.getItemId();
		String itemName = market.getItemName(itemId);
		tracked.setItemName(itemName);

		if (tracked.isBuying())
		{
			// The exchange refunds the difference when it fills below the asking price, so the
			// money actually spent is the truth and the offer price is only an upper bound.
			//
			// A null offer means this is a late settle-up from the EMPTY branch, where the game has
			// already taken the slot back and only what was last recorded on the tracked offer is
			// left to go on.
			long spentSoFar = offer == null ? tracked.getSpent() : offer.getSpent();
			long cost = Math.max(0, spentSoFar - tracked.getRecordedSpent());
			if (cost <= 0)
			{
				cost = (long) tracked.getPrice() * quantity;
			}
			tracked.setRecordedSpent(Math.max(spentSoFar, tracked.getRecordedSpent() + cost));

			Position position = positions.recordBuy(itemId, itemName, quantity, cost, now);
			// Carry the exit the engine planned onto the position it just created.
			tradePlans.applyTo(position);
			buyLimits.recordPurchase(itemId, quantity, now);
			log.debug("bought {} x {} for {}", quantity, itemName, cost);
		}
		else
		{
			// The exchange deducts the tax before returning coins for a sell offer, so the money actually
			// received is the truth. If the user listed it for 1 gp to instant-sell, the price in the offer
			// is 1 gp, but they actually received the true market value. Using tracked.getPrice() here
			// caused massive artificial losses to be recorded.
			long spentSoFar = offer == null ? tracked.getSpent() : offer.getSpent();
			long net = Math.max(0, spentSoFar - tracked.getRecordedSpent());
			long gross;
			long tax;

			if (net > 0)
			{
				long expectedNet = (long) tracked.getPrice() * quantity - taxCalculator.taxFor(itemId, tracked.getPrice(), quantity);
				if (net == expectedNet)
				{
					gross = (long) tracked.getPrice() * quantity;
					tax = gross - net;
				}
				else
				{
					long netPerItem = net / quantity;
					long grossPerItem;
					if (taxCalculator.isExempt(itemId))
					{
						grossPerItem = netPerItem;
					}
					else if (netPerItem >= TaxCalculator.CAP_THRESHOLD - TaxCalculator.MAX_TAX_PER_ITEM)
					{
						grossPerItem = netPerItem + TaxCalculator.MAX_TAX_PER_ITEM;
					}
					else
					{
						grossPerItem = (long) Math.round(netPerItem / (1.0 - TaxCalculator.TAX_RATE));
					}
					gross = grossPerItem * quantity;
					tax = gross - net;
				}
			}
			else
			{
				int price = tracked.getPrice();
				gross = (long) price * quantity;
				tax = taxCalculator.taxFor(itemId, price, quantity);
				net = gross - tax;
			}
			
			tracked.setRecordedSpent(Math.max(spentSoFar, tracked.getRecordedSpent() + net));

			Position position = positions.get(itemId);
			int averageCost = position == null ? 0 : position.getAverageCost();
			double predictedMinutes = position == null ? 0 : position.getPredictedSellMinutes();
			long openedAt = position == null ? now.getEpochSecond() : position.getOpenedAt();
			long predictedProfit = predictedProfit(itemId, averageCost, position, quantity);

			// Take the cost out of the book now -- the position has to stay honest for the sell
			// timing engine -- but accrue the journal entry rather than writing it. The entry is due
			// when the offer finishes, not when the market happens to fill a piece of it.
			Position.Removal removal = positions.recordSell(itemId, quantity);
			tracked.accrueSell(quantity, gross, tax, removal, openedAt, predictedMinutes,
				predictedProfit);

			log.debug("sold {} x {} for {} net (tax {})", quantity, itemName, net, tax);
		}
	}

	/**
	 * What the model expected this flip to make, so the journal can compare prediction to outcome.
	 * Falls back to the realised structure when no target was recorded, which simply makes the
	 * entry uninformative for calibration rather than misleading.
	 */
	private long predictedProfit(int itemId, int averageCost, Position position, int quantity)
	{
		if (position == null || position.getTargetSellPrice() <= 0 || averageCost <= 0)
		{
			return 0;
		}
		return taxCalculator.netProfit(itemId, averageCost, position.getTargetSellPrice(), quantity);
	}

	/**
	 * Told when a buy offer goes from working to cancelled.
	 * <p>
	 * A callback rather than a dependency: deciding what an abandoned buy means needs the suggestion
	 * engine and the sell-only state, and an offer ledger that reached into either of those would be
	 * two things at once.
	 */
	public interface AbandonedBuyListener
	{
		/**
		 * @param filled       how much had been bought when it was cancelled
		 * @param ordered      how much had been asked for
		 * @param minutesOpen  how long the offer had been standing
		 */
		void onBuyAbandoned(int itemId, int filled, int ordered, long minutesOpen);
	}

	public void setAbandonedBuyListener(AbandonedBuyListener listener)
	{
		this.abandonedBuyListener = listener;
	}

	private void notifyChanged()
	{
		Runnable listener = changeListener;
		if (listener != null)
		{
			try
			{
				listener.run();
			}
			catch (Exception ex)
			{
				log.debug("offer change listener threw", ex);
			}
		}
	}

	public synchronized void clear()
	{
		bySlot.clear();
		loadedFrom = null;
	}
}
