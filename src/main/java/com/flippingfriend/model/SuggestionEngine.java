package com.flippingfriend.model;

import com.flippingfriend.FlippingFriendConfig;
import com.flippingfriend.RiskProfile;
import com.flippingfriend.TradingHorizon;
import com.flippingfriend.data.Candle;
import com.flippingfriend.data.ItemMetadata;
import com.flippingfriend.data.LatestPrice;
import com.flippingfriend.data.MarketDataService;
import com.flippingfriend.data.MarketSnapshot;

import com.flippingfriend.session.AccountMonitor;
import com.flippingfriend.session.AccountState;
import com.flippingfriend.session.BuyLimitTracker;
import com.flippingfriend.session.OfferTracker;
import com.flippingfriend.session.Position;
import com.flippingfriend.session.PositionBook;
import com.flippingfriend.session.SellDecision;
import com.flippingfriend.session.SellTimingEngine;
import com.flippingfriend.session.SkipList;
import com.flippingfriend.session.TradePlans;
import com.flippingfriend.session.TrackedOffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Decides the single next thing the player should do.
 * <p>
 * The ordering is the advice. Collecting comes first because a finished offer is holding a slot and
 * coins hostage; fixing a mispriced offer comes next because an offer that will never fill is worse
 * than no offer; selling beats buying because capital that is already committed should be freed
 * before more is spent. Only when none of those apply does the engine go looking for something new
 * to buy.
 * <p>
 * Candidate selection is a funnel, for a practical reason: the wiki publishes prices for thousands
 * of items, but per-item history costs a request each. So a cheap pass over everything — margin
 * after tax, affordability, buy limit, rough volume — narrows the field to a few dozen, and only
 * those get the expensive treatment of real history, feature extraction, manipulation screening and
 * price optimisation.
 * <p>
 * Runs entirely off the client thread on immutable snapshots.
 */
@Singleton
public class SuggestionEngine
{
	/** How many items survive the cheap screen and get full analysis. */
	private static final int DEEP_ANALYSIS_LIMIT = 30;

	/**
	 * Winding the session down: no new positions, and get out of the ones that are open.
	 * <p>
	 * Deliberately not a stored setting. It is cleared on login, because a switch that silently stops
	 * every buy is exactly the kind of thing that should not survive a restart and be forgotten about
	 * -- the failure mode is a plugin that appears to have stopped working.
	 * <p>
	 * It does not change how anything is priced. Selling still goes through the same targets, decay
	 * and stop-loss, so ending a session never means being talked into a worse price than the trade
	 * deserved; it means nothing new is opened and what is already committed is released.
	 */
	private volatile boolean sellOnly;
	/** Five-minute buckets: roughly thirty hours of history, which is the useful window for flipping. */
	private static final String TIMESTEP = "5m";
	private static final int BUCKET_SECONDS = 300;
	/** Roughly a fortnight, for the context a single day of data cannot supply. */
	private static final String LONG_TIMESTEP = "1h";
	/** Most of the bank that may sit in any one item, however good it looks. */
	private static final double MAX_ITEM_EXPOSURE = 0.35;
	/** Most of the bank that may sit in one family of items that move together. */
	private static final double MAX_GROUP_EXPOSURE = 0.50;
	/**
	 * Share of an item's hourly volume assumed reachable when ranking, matching the fill model's
	 * own assumption so the cheap screen and the expensive score do not disagree about what is
	 * achievable.
	 */
	private static final double VOLUME_CAPTURE = 0.35;



	private final MarketDataService marketData;
	private final FeatureEngine featureEngine;
	private final ManipulationFilter filter;
	private final Scorer scorer;
	private final Explainer explainer;
	private final Calibrator calibrator;
	private final TaxCalculator taxCalculator;
	private final AccountMonitor accountMonitor;
	private final BuyLimitTracker buyLimits;
	private final PositionBook positions;
	private final OfferTracker offers;
	private final SellTimingEngine sellTiming;
	private final TradePlans tradePlans;
	private final FlippingFriendConfig config;
	private final LstmForecasterClient lstmClient;
	private final com.flippingfriend.model.arbitrage.ArbitrageRegistry arbitrageRegistry;

	private final AtomicReference<Suggestion> current = new AtomicReference<>(Suggestion.idle());
	private volatile Suggestion pendingAdjustment = null;
	/**
	 * A buy this engine told the player to cancel so it could be re-placed higher, or null.
	 * <p>
	 * The Grand Exchange cannot change the price of a running offer, so every reprice is three
	 * separate actions -- cancel, collect, place again -- and the first two destroy the evidence of
	 * the third. Without something to carry the intent across them, the engine issued the cancel and
	 * then had nothing to say about the item at all: the offer left the book, the reprice card went
	 * with it, and what appeared next was whatever the planner ranked top on that cycle. Telling a
	 * player to abandon a working offer and then not replacing it is worse than saying nothing.
	 */
	private volatile Reprice reprice = null;

	/**
	 * How long an outstanding reprice is honoured for.
	 * <p>
	 * Half an hour, which is long by the standards of a price but this does not carry a price: the
	 * replacement is quoted fresh from the market at the moment it is offered, so age costs nothing
	 * in accuracy. What it bounds is a player who cancelled an offer, wandered off, and came back to
	 * an instruction about a trade they no longer remember. Five minutes was the first guess and it
	 * was far too short -- a sale placed in between pushes the replacement behind it, and anyone who
	 * steps away from the Exchange for a moment loses the offer they were told to abandon.
	 */
	private static final long REPRICE_VALID_SECONDS = 1_800;
	
	/** When the held card was pinned, so a pin that is never released cannot outlive its reason. */
	private volatile long pendingAdjustmentAt;

	/**
	 * How long a held card may stand without being re-pinned.
	 * <p>
	 * A backstop, not the mechanism. The caller re-pins on every refresh while the player is in the
	 * editor, so this only expires when those refreshes stop arriving -- and a card that is returned
	 * ahead of every other decision must not be able to freeze the engine for the rest of a session
	 * because one client-thread call was missed. The walkthrough holds its own advice steady while an
	 * offer is half-typed, so letting this go early costs nothing.
	 */
	private static final long PENDING_ADJUSTMENT_SECONDS = 300;

	public void setPendingAdjustment(Suggestion pending)
	{
		this.pendingAdjustment = pending;
		this.pendingAdjustmentAt = pending == null ? 0 : Instant.now().getEpochSecond();
	}

	/** Everything looked at on the last pass, accepted and rejected, for the shadow trader. */
	/**
	 * What the sell engine concluded about each holding on the last pass, published for the panel.
	 * <p>
	 * Same shape as {@link #lastEvaluated}: computed on the engine thread, read on the Swing one.
	 * Recomputing any of this while painting would put model work on the event thread, which is a bug
	 * already removed from this panel's neighbour once.
	 */
	private final AtomicReference<Map<Integer, PositionStatus>> positionStatuses =
		new AtomicReference<>(Collections.emptyMap());

	private final AtomicReference<List<EvaluatedCandidate>> lastEvaluated =
		new AtomicReference<>(Collections.emptyList());
	private final SkipList skipped;

	@Inject
	public SuggestionEngine(MarketDataService marketData, FeatureEngine featureEngine,
		ManipulationFilter filter, Scorer scorer, Explainer explainer, Calibrator calibrator,
		TaxCalculator taxCalculator, AccountMonitor accountMonitor, BuyLimitTracker buyLimits,
		PositionBook positions, OfferTracker offers, SellTimingEngine sellTiming, TradePlans tradePlans,
		FlippingFriendConfig config, SkipList skipped,
		LstmForecasterClient lstmClient, com.flippingfriend.model.arbitrage.ArbitrageRegistry arbitrageRegistry)
	{
		this.skipped = skipped;
		this.marketData = marketData;
		this.featureEngine = featureEngine;
		this.filter = filter;
		this.scorer = scorer;
		this.explainer = explainer;
		this.calibrator = calibrator;
		this.taxCalculator = taxCalculator;
		this.accountMonitor = accountMonitor;
		this.buyLimits = buyLimits;
		this.positions = positions;
		this.offers = offers;
		this.sellTiming = sellTiming;
		this.tradePlans = tradePlans;
		this.config = config;
		this.lstmClient = lstmClient;
		this.arbitrageRegistry = arbitrageRegistry;
	}

	public Suggestion getCurrent()
	{
		return current.get();
	}

	/** The candidates from the most recent scoring pass, including the ones that were screened out. */
	/** What is happening with each holding, keyed by item id. Never null. */
	public Map<Integer, PositionStatus> getPositionStatuses()
	{
		return positionStatuses.get();
	}

	public List<EvaluatedCandidate> getLastEvaluated()
	{
		return lastEvaluated.get();
	}

	/** The items currently skipped, so the companion path can honour them too. */
	public Set<Integer> skippedItems()
	{
		return skipped.skipped(Instant.now());
	}

	/** Temporarily ignores an item the user has skipped, until the plugin restarts. */
	public void skip(int itemId)
	{
		Reprice pending = reprice;
		if (pending != null && pending.itemId == itemId)
		{
			reprice = null;
		}
		skipped.skipForSession(itemId);
	}

	/**
	 * Forgets the "show me something else" skips.
	 * <p>
	 * Not the timed ones. Those record a decision about an item -- a buy abandoned part way -- and a
	 * change of risk level is not a change of mind about that.
	 */
	public void clearSkipped()
	{
		skipped.clearSessionSkips();
	}

	public boolean isSellOnly()
	{
		return sellOnly;
	}

	public void setSellOnly(boolean sellOnly)
	{
		this.sellOnly = sellOnly;
	}

	/** Recomputes the suggestion. Safe to call from any thread except the client thread. */
	public Suggestion refresh()
	{
		return refresh(true);
	}

	/**
	 * The local decision chain: collect, then reprice, then sell, then -- only if asked -- buy.
	 * <p>
	 * The companion owns which new position to open, and used to have its answer computed here first
	 * and thrown away. That cost a full scoring pass every cycle, and it is where every divergence
	 * between the two paths came from: the buy limit rule, the exposure ceilings, the profit floor and
	 * the volume screen all had a second implementation that ran, was discarded, and so could rot
	 * without anything failing. Passing {@code false} stops at the point where a buy is what is
	 * wanted and says so, leaving the answer to whoever is authoritative.
	 *
	 * @param computeBuy whether to fall back to this engine's own buy selection
	 * @return the suggestion, or null meaning "nothing local to do; a new buy is what is needed"
	 */
	public Suggestion refresh(boolean computeBuy)
	{
		Suggestion suggestion = compute(computeBuy);
		if (suggestion != null)
		{
			current.set(suggestion);
		}
		return suggestion;
	}

	/**
	 * This engine's own buy selection, for when the companion cannot answer.
	 * <p>
	 * Only reachable after {@link #refresh(boolean)} has returned null, which already establishes that
	 * the player is logged in, the market is usable and a slot is free.
	 */
	public Suggestion buyFallback()
	{
		AccountState account = accountMonitor.getState();
		MarketSnapshot market = marketData.getSnapshot();
		if (!account.isLoggedIn() || !market.isUsable())
		{
			return Suggestion.idle();
		}
		if (sellOnly)
		{
			// The companion is told about the mode as well, but this path exists precisely for when it
			// cannot answer -- so it has to be able to refuse on its own.
			Suggestion idle = sellOnlyIdle();
			current.set(idle);
			return idle;
		}

		Suggestion suggestion = buySuggestion(market,
			TradingHorizon.of(config.riskProfile(), config.checkInterval(), config.targetHoldMinutes()),
			account, Instant.now());
		current.set(suggestion);
		return suggestion;
	}

	private Suggestion compute(boolean computeBuy)
	{
		AccountState account = accountMonitor.getState();
		MarketSnapshot market = marketData.getSnapshot();
		Instant now = Instant.now();

		// The reprice the player is partway through carrying out, held still so it does not change
		// under them. Checked after the account is read rather than before, because a card pinned
		// while logged in used to survive logging out: the engine returned it ahead of the
		// logged-in check and went on telling a logged-out player to adjust an offer.
		Suggestion pending = pendingAdjustment;
		if (pending != null)
		{
			if (now.getEpochSecond() - pendingAdjustmentAt > PENDING_ADJUSTMENT_SECONDS
				|| !account.isLoggedIn())
			{
				setPendingAdjustment(null);
			}
			else
			{
				return pending;
			}
		}

		if (!account.isLoggedIn())
		{
			return Suggestion.waiting("Log in to start",
				"Once you are logged in, the plugin will look at what you have and suggest a trade.");
		}

		if (account.isIronman())
		{
			return Suggestion.waiting("Ironman accounts cannot flip",
				"Ironman accounts can only buy bonds on the Grand Exchange, so there is nothing "
					+ "for this plugin to suggest.");
		}

		if (!market.isUsable())
		{
			// "Getting the latest prices" is the right thing to say for the first few seconds and a
			// lie after that. It was said for ever: the price feed could die permanently and
			// silently, and this card gave a stuck plugin exactly the same face as a starting one.
			// Ask the service what is actually missing, and only fall back to the loading card while
			// there is genuinely nothing to report yet.
			String reason = marketData.unavailableReason();
			return reason == null ? Suggestion.idle()
				: Suggestion.waiting("Still waiting for market data", reason);
		}

		TradingHorizon horizon = TradingHorizon.of(config.riskProfile(), config.checkInterval(),
			config.targetHoldMinutes());

		Suggestion collect = collectSuggestion(market);
		if (collect != null)
		{
			return collect;
		}

		// Above repricing, because there is no point tuning the price of an offer that is about to be
		// abandoned. Largest reservation first, so the coins come back in the order that frees the most.
		if (sellOnly)
		{
			Suggestion abandon = abandonBuySuggestion(market);
			if (abandon != null)
			{
				return abandon;
			}
		}

		// Finishing what the player has already started comes before starting anything new.
		//
		// This block used to sit below the sell pass, directly contradicting its own comment -- "the
		// player has already cancelled an offer on this plugin's instruction and is owed the
		// replacement before anything else is asked of them" -- and the contradiction had a cost.
		// The Grand Exchange cannot reprice a running offer, so a reprice is: cancel, collect, place
		// again. Collecting a part-filled buy puts those units in the inventory, and the moment the
		// cancelled offer leaves the board the item is no longer "still being bought" -- so the sell
		// pass saw a holding with nothing listed and claimed it.
		//
		// Live, on a 50-unit Awakener's orb order with one filled: the card promised "you will be
		// told to place the replacement at 282,024 gp straight afterwards", the player cancelled as
		// instructed, and the next card told them to sell the single orb. A 586,800 gp plan replaced
		// by a 13,600 gp one, by following the instruction it had just been given.
		Suggestion replace = replaceRepricedBuy(market, account, now);
		if (replace != null)
		{
			return replace;
		}

		// Sells, then modifications, then buys.
		//
		// Selling used to rank below repricing, and the argument for that was that there is no point
		// tuning an offer that is about to be abandoned. It is the wrong way round. A sale turns a
		// holding into coins that can be spent on the next trade; a reprice only improves an offer
		// that is, by definition, already sitting there not filling. Putting the reprice first meant
		// a position ready to leave waited behind housekeeping on an order that was going nowhere,
		// and on three slots that is the whole account waiting.
		//
		// Nothing is lost by the swap. An item already fully listed produces no sell suggestion at
		// all -- the sell pass skips it and records that it is selling -- so the offer that needs
		// repricing is exactly the one the sell pass has nothing to say about, and the reprice runs
		// on the very next check.
		Suggestion sell = sellSuggestion(market, horizon, account, now);
		if (sell != null)
		{
			return sell;
		}

		Suggestion adjust = adjustSuggestion(market, horizon, now);
		if (adjust != null)
		{
			return adjust;
		}

		if (account.getFreeSlots() <= 0)
		{
			return Suggestion.waiting("All your Grand Exchange slots are busy",
				"Every slot has an offer in it. The plugin will suggest the next trade as soon as "
					+ "one of them finishes.");
		}

		// Do not spend the last slot on a buy while something is still waiting to be sold.
		//
		// A position needs a slot to leave by, and a buy takes one for hours. Fill the last one with a
		// purchase and the holding cannot be sold at all until something else finishes -- so the
		// moment its price finally arrives, there is nowhere to put the offer. That is not
		// hypothetical: nine thousand Ruby necklaces sat through their target being reached because
		// all three slots held other trades, two of them buys this engine had itself suggested after
		// the necklaces were already in hand.
		//
		// It matters most where slots are scarcest. On three, one careless buy is a third of the
		// account's capacity to act.
		// Only for a holding whose exit is actually in sight.
		//
		// This used to fire for anything held at all, so a position waiting hours for a price it might
		// never reach kept a Grand Exchange slot idle the whole time -- on eight slots that is wasteful
		// and on three it was crippling. The sell engine has already decided, this pass, whether each
		// holding is a pricing step from its target or near enough its deadline that the clock will
		// force the sale; only those are worth a slot.
		//
		// Relaxing this is only safe because of what sellSuggestion now does when the exit does arrive
		// and nothing is free: it cancels a buy to make room, rather than telling the player to sort it
		// out themselves. The holding is not forgotten -- room is taken back for it.
		// Every holding qualifies now, because every holding is being listed. The filter that used to
		// stand here -- reserve only for a position whose exit is actually in sight -- existed to
		// stop a slot sitting idle for something that might never leave, and nothing is kept back
		// long enough for that to happen any more.
		//
		// In practice this rarely fires: a holding that can be priced produces a sell suggestion
		// above this, and that sale takes the slot itself. What is left is the holding that cannot be
		// priced at all, which is exactly the one worth keeping a slot for -- the moment a price
		// arrives it is listed.
		Position awaiting = awaitingExit();
		if (awaiting != null && account.getFreeSlots() <= 1)
		{
			return Suggestion.waiting("Keeping this slot free to sell",
				"You still hold " + explainer.formatNumber(awaiting.getQuantity()) + " "
					+ awaiting.getItemName() + " that has to be sold, and this is your last free slot. "
					+ "Buying with it would leave nowhere to place that sale.");
		}

		if (sellOnly)
		{
			return sellOnlyIdle();
		}

		if (!computeBuy)
		{
			// A buy is what is wanted, and this engine is no longer the one that decides it.
			return null;
		}

		Suggestion arbitrage = arbitrageSuggestion(market, account, now);
		if (arbitrage != null)
		{
			return arbitrage;
		}

		return buySuggestion(market, horizon, account, now);
	}

	// ------------------------------------------------------------------ collect

	private Suggestion collectSuggestion(MarketSnapshot market)
	{
		for (TrackedOffer offer : offers.getOffers())
		{
			if (!isCollectable(offer))
			{
				continue;
			}

			String name = market.getItemName(offer.getItemId());
			String what = offer.isBuying() ? "bought" : "sold";
			return Suggestion.builder(SuggestionType.COLLECT)
				.item(offer.getItemId(), name)
				.slot(offer.getSlot())
				.quantity(offer.getQuantityFilled())
				.headline("Collect your " + name)
				.detail("Your offer to " + (offer.isBuying() ? "buy " : "sell ") + name
					+ " has finished. Collect it to free up the slot"
					+ (offer.isBuying() ? " and pick up your items." : " and pick up your coins."))
				.build();
		}
		return null;
	}

	private static boolean isCollectable(TrackedOffer offer)
	{
		String state = offer.getState();
		if (state == null)
		{
			return false;
		}
		switch (state)
		{
			case "BOUGHT":
			case "SOLD":
				return true;
			case "CANCELLED_BUY":
			case "CANCELLED_SELL":
				// A cancelled offer still holds whatever it managed to fill, plus any refund.
				return true;
			default:
				return false;
		}
	}

	// ------------------------------------------------------------------- adjust

	/**
	 * Spots offers that have been overtaken by the market. A buy offer priced below what people are
	 * now selling at, or a sell offer priced above what people are now paying, will sit there
	 * indefinitely — the slot is doing nothing, and the player has no way of knowing.
	 */
	private Suggestion adjustSuggestion(MarketSnapshot market, TradingHorizon horizon, Instant now)
	{
		for (TrackedOffer offer : mostStrandedFirst(offers.getOffers(), market))
		{
			String state = offer.getState();
			boolean open = "BUYING".equals(state) || "SELLING".equals(state);
			if (!open || offer.getQuantityFilled() >= offer.getTotalQuantity())
			{
				continue;
			}

			// Never nag about an offer the player has not had a chance to look at yet.
			//
			// A sale waits longer than that, and waits in proportion to what it was told to expect.
			// The two sides are not the same problem. A buy that is being skipped over has had the
			// market move past it -- that is information about the price, available immediately, and
			// acting on it costs nothing now that the replacement has to be worth placing. A sale
			// that has not filled yet has produced no information at all until enough of its own
			// predicted time has gone by, and acting early costs real money, because the only thing
			// impatience can buy here is a worse price. Fifteen minutes into a leg the plan predicted
			// would take two hours, an unfilled offer means nothing except that two hours is longer
			// than fifteen minutes -- and that was enough to have a 90,000 gp position marked down by
			// nearly 200,000.
			long patience = horizon.staleOfferMinutes();
			if (!offer.isBuying())
			{
				Position planned = positions.get(offer.getItemId());
				patience = horizon.patienceMinutes(planned == null ? 0
					: planned.getPredictedSellMinutes());
			}
			if (offer.minutesOpen(now.getEpochSecond()) < patience)
			{
				continue;
			}

			if (offer.isBuying())
			{
				ItemMetadata metadata = market.metadata(offer.getItemId());
				int limit = metadata != null ? metadata.getBuyLimit() : 0;
				if (limit > 0 && buyLimits.remaining(offer.getItemId(), limit, now) <= 0)
				{
					return Suggestion.builder(SuggestionType.CANCEL)
						.item(offer.getItemId(), market.getItemName(offer.getItemId()))
						.slot(offer.getSlot())
						.price(offer.getPrice())
						.quantity(offer.getRemaining())
						.headline("Cancel your " + market.getItemName(offer.getItemId()) + " offer")
						.detail("You have reached the buy limit for this item. Cancelling the offer frees up the slot for another item.")
						.build();
				}
			}

			LatestPrice price = market.latest(offer.getItemId());
			com.flippingfriend.data.Candle trend = market.fiveMinute(offer.getItemId());
			
			if (price == null || !price.isComplete())
			{
				continue;
			}

			String name = market.getItemName(offer.getItemId());
			int remaining = offer.getRemaining();

			if (horizon.isOutbid(offer.isBuying(), offer.getPrice(), price, trend))
			{
				if (offer.isBuying())
				{
					// Settled before it is judged, so the profitability gate below and the card the
					// player reads are talking about the same price. See advisedPrice.
					int newPrice = advisedPrice(offer.getItemId(), true, price.getLow() + 1,
						now.getEpochSecond());
					int targetSellPrice = Math.max(1, price.getHigh() - 1);

					long expectedProfit = taxCalculator.netProfit(offer.getItemId(), newPrice, targetSellPrice, remaining);

					// Chasing the price up is only worth doing while the trade is still worth doing.
					//
					// This branch worked out what the repriced flip would earn, put the figure on the
					// card, and then never looked at it -- so when the spread had closed while the
					// offer sat there, the advice was to cancel a working offer and re-place it at a
					// price where the round trip loses money. Reported from a live session, and it is
					// hard to argue with: nothing recommends entering a trade at a loss before it has
					// begun. The offer is being skipped over *and* it is no longer worth having, so
					// the honest answer is the slot, not a better price for a bad trade.
					if (expectedProfit < Math.max(1, config.minProfitPerFlip()))
					{
						return Suggestion.builder(SuggestionType.CANCEL)
							.item(offer.getItemId(), name)
							.slot(offer.getSlot())
							.price(offer.getPrice())
							.quantity(remaining)
							.expectedProfit(expectedProfit)
							.headline("Give up on " + name)
							.detail("Your offer at " + explainer.formatNumber(offer.getPrice())
								+ " gp is being skipped over, and the gap has closed too far to be "
								+ "worth chasing -- buying at " + explainer.formatNumber(newPrice)
								+ " gp and selling at " + explainer.formatNumber(targetSellPrice)
								+ " gp would " + (expectedProfit < 0 ? "lose " : "make only ")
								+ explainer.formatGp(Math.abs(expectedProfit)) + " gp after tax.\n\n"
								+ "Cancel the offer to free the slot and get "
								+ explainer.formatGp((long) offer.getPrice() * remaining)
								+ " gp back for something better.")
							.build();
					}

					// Remembered, so the re-placing actually happens.
					//
					// The Grand Exchange has no way to change the price of a running offer: this is
					// a cancel, a collect, and a fresh offer, and the first two wipe every trace of
					// why. The engine used to issue the cancel and then forget it -- the offer left
					// the book, the reprice card went with it, and what the player was shown next
					// was whatever the planner happened to rank top, at whatever size it liked.
					// Telling someone to abandon a working offer and then not replacing it is worse
					// than never having said anything.
					reprice = new Reprice(offer.getItemId(), remaining, now.getEpochSecond());

					return Suggestion.builder(SuggestionType.MODIFY_BUY)
						.item(offer.getItemId(), name)
						.slot(offer.getSlot())
						.price(newPrice)
						.quantity(remaining)
						.targetSellPrice(targetSellPrice)
						.expectedProfit(expectedProfit)
						.headline("Your " + name + " offer is too low")
						.detail("You offered " + explainer.formatNumber(offer.getPrice()) + " gp, but people "
							+ "are now selling at " + explainer.formatNumber(price.getLow()) + " gp, so your "
							+ "offer is being skipped over.\n\nThe Grand Exchange cannot change the price "
							+ "of an offer that is already running, so cancel this one and collect what it "
							+ "bought along with the coins it gives back. You will be told to place the "
							+ "replacement at " + explainer.formatNumber(newPrice) + " gp straight "
							+ "afterwards.")
						.build();
				}
			}

			/*
			 * The sell arm of this branch has been removed, and its removal is the fix for the
			 * worst-behaved thing in this class.
			 *
			 * It fired on "your ask is above the market" and dropped the price straight to one under
			 * the best bid, with no floor of any kind. The block below asks the same question --
			 * price.getHigh() < offer.getPrice() is exactly the condition, minus a trend
			 * confirmation -- and has since gained a break-even floor and a hand-off to the sell
			 * engine for anything under it. But this arm returned first, so that floor was
			 * unreachable on nearly every real offer. It was written from the journal, to stop the
			 * losing flips the journal was full of, and it was guarding a door nobody used.
			 *
			 * Live consequence: a position entered for 90,000 gp of profit was told, a quarter of an
			 * hour after being listed, to drop its price by nearly 200,000 -- straight through
			 * break-even, straight through the stop, without either being consulted.
			 *
			 * Nothing is lost by deleting it. The condition below is strictly weaker, so every offer
			 * this caught is still caught; it is caught by the path that knows what the position cost.
			 */

			// The same deadband the buy side uses. A strict comparison here would chase a quote that
			// wobbles by a gp, cancelling and re-listing a working offer for no gain, which is the
			// other half of the flashing the player reported.
			if (!offer.isBuying()
				&& offer.getPrice() - price.getHigh() >= horizon.outbidDeadband(offer.getPrice()))
			{
				int newPrice = Math.max(1, price.getHigh() - 1);
				// Never reprice a sale below what the position cost.
				//
				// This step used to chase a falling market with no floor but 1 gp, and because adjust ranks
				// above sell in the chain it walked straight through the stop-loss logic that exists to
				// prevent exactly this. Every losing flip in the journal came from here and nowhere else:
				// Maple logs bought at 11 and repriced to 10 for -15,000; Soft clay bought at 119 and
				// repriced to 116, twice, for -25,540. At a 12% loss cut those clay positions had a stop at
				// 104 -- they were repriced twelve gp above the price at which the sell engine would have
				// cut them, so it was never asked.
				//
				// Below break-even this is not a repricing decision at all, it is a decision to take a loss,
				// and that belongs to SellTimingEngine: it holds while the position is above its stop and
				// cuts when it is not. Say nothing here and let it answer.
				Position position = positions.get(offer.getItemId());
				boolean costKnown = position != null && position.isCostKnown()
					&& position.getAverageCost() > 0;
				int breakEven = costKnown
					? taxCalculator.breakEvenSellPrice(offer.getItemId(), position.getAverageCost()) : 0;
				boolean isCut = false;
				if (!repriceAllowed(newPrice, position, breakEven))
				{
					// Below break-even this stops being a repricing decision and becomes a decision to
					// take a loss, and only one thing authorises that: the stop.
					//
					// This used to hand the question to the sell engine and act on any answer that was
					// a sale. That worked while the engine could still answer HOLD. It cannot any more
					// -- under the never-hold rule every position it is asked about comes back as a
					// sale -- so the escape hatch stopped existing and the floor above it stopped
					// meaning anything, in the one branch that still reached it.
					//
					// Listing is not the same as marking down. An offer already on the market is
					// already listed; the never-hold rule is satisfied and has nothing further to say
					// about what price it sits at. So the offer chases the market down as far as
					// break-even and no further, and only a position through its stop goes past.
					List<Candle> series = marketData.getSeries(offer.getItemId(), TIMESTEP);
					ItemFeatures features = featureEngine.compute(offer.getItemId(), series, BUCKET_SECONDS);
					boolean inInventory = accountMonitor.getState().getInventoryHoldings().containsKey(offer.getItemId())
						&& accountMonitor.getState().getInventoryHoldings().get(offer.getItemId()) > 0;
					SellDecision decision = sellTiming.evaluate(position, price, features, series, horizon, now,
						config.minProfitPerFlip(), inInventory, sellOnly, skippedItems().contains(offer.getItemId()));

					if (decision.getAction() == SellDecision.Action.CUT)
					{
						// Through the stop. This one is meant to realise a loss, and the card says so.
						newPrice = decision.getPrice();
						isCut = true;
					}
					else if (breakEven < offer.getPrice())
					{
						// Not through the stop: come down to the cheapest price that is not a loss.
						// Better placed in the queue than it was, and costing nothing to get there.
						newPrice = breakEven;
					}
					else
					{
						// The offer is already at or below break-even and the position is still
						// healthy. There is nothing to do that would not be a loss; leave it alone.
						continue;
					}
				}
				// Settled through noise, and then floored again: stickiness must never be the thing
				// that walks a price below what the position cost.
				newPrice = advisedPrice(offer.getItemId(), false, newPrice, now.getEpochSecond());
				if (!isCut && costKnown)
				{
					newPrice = Math.max(newPrice, breakEven);
				}

				// What the new price is actually worth. The reprice card carried no figure at all, so a
				long change = taxCalculator.netProfit(offer.getItemId(), costKnown ? position.getAverageCost() : 0, newPrice, remaining);
				return Suggestion.builder(SuggestionType.MODIFY_SELL)
					.item(offer.getItemId(), name)
					.slot(offer.getSlot())
					.price(newPrice)
					.quantity(remaining)
					.expectedProfit(change)
					.lossCut(isCut)
					.headline(isCut ? "Cut your losses on " + name : "Your " + name + " offer is too high")
					.detail((isCut
						? "This has fallen past the point where holding is worth the risk. "
						: "You asked " + explainer.formatNumber(offer.getPrice()) + " gp, but buyers are "
							+ "only paying " + explainer.formatNumber(price.getHigh()) + " gp, so nobody is "
							+ "taking it. ")
						+ "\n\nThe Grand Exchange cannot change the price of an offer that is already "
						+ "running, so cancel this one, collect the items back, and list them again at "
						+ explainer.formatNumber(newPrice) + " gp.")
					.build();
			}
		}
		return null;
	}

	/**
	 * Places the buy that a reprice cancelled, once the old offer is out of the way.
	 * <p>
	 * The price is worked out afresh rather than replayed. The quote that justified the reprice is
	 * by then a cancel and a collect old, and the point of the exercise was to be at the front of
	 * the book -- a stale number would put the replacement straight back where the original was.
	 * The size is what was left unfilled, trimmed to what the buy limit and the coins still allow.
	 * <p>
	 * Deliberately narrow. It fires only for a buy this engine itself asked to have cancelled, only
	 * while no offer for that item is open, only with a slot free, and only for five minutes. It is
	 * finishing an instruction already given, not choosing a trade -- which remains the companion's
	 * job, and which is why the reprice is cleared the moment it has been offered and an offer for
	 * the item appears.
	 */
	/**
	 * True while this plugin still owes the player a replacement for an offer it had them cancel.
	 * <p>
	 * Deliberately the same question {@link #replaceRepricedBuy} asks, so the two cannot disagree:
	 * anything short of a verdict to drop the reprice means the trade is still in flight, even on a
	 * pass where the replacement itself cannot be offered yet because every slot is busy. That gap is
	 * exactly where a part-filled position would otherwise be claimed by the sell pass.
	 */
	private boolean awaitingReplacement(int itemId, AccountState account, Instant now)
	{
		Reprice pending = reprice;
		if (pending == null || pending.itemId != itemId)
		{
			return false;
		}
		return repriceVerdict(pending, now.getEpochSecond(),
			offers.itemsWithOpenOffers().contains(itemId), skippedItems().contains(itemId),
			sellOnly, account.getFreeSlots()) != RepriceVerdict.DROP;
	}

	private Suggestion replaceRepricedBuy(MarketSnapshot market, AccountState account, Instant now)
	{
		Reprice pending = reprice;
		RepriceVerdict verdict = repriceVerdict(pending, now.getEpochSecond(),
			pending != null && offers.itemsWithOpenOffers().contains(pending.itemId),
			pending != null && skippedItems().contains(pending.itemId), sellOnly,
			account.getFreeSlots());

		if (verdict == RepriceVerdict.DROP)
		{
			reprice = null;
			return null;
		}
		if (verdict == RepriceVerdict.WAIT)
		{
			return null;
		}

		LatestPrice price = market.latest(pending.itemId);
		if (price == null || !price.isComplete())
		{
			return null;
		}

		int newPrice = price.getLow() + 1;
		ItemMetadata metadata = market.metadata(pending.itemId);
		int buyLimit = metadata == null ? 0 : metadata.getBuyLimit();
		int allowedByLimit = buyLimit > 0
			? buyLimits.remaining(pending.itemId, buyLimit, now) : pending.quantity;
		int quantity = replacementQuantity(pending.quantity, allowedByLimit,
			account.spendableCoins(config.includeBankValue(), config.bankrollCap()), newPrice);
		if (quantity <= 0)
		{
			// The limit or the coins ran out while the offer was being cancelled. Nothing to replace.
			reprice = null;
			return null;
		}

		String name = market.getItemName(pending.itemId);
		int targetSell = Math.max(newPrice + 1, price.getHigh() - 1);
		long expectedProfit = taxCalculator.netProfit(pending.itemId, newPrice, targetSell, quantity);

		pending.markOffered();
		// The exit plan is rewritten around the new entry price, but the time estimate is carried
		// over from the plan the original offer was opened with. This is the same trade continuing,
		// and there is nothing here that could produce a better one -- writing a zero would leave
		// the position with no expected duration, which is what the holding card counts down and
		// what the journal scores the prediction against.
		TradePlans.PlannedExit existing = tradePlans.get(pending.itemId);
		tradePlans.plan(pending.itemId, targetSell,
			(int) Math.round(newPrice * (1 - config.riskProfile().getLossCutPct())),
			existing == null ? 0 : existing.getPredictedMinutes(), expectedProfit);

		return Suggestion.builder(SuggestionType.BUY)
			.item(pending.itemId, name)
			.price(newPrice)
			.quantity(quantity)
			.targetSellPrice(targetSell)
			.expectedProfit(expectedProfit)
			.breakEvenPrice(taxCalculator.breakEvenSellPrice(pending.itemId, newPrice))
			.headline("Place your " + name + " offer again at "
				+ explainer.formatNumber(newPrice) + " gp")
			.detail("This is the replacement for the offer you just cancelled. The price has been "
				+ "worked out again from the market as it is now, so it goes in at the front of the "
				+ "queue rather than back where the old one was.")
			.build();
	}

	/** What to do about an outstanding reprice on this pass. */
	enum RepriceVerdict
	{
		/** Put the replacement offer in front of the player. */
		PLACE,
		/** Not yet, but the intent still stands. */
		WAIT,
		/** Forget it; the replacement is no longer wanted or no longer possible. */
		DROP
	}

	/**
	 * Whether a cancelled buy should be placed again yet.
	 * <p>
	 * The three answers are genuinely different and the difference matters. DROP forgets the
	 * instruction; WAIT keeps it and says nothing this pass. Collapsing them was the mistake waiting
	 * to be made here: an intent dropped because a slot happened to be busy would leave the player
	 * having cancelled a working offer for no reason at all, which is the whole failure this exists
	 * to fix.
	 *
	 * @param itemOnOffer whether any offer for the item currently occupies a slot, which means
	 *                    either that the cancellation has not happened yet or that the replacement
	 *                    is already placed -- told apart by whether the replacement was ever shown
	 */
	static RepriceVerdict repriceVerdict(Reprice pending, long nowSeconds, boolean itemOnOffer,
		boolean skipped, boolean sellOnly, int freeSlots)
	{
		if (pending == null)
		{
			return RepriceVerdict.WAIT;
		}
		// Past its shelf life, rejected by the player, or overtaken by a decision to stop buying
		// altogether. None of these will improve by waiting.
		if (nowSeconds - pending.recordedAt > REPRICE_VALID_SECONDS || skipped || sellOnly)
		{
			return RepriceVerdict.DROP;
		}
		if (itemOnOffer)
		{
			return pending.offered ? RepriceVerdict.DROP : RepriceVerdict.WAIT;
		}
		// Collect ranks above this and frees the slot the cancelled offer is still holding, so a
		// full board is a reason to say nothing this pass rather than to give up.
		return freeSlots > 0 ? RepriceVerdict.PLACE : RepriceVerdict.WAIT;
	}

	/**
	 * How much of the cancelled order can actually be placed again.
	 * <p>
	 * What was left unfilled, less anything the buy limit has since swallowed, less anything the
	 * coins no longer cover. The unfilled part of the old offer had its coins reserved by the
	 * exchange; between the cancel and the replacement those coins come back and can be spent
	 * elsewhere, so the affordability question has to be asked again rather than assumed.
	 */
	static int replacementQuantity(int unfilled, int buyLimitRemaining, long spendable, int price)
	{
		if (unfilled <= 0 || price <= 0 || spendable <= 0)
		{
			return 0;
		}
		long affordable = spendable / price;
		return (int) Math.max(0, Math.min(Math.min(unfilled, buyLimitRemaining), affordable));
	}

	/** A buy that was cancelled for repricing and has not been placed again yet. */
	static final class Reprice
	{
		private final int itemId;
		private final int quantity;
		private final long recordedAt;
		/** Set once the replacement has been put in front of the player, so it can be retired. */
		private volatile boolean offered;

		Reprice(int itemId, int quantity, long recordedAt)
		{
			this.itemId = itemId;
			this.quantity = quantity;
			this.recordedAt = recordedAt;
		}

		void markOffered()
		{
			offered = true;
		}
	}

	/**
	 * What to say once there is nothing left to wind down.
	 * <p>
	 * Deliberately explicit about the mode being on. A plugin that has simply stopped suggesting
	 * trades looks broken, and this is the one state where that is the intended behaviour.
	 */
	private Suggestion sellOnlyIdle()
	{
		return Suggestion.waiting("Sell-only mode is on",
			positions.isEmpty()
				? "Everything is sold and no new trades will be suggested. Log in again, or switch "
					+ "sell-only off, to start trading."
				: "No new trades will be suggested. What you still hold will be offered for sale as "
					+ "each one reaches a price worth taking.");
	}

	/**
	 * An open buy offer that should be abandoned so the session can end.
	 * <p>
	 * Only buys. A sell is already doing the thing sell-only wants, and cancelling one would put the
	 * item back in the inventory to be listed again for no gain. Ordered by the coins each one has
	 * tied up, so the largest reservation comes back first.
	 */
	private Suggestion abandonBuySuggestion(MarketSnapshot market)
	{
		TrackedOffer biggest = largestOpenBuy(offers.getOffers());
		if (biggest == null)
		{
			return null;
		}

		String name = market.getItemName(biggest.getItemId());
		long coins = reserved(biggest);
		String filled = biggest.getQuantityFilled() > 0
			? " The " + explainer.formatNumber(biggest.getQuantityFilled()) + " you have already "
				+ "bought stays yours, and will be offered for sale once it is collected."
			: "";
		return Suggestion.builder(SuggestionType.CANCEL)
			.item(biggest.getItemId(), name)
			.slot(biggest.getSlot())
			.price(biggest.getPrice())
			.quantity(biggest.getRemaining())
			.headline("Cancel your " + name + " offer")
			.detail("Sell-only mode is on, so this buy is not wanted any more. Cancelling it frees the "
				+ "slot and returns " + explainer.formatGp(coins) + " gp." + filled)
			.build();
	}

	/**
	 * The open buy with the most coins tied up in it, or null when none is running.
	 * <p>
	 * Only buys, and only ones still working. A sell is already doing what sell-only wants, and
	 * cancelling it would put the item back in the inventory to be listed again for nothing. Ordering
	 * by the reservation rather than by slot means the coins come back fastest.
	 */
	static TrackedOffer largestOpenBuy(java.util.Collection<TrackedOffer> open)
	{
		TrackedOffer biggest = null;
		for (TrackedOffer offer : open)
		{
			if (!offer.isBuying() || !"BUYING".equals(offer.getState()) || offer.getRemaining() <= 0)
			{
				continue;
			}
			if (biggest == null || reserved(offer) > reserved(biggest))
			{
				biggest = offer;
			}
		}
		return biggest;
	}

	/** Coins the exchange is still holding against an open buy. */
	private static long reserved(TrackedOffer offer)
	{
		return (long) offer.getPrice() * offer.getRemaining();
	}

	/**
	 * Whether a sell offer may be repriced down to this price.
	 * <p>
	 * A position with no known cost basis has nothing to protect -- an item that was already in the
	 * bank costs nothing to give up -- so those reprice freely. One that was bought has a floor at
	 * break-even, because below it this stops being a pricing decision and becomes a decision to take
	 * a loss, and {@link com.flippingfriend.session.SellTimingEngine} is the thing that makes those:
	 * it holds while the position is above its stop and cuts when it is not.
	 * <p>
	 * Without the floor this step chased a falling market down with nothing beneath it but 1 gp, and
	 * since adjust ranks above sell in the chain it bypassed the stop entirely.
	 */
	/**
	 * The open offers, worst first: whichever the market has left furthest behind comes first.
	 * <p>
	 * Only one instruction is shown at a time, so when several offers are mispriced the engine has to
	 * choose -- and it was choosing by whatever order a {@link java.util.concurrent.ConcurrentHashMap}
	 * keyed by slot happened to iterate in. That is arbitrary rather than random: it is stable while
	 * the slots are, and it rearranges as offers settle and new ones are placed, so the card could
	 * hand itself to a different item because something unrelated finished. Measured, it picked an
	 * Adamant bar offer stranded by 0.05% over a Grapes offer stranded by 20%.
	 * <p>
	 * Distance is measured as a share of the offer's own price, so a hundred gp adrift on a bond and
	 * a hundred gp adrift on a rune are not treated as the same problem.
	 */
	private List<TrackedOffer> mostStrandedFirst(Collection<TrackedOffer> open, MarketSnapshot market)
	{
		List<TrackedOffer> ordered = new ArrayList<>(open);
		ordered.sort(Comparator
			.comparingDouble((TrackedOffer offer) -> strandedBy(offer, market)).reversed()
			// A stable tie-break, so two equally-placed offers do not swap between refreshes.
			.thenComparingInt(TrackedOffer::getSlot));
		return ordered;
	}

	/** How far past this offer the market has moved, as a fraction of the offer's price. */
	private static double strandedBy(TrackedOffer offer, MarketSnapshot market)
	{
		LatestPrice price = market.latest(offer.getItemId());
		if (price == null || !price.isComplete() || offer.getPrice() <= 0)
		{
			return 0;
		}
		int gap = offer.isBuying()
			? price.getLow() - offer.getPrice()
			: offer.getPrice() - price.getHigh();
		return Math.max(0, gap) / (double) offer.getPrice();
	}

	/**
	 * How long a price already put in front of the player is honoured before being re-derived.
	 * <p>
	 * Long enough to walk to a banker and type it, short enough that it re-anchors to the market
	 * within a session.
	 */
	private static final long ADVISED_PRICE_SECONDS = 600;

	/**
	 * How far a price may drift before the card is allowed to name a different one.
	 * <p>
	 * Wider than the outbid deadband on purpose, because the two answer different questions. That one
	 * asks whether an offer has become uncompetitive enough to be worth cancelling; this one asks
	 * whether a number the player is in the middle of typing is now wrong enough to be worth making
	 * them start again. Having committed to an action, the cost of changing its terms is higher than
	 * the cost of being a few gp off -- so this gives way later. Bounded anyway by
	 * {@link #ADVISED_PRICE_SECONDS}, after which the price re-anchors regardless.
	 */
	private static final double ADVISED_PRICE_TOLERANCE = 0.02;

	/** The drift this price may absorb before the card is rewritten. Never less than a gp. */
	private static int advisedPriceDeadband(int price)
	{
		return Math.max(1, (int) Math.round(Math.max(0, price) * ADVISED_PRICE_TOLERANCE));
	}

	/** A price the player has already been shown, and when they were shown it. */
	private static final class AdvisedPrice
	{
		final int price;
		final long at;

		AdvisedPrice(int price, long at)
		{
			this.price = price;
			this.at = at;
		}
	}

	private final Map<Long, AdvisedPrice> advisedPrices = new java.util.concurrent.ConcurrentHashMap<>();

	/** One item can carry a buy instruction and a sell instruction at once; they are not the same. */
	private static long advisedKey(int itemId, boolean buying)
	{
		return ((long) itemId << 1) | (buying ? 1L : 0L);
	}

	/**
	 * The price to show for this item, which is the one already shown unless the market has actually
	 * moved.
	 * <p>
	 * <b>An instruction must not be rewritten while the player is carrying it out.</b> Every price on
	 * every card was re-derived from the live quote on each refresh, and the live quote moves every
	 * time the feed republishes -- so a card reading "sell 100 Adamant bars at 2,099 gp" became 2,105,
	 * then 2,093, on a board where nothing had happened. Measured over twenty refreshes with the quote
	 * drifting a third of a percent, the advice was rewritten fourteen times. Someone halfway through
	 * typing that number watches it change, which is indistinguishable from the plugin having changed
	 * its mind -- and was reported as recommendations flashing and being lost.
	 * <p>
	 * The deadband is the item's own, so this holds a price still through noise and gives way as soon
	 * as the market genuinely moves past it.
	 */
	private int advisedPrice(int itemId, boolean buying, int fresh, long nowSeconds)
	{
		int deadband = advisedPriceDeadband(fresh);
		long key = advisedKey(itemId, buying);
		AdvisedPrice held = advisedPrices.get(key);
		if (held != null && nowSeconds - held.at < ADVISED_PRICE_SECONDS
			&& Math.abs(fresh - held.price) < Math.max(1, deadband))
		{
			return held.price;
		}
		advisedPrices.put(key, new AdvisedPrice(fresh, nowSeconds));
		return fresh;
	}

	static boolean repriceAllowed(int newPrice, Position position, int breakEvenPrice)
	{
		if (position == null || !position.isCostKnown() || position.getAverageCost() <= 0)
		{
			return true;
		}
		return newPrice >= breakEvenPrice;
	}

	// --------------------------------------------------------------------- sell

	/**
	 * A holding that still has to be sold and has no offer carrying it out, or null when everything
	 * held is already on its way out.
	 * <p>
	 * Counted against open sell offers rather than holdings, because a position halfway through being
	 * sold already has the slot it needs.
	 */
	private Position awaitingExit()
	{
		return awaitingExit(positions.all(), offers.listedForSale(), offers.itemsBeingBought());
	}

	static Position awaitingExit(java.util.Collection<Position> held, Map<Integer, Integer> listed)
	{
		return awaitingExit(held, listed, java.util.Collections.emptySet());
	}

	/**
	 * @param stillBuying items whose buy offer is still working, which do not reserve anything
	 */
	static Position awaitingExit(java.util.Collection<Position> held, Map<Integer, Integer> listed,
		java.util.Set<Integer> stillBuying)
	{
		for (Position position : held)
		{
			if (position.getQuantity() <= 0)
			{
				continue;
			}
			// A position that is still being bought does not need an exit yet.
			//
			// Holding the last slot to sell part of an order that is still growing spends the scarcest
			// thing the account has on an option it cannot use: the quantity will change, the price
			// will change, and nothing can be listed until the buy is done anyway. On three slots that
			// is a third of the account idle for the length of a 13,883-unit order.
			//
			// The exit is not lost, only deferred -- and if the position turns against us in the
			// meantime the answer is to abandon the buy, not to sell around it. See sellSuggestion.
			if (stillBuying.contains(position.getItemId()))
			{
				continue;
			}
			if (position.getQuantity() > listed.getOrDefault(position.getItemId(), 0))
			{
				return position;
			}
		}
		return null;
	}

	/**
	 * Whether the un-listed part of a holding deserves a Grand Exchange slot of its own.
	 * <p>
	 * Nothing listed means this is the first offer, and it always goes ahead. Once one is running, a
	 * second costs a second slot for hours, and the remainder of a part-filled buy arrives in dribs --
	 * an order for twenty thousand grapes filling a few hundred at a time leaves a new "sellable"
	 * remainder on every refresh. On three slots, honouring each one would commit the whole account to
	 * selling a single item in pieces to realise a few hundred coins.
	 * <p>
	 * The bar is the one the player already set for whether a trade is worth a slot. It is the same
	 * question, so it gets the same answer: a real remainder clears it easily, a trickle waits for the
	 * offer already running.
	 */
	static boolean remainderWarrantsItsOwnOffer(int alreadyListed, long remainderProfit,
		long minProfitPerFlip)
	{
		return alreadyListed <= 0 || remainderProfit >= minProfitPerFlip;
	}

	/**
	 * What to do when a sale is wanted and every slot is busy.
	 *
	 * @param give     the buy to give up, or null when every slot holds a sale and there is nothing
	 *                 to cancel
	 * @param giveName what that buy is for
	 * @param holding  the item that wants the slot
	 */
	static Suggestion makeRoomToSell(TrackedOffer give, String giveName, String holding,
		Explainer explainer)
	{
		if (give == null)
		{
			// Every slot holds a sale of its own. Cancelling one to place another would be a trade of
			// nothing for nothing, so this is the one case where waiting really is the answer.
			return Suggestion.waiting("Time to sell, but every slot is busy",
				"The plugin wants to sell your " + holding
					+ ", but every slot holds a sale already. One of them will finish shortly.");
		}

		long coins = (long) give.getPrice() * give.getRemaining();
		return Suggestion.builder(SuggestionType.CANCEL)
			.item(give.getItemId(), giveName)
			.slot(give.getSlot())
			.price(give.getPrice())
			.quantity(give.getRemaining())
			.headline("Cancel your " + giveName + " offer to free a slot")
			.detail("It is time to sell your " + holding
				+ " and every slot is busy. This buy is the furthest from finishing, so "
				+ "cancelling it gives up the least and returns " + explainer.formatGp(coins)
				+ " gp. You will be told to place the sale straight afterwards.")
			.build();
	}

	/** The buy still working on this item, or null. */
	private TrackedOffer openBuyFor(int itemId)
	{
		for (TrackedOffer offer : offers.getOffers())
		{
			if (offer.getItemId() == itemId && offer.isBuying() && "BUYING".equals(offer.getState())
				&& offer.getRemaining() > 0)
			{
				return offer;
			}
		}
		return null;
	}

	/**
	 * Stop buying something we have decided to leave.
	 * <p>
	 * The exit cannot happen while our own order keeps adding to the position, and selling around it
	 * would spend a second slot to leave a holding that is still growing. Cancelling is one action
	 * instead of two: it frees the slot, returns the unspent coins, and on the next pass the ordinary
	 * sell path takes over with nothing in its way.
	 * <p>
	 * Only for a position through its stop. An accumulation that is merely unfinished is left alone
	 * to finish.
	 */
	private Suggestion cancelBuyBlockingExit(Position position, SellDecision decision,
		MarketSnapshot market)
	{
		int itemId = position.getItemId();
		TrackedOffer buy = openBuyFor(itemId);
		if (buy == null)
		{
			return null;
		}

		String name = market.getItemName(itemId);
		long coins = (long) buy.getPrice() * buy.getRemaining();
		return Suggestion.builder(SuggestionType.CANCEL)
			.item(itemId, name)
			.slot(buy.getSlot())
			.price(buy.getPrice())
			.quantity(buy.getRemaining())
			.lossCut(true)
			.headline("Stop buying " + name)
			.detail("This has fallen past the point where holding it is worth the risk, so buying "
				+ "more of it makes no sense. Cancel the offer to free the slot and get "
				+ explainer.formatGp(coins) + " gp back. You will be told to sell the "
				+ explainer.formatNumber(position.getQuantity()) + " you already have straight "
				+ "afterwards.")
			.build();
	}

	/** What the open sell offer on this item is doing, for the card to report. */
	private PositionStatus sellingStatus(int itemId)
	{
		return sellingStatus(itemId, 0);
	}

	/**
	 * The same, for a holding that is only partly listed.
	 *
	 * @param withheld units deliberately not given a second offer of their own
	 */
	private PositionStatus sellingStatus(int itemId, int withheld)
	{
		for (TrackedOffer offer : offers.getOffers())
		{
			if (offer.getItemId() == itemId && !offer.isBuying() && offer.getRemaining() > 0)
			{
				return PositionStatus.sellingWithRemainder(offer.getPrice(),
					offer.getQuantityFilled(), offer.getTotalQuantity(), withheld);
			}
		}
		return PositionStatus.sellingWithRemainder(0, 0, 0, withheld);
	}

	private Suggestion sellSuggestion(MarketSnapshot market, TradingHorizon horizon, AccountState account,
		Instant now)
	{
		if (positions.isEmpty())
		{
			positionStatuses.set(Collections.emptyMap());
			return null;
		}

		Map<Integer, PositionStatus> statuses = new HashMap<>();
		Map<Integer, Integer> listedForSale = offers.listedForSale();
		java.util.Set<Integer> stillBuying = offers.itemsBeingBought();
		SellCandidate best = null;

		for (Position position : new ArrayList<>(positions.all()))
		{
			int itemId = position.getItemId();
			if (position.getQuantity() <= 0)
			{
				continue;
			}

			// What an offer is already carrying out is not a sale waiting to happen.
			//
			// An item in a sell offer sits in no container at all, so it looks identical to a holding
			// that cannot be seen. The original code got this right by accident -- quantityHeld came
			// back zero and the position was skipped -- and the unconfirmed fallback added above took
			// that protection away, so a position already on the market was reported as unseen and
			// slot-blocked at the same time. Subtract it explicitly instead of relying on a
			// coincidence.
			int listed = listedForSale.getOrDefault(itemId, 0);
			int toSell = position.getQuantity() - listed;
			if (toSell <= 0)
			{
				statuses.put(itemId, sellingStatus(itemId));
				continue;
			}
			// Only suggest selling what is actually in hand; anything already listed is accounted
			// for by the adjust check above.
			//
			// Except that "in hand" is only knowable once the bank has been opened. PositionBook
			// refuses to delete a record while the picture is incomplete, in its own words because
			// "an item that is merely banked looks exactly like one that has been sold" -- and this
			// loop dropped the very positions that guard exists to protect, silently and for as long
			// as the player went without opening their bank.
			//
			// The book is built from fills this plugin watched happen, so when the containers cannot
			// answer it is the better witness. Say the holding is unconfirmed rather than pretend
			// otherwise; reconcile corrects the record the moment the bank is seen.
			int held = account.quantityHeld(itemId);
			boolean unconfirmed = false;
			boolean inInventory = account.getInventoryHoldings().containsKey(itemId) && account.getInventoryHoldings().get(itemId) > 0;

			if (held <= 0)
			{
				if (listed > 0)
				{
					// Part of it is already selling and the rest is not visible. Report what is
					// actually happening rather than inventing a second sale for the remainder.
					statuses.put(itemId, sellingStatus(itemId));
					continue;
				}
				// No longer a reason to say nothing.
				//
				// This used to `continue` whenever the bank had been seen -- writing no status at all,
				// so the card went blank and no sell advice was produced, on the strength of a bank
				// snapshot that stops updating the moment the interface closes. An item collected to
				// the bank afterwards is invisible by that test, and the holding it belonged to was
				// then deleted by reconcile a moment later. Being unable to see something is not
				// knowing it is gone, and the honest answer is to say so.
				held = toSell;
				unconfirmed = true;
			}

			LatestPrice price = market.latest(itemId);
			List<Candle> series = marketData.getSeries(itemId, TIMESTEP);
			ItemFeatures features = featureEngine.compute(itemId, series, BUCKET_SECONDS);

			boolean isSkipped = skippedItems().contains(itemId);
			SellDecision decision = sellTiming.evaluate(position, price, features, series, horizon, now,
				config.minProfitPerFlip(), inInventory, sellOnly, isSkipped);

			// A buy for this item is still working, so nothing of it is listed.
			//
			// <b>Half a trade is not a holding.</b> The rule that everything held is listed at once is
			// about a position that has finished arriving; it was never meant to reach into the middle
			// of an order that is still filling. The guard here first asked "is this a sell?", which
			// stopped meaning anything once the sell engine began answering yes to everything, and
			// then asked "can we see the units?" -- which is no better, because collecting a part-
			// filled buy is exactly how the units become visible. Both let the same thing through: a
			// buy that had filled a fraction of itself was treated as a finished holding and put up
			// for sale, and with no slot free the engine cancelled another item's buy to make room.
			//
			// Being able to see the units is not the question. It is one accumulation, and selling
			// the early part of it back into the same book while the rest is still arriving is not
			// market-making -- it pays the tax twice on the same capital, spends a second slot, and
			// abandons the trade that was actually planned. The order finishes, or it is cancelled,
			// and then the position is listed. Which happens on the very next pass.
			//
			// A position that has gone through its stop is the one exception, and the answer there is
			// still not to sell around the buy: it is to stop buying. Continuing to accumulate
			// something we have decided to leave makes no sense at any slot count. Cancel the buy,
			// and the next pass finds no open order and sells normally.
			// The same accumulation, in the gap between a cancel and its replacement.
			//
			// A reprice is cancel, collect, place again, and for the length of that the item has no
			// offer on the board -- so the guard below, which asks whether a buy is running, sees
			// nothing and lets the partial fill through. That is how a player who cancelled exactly
			// as instructed was then told to sell the one unit that had filled. The plugin owes them
			// a replacement; until it has given them one, this is still a trade in progress.
			if (awaitingReplacement(itemId, account, now))
			{
				TrackedOffer buy = openBuyFor(itemId);
				statuses.put(itemId, buy == null
					? PositionStatus.awaitingReplacement(position.getQuantity(), 0)
					: PositionStatus.awaitingReplacement(buy.getQuantityFilled(),
						buy.getTotalQuantity()));
				continue;
			}

			if (stillBuying.contains(itemId))
			{
				if (decision.getAction() == SellDecision.Action.CUT)
				{
					Suggestion stopBuying = cancelBuyBlockingExit(position, decision, market);
					if (stopBuying != null)
					{
						// Not the plain sell reason. The card has to say the same thing the suggestion
						// beside it says -- stop buying, then sell -- or the two read as disagreeing.
						statuses.put(itemId, PositionStatus.exitBlockedByOwnBuy(decision));
						positionStatuses.set(Collections.unmodifiableMap(statuses));
						return stopBuying;
					}
					// The buy finished between the status pass and here; sell normally.
				}
				else
				{
					TrackedOffer buy = openBuyFor(itemId);
					statuses.put(itemId, buy == null ? PositionStatus.stillBuying(0, 0)
						: PositionStatus.stillBuying(buy.getQuantityFilled(), buy.getTotalQuantity()));
					continue;
				}
			}

			// Recorded whether or not it wins, and whether or not it is a sale. A holding being waited
			// on deliberately is exactly what the player cannot otherwise see.
			statuses.put(itemId, new PositionStatus(decision, unconfirmed, false));
			if (!decision.isSell())
			{
				continue;
			}

			int quantity = Math.min(held, toSell);
			// A second offer on an item already selling costs a second slot.
			//
			// The remainder of a part-filled buy arrives in dribs: an order for twenty thousand grapes
			// that fills a few hundred at a time leaves a new "sellable" remainder on every refresh,
			// and each one would ask for its own offer. On three slots that is the whole account
			// committed to selling one item in pieces, for hours, to realise a few hundred coins.
			//
			// So the remainder has to clear the same bar a new trade does. It is the same question --
			// is this worth a Grand Exchange slot -- and the player has already answered it by setting
			// the minimum profit per flip. A genuine remainder passes easily; a trickle does not, and
			// waits for the offer already running to clear.
			long remainderProfit = taxCalculator.netProfit(itemId, position.isCostKnown() ? position.getAverageCost() : 0,
				decision.getPrice(), quantity);
			if (!remainderWarrantsItsOwnOffer(listed, remainderProfit, config.minProfitPerFlip()))
			{
				statuses.put(itemId, sellingStatus(itemId, quantity));
				continue;
			}
			SellCandidate candidate = new SellCandidate(position, decision, quantity, unconfirmed, inInventory);
			if (best == null || SellCandidate.MOST_URGENT_FIRST.compare(candidate, best) < 0)
			{
				best = candidate;
			}
		}

		if (best == null)
		{
			positionStatuses.set(Collections.unmodifiableMap(statuses));
			return null;
		}

		if (account.getFreeSlots() <= 0)
		{
			// The one thing the position itself cannot know: it is ready, and there is nowhere to go.
			statuses.put(best.position.getItemId(),
				new PositionStatus(best.decision, best.unconfirmed, true));
			positionStatuses.set(Collections.unmodifiableMap(statuses));

			// Make room rather than asking for it.
			//
			// This used to say "collect or cancel an offer to free one up" and stop, which is a shrug
			// at the exact moment the player needs an answer -- and now that a slot is no longer held
			// in reserve all session, it is the only thing standing between a holding and its exit.
			// Collect runs before this in the chain, so every slot here holds a working offer; the one
			// to give up is the buy with the most still reserved, which is furthest from finishing and
			// frees the most coins.
			TrackedOffer give = largestOpenBuy(offers.getOffers());
			return makeRoomToSell(give, give == null ? "" : market.getItemName(give.getItemId()),
				best.position.getItemName(), explainer);
		}

		positionStatuses.set(Collections.unmodifiableMap(statuses));

		Position position = best.position;
		SellDecision decision = best.decision;
		int itemId = position.getItemId();
		boolean cut = decision.getAction() == SellDecision.Action.CUT;

		// Held still through noise. See advisedPrice: the quote moves on every feed update, and a
		// card whose number changes while the player is typing it reads as the plugin changing its
		// mind about a trade it has not changed its mind about.
		int sellPrice = advisedPrice(itemId, false, decision.getPrice(), now.getEpochSecond());

		long profit = taxCalculator.netProfit(itemId, position.isCostKnown() ? position.getAverageCost() : 0, sellPrice,
			best.quantity);

		int collectionSlot = -1;
		if (!best.inInventory)
		{
			for (TrackedOffer offer : offers.getOffers())
			{
				if (offer.getItemId() == itemId && offer.getQuantityFilled() > 0)
				{
					collectionSlot = offer.getSlot();
					break;
				}
			}
		}

		// A loss cut is a sell -- the player places a sell offer to do it -- so it keeps the SELL type
		// and says what it is with a flag. It used to be given the CANCEL type, which meant nothing
		// could use CANCEL for its documented meaning of abandoning an offer.
		Suggestion.Builder builder = Suggestion.builder(SuggestionType.SELL)
			.lossCut(cut)
			.item(itemId, position.getItemName())
			.price(sellPrice)
			.quantity(best.quantity)
			.slot(collectionSlot)
			.expectedProfit(profit)
			.expectedMinutes(decision.getExpectedMinutes())
			.fillMinutes(0, decision.getExpectedMinutes())
			.headline((cut ? "Cut your losses on " : "Sell your ") + position.getItemName());


		// A loss cut is still a sell offer as far as the walkthrough is concerned; only the wording
		// and the overlay's warning change.
		return builder.build();
	}

	// ---------------------------------------------------------------------- buy

	private Suggestion arbitrageSuggestion(MarketSnapshot market, AccountState account, Instant now)
	{
		long spendable = account.spendableCoins(config.includeBankValue(), config.bankrollCap());
		
		for (com.flippingfriend.model.arbitrage.ArbitrageRecipe recipe : arbitrageRegistry.getRecipes())
		{
			long totalCost = 0;
			boolean missingData = false;
			for (Map.Entry<Integer, Integer> entry : recipe.getInputs().entrySet())
			{
				LatestPrice compPrice = market.latest(entry.getKey());
				if (compPrice == null || !compPrice.isComplete())
				{
					missingData = true;
					break;
				}
				totalCost += (long) compPrice.getHigh() * entry.getValue();
			}
			
			if (missingData || totalCost > spendable) continue;
			
			long expectedRevenue = 0;
			int mainOutputId = -1;
			int totalOutputQuantity = 0;
			for (Map.Entry<Integer, Integer> entry : recipe.getOutputs().entrySet())
			{
				LatestPrice outPrice = market.latest(entry.getKey());
				if (outPrice == null || !outPrice.isComplete())
				{
					missingData = true;
					break;
				}
				expectedRevenue += (long) outPrice.getLow() * entry.getValue();
				mainOutputId = entry.getKey();
				totalOutputQuantity += entry.getValue();
			}
			
			if (missingData || mainOutputId == -1) continue;
			
			// Let's rely on standard tax rule: 1% capped at 5m per item.
			long tax = 0;
			for (Map.Entry<Integer, Integer> entry : recipe.getOutputs().entrySet())
			{
				LatestPrice outPrice = market.latest(entry.getKey());
				long itemTax = (long) (outPrice.getLow() * 0.01);
				if (itemTax > 5_000_000) itemTax = 5_000_000;
				tax += itemTax * entry.getValue();
			}
			
			long netProfit = expectedRevenue - totalCost - tax;
			
			if (netProfit > config.minProfitPerFlip())
			{
				return Suggestion.builder(recipe.getActionType())
					.item(mainOutputId, recipe.getName())
					.quantity(totalOutputQuantity)
					.expectedProfit(netProfit)
					.headline("Arbitrage Opportunity: " + recipe.getName())
					.build();
			}
		}
		
		return null;
	}

	private Suggestion buySuggestion(MarketSnapshot market, TradingHorizon horizon, AccountState account,
		Instant now)
	{
		RiskProfile profile = horizon.getProfile();
		long spendable = account.spendableCoins(config.includeBankValue(), config.bankrollCap());
		if (spendable < 1000)
		{
			return Suggestion.waiting("Not enough coins to trade with",
				"The plugin can see " + explainer.formatGp(spendable) + " to work with. Flipping needs "
					+ "some starting capital — even a few thousand coins is enough to begin.");
		}

		Set<String> blocked = parseBlocked(config.blockedItems());
		List<Screened> shortlist = screen(market, profile, account, spendable, blocked, horizon, now);

		if (shortlist.isEmpty())
		{
			return Suggestion.waiting("Nothing worth trading right now",
				"Nothing currently clears the bar for your " + profile.getDisplayName().toLowerCase()
					+ " risk setting. This is normal — the plugin would rather say nothing than "
					+ "suggest a bad trade. It keeps checking every few seconds.");
		}

		// Warm the history cache for everything on the shortlist, so items we cannot analyse yet
		// become available on the next pass rather than never.
		List<Integer> ids = new ArrayList<>(shortlist.size());
		for (Screened screened : shortlist)
		{
			ids.add(screened.metadata.getId());
		}
		marketData.prefetchSeries(ids, TIMESTEP);
		marketData.prefetchSeries(ids, LONG_TIMESTEP);

		// Ask the LSTM forecaster for momentum predictions on every shortlisted item.
		// The call is best-effort: if the server is down, predictions will be empty and
		// the engine runs exactly as before.
		Map<Integer, List<Double>> histories = new HashMap<>();
		for (Screened screened : shortlist)
		{
			int itemId = screened.metadata.getId();
			List<Candle> series = marketData.getSeries(itemId, TIMESTEP);
			// dataset.py requires at least window_size (30) + 2 samples to train.
			if (series.size() >= 32)
			{
				List<Double> prices = new ArrayList<>(series.size());
				for (Candle c : series)
				{
					prices.add((double) c.getAvgHighPrice());
				}
				histories.put(itemId, prices);
			}
		}
		Map<Integer, Double> predictions = lstmClient.predictBulk(histories);
		featureEngine.setPredictedMomentums(predictions);

		LatestPrice natureRuneLatest = market.latest(561);
		int natureRunePrice = natureRuneLatest != null && natureRuneLatest.getHigh() > 0 ? natureRuneLatest.getHigh() : 200;

		Candidate best = null;
		String lastRejection = null;
		boolean sawHistory = false;
		int withHistory = 0;
		List<EvaluatedCandidate> evaluated = new ArrayList<>();
		List<Candidate> ranked = new ArrayList<>();

		for (Screened screened : shortlist)
		{
			int itemId = screened.metadata.getId();
			List<Candle> series = marketData.getSeries(itemId, TIMESTEP);
			if (series.isEmpty())
			{
				continue;
			}
			sawHistory = true;
			withHistory++;

			ItemFeatures features = featureEngine.compute(itemId, series, BUCKET_SECONDS);

			// The fortnight view. Empty on the first pass for an item while the request is in
			// flight, in which case the model simply falls back to what a day of data can tell it.
			MarketContext context = MarketContext.from(marketData.getSeries(itemId, LONG_TIMESTEP),
				screened.price.getLow());

			FilterResult verdict = filter.screen(screened.metadata, screened.price, features, context,
				profile.vetoThresholds(), account.canBuyMembersItems(), now);

			// The setup as the learned model sees it, recorded whether or not the trade is taken, so
			// the outcome can train the model either way.
			double screenMargin = (double) taxCalculator.netMarginPerItem(itemId,
				screened.price.getLow(), screened.price.getHigh()) / Math.max(1, screened.price.getLow());

			if (!verdict.isAccepted())
			{
				lastRejection = verdict.getReason();
				// Recorded rather than discarded: a rejection nobody ever checks is a rule nobody
				// can tell is wrong.
				evaluated.add(EvaluatedCandidate.rejected(itemId, screened.metadata.getName(),
					screened.price.getLow(), screened.price.getHigh(),
					Math.max(1, Math.min(screened.buyLimitRemaining, 100)), verdict.getReason(),
					null));
				continue;
			}


			Candidate candidate = scorer.score(screened.metadata, screened.price, features, series, context,
				horizon, spendable, screened.buyLimitRemaining, config.useCalibration(), natureRunePrice, now);

			if (candidate == null || candidate.getNetProfit() < config.minProfitPerFlip())
			{
				continue;
			}

			// The learned model's say, computed here because this is where the item's market context
			// still exists. It used to be applied at sort time with MarketContext.unknown(), whose
			// sample count is zero -- so three of the sixteen features the model was trained on
			// (price percentile, volatility ratio, hour liquidity) collapsed to the constants 0.5,
			// 0.25 and 0.5 at inference. Any weight the model had learned on them was applied
			// identically to every candidate, which is worse than not using them at all.
			double scorePenalty = 1.0;
			int benchmark = config.benchmarkProfitPerFlip();
			if (benchmark > 0 && candidate.getNetProfit() < benchmark)
			{
				scorePenalty = (double) candidate.getNetProfit() / benchmark;
			}

			candidate.setAdjustedScore(candidate.getScore() * scorePenalty);

			evaluated.add(EvaluatedCandidate.accepted(candidate, null));
			ranked.add(candidate);
		}

		// Ranked on the adjusted score set above, where the model had the real context to work from.
		// An untrained or unhelpful model returns 1.0 for everything and changes nothing.
		ranked.sort(Comparator.comparingDouble(Candidate::getAdjustedScore).reversed());

		if (!ranked.isEmpty())
		{
			best = ranked.get(0);
		}

		lastEvaluated.set(Collections.unmodifiableList(evaluated));

		if (best == null)
		{
			// Counted rather than guessed at.
			//
			// This said "a few seconds the first time" for what was sixty throttled requests made one
			// at a time, and gave no sign of progress -- so a warm-up that was working looked exactly
			// like one that had stalled. Saying how many of the shortlist have history yet is the
			// difference between waiting and wondering.
			String detail = !sawHistory
				? "Loading price history for the " + shortlist.size() + " items on the shortlist. "
					+ "None of it has arrived yet, so there is nothing to judge."
				: withHistory < shortlist.size()
					? "Price history has arrived for " + withHistory + " of " + shortlist.size()
						+ " shortlisted items. Nothing among them is worth trading yet."
					: "Some items looked promising but did not survive a closer look."
						+ (lastRejection == null ? "" : " The most recent was ruled out because: "
						+ lowerFirst(lastRejection));
			return Suggestion.waiting("Checking the market", detail);
		}

		// The stop is part of the plan, not an afterthought: it is decided from the price we intend
		// to pay, at the moment we decide to pay it.
		int stopPrice = (int) Math.round(best.getBuyPrice() * (1 - profile.getLossCutPct()));
		tradePlans.plan(best.getItemId(), best.getSellPrice(), stopPrice, best.getRoundTripMinutes(),
			best.getNetProfit());

		return Suggestion.builder(SuggestionType.BUY)
			.item(best.getItemId(), best.getItemName())
			.price(best.getBuyPrice())
			.quantity(best.getQuantity())
			.expectedProfit(best.getNetProfit())
			.confidence(best.getConfidence())
			.expectedMinutes(best.getRoundTripMinutes())
			.fillMinutes(best.getBuyFill().getExpectedMinutes(), best.getSellFill().getExpectedMinutes())
			.breakEvenPrice(best.getBreakEvenPrice())
			.targetSellPrice(best.getSellPrice())
			.headline("Buy " + explainer.formatNumber(best.getQuantity()) + " × " + best.getItemName())
			.build();
	}

	/**
	 * The cheap pass. Everything here is a lookup or a multiplication, so it can run over every item
	 * the wiki publishes without noticeable cost.
	 */
	private List<Screened> screen(MarketSnapshot market, RiskProfile profile, AccountState account,
		long spendable, Set<String> blocked, TradingHorizon horizon, Instant now)
	{
		List<Screened> shortlist = new ArrayList<>();
		long capitalCeiling = (long) (spendable * profile.getMaxCapitalFraction());

		// What is already at risk, so a second suggestion cannot quietly double a position the
		// player is still holding.
		Exposure exposure = currentExposure(market);
		long perItemCap = (long) (spendable * MAX_ITEM_EXPOSURE);
		long perGroupCap = (long) (spendable * MAX_GROUP_EXPOSURE);

		// Anything already sitting in a slot must not be suggested again. Buy limits only decrement
		// when an offer actually fills, so for the whole time an offer is open the item still looks
		// completely untouched to the limit check — and the engine would cheerfully tell you to buy
		// the thing you just bought, over and over, until it filled.
		Set<Integer> alreadyOnOffer = offers.itemsWithOpenOffers();
		// Resolved once for the sweep rather than per item: the set prunes lapsed entries as it is
		// built, and doing that eighteen hundred times a pass would be work for nothing.
		Set<Integer> skippedNow = skippedItems();

		for (Map.Entry<Integer, LatestPrice> entry : market.getLatest().entrySet())
		{
			int itemId = entry.getKey();
			if (skippedNow.contains(itemId))
			{
				continue;
			}

			if (alreadyOnOffer.contains(itemId))
			{
				continue;
			}

			ItemMetadata metadata = market.metadata(itemId);
			if (metadata == null)
			{
				continue;
			}
			if (!account.canBuyMembersItems() && metadata.isMembers())
			{
				continue;
			}
			if (blocked.contains(metadata.getName().toLowerCase(Locale.ROOT)))
			{
				continue;
			}

			// Reconcile the spot quote against the five-minute average before believing it, so a
			// single odd transaction cannot manufacture a shortlist entry.
			LatestPrice price = PriceAnchor.anchor(entry.getValue(), market.fiveMinute(itemId));
			if (price == null || !price.isComplete())
			{
				continue;
			}

			int buyPrice = price.getLow();
			int sellPrice = price.getHigh();
			if (buyPrice <= 0 || buyPrice > capitalCeiling)
			{
				continue;
			}

			long marginPerItem = taxCalculator.netMarginPerItem(itemId, buyPrice, sellPrice);
			if (marginPerItem <= 0 || (double) marginPerItem / buyPrice < profile.getMinNetMarginPct())
			{
				continue;
			}

			int buyLimit = metadata.getBuyLimit();
			double hourlyVolume = hourlyVolume(market, itemId);
			if (hourlyVolume < profile.getMinVolumeToLimitRatio() * buyLimit)
			{
				continue;
			}

			int remaining = buyLimits.remaining(itemId, buyLimit, now);
			if (remaining <= 0)
			{
				continue;
			}

			// Concentration guards. Buy limits cap a single four-hour window; without these, nothing
			// stops the engine rebuilding the same position window after window, or filling every
			// slot with items that all move together.
			if (exposure.forItem(itemId) >= perItemCap)
			{
				continue;
			}

			String group = ItemGroups.groupOf(metadata.getName());
			if (!ItemGroups.UNGROUPED.equals(group) && exposure.forGroup(group) >= perGroupCap)
			{
				continue;
			}

			// Rank by what a slot can actually produce, not by what the bank could theoretically
			// afford. Whichever of the buy limit, the traded volume or the cash runs out first is
			// the real ceiling, and an item with a huge limit that nobody trades is worth nothing.
			// Reachable flow is a rate; it only becomes a quantity once multiplied by the time we are
			// willing to wait. Without the horizon this compared units against units-per-hour and
			// implicitly assumed one, which ranked volume-bound items at a fraction of their real
			// throughput against items bound by the buy limit or by cash. The same mistake was fixed
			// in the companion's own screen; this copy kept it.
			int byVolume = (int) Math.max(1,
				hourlyVolume * VOLUME_CAPTURE * horizon.legHorizonHours());
			int fillable = (int) Math.min(Math.min(remaining, capitalCeiling / buyPrice), byVolume);
			if (fillable <= 0)
			{
				continue;
			}

			long potential = marginPerItem * fillable;
			if (potential < config.minProfitPerFlip())
			{
				continue;
			}

			shortlist.add(new Screened(metadata, price, remaining, potential));
		}

		shortlist.sort(Comparator.comparingLong((Screened s) -> s.roughProfit).reversed());
		return shortlist.size() > DEEP_ANALYSIS_LIMIT
			? new ArrayList<>(shortlist.subList(0, DEEP_ANALYSIS_LIMIT))
			: shortlist;
	}

	/**
	 * Value of everything currently held or on order, by item and by family.
	 * <p>
	 * Positions are valued at what was paid rather than at today's price: the question is how much is
	 * committed, not what it happens to be worth this minute.
	 */
	private Exposure currentExposure(MarketSnapshot market)
	{
		Map<Integer, Long> byItem = new HashMap<>();
		Map<String, Long> byGroup = new HashMap<>();

		for (Position position : positions.all())
		{
			long value = position.isCostKnown()
				? position.getTotalCost()
				: (long) position.getQuantity() * priceOf(market, position.getItemId());
			accrue(byItem, byGroup, position.getItemId(), position.getItemName(), value);
		}

		for (TrackedOffer offer : offers.getOffers())
		{
			if (!offer.isBuying())
			{
				continue;
			}
			accrue(byItem, byGroup, offer.getItemId(), market.getItemName(offer.getItemId()),
				(long) offer.getPrice() * offer.getRemaining());
		}

		return new Exposure(byItem, byGroup);
	}

	private static void accrue(Map<Integer, Long> byItem, Map<String, Long> byGroup, int itemId,
		String itemName, long value)
	{
		if (value <= 0)
		{
			return;
		}
		byItem.merge(itemId, value, Long::sum);
		String group = ItemGroups.groupOf(itemName);
		if (ItemGroups.isGrouped(group))
		{
			byGroup.merge(group, value, Long::sum);
		}
	}

	private static int priceOf(MarketSnapshot market, int itemId)
	{
		LatestPrice price = market.latest(itemId);
		return price == null || price.getLow() == null ? 0 : price.getLow();
	}

	private static final class Exposure
	{
		private final Map<Integer, Long> byItem;
		private final Map<String, Long> byGroup;

		Exposure(Map<Integer, Long> byItem, Map<String, Long> byGroup)
		{
			this.byItem = byItem;
			this.byGroup = byGroup;
		}

		long forItem(int itemId)
		{
			return byItem.getOrDefault(itemId, 0L);
		}

		long forGroup(String group)
		{
			return byGroup.getOrDefault(group, 0L);
		}
	}

	private static double hourlyVolume(MarketSnapshot market, int itemId)
	{
		Candle hourly = market.hourly(itemId);
		if (hourly != null && hourly.getTotalVolume() > 0)
		{
			return hourly.getTotalVolume();
		}
		Candle fiveMinute = market.fiveMinute(itemId);
		return fiveMinute == null ? 0 : fiveMinute.getTotalVolume() * 12.0;
	}

	public static Set<String> parseBlocked(String raw)
	{
		Set<String> blocked = new HashSet<>();
		if (raw == null || raw.trim().isEmpty())
		{
			return blocked;
		}
		for (String part : raw.split(","))
		{
			String name = part.trim().toLowerCase(Locale.ROOT);
			if (!name.isEmpty())
			{
				blocked.add(name);
			}
		}
		return blocked;
	}

	private static String lowerFirst(String text)
	{
		if (text == null || text.isEmpty())
		{
			return "";
		}
		return Character.toLowerCase(text.charAt(0)) + text.substring(1);
	}

	private static final class Screened
	{
		private final ItemMetadata metadata;
		private final LatestPrice price;
		private final int buyLimitRemaining;
		private final long roughProfit;

		Screened(ItemMetadata metadata, LatestPrice price, int buyLimitRemaining, long roughProfit)
		{
			this.metadata = metadata;
			this.price = price;
			this.buyLimitRemaining = buyLimitRemaining;
			this.roughProfit = roughProfit;
		}
	}

	private static final class SellCandidate
	{
		private final Position position;
		private final SellDecision decision;
		private final int quantity;
		/** True when the holding is on record but has not been seen in any container. */
		private final boolean unconfirmed;
		private final boolean inInventory;

		SellCandidate(Position position, SellDecision decision, int quantity, boolean unconfirmed, boolean inInventory)
		{
			this.position = position;
			this.decision = decision;
			this.quantity = quantity;
			this.unconfirmed = unconfirmed;
			this.inInventory = inInventory;
		}

		/**
		 * Which of two ready sales goes first.
		 * <p>
		 * Three tiers, compared in order rather than added together. Something already in the
		 * inventory is one click from being listed and is blocking nothing while it sits there, so
		 * it goes first. A loss cut beats banking a winner, because the winner is not getting worse.
		 * Past that, the larger profit goes first.
		 * <p>
		 * This was a single {@code double}: {@code (inInventory ? 1e24 : 0) + (cut ? 1e12 : 0) +
		 * profit}. A double carries about sixteen significant digits, so adding a profit of a few
		 * million to 1e24 came back as 1e24 exactly -- with more than one item in the inventory the
		 * profit term vanished entirely and the order came down to whichever the map happened to
		 * iterate first. Tiers that mean different things do not belong on one number line.
		 */
		static final Comparator<SellCandidate> MOST_URGENT_FIRST =
			Comparator.comparing((SellCandidate c) -> c.inInventory)
				.thenComparing(c -> c.decision.getAction() == SellDecision.Action.CUT)
				.thenComparingLong(c -> c.decision.getExpectedProfit())
				.reversed();
	}
}
