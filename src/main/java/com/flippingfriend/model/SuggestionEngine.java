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
	/**
	 * Built in the constructor, not at the field.
	 * <p>
	 * A field initialiser runs before the constructor body, so {@code new
	 * ConversionEngine(taxCalculator)} here would have captured a null and thrown the first time a
	 * conversion was priced. It compiles perfectly.
	 */
	private final ConversionEngine conversionEngine;
	/** Rebuilt when the mapping changes, because the decant recipes are derived from it. */
	private volatile ConversionRegistry conversionRegistry;
	private volatile int conversionsBuiltFrom = -1;

	private final AtomicReference<Suggestion> current = new AtomicReference<>(Suggestion.idle());
	private volatile Suggestion pendingAdjustment = null;
	
	public void setPendingAdjustment(Suggestion pending)
	{
		this.pendingAdjustment = pending;
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

	/**
	 * Whether an open offer is still priced where this engine would price it, and whether saying so
	 * is worth interrupting the player for. Holds the per-slot cooldown, so it is one instance.
	 */
	private final RepriceReview repriceReview = new RepriceReview();

	@Inject
	public SuggestionEngine(MarketDataService marketData, FeatureEngine featureEngine,
		ManipulationFilter filter, Scorer scorer, Explainer explainer, Calibrator calibrator,
		TaxCalculator taxCalculator, AccountMonitor accountMonitor, BuyLimitTracker buyLimits,
		PositionBook positions, OfferTracker offers, SellTimingEngine sellTiming, TradePlans tradePlans,
		FlippingFriendConfig config, SkipList skipped)
	{
		this.skipped = skipped;
		this.marketData = marketData;
		this.featureEngine = featureEngine;
		this.filter = filter;
		this.scorer = scorer;
		this.explainer = explainer;
		this.calibrator = calibrator;
		this.taxCalculator = taxCalculator;
		this.conversionEngine = new ConversionEngine(taxCalculator);
		this.accountMonitor = accountMonitor;
		this.buyLimits = buyLimits;
		this.positions = positions;
		this.offers = offers;
		this.sellTiming = sellTiming;
		this.tradePlans = tradePlans;
		this.config = config;
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

	/**
	 * The advice to show, holding the player on the trade they are part way through entering.
	 *
	 * <p>While an offer is being adjusted this used to return the pinned suggestion and compute
	 * nothing at all, so the price on the card and in the overlay was whatever had been calculated at
	 * the moment the editor opened. Reprice advice is worth exactly its price, and the number the
	 * player was being told to type went stale the instant the market moved -- on both surfaces at
	 * once, because they read the same object.
	 *
	 * <p>StepGuide already had the right rule and could never exercise it: "a correction to the trade
	 * already being typed is applied even mid-entry -- the whole point of holding advice back is to
	 * avoid placing the wrong offer, and letting a price go stale under the player's fingers achieves
	 * exactly that by a different route. Only a genuinely different trade waits." It never saw a
	 * correction, because nothing upstream produced one.
	 *
	 * <p>So the pin holds the CHOICE of trade and not its numbers. Same item, same slot, same action:
	 * the fresh figures win, and the guide decides whether the player has already typed past them. A
	 * genuinely different trade still waits, which is what the pin was for.
	 */
	private Suggestion compute(boolean computeBuy)
	{
		Suggestion pinned = pendingAdjustment;
		Suggestion fresh = computeFresh(computeBuy);
		if (pinned == null)
		{
			return fresh;
		}
		return fresh != null && fresh.isSameTradeAs(pinned) ? fresh : pinned;
	}

	private Suggestion computeFresh(boolean computeBuy)
	{
		AccountState account = accountMonitor.getState();
		MarketSnapshot market = marketData.getSnapshot();
		Instant now = Instant.now();

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
			return Suggestion.idle();
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

		Suggestion adjust = adjustSuggestion(market, horizon, now);
		if (adjust != null)
		{
			return adjust;
		}

		Suggestion sell = sellSuggestion(market, horizon, account, now);
		if (sell != null)
		{
			return sell;
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
		Position awaiting = awaitingExit();
		if (awaiting != null && !exitIsNear(awaiting))
		{
			awaiting = null;
		}
		if (awaiting != null && account.getFreeSlots() <= 1)
		{
			return Suggestion.waiting("Keeping this slot free to sell",
				"You still hold " + explainer.formatNumber(awaiting.getQuantity()) + " "
					+ awaiting.getItemName() + " that has to be sold, and this is your last free slot. "
					+ "Buying with it would leave nowhere to place that sale when the price arrives.");
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
		for (TrackedOffer offer : offers.getOffers())
		{
			String state = offer.getState();
			boolean open = "BUYING".equals(state) || "SELLING".equals(state);
			if (!open || offer.getQuantityFilled() >= offer.getTotalQuantity())
			{
				continue;
			}

			// Never nag about an offer the player has not had a chance to look at yet.
			if (offer.minutesOpen(now.getEpochSecond()) < horizon.staleOfferMinutes())
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

			// Is this offer still priced where we would price it now -- in EITHER direction?
			//
			// Everything below this asks the one-directional question: has the offer become too
			// passive to fill. That leaves the opposite case unspoken. A sell listed at 100 while
			// buyers move to 110 is not stalled, it is underpriced, and it fills at 100 with nobody
			// having mentioned it. The same in reverse for a buy left above a bid that has fallen.
			//
			// Top of book either way, so this never trades fill probability for price: a buy at one
			// over the best bid and a sell at one under the best ask both still lead the queue. What
			// changes is only how much the trade earns, which is why the decision is purely "is the
			// improvement worth the click" and RepriceReview can answer it without a fill model.
			Suggestion improvement = improvePricing(offer, price, trend, name, remaining, horizon, now);
			if (improvement != null)
			{
				return improvement;
			}

			if (horizon.isOutbid(offer.isBuying(), offer.getPrice(), price, trend))
			{
				if (offer.isBuying())
				{
					int newPrice = price.getLow() + 1;
					int targetSellPrice = Math.max(1, price.getHigh() - 1);

					long expectedProfit = taxCalculator.netProfit(offer.getItemId(), newPrice, targetSellPrice, remaining);

					RiskProfile profile = horizon.getProfile();
					long marginPerItem = taxCalculator.netMarginPerItem(offer.getItemId(), newPrice, targetSellPrice);
					if (marginPerItem <= 0 || (double) marginPerItem / newPrice < profile.getMinNetMarginPct())
					{
						continue;
					}

					if (expectedProfit < config.minProfitPerFlip())
					{
						continue;
					}

					// The rescue paths start the cooldown too. Without this, a rescue and an
					// improvement could take turns moving the same offer every pass, each one
					// perfectly justified on its own and the pair of them useless.
					repriceReview.noteAdvised(offer.getSlot(), offer.getItemId(), newPrice,
						now.getEpochSecond());
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
							+ "offer is being skipped over.\n\nAdjust your offer to "
							+ explainer.formatNumber(newPrice) + " gp.")
						.build();
				}
				else
				{
					// Never below what the position cost.
					//
					// The branch further down carries this floor already, and its comment is
					// unambiguous about why: "every losing flip in the journal came from here and
					// nowhere else -- Maple logs bought at 11 and repriced to 10 for -15,000; Soft
					// clay bought at 119 and repriced to 116, twice, for -25,540". The floor was added
					// there and not here, and this branch returns FIRST, so it went on doing exactly
					// what that comment describes.
					//
					// It fires constantly, too. For a sale, isOutbid means our ask is above the market
					// -- which is the normal condition of every healthy flip in progress, since the
					// whole trade is buying at the bid and asking above it. So each time the market
					// ticked, this proposed walking the ask down to meet it, with no floor but 1 gp
					// and no reference to what had been paid.
					//
					// Below break-even this is not repricing at all, it is deciding to take a loss,
					// and that belongs to SellTimingEngine: it holds while the position is above its
					// stop and cuts when it is not. Say nothing here and let it answer.
					Position position = positions.get(offer.getItemId());
					boolean costKnown = position != null && position.isCostKnown()
						&& position.getAverageCost() > 0;
					int newPrice = repricedSell(price.getHigh(), position, costKnown
						? taxCalculator.breakEvenSellPrice(offer.getItemId(), position.getAverageCost())
						: 0);
					if (newPrice <= 0)
					{
						continue;
					}

					// The rescue paths start the cooldown too. Without this, a rescue and an
					// improvement could take turns moving the same offer every pass, each one
					// perfectly justified on its own and the pair of them useless.
					repriceReview.noteAdvised(offer.getSlot(), offer.getItemId(), newPrice,
						now.getEpochSecond());
					return Suggestion.builder(SuggestionType.MODIFY_SELL)
						.item(offer.getItemId(), name)
						.slot(offer.getSlot())
						.price(newPrice)
						.quantity(remaining)
						.headline("Reprice your " + name + " sell offer to **" + explainer.formatGp(newPrice) + "** gp. You have been outbid.")
						.build();
				}
			}

			if (!offer.isBuying() && price.getHigh() < offer.getPrice())
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
				boolean isCut = false;
				if (!repriceAllowed(newPrice, position, costKnown
					? taxCalculator.breakEvenSellPrice(offer.getItemId(), position.getAverageCost()) : 0))
				{
					// The new competitive price is below break-even. We only reprice if the timing engine
					// says we should cut our losses.
					List<Candle> series = marketData.getSeries(offer.getItemId(), TIMESTEP);
					ItemFeatures features = featureEngine.compute(offer.getItemId(), series, BUCKET_SECONDS);
					boolean inInventory = accountMonitor.getState().getInventoryHoldings().containsKey(offer.getItemId())
						&& accountMonitor.getState().getInventoryHoldings().get(offer.getItemId()) > 0;
					SellDecision decision = sellTiming.evaluate(position, price, features, series, horizon, now,
						config.minProfitPerFlip(), inInventory, sellOnly, skippedItems().contains(offer.getItemId()));

					if (!decision.isSell())
					{
						continue;
					}
					// Use the price decided by the timing engine for the loss cut.
					newPrice = decision.getPrice();
					isCut = decision.getAction() == SellDecision.Action.CUT;
				}
				// What the new price is actually worth. The reprice card carried no figure at all, so a
				long change = taxCalculator.netProfit(offer.getItemId(), costKnown ? position.getAverageCost() : 0, newPrice, remaining);
				repriceReview.noteAdvised(offer.getSlot(), offer.getItemId(), newPrice,
					now.getEpochSecond());
				return Suggestion.builder(SuggestionType.MODIFY_SELL)
					.item(offer.getItemId(), name)
					.slot(offer.getSlot())
					.price(newPrice)
					.quantity(remaining)
					.expectedProfit(change)
					.lossCut(isCut)
					.headline(isCut ? "Cut your losses on " + name : "Your " + name + " offer is too high")
					.detail(isCut
						? "This has fallen past the point where holding is worth the risk. Adjust your offer down to "
							+ explainer.formatNumber(newPrice) + " gp to keep the loss small."
						: "You asked " + explainer.formatNumber(offer.getPrice()) + " gp, but buyers are "
							+ "only paying " + explainer.formatNumber(price.getHigh()) + " gp, so nobody is "
							+ "taking it.\n\nAdjust your offer to "
							+ explainer.formatNumber(newPrice) + " gp.")
					.build();
			}
		}
		return null;
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
	 * The same offer, priced where it would be priced now, when that is worth the interruption.
	 *
	 * <p>Top of book on both sides: a buy belongs one coin above the best bid and a sell one coin
	 * below the best ask, which is where the rescue paths below already move them. Applying it in the
	 * favourable direction too is the whole of this method -- the market moving your way is not a
	 * reason to hear nothing.
	 *
	 * <p>Value is measured as the flip's expected profit at each price, so the comparison is in the
	 * units the player cares about and the thresholds in {@link RepriceReview} mean something
	 * concrete. A sell is valued against what the position cost; a buy against what it could then be
	 * sold for.
	 *
	 * @return the advice, or null when the offer is priced well enough to leave alone
	 */
	private Suggestion improvePricing(TrackedOffer offer, LatestPrice price,
		com.flippingfriend.data.Candle trend, String name, int remaining, TradingHorizon horizon,
		Instant now)
	{
		if (remaining <= 0 || price == null || !price.isComplete())
		{
			return null;
		}

		Position position = positions.get(offer.getItemId());
		boolean costKnown = position != null && position.isCostKnown()
			&& position.getAverageCost() > 0;
		int itemId = offer.getItemId();
		long minProfit = config.minProfitPerFlip();
		long nowSeconds = now.getEpochSecond();

		if (offer.isBuying())
		{
			int leading = price.getLow() + 1;
			// What the flip is worth bought at each price and sold where a buy is planned to sell.
			int exit = Math.max(1, price.getHigh() - 1);
			long valueNow = taxCalculator.netProfit(itemId, offer.getPrice(), exit, remaining);
			long valueMoved = taxCalculator.netProfit(itemId, leading, exit, remaining);

			// Only ever downwards here. Bidding UP to reach a market that has risen is the rescue
			// case below, which has its own margin and minimum-profit gates; duplicating them here
			// would be a second copy of a rule to drift from.
			if (leading >= offer.getPrice())
			{
				return null;
			}
			// And the five-minute average has to agree that sellers have come down, for the same
			// reason as the sell side: one cheap trade is not a market, and bidding down to meet it
			// leaves the offer below a market that has not actually moved.
			if (trend == null || trend.getAvgLowPrice() == null
				|| trend.getAvgLowPrice() >= offer.getPrice())
			{
				return null;
			}
			if (!repriceReview.worthMoving(offer.getSlot(), itemId, offer.getPrice(), leading,
				valueNow, valueMoved, minProfit, nowSeconds))
			{
				return null;
			}

			RiskProfile profile = horizon.getProfile();
			long marginPerItem = taxCalculator.netMarginPerItem(itemId, leading, exit);
			if (marginPerItem <= 0 || (double) marginPerItem / leading < profile.getMinNetMarginPct())
			{
				return null;
			}

			repriceReview.noteAdvised(offer.getSlot(), itemId, leading, nowSeconds);
			return Suggestion.builder(SuggestionType.MODIFY_BUY)
				.item(itemId, name)
				.slot(offer.getSlot())
				.price(leading)
				.quantity(remaining)
				.targetSellPrice(exit)
				.expectedProfit(valueMoved)
				.headline("You can buy " + name + " cheaper")
				.detail("Sellers have come down to " + explainer.formatNumber(price.getLow())
					+ " gp, and your offer is at " + explainer.formatNumber(offer.getPrice())
					+ " gp." + System.lineSeparator() + System.lineSeparator()
					+ "Adjust it to " + explainer.formatNumber(leading)
					+ " gp — still first in the queue, and "
					+ explainer.formatGp(valueMoved - valueNow) + " gp better on this trade.")
				.build();
		}

		int leading = Math.max(1, price.getHigh() - 1);
		// Only ever upwards here, for the same reason: a sell that has been left above the market is
		// the rescue case, and it owns the break-even floor that goes with selling into a fall.
		if (leading <= offer.getPrice())
		{
			return null;
		}
		// And the five-minute average has to agree that buyers have moved up.
		//
		// A spot price is one trade. Acting on it alone would chase every spike up and then, when it
		// fell back a minute later, leave the offer stranded above a market that was never really
		// there -- turning an improvement into the very stall this whole path exists to prevent.
		// isOutbid takes both readings for exactly this reason, and so does this.
		if (trend == null || trend.getAvgHighPrice() == null
			|| trend.getAvgHighPrice() <= offer.getPrice())
		{
			return null;
		}

		int cost = costKnown ? position.getAverageCost() : 0;
		long valueNow = taxCalculator.netProfit(itemId, cost, offer.getPrice(), remaining);
		long valueMoved = taxCalculator.netProfit(itemId, cost, leading, remaining);
		if (!repriceReview.worthMoving(offer.getSlot(), itemId, offer.getPrice(), leading,
			valueNow, valueMoved, minProfit, nowSeconds))
		{
			return null;
		}

		repriceReview.noteAdvised(offer.getSlot(), itemId, leading, nowSeconds);
		return Suggestion.builder(SuggestionType.MODIFY_SELL)
			.item(itemId, name)
			.slot(offer.getSlot())
			.price(leading)
			.quantity(remaining)
			.expectedProfit(valueMoved)
			.headline("You can sell " + name + " for more")
			.detail("Buyers have come up to " + explainer.formatNumber(price.getHigh())
				+ " gp, and your offer is at " + explainer.formatNumber(offer.getPrice())
				+ " gp." + System.lineSeparator() + System.lineSeparator()
					+ "Adjust it to " + explainer.formatNumber(leading)
				+ " gp — still first in the queue, and "
				+ explainer.formatGp(valueMoved - valueNow) + " gp better on this trade.")
			.build();
	}

	static boolean repriceAllowed(int newPrice, Position position, int breakEvenPrice)
	{
		if (position == null || !position.isCostKnown() || position.getAverageCost() <= 0)
		{
			return true;
		}
		return newPrice >= breakEvenPrice;
	}

	/**
	 * Where to move a sell offer that is not filling, or 0 when moving it there would realise a loss.
	 *
	 * <p>One function because there were two places doing this and only one of them had the floor.
	 * The guarded one carried the explanation -- "every losing flip in the journal came from here and
	 * nowhere else" -- and the unguarded one sat ABOVE it in the chain and returned first, so it went
	 * on doing precisely what that comment describes. A rule enforced at one of its two call sites is
	 * not enforced.
	 *
	 * <p>Below break-even this is not a repricing decision at all, it is a decision to take a loss,
	 * and that belongs to the sell engine: it holds while the position is above its stop and cuts when
	 * it is not. Returning 0 is how this says "not mine to answer".
	 */
	static int repricedSell(int marketHigh, Position position, int breakEvenPrice)
	{
		int newPrice = Math.max(1, marketHigh - 1);
		return repriceAllowed(newPrice, position, breakEvenPrice) ? newPrice : 0;
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
	 * Whether this holding is close enough to leaving to be worth a slot.
	 * <p>
	 * Read from the status the sell pass has just published, so the reservation and the card agree.
	 * A holding with no status yet -- a position seen before the first sell evaluation -- counts as
	 * near, because refusing to reserve on no information is the one direction that can strand it.
	 */
	private boolean exitIsNear(Position position)
	{
		return exitIsNear(positionStatuses.get().get(position.getItemId()));
	}

	static boolean exitIsNear(PositionStatus status)
	{
		if (status == null || status.getDecision() == null)
		{
			return true;
		}
		return status.getDecision().isExitNear();
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

	/**
	 * What a working buy on the same item does to the wish to sell it.
	 *
	 * <p>Pulled out of the sell loop and named, because it is the whole of the rule and it was
	 * previously a fall-through with a comment: the engine sold the part of an order that had arrived
	 * while the rest of the order was still filling behind it. The player was told to sell stock they
	 * were in the middle of buying.
	 */
	enum BuyInTheWay
	{
		/** No buy running. The ordinary sell path applies. */
		NOTHING_IN_THE_WAY,
		/** A buy is filling. Sell nothing until it stops; the target will still be there. */
		WAIT_FOR_THE_BUY,
		/** The trade has gone wrong. Cancel the buy, then sell whatever arrived before it stopped. */
		STOP_THE_BUY
	}

	/**
	 * The rule, in one place: a part-filled order is a position being built, not a position.
	 *
	 * <p>Listing the part that has arrived puts the same item on both sides of the book -- the buy
	 * keeps filling behind the sale, so the round ends holding stock that was reported as sold, taxed
	 * on a flip that never closed, having spent a second slot to do it.
	 *
	 * <p>A cut is the exception, and not because selling early is suddenly acceptable: it is because
	 * an order that keeps buying more of a collapsing item makes the hole deeper every minute. Even
	 * then the action is to stop the buy, not to sell around it.
	 */
	static BuyInTheWay buyInTheWay(boolean buyStillWorking, SellDecision decision)
	{
		if (!buyStillWorking)
		{
			return BuyInTheWay.NOTHING_IN_THE_WAY;
		}
		if (decision == null || decision.getAction() != SellDecision.Action.CUT)
		{
			return BuyInTheWay.WAIT_FOR_THE_BUY;
		}
		return BuyInTheWay.STOP_THE_BUY;
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
	 * instead of two, it frees the slot and returns the unspent coins, and on the next pass the
	 * ordinary sell path takes over with nothing in its way.
	 */
	private Suggestion cancelBuyBlockingExit(SellCandidate wanted, MarketSnapshot market)
	{
		int itemId = wanted.position.getItemId();
		TrackedOffer buy = openBuyFor(itemId);
		if (buy == null)
		{
			// It finished between the status pass and here; the ordinary sell path can have it.
			return null;
		}

		String name = market.getItemName(itemId);
		boolean cut = wanted.decision.getAction() == SellDecision.Action.CUT;
		long coins = (long) buy.getPrice() * buy.getRemaining();
		return Suggestion.builder(SuggestionType.CANCEL)
			.item(itemId, name)
			.slot(buy.getSlot())
			.price(buy.getPrice())
			.quantity(buy.getRemaining())
			.lossCut(cut)
			.headline("Stop buying " + name)
			.detail((cut
				? "This has fallen past the point where holding it is worth the risk, so buying more "
					+ "of it makes no sense. "
				: "It is time to sell this, and the buy still running would keep adding to what you "
					+ "have to sell. ")
				+ "Cancel the offer to free the slot and get "
				+ explainer.formatGp(coins) + " gp back. You will be told to sell the "
				+ explainer.formatNumber(wanted.quantity) + " you already have straight afterwards.")
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

			// A buy for this item is still working, so the position is still growing.
			//
			// Nothing can be listed until it stops, and reserving a slot to sell a fraction of a
			// growing order wastes the scarcest thing on the account. So an ordinary exit waits.
			//
			// An exit the engine actually wants is different, and the answer is not to sell around the
			// buy -- it is to stop buying. If the price has turned, continuing to accumulate an item we
			// are trying to leave makes no sense at any slot count, and on three it is indefensible.
			// Abandon the buy; the next pass sees no open order and sells normally.
			//
			// Nothing is ever sold around a working buy. A part-filled order is not a position, it is
			// a position being built, and listing the part that has arrived sells the same item on
			// both sides of the book at once: the buy keeps filling behind the sale, so the player
			// ends the round holding stock they were told they had sold, having paid tax on a flip
			// that never closed and spent a second slot to do it. This fell through to the ordinary
			// sell path on the strength of a roadmap note about turning part-filled slots into sell
			// instructions -- which is right the moment the buy stops, and wrong while it is running.
			//
			// So there are exactly two ways a holding leaves: the buy finishes, or the buy is
			// cancelled and what arrived before it stopped is sold.
			if (stillBuying.contains(itemId))
			{
				if (buyInTheWay(true, decision) == BuyInTheWay.WAIT_FOR_THE_BUY)
				{
					TrackedOffer buy = openBuyFor(itemId);
					statuses.put(itemId, buy == null ? PositionStatus.stillBuying(0, 0)
						: PositionStatus.stillBuying(buy.getQuantityFilled(), buy.getTotalQuantity()));
					continue;
				}

				// A cut is the one case that cannot wait: the trade has gone wrong, and an order that
				// keeps buying more of it makes the hole deeper every minute. The answer is still not
				// to sell around the buy -- it is to stop the buy. That is one action instead of two,
				// it frees the slot and returns the unspent coins, and the next pass finds nothing in
				// the way and sells what actually arrived.
				statuses.put(itemId, PositionStatus.exitBlockedByOwnBuy(decision));
				SellCandidate stop = new SellCandidate(position, decision, Math.min(held, toSell),
					unconfirmed, inInventory).blockedByOwnBuy();
				if (best == null || stop.priority() > best.priority())
				{
					best = stop;
				}
				continue;
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
			if (best == null || candidate.priority() > best.priority())
			{
				best = candidate;
			}
		}

		if (best == null)
		{
			positionStatuses.set(Collections.unmodifiableMap(statuses));
			return null;
		}

		if (best.blockedByOwnBuy)
		{
			// Cancelling does not need a free slot -- it makes one -- so this is settled before the
			// slot check below, which would otherwise start cancelling some unrelated offer to make
			// room for a sale that must not be placed yet anyway.
			Suggestion stop = cancelBuyBlockingExit(best, market);
			if (stop != null)
			{
				positionStatuses.set(Collections.unmodifiableMap(statuses));
				return stop;
			}
			// The buy finished between the status pass and here, so there is nothing left to cancel
			// and the holding is free to leave by the ordinary path.
			best = best.unblocked();
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

		long profit = taxCalculator.netProfit(itemId, position.isCostKnown() ? position.getAverageCost() : 0, decision.getPrice(),
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
			.price(decision.getPrice())
			.quantity(best.quantity)
			.slot(collectionSlot)
			.expectedProfit(profit)
			.expectedMinutes(decision.getExpectedMinutes())
			.fillMinutes(0, decision.getExpectedMinutes())
			// The claim the calibrator grades. Without it recordSellAdvice hands the ledger a zero,
			// the ledger stamps a zero onto the settled offer, and FillCalibration reads that as "no
			// claim was made" and throws the observation away -- which is why the sell leg had sat at
			// nought observations while the buy leg passed sixty.
			.confidence(decision.getCompletionProbability())
			.headline((cut ? "Cut your losses on " : "Sell your ") + position.getItemName());


		// A loss cut is still a sell offer as far as the walkthrough is concerned; only the wording
		// and the overlay's warning change.
		return builder.build();
	}

	// ---------------------------------------------------------------------- buy

	/**
	 * The best conversion the game is currently offering, or null when none clears the floor.
	 *
	 * <p>A conversion is a different profit source from a flip and a better one where it exists: the
	 * clerk packs a set on the spot and Bob Barter decants on the spot, so the conversion itself is
	 * free, instant and certain, and the only market risk left is the buy and sell legs a flip already
	 * carries.
	 *
	 * <p>The pricing used to be done inline here and got four things wrong, each of which flattered the
	 * answer. It applied a <b>1% tax</b> when the Grand Exchange charges 2% and every other line in
	 * this codebase knows it — a second copy of the tax rule, at half the rate, turning losing
	 * conversions into winning ones on thin margins. It ignored buy limits, so a recipe needing a
	 * hundred of a component limited to seventy was reported as available. It always proposed a single
	 * run, whatever the margin. And it returned the first profitable recipe it met rather than the
	 * best, which ranks by the order somebody happened to type the registry in.
	 *
	 * <p>All four now live in {@link ConversionEngine}, which takes {@link TaxCalculator} and does not
	 * know what the rate is.
	 */
	private Suggestion arbitrageSuggestion(MarketSnapshot market, AccountState account, Instant now)
	{
		long spendable = account.spendableCoins(config.includeBankValue(), config.bankrollCap());
		ConversionEngine.Conversion best = conversionEngine.best(conversions(market).getRecipes(),
			market.getLatest(), buyLimitRemaining(market, now), spendable, config.minProfitPerFlip(),
			now);
		if (best == null)
		{
			return null;
		}

		ConversionRecipe recipe = best.getRecipe();
		int outputId = recipe.getOutputs().keySet().iterator().next();
		return Suggestion.builder(typeOf(recipe))
			.item(outputId, recipe.getName())
			.quantity(best.getRuns())
			.expectedProfit(best.getTotalProfit())
			.headline(recipe.getName())
			.detail(explainer.formatGp(best.getProfitPerRun()) + " a time, "
				+ best.getRuns() + " of them within the buy limit, "
				+ recipe.getVenue().describe() + ".")
			.build();
	}

	/**
	 * The registry, rebuilt when the item mapping changes.
	 *
	 * <p>Decant recipes are derived from the mapping rather than listed, so the set of them depends on
	 * what the feed knows about. Rebuilding on every suggestion would re-scan four and a half thousand
	 * item names several times a second; rebuilding when the mapping's size changes is enough, because
	 * the mapping only changes on a game update.
	 */
	private ConversionRegistry conversions(MarketSnapshot market)
	{
		int mapped = market.getMetadata().size();
		ConversionRegistry cached = conversionRegistry;
		if (cached == null || mapped != conversionsBuiltFrom)
		{
			cached = new ConversionRegistry(market.getMetadata().values());
			conversionRegistry = cached;
			conversionsBuiltFrom = mapped;
		}
		return cached;
	}

	/**
	 * What is left of each input's four-hour limit, which gates how many times a recipe can run.
	 * <p>
	 * Only items with a window already open appear. An item absent from the tracker has spent none
	 * of its limit, and {@link ConversionEngine} reads a missing entry as unlimited — which is the
	 * right reading, since the limit only binds once buying has started.
	 */
	private Map<Integer, Integer> buyLimitRemaining(MarketSnapshot market, Instant now)
	{
		Map<Integer, Integer> remaining = new HashMap<>();
		for (Map.Entry<Integer, Integer> window : buyLimits.activeWindows(now).entrySet())
		{
			ItemMetadata item = market.metadata(window.getKey());
			if (item != null)
			{
				remaining.put(window.getKey(),
					buyLimits.remaining(window.getKey(), item.getBuyLimit(), now));
			}
		}
		return remaining;
	}

	private static SuggestionType typeOf(ConversionRecipe recipe)
	{
		if (recipe.getVenue() == ConversionRecipe.Venue.DECANTER)
		{
			return SuggestionType.DECANT;
		}
		// A set recipe whose output is a single item is a pack; one that produces the pieces is an
		// unpack. Reading it off the shape rather than storing it keeps the two from disagreeing.
		return recipe.getOutputs().size() == 1 ? SuggestionType.PACK : SuggestionType.UNPACK;
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

		// The LSTM momentum call used to happen here, and it is gone for three reasons at once.
		//
		// It crashed. `prices.add((double) c.getAvgHighPrice())` unboxes an Integer that is null
		// whenever a five-minute bucket saw no instant-buy, which is common on anything but the
		// busiest items. The NPE propagated into the engine refresh, where a catch-all logged a
		// warning and abandoned the whole cycle - so the panel silently stopped producing
		// suggestions and the only trace was one line in the client log. A systematic failure
		// presenting as "nothing worth trading right now" is the worst shape a bug can take here.
		//
		// Its only consumer was the sell-price grid collapse in Scorer, now removed: the prediction
		// replaced a seven-point search with one unbounded number.
		//
		// And it was expensive. Up to thirty items per refresh, every thirty seconds, each becoming a
		// blocking wiki fetch inside the Python service because _predict_single ignores the payload it
		// is sent and re-fetches (audit item 40).
		//
		// A momentum forecast returns through LearnedFillModel's gate, on features that include
		// liquidity, having beaten the analytical answer out of sample.

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
		/** Set when this holding cannot be sold yet because our own buy is still adding to it. */
		private boolean blockedByOwnBuy;

		SellCandidate(Position position, SellDecision decision, int quantity, boolean unconfirmed, boolean inInventory)
		{
			this.position = position;
			this.decision = decision;
			this.quantity = quantity;
			this.unconfirmed = unconfirmed;
			this.inInventory = inInventory;
		}

		/**
		 * The same candidate, wanting out, with its own buy in the way.
		 *
		 * <p>It competes on priority with the ordinary sales rather than short-circuiting them,
		 * because a cut on a collapsing item should outrank banking a winner and the priority
		 * function already says so. What changes is what gets emitted if it wins: stop the buy.
		 */
		SellCandidate blockedByOwnBuy()
		{
			this.blockedByOwnBuy = true;
			return this;
		}

		SellCandidate unblocked()
		{
			this.blockedByOwnBuy = false;
			return this;
		}

		/** 
		 * Inventory items always take absolute priority.
		 * Cutting a losing position is more urgent than banking a winning one. 
		 */
		double priority()
		{
			double base = inInventory ? 1e24 : 0;
			base += decision.getAction() == SellDecision.Action.CUT ? 1e12 : 0;
			return base + decision.getExpectedProfit();
		}
	}
}
