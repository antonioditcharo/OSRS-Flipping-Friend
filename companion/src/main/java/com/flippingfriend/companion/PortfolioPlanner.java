package com.flippingfriend.companion;

import com.flippingfriend.core.AccountSnapshot;
import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.core.PlanDiagnostics;
import com.flippingfriend.core.PortfolioAlert;
import com.flippingfriend.core.PortfolioAllocation;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.core.PortfolioConstraints;
import com.flippingfriend.core.PortfolioOptimizer;
import com.flippingfriend.core.PortfolioPlan;
import com.flippingfriend.model.RiskAppetite;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Decides what the eight slots should be doing, subject to everything that could stop them.
 * <p>
 * The order of the checks below is deliberate. Refusals that make the whole plan void — stale data,
 * a tripped drawdown breaker — are answered before any market work is done, because producing a
 * beautifully optimised plan and then discarding it wastes a cycle and, worse, risks it being shown.
 */
final class PortfolioPlanner
{
	/** Beyond this the prices a plan was built from are no longer worth acting on. */
	private static final long MAX_MARKET_AGE_SECONDS = 180;
	/**
	 * The session drawdown circuit breaker. At this fraction of session-start equity the plan stops
	 * opening new positions; it does not force liquidation, because forcing a sale into whatever
	 * caused the drawdown is usually how a bad session becomes a ruinous one.
	 */
	static final double DRAWDOWN_LIMIT = 0.15;
	/**
	 * How much of a checking cycle an offer is expected to need. Matches {@code TradingHorizon}, which
	 * has used the same figure on the plugin side since before the companion existed.
	 */
	private static final double HABIT_COMFORT_FACTOR = 0.8;
	// Exposure ceilings and leg horizon now come from the risk appetite. They are diversification
	// limits, not a cap on how much of the bankroll may be used: the whole balance is always
	// available, and at the boldest setting the ceilings are lifted entirely so the only limits left
	// are the game's buy limit and what the market will actually absorb.

	private final PortfolioOptimizer optimizer = new PortfolioOptimizer();
	private final CandidateFactory candidates;


	PortfolioPlanner(SeriesCache series)
	{
		this.candidates = new CandidateFactory(series);
	}

	/** Hands the factory the calibration learned from settled offers. */
	void setCalibration(FillCalibration calibration)
	{
		candidates.setCalibration(calibration);
	}

	PortfolioPlan plan(MarketIngestionService.MarketState market, AccountSnapshot account,
		Map<Integer, Integer> buyLimitRemaining, Collection<OfferEvent> activeOffers)
	{
		Instant now = Instant.now();
		long nowSeconds = now.getEpochSecond();
		String correlationId = account.getCorrelationId() == null
			? UUID.randomUUID().toString()
			: account.getCorrelationId();

		if (market.observedAt == 0 || nowSeconds - market.observedAt > MAX_MARKET_AGE_SECONDS)
		{
			return describing(PortfolioPlan.unavailable(correlationId,
				"Market data is stale, so no buy will be suggested until a fresh snapshot arrives.",
				nowSeconds), account);
		}

		long equity = account.getSpendableCoins() + account.getCommittedCoins();
		long lossBudget = (long) (equity * DRAWDOWN_LIMIT) - (long) account.getMarkedSessionDrawdown();
		if (lossBudget <= 0)
		{
			return describing(PortfolioPlan.unavailable(correlationId,
				"Session drawdown has reached 15%. New buys are frozen; selling and collecting are "
					+ "unaffected. The freeze clears when a new session starts.",
				nowSeconds), account);
		}

		if (account.isSellOnly())
		{
			// Before the slot check, because sell-only is the player's own decision and should be
			// reported as such rather than hidden behind whatever else happens to be true.
			return describing(PortfolioPlan.unavailable(correlationId,
				"Sell-only mode: no new positions while you are winding the session down.",
				nowSeconds), account);
		}

		RiskAppetite appetite = RiskAppetite.forName(account.getRiskAppetite());
		candidates.setRiskAppetite(appetite);

		List<PortfolioCandidate> tactics = candidates.build(market, horizonFor(account, appetite),
			buyLimitRemaining, account.getSpendableCoins(), account.isMembers(), now);

		// Roll what is already held up into the same groups the candidates use, so a ceiling counts
		// the book as well as the plan. The plugin can only report per item; the grouping is this
		// side's own rule, so it is applied here rather than sent over the wire.
		Map<String, Long> committedByGroup = new java.util.HashMap<>();
		for (Map.Entry<Integer, Long> held : account.getCommittedByItem().entrySet())
		{
			MarketIngestionService.Item item = market.mapping.get(held.getKey());
			String group = item == null ? "" : com.flippingfriend.model.ItemGroups.groupOf(item.name);
			committedByGroup.merge(group, held.getValue(), Long::sum);
		}

		PortfolioConstraints limits = new PortfolioConstraints(account.getFreeSlots(),
			account.getSpendableCoins(), lossBudget,
			(long) (equity * appetite.getItemExposureLimit()),
			(long) (equity * appetite.getGroupExposureLimit()),
			account.getCommittedByItem(), committedByGroup, account.getMinProfitPerFlip());

		// Drop what the player has already rejected before optimising, not after. Filtering at
		// selection left the plan itself describing trades that would never be offered: the portfolio
		// list showed a blocked item as rank one under the card that had just dismissed it, and the
		// headline gp/slot-hour was an average over trades nobody could take.
		List<PortfolioCandidate> offerable = new java.util.ArrayList<>();
		for (PortfolioCandidate candidate : tactics)
		{
			if (rejected(candidate, account))
			{
				continue;
			}
			offerable.add(candidate);
		}

		// Two rankings over the same candidates, for two different questions.
		//
		// What to offer is decided against the slots actually free, exactly as before -- planning
		// against the whole board would change it, because the optimizer maximises the total subject
		// to shared capital and exposure limits, and the best *trio* can begin with a slightly worse
		// first trade in order to fit two better ones behind it. You place one now and the plan is
		// rebuilt the moment a slot frees, so taking a worse trade today for a combination that may
		// never happen is simply wrong.
		//
		// The board is ranked to be measured, and -- since it is already the full ranked queue --
		// to be shown. It asks "what is the best set this planner can field right now", which has to
		// be the same question every cycle regardless of how busy the account is. That means the
		// whole bankroll as well as the whole slot count: ranking it on free coins alone made it
		// shrink as money went into offers, so the chart still tracked how busy you were. Equity is
		// spendable plus committed, which is the money the planner would have to work with once the
		// current trades come back.
		PortfolioPlan plan = optimizer.optimize(offerable, limits, correlationId, nowSeconds);
		PortfolioPlan board = optimizer.optimize(offerable,
			limits.withSlots(Math.max(1, account.getTotalSlots())).withCoins(equity),
			correlationId, nowSeconds);

		List<PortfolioAlert> alerts = new java.util.ArrayList<>();
		if (activeOffers != null)
		{
			for (OfferEvent offer : activeOffers)
			{
				long offerAge = nowSeconds - offer.getFirstSeenAt();
				if (offerAge > 3600)
				{
					alerts.add(new PortfolioAlert("STALE", offer.getItemId(), offer.getSlot(), "Offer has been open for over an hour."));
				}

				if (market.latest != null && market.latest.has(String.valueOf(offer.getItemId())))
				{
					com.google.gson.JsonElement pricingEl = market.latest.get(String.valueOf(offer.getItemId()));
					if (pricingEl.isJsonObject())
					{
						com.google.gson.JsonObject latestPricing = pricingEl.getAsJsonObject();
						if (offer.isBuying())
						{
							if (latestPricing.has("low"))
							{
								int marketLow = latestPricing.get("low").getAsInt();
								if (marketLow > 0 && offer.getPrice() < marketLow * 0.95)
								{
									alerts.add(new PortfolioAlert("ABORT", offer.getItemId(), offer.getSlot(), "Buy offer is significantly below market price."));
								}
							}
						}
						else
						{
							if (latestPricing.has("high"))
							{
								int marketHigh = latestPricing.get("high").getAsInt();
								if (marketHigh > 0 && offer.getPrice() > marketHigh * 1.05)
								{
									alerts.add(new PortfolioAlert("ABORT", offer.getItemId(), offer.getSlot(), "Sell offer is significantly above market price."));
								}
							}
						}
					}
				}
			}
		}

		plan = new PortfolioPlan(plan.getCorrelationId(), plan.getCreatedAt(), plan.getExpiresAt(),
			plan.getStatus(), plan.getReason(), plan.getExpectedGpPerSlotHour(), plan.getAllocations(),
			alerts, null);

		// Attach the funnel to every plan, not just empty ones: a plan that filled three of eight
		// slots owes the same explanation for the other five as one that filled none.
		long allocated = plan.getAllocations().stream()
			.mapToLong(allocation -> allocation.getCandidate().getCapitalRequired()).sum();
		PlanDiagnostics funnel = candidates
			.lastFunnel(tactics.size(), account.getSpendableCoins())
			.withOutcome(plan.getAllocations().size(), allocated)
			.withAccount(occupiedSlots(account), account.getTotalSlots(), heldCapital(account));

		// slotsFilled keeps meaning what this plan would place. The board is not an allocation and must
		// not inflate the utilisation numbers the Trading tab draws from it.
		return plan.withDiagnostics(funnel)
			.withBoard(board.getExpectedGpPerSlotHour(), board.getAllocations().size(),
				bench(plan.getAllocations(), board.getAllocations()));
	}

	/**
	 * The full queue: what can be placed now, then the rest of the board behind it.
	 * <p>
	 * The allocations go first and keep their order, so the top of the list is always the trade the
	 * card is telling the player to place -- the two are ranked separately and the optimizer is a
	 * knapsack, so the board's own first pick is not always the plan's, and a list whose head
	 * disagreed with the card would be worse than a short one.
	 * <p>
	 * Ranks are reassigned over the merged list. Each side numbered from one, so keeping them would
	 * have produced two number ones.
	 */
	static List<PortfolioAllocation> bench(List<PortfolioAllocation> actionable,
		List<PortfolioAllocation> board)
	{
		List<PortfolioAllocation> queue = new java.util.ArrayList<>();
		java.util.Set<Integer> seen = new java.util.HashSet<>();

		for (PortfolioAllocation allocation : actionable)
		{
			if (allocation == null || allocation.getCandidate() == null)
			{
				continue;
			}
			seen.add(allocation.getCandidate().getItemId());
			queue.add(allocation);
		}
		for (PortfolioAllocation allocation : board)
		{
			if (allocation == null || allocation.getCandidate() == null
				|| !seen.add(allocation.getCandidate().getItemId()))
			{
				continue;
			}
			queue.add(allocation);
		}

		List<PortfolioAllocation> ranked = new java.util.ArrayList<>(queue.size());
		for (int i = 0; i < queue.size(); i++)
		{
			PortfolioAllocation allocation = queue.get(i);
			ranked.add(new PortfolioAllocation(i + 1, allocation.getCandidate(),
				allocation.getAction()));
		}
		return ranked;
	}

	/**
	 * Attaches the account to a plan that could not be made.
	 * <p>
	 * These returns carried nothing at all, so the monitor had no way to chart what the account was
	 * doing during the stretches when it was busiest -- which are, by definition, the stretches these
	 * plans cover. Two thirds of a typical hour went unrecorded that way.
	 */
	private static PortfolioPlan describing(PortfolioPlan plan, AccountSnapshot account)
	{
		return plan.withDiagnostics(new PlanDiagnostics(0, 0, 0, 0, 0, 0, 0,
			account.getSpendableCoins(), java.util.Collections.emptyMap(),
			java.util.Collections.emptyMap(),
			occupiedSlots(account), account.getTotalSlots(), heldCapital(account)));
	}

	private static int occupiedSlots(AccountSnapshot account)
	{
		return Math.max(0, account.getTotalSlots() - account.getFreeSlots());
	}

	/**
	 * What the account currently has tied up in offers and holdings.
	 * <p>
	 * Summed from the per-item breakdown, which is the broader of the two measures the snapshot
	 * carries and the one the exposure ceilings are already computed from, so this agrees with the
	 * limits actually being enforced. {@code getCommittedCoins()} counts only coins locked in open
	 * <em>buy</em> offers -- correctly zero when the position has been bought and is sitting as
	 * inventory, or when the open offers are all sells -- so it is the floor here rather than the
	 * figure. Measured against the offer events, it agrees with what was outstanding in 616 of 617
	 * snapshots, the exception being a snapshot taken in the same tick an offer was placed.
	 */
	private static long heldCapital(AccountSnapshot account)
	{
		long held = 0;
		for (long value : account.getCommittedByItem().values())
		{
			held += value;
		}
		return Math.max(held, account.getCommittedCoins());
	}

	/** Whether the player has put this item out of bounds, by any of the three means available. */
	static boolean rejected(PortfolioCandidate candidate, AccountSnapshot account)
	{
		if (account.getSkippedItems().contains(candidate.getItemId())
			|| account.getItemsOnOffer().contains(candidate.getItemId()))
		{
			return true;
		}
		String name = candidate.getItemName();
		return name != null
			&& account.getBlockedItems().contains(name.toLowerCase(java.util.Locale.ROOT));
	}

	/**
	 * How long one leg is given to fill.
	 * <p>
	 * The risk appetite sets the floor and the player's habits can only lengthen it: someone who
	 * leaves offers for three hours should be given a bigger order than someone stood at the Exchange,
	 * because an order that completes while they are away holds its slot doing nothing until they come
	 * back. Checking more often does not shorten it -- being present does not make the market fill any
	 * faster, and a shorter window would only mean smaller orders.
	 * <p>
	 * This mirrors {@code TradingHorizon.legHorizonHours()} on the plugin side, which is where the
	 * setting has always been honoured. Nothing carried it across the wire, so on the path that
	 * actually chooses trades the dropdown did nothing at all.
	 */
	static double horizonFor(AccountSnapshot account, RiskAppetite appetite)
	{
		double fromHabit = account.getCheckIntervalMinutes() * HABIT_COMFORT_FACTOR / 60.0;
		return Math.max(appetite.getHorizonHours(), fromHabit);
	}



	/** What the screen wants history for, so the warmer can fetch it between planning cycles. */
	List<Integer> shortlistIds()
	{
		return candidates.shortlistIds();
	}

}
