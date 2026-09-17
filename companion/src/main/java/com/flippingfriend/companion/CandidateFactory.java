package com.flippingfriend.companion;

import com.flippingfriend.core.PlanDiagnostics;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.data.Candle;
import com.flippingfriend.data.ItemMetadata;
import com.flippingfriend.data.LatestPrice;

import com.flippingfriend.model.FeatureEngine;
import com.flippingfriend.model.FillCurve;
import com.flippingfriend.model.FillEstimate;
import com.flippingfriend.model.FillModel;
import com.flippingfriend.model.FilterResult;
import com.flippingfriend.model.ItemFeatures;
import com.flippingfriend.model.ItemGroups;
import com.flippingfriend.model.ManipulationFilter;
import com.flippingfriend.model.MarketContext;
import com.flippingfriend.model.PriceAnchor;
import com.flippingfriend.model.RiskAppetite;
import com.flippingfriend.model.TaxCalculator;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.Map;

/**
 * Turns raw market data into vetted, fully priced tactics for the optimizer to choose between.
 * <p>
 * Two things distinguish this from simply ranking spreads.
 * <p>
 * <b>Several tactics per item, not one.</b> The obvious trade — buy at the quoted low, sell at the
 * quoted high — is the slowest-filling version of itself, because everyone else is queued at exactly
 * those prices. Paying a little more, or asking a little less, trades margin for speed. Since the
 * objective is profit per <em>slot-hour</em> rather than profit per trade, the best price is usually
 * not the best margin, and the only way to find out is to offer the optimizer the alternatives and
 * let the portfolio decide. Order size is varied for the same reason: a smaller slice of a busy item
 * can be worth more per slot-hour than a full buy limit that takes all day.
 * <p>
 * <b>Vetoes come first, and they are about truth rather than profit.</b> An item is screened for
 * whether its price can be believed at all — manipulated quotes, dead items, stale sides of the
 * book, a level that has just shifted under a game update — before anything asks whether it is
 * lucrative. This reuses the filters already proven in the plugin rather than reimplementing them,
 * because a second implementation is a second thing to get subtly wrong.
 */
final class CandidateFactory
{
	/** Live planning: five-minute bars for behaviour, hourly bars for regime and seasonality. */
	static final String LIVE_SHORT_STEP = "5m";
	static final String LIVE_LONG_STEP = "1h";
	static final int LIVE_BUCKET_SECONDS = 300;

	/**
	 * Items given full analysis each cycle.
	 * <p>
	 * This was forty-five for a bad reason and is ninety for a good one. The old number was a latency
	 * budget: deep analysis fetched history inline, so every extra item lengthened the planning cycle
	 * and the breadth of the search was decided by how long a cycle could take. Moving fetching to a
	 * background warmer removed that constraint entirely.
	 * <p>
	 * The obvious next move was to raise it as far as possible, and held-out replay says that is
	 * wrong. Sixty and ninety items both clear every promotion gate comfortably. Three hundred fails
	 * five of nine — it wins one fold in three, breaches the drawdown limit, and profit calibration
	 * falls from 0.79x to 0.51x. The items admitted by widening are progressively thinner-traded, the
	 * model understands them worst, and a larger pool mostly gives the ranking more chances to pick
	 * whatever it has most overestimated. Breadth is only an advantage over candidates the model can
	 * actually judge.
	 */
	private static final int DEEP_ANALYSIS_LIMIT = 90;
	/**
	 * Liquidity floors, in units and in gold.
	 * <p>
	 * <b>Units alone was a serious mistake.</b> Requiring sixty trades an hour sounds like a
	 * reasonable liquidity bar and is in fact a bar on <em>price</em>: it throws out 1,821 of the
	 * 3,088 quoted items, and among them 285 that move over five million gp an hour. Dragon claws
	 * trade forty-four times an hour at thirty-seven million each — one and a half billion gp of
	 * hourly liquidity — and failed a sixty-unit test that a stack of bananas passes comfortably.
	 * <p>
	 * The consequence was not subtle. Every item cheap enough to trade in bulk survived and every
	 * item valuable enough to absorb real capital did not, so a hundred-million bankroll was being
	 * offered nothing but four-hundred-gp javelin tips and could never deploy more than a fraction of
	 * itself. That was mistaken for a limit of the market when it was a limit of this constant.
	 * <p>
	 * An item is liquid enough if it moves meaningful <em>value</em> or meaningful <em>volume</em>.
	 * The unit floor stays as a sanity check — something trading twice a day is untradeable whatever
	 * it costs — but it is now a floor rather than the whole test.
	 */
	private static final int MIN_HOURLY_VOLUME = 60;
	/** Or this much gold changing hands per hour, which is what liquidity means to a large bankroll. */
	private static final long MIN_HOURLY_TURNOVER = 1_000_000;
	/** Below this an item is too rarely traded to plan around, however valuable each unit is. */
	private static final int ABSOLUTE_MIN_HOURLY_VOLUME = 6;

	/**
	 * Price steps and patience now come from the risk appetite rather than being fixed.
	 * <p>
	 * Paying over the quote is the difference between sitting at a price a few units cross at and
	 * reaching the depth behind it, which is what allows a large bankroll to be deployed at all.
	 */
	private volatile RiskAppetite appetite = RiskAppetite.BALANCED;
	/**
	 * "Learn from my trades", inverted. Turning it off left the companion learning regardless, which
	 * made the setting a statement about the engine that no longer chooses anything.
	 */
	private volatile boolean learningDisabled;

	void setLearningDisabled(boolean disabled)
	{
		this.learningDisabled = disabled;
	}

	/** Tactics kept per item; more than this floods the optimizer with near-duplicates. */
	private static final int TACTICS_PER_ITEM = 3;

	/**
	 * Where a genuine worst case sits below entry. This feeds the session loss budget only, never
	 * the expected cost of a flip: most flips that fail simply never fill, and cost nothing.
	 * <p>
	 * It comes from the risk appetite, because that is the depth at which the position is actually
	 * cut. A flat 5% here meant the cautious setting budgeted two and a half times the loss it would
	 * really take -- under-filling slots to protect against something that could not happen -- and the
	 * bold setting budgeted under half of it, arming the drawdown breaker far later than the setting
	 * implies.
	 */
	private double stopDistance()
	{
		return appetite == null ? 0.05 : appetite.getLossCutPct();
	}
	/** Share of the full Kelly stake actually taken, against probabilities that are estimates. */
	private static final double KELLY_SHARE = 0.35;

	/**
	 * Multiples of the fractional-Kelly order offered to the optimizer, smallest first.
	 * <p>
	 * Spans from half that order up to whatever the market, the buy limit and the coins on hand
	 * allow, so the search can find the size where profit per slot-hour actually peaks instead of
	 * being handed one point on that curve. One of these is 1.0, so the old behaviour is still on
	 * the menu and wins whenever it deserves to.
	 */
	private static final double[] SIZE_GRID = { 0.5, 1.0, 2.0, 4.0, 8.0 };
	/**
	 * Smallest share of the available flow ever ordered.
	 * <p>
	 * A floor rather than zero because the model being pessimistic about one item is not a reason to
	 * sit out entirely -- but a fraction resting on this floor is the model saying it does not like
	 * the trade, and a whole plan resting on it means something upstream is wrong.
	 */
	private static final double MIN_KELLY_FRACTION = 0.1;

	/** Floor on assumed drift, so an item that is flat right now is not treated as risk-free. */
	private static final double MIN_DRIFT_FRACTION = 0.002;

	/**
	 * How much of one standard deviation of price movement to charge against a stranded position.
	 *
	 * A position strands because the sell leg did not clear inside the horizon, which is mostly a
	 * queue-position and volume story rather than a price story -- but not purely, so some adverse
	 * movement belongs in the expectation. A quarter is a judgement, not a measurement; it is the
	 * figure the walk-forward gates are run against.
	 */
	private static final double ADVERSE_DRIFT_SHARE = 0.25;

	/**
	 * The ceiling the learner already puts on this feature in FlipFeatures. Below a few gp, integer
	 * price granularity dominates the log-return standard deviation -- a water rune's mid can only
	 * be 5, 5.5 or 6, which reads as a 7% move every five minutes -- so the raw figure stops meaning
	 * volatility and starts meaning "this item is cheap". Two consumers of one feature disagreeing
	 * about whether it is trustworthy is how that got missed.
	 */
	private static final double MAX_USABLE_VOLATILITY = 0.2;
	/** How long a plan's prices stay valid. */
	private static final long PLAN_TTL_SECONDS = 150;

	private final TaxCalculator tax;
	private final FeatureEngine featureEngine = new FeatureEngine();
	private final FillModel fillModel;
	private final ManipulationFilter filter = new ManipulationFilter();
	private final SeriesSource series;

	/**
	 * Everything learned so far; never null, and neutral until something has been learned.
	 * <p>
	 * This javadoc outlived its field. The comment sat here on its own, describing a learned model
	 * that had been deleted, while the line further down marked "where everything that has been
	 * learned re-enters the decision" read four values straight off the raw fill model. Meanwhile the
	 * recorder kept writing every settled offer to {@code execution_stat} and
	 * {@code SqliteStore.executionStats()} sat there with no callers -- so the account had 1,386
	 * observations of how wrong the timings were and not one of them reached a decision.
	 */
	private volatile FillCalibration calibration = FillCalibration.NEUTRAL;

	/** Replaces what has been learned. Called when the recorder's totals are re-read. */
	void setCalibration(FillCalibration updated)
	{
		this.calibration = updated == null ? FillCalibration.NEUTRAL : updated;
	}

	/** What is currently being applied, for the health line and for tests. */
	FillCalibration getCalibration()
	{
		return calibration;
	}

	/**
	 * Which resolutions this factory reads, and how long one bar covers.
	 * <p>
	 * These have to travel together. The feature engine is told a bucket length, the fill curve is
	 * built from bars of that length, and volatility is scaled to a holding period by counting
	 * buckets per hour — so three separate places must agree about what a bar is. Hardcoding
	 * five minutes made them agree by luck, and silently produced nothing at all when a caller
	 * supplied hourly history instead: every item was vetoed for having no price history, because
	 * the factory was asking for a resolution that had not been loaded.
	 */
	private final String shortStep;
	private final String longStep;
	private final int bucketSeconds;

	/** Whether the account may trade members-only items; false for free-to-play. */
	private volatile boolean membersAccount = true;

	void setRiskAppetite(RiskAppetite appetite)
	{
		RiskAppetite chosen = appetite == null ? RiskAppetite.BALANCED : appetite;
		if (chosen.getCaptureShare() != this.appetite.getCaptureShare() || riskModel == null)
		{
			// The capture share lives inside the fill model, so changing appetite means a new model —
			// derived from the configured one rather than built from defaults. Constructing a fresh
			// model here discarded every other setting the factory was given, which made two arms of
			// an experiment run identically while reporting as different.
			riskModel = fillModel.withCaptureRate(chosen.getCaptureShare());
		}
		this.appetite = chosen;
	}

	/** Fill model reflecting the current appetite; falls back to the injected one before any is set. */
	private FillModel fillModel()
	{
		FillModel model = riskModel;
		return model == null ? fillModel : model;
	}

	private volatile FillModel riskModel;

	/** Working from the most recent sizing decision, attached to the candidate it produced. */

	/** Names shown per veto reason. Three is enough to spot-check a filter, few enough to read. */
	private static final int VETO_EXAMPLES_PER_REASON = 3;

	private final Map<Integer, String> lastVeto = new ConcurrentHashMap<>();
	/** Names of the items behind those reasons, so the panel can say which ones rather than only how many. */
	private final Map<Integer, String> vetoedNames = new ConcurrentHashMap<>();

	/**
	 * Records why one item was turned away.
	 * <p>
	 * The name is kept alongside the reason because a count on its own is the wrong half of the
	 * answer. "145 items rejected: too little value changes hands" tells a player nothing they can
	 * check; naming three of them lets them look at those items and decide whether the filter is
	 * right.
	 */
	private void veto(int itemId, String itemName, String reason)
	{
		lastVeto.put(itemId, reason);
		vetoedNames.put(itemId, itemName == null ? "" : itemName);
	}
	/** Why one item was turned away on the last build, or null if it was not. Diagnostics only. */
	String vetoFor(int itemId)
	{
		return lastVeto.get(itemId);
	}

	private boolean exemptionsResolved;

	/** Items the screen wants history for, published so the warmer knows what to fetch next. */
	private volatile List<Integer> shortlistIds = new ArrayList<>();

	/** Funnel counters for the most recent build, so an empty plan can account for itself. */
	private final AtomicInteger itemsInFeed = new AtomicInteger();
	private final AtomicInteger itemsQuoted = new AtomicInteger();
	private final AtomicInteger itemsShortlisted = new AtomicInteger();
	private final AtomicInteger itemsAnalysed = new AtomicInteger();

	/** Live planning, at the resolutions the wiki feed provides. */
	CandidateFactory(SeriesSource series)
	{
		this(series, new TaxCalculator(), LIVE_SHORT_STEP, LIVE_LONG_STEP, LIVE_BUCKET_SECONDS);
	}

	CandidateFactory(SeriesSource series, TaxCalculator tax, String shortStep, String longStep,
		int bucketSeconds)
	{
		this(series, tax, shortStep, longStep, bucketSeconds, new FillModel());
	}

	CandidateFactory(SeriesSource series, TaxCalculator tax, String shortStep, String longStep,
		int bucketSeconds, FillModel fillModel)
	{
		this.fillModel = fillModel;
		this.series = series;
		this.tax = tax;
		this.shortStep = shortStep;
		this.longStep = longStep;
		this.bucketSeconds = bucketSeconds;
	}

	/**
	 * @param horizonHours how long one leg of a flip is allowed to take, which sets order size
	 */
	List<PortfolioCandidate> build(MarketIngestionService.MarketState market, double horizonHours,
		Map<Integer, Integer> buyLimitRemaining, long spendableCoins, boolean members, Instant now)
	{
		resolveExemptions(market.mapping.values());
		return build(quotedUniverse(market), horizonHours, buyLimitRemaining, spendableCoins,
			members, now);
	}

	/**
	 * The shared decision path, over a universe of items that already have quotes attached.
	 * <p>
	 * Live planning reaches this by unpacking the wiki's JSON; replay reaches it by reading bars out
	 * of stored history. Everything from here down — the screen, the vetoes, the fill estimates, the
	 * tactics — is identical for both, which is the only way a backtest measures the strategy that
	 * actually runs rather than a second copy of it.
	 */
	List<PortfolioCandidate> build(Collection<QuotedItem> universe, double horizonHours,
		Map<Integer, Integer> buyLimitRemaining, long spendableCoins, Instant now)
	{
		return build(universe, horizonHours, buyLimitRemaining, spendableCoins, true, now);
	}

	/**
	 * @param members false for a free-to-play account, which cannot trade members-only items at all
	 */
	List<PortfolioCandidate> build(Collection<QuotedItem> universe, double horizonHours,
		Map<Integer, Integer> buyLimitRemaining, long spendableCoins, boolean members, Instant now)
	{
		this.membersAccount = members;
		lastVeto.clear();
		vetoedNames.clear();
		itemsInFeed.set(universe.size());
		itemsQuoted.set(0);
		itemsAnalysed.set(0);

		List<Screened> shortlist = screen(universe, buyLimitRemaining, spendableCoins, horizonHours);
		itemsShortlisted.set(shortlist.size());

		// Published in screen order, so the warmer spends its budget on the most promising items
		// first and the shortlist becomes usable from the top down rather than all at once.
		List<Integer> ids = new ArrayList<>(shortlist.size());
		for (Screened screened : shortlist)
		{
			ids.add(screened.item.id);
		}
		shortlistIds = ids;


		itemsAnalysed.set(shortlist.size());
		List<PortfolioCandidate> candidates = shortlist.parallelStream()
			.flatMap(screened -> tacticsFor(screened, horizonHours, spendableCoins, now).stream())
			.collect(java.util.stream.Collectors.toList());

		return candidates;
	}

	/** Unpacks the live feed's JSON into the quoted universe the shared path consumes. */
	private List<QuotedItem> quotedUniverse(MarketIngestionService.MarketState market)
	{
		List<QuotedItem> universe = new ArrayList<>(market.mapping.size());
		for (Map.Entry<Integer, MarketIngestionService.Item> entry : market.mapping.entrySet())
		{
			int itemId = entry.getKey();
			LatestPrice quote = quote(market.latest, itemId);
			Candle bar = bar(market.fiveMinute, itemId);
			if (quote == null || bar == null)
			{
				continue;
			}
			universe.add(new QuotedItem(entry.getValue(), quote, bar, bar(market.hourly, itemId)));
		}
		return universe;
	}


	/** What the screen would like history for, most promising first. */
	List<Integer> shortlistIds()
	{
		return shortlistIds;
	}

	/**
	 * The funnel from the last build. Capital figures are left at zero here because only the
	 * optimizer knows what was finally allocated.
	 */
	PlanDiagnostics lastFunnel(int tacticsGenerated, long capitalAvailable)
	{
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (String reason : lastVeto.values())
		{
			counts.merge(reason, 1, Integer::sum);
		}
		Map<String, Integer> ordered = new LinkedHashMap<>();
		counts.entrySet().stream()
			.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
			.forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));

		// A handful of names per reason. Enough to check the filter's work against the actual Grand
		// Exchange, few enough that the panel stays readable and the plan stays small on the wire.
		Map<String, List<String>> examples = new LinkedHashMap<>();
		for (String reason : ordered.keySet())
		{
			List<String> names = new ArrayList<>();
			for (Map.Entry<Integer, String> entry : lastVeto.entrySet())
			{
				if (!reason.equals(entry.getValue()))
				{
					continue;
				}
				String name = vetoedNames.get(entry.getKey());
				if (name != null && !name.isEmpty())
				{
					names.add(name);
				}
				if (names.size() >= VETO_EXAMPLES_PER_REASON)
				{
					break;
				}
			}
			examples.put(reason, names);
		}

		return new PlanDiagnostics(itemsInFeed.get(), itemsQuoted.get(), itemsShortlisted.get(), itemsAnalysed.get(),
			tacticsGenerated, 0, 0, capitalAvailable, ordered, examples);
	}


	/**
	 * The exact tax rules need the exempt-item list, which is only knowable once the mapping is
	 * loaded. Bonds, tools, low-level food and teleport tablets are never taxed, and treating them
	 * as taxed understates their profit by 2% — more than the entire margin on many of them.
	 */
	void resolveExemptions(Collection<MarketIngestionService.Item> mapping)
	{
		if (exemptionsResolved || mapping.isEmpty())
		{
			return;
		}
		List<ItemMetadata> items = new ArrayList<>(mapping.size());
		for (MarketIngestionService.Item item : mapping)
		{
			items.add(new ItemMetadata(item.id, item.name, false, item.buyLimit, 0));
		}
		tax.resolveExemptions(items);
		exemptionsResolved = true;
	}

	/**
	 * The cheap pass over every item. Everything here is a lookup or a multiplication, so it can run
	 * across the whole feed without noticeable cost, and it ranks by what a slot could actually
	 * produce rather than by margin — an item with a huge buy limit that nobody trades is worth
	 * nothing at all.
	 */
	private List<Screened> screen(Collection<QuotedItem> universe,
		Map<Integer, Integer> buyLimitRemaining, long spendableCoins, double horizonHours)
	{
		List<Screened> shortlist = new ArrayList<>();

		for (QuotedItem quoted : universe)
		{
			int itemId = quoted.item.id;
			MarketIngestionService.Item item = quoted.item;
			Candle bar = quoted.fiveMinute;

			// A free account cannot place an offer on a members item at all, so recommending one is
			// not a marginal call the optimizer should weigh — it is an instruction the player
			// physically cannot carry out.
			if (item.members && !membersAccount)
			{
				continue;
			}

			// Believe the volume-weighted average over a single transaction when they disagree: one
			// misclick should not put an item on the shortlist.
			LatestPrice anchored = PriceAnchor.anchor(quoted.quote, bar);
			if (anchored == null || !anchored.isComplete())
			{
				continue;
			}
			itemsQuoted.incrementAndGet();

			int buy = anchored.getLow();
			int sell = anchored.getHigh();
			if (buy <= 0 || sell <= buy || buy > spendableCoins)
			{
				continue;
			}

			long marginPerItem = tax.netMarginPerItem(itemId, buy, sell);
			if (marginPerItem <= 0)
			{
				veto(itemId, item.name, "Spread does not cover Grand Exchange tax.");
				continue;
			}

			// Judge liquidity on the hourly bar when there is one. Five minutes multiplied by twelve
			// turns any single large trade into a phantom torrent, and the shortlist is a budget: an
			// item that ranks high on a burst is one that a genuinely busy item did not get.
			Candle hourBar = quoted.hourly;
			int hourlyVolume = hourBar != null ? hourBar.getTotalVolume() : bar.getTotalVolume() * 12;
			long hourlyTurnover = (long) hourlyVolume * buy;
			boolean liquidEnough = hourlyVolume >= ABSOLUTE_MIN_HOURLY_VOLUME
				&& (hourlyVolume >= MIN_HOURLY_VOLUME || hourlyTurnover >= MIN_HOURLY_TURNOVER);
			if (!liquidEnough)
			{
				veto(itemId, item.name, hourlyVolume < ABSOLUTE_MIN_HOURLY_VOLUME
					? "Barely trades at all."
					: "Too little value changes hands per hour to rely on.");
				continue;
			}

			int remaining = buyLimitRemaining.getOrDefault(itemId, item.buyLimit);
			if (remaining <= 0)
			{
				veto(itemId, item.name, "Four-hour buy limit is spent.");
				continue;
			}

			// Reachable flow is a rate, so it only becomes a quantity once multiplied by the time
			// we are willing to wait. Leaving the horizon out compared units against units-per-hour
			// and implicitly assumed one hour, which ranked volume-bound items at a fraction of
			// their real throughput against items bound by the buy limit or by coins. The capture
			// share comes from the appetite for the same reason: it moved there, and this copy kept
			// the old fixed value.
			double reachable = hourlyVolume * appetite.getCaptureShare() * horizonHours;
			int fillable = (int) Math.min(Math.min(remaining, spendableCoins / buy),
				Math.max(1, reachable));
			// A valuable item that trades a handful of times an hour still deserves a slot: one unit
			// of it can carry more capital than a full buy limit of something cheap.
			if (fillable <= 0)
			{
				continue;
			}

			shortlist.add(new Screened(item, anchored, remaining, fillable, hourlyVolume,
				marginPerItem * fillable));
		}

		shortlist.sort(Comparator.comparingLong((Screened s) -> s.throughput).reversed());
		return shortlist.size() > DEEP_ANALYSIS_LIMIT
			? new ArrayList<>(shortlist.subList(0, DEEP_ANALYSIS_LIMIT))
			: shortlist;
	}

	/** The expensive pass: real history, real vetoes, real fill estimates. */
	private List<PortfolioCandidate> tacticsFor(Screened screened, double horizonHours,
		long spendableCoins, Instant now)
	{
		int itemId = screened.item.id;
		List<Candle> shortSeries = series.series(itemId, shortStep);
		if (shortSeries.isEmpty())
		{
			veto(itemId, screened.item.name, "Waiting for price history.");
			return java.util.Collections.emptyList();
		}

		ItemFeatures features = featureEngine.compute(itemId, shortSeries, bucketSeconds);
		MarketContext context = MarketContext.from(series.series(itemId, longStep),
			screened.price.getLow());

		ItemMetadata metadata = new ItemMetadata(itemId, screened.item.name, false,
			screened.item.buyLimit, 0);
		FilterResult verdict = filter.screen(metadata, screened.price, features, context,
			appetite.getVetoes(), true, now);
		if (!verdict.isAccepted())
		{
			veto(itemId, screened.item.name, verdict.getReason());
			return java.util.Collections.emptyList();
		}

		FillCurve curve = FillCurve.overRecentHistory(shortSeries);
		if (curve.isEmpty())
		{
			veto(itemId, screened.item.name, "Not enough traded history to estimate fills.");
			return java.util.Collections.emptyList();
		}

		double season = context.isUsable()
			? context.liquidityMultiplier(now.atZone(ZoneOffset.UTC).getHour())
			: 1.0;
		String group = ItemGroups.groupOf(screened.item.name);
		long expiresAt = now.getEpochSecond() + PLAN_TTL_SECONDS;

		List<PortfolioCandidate> tactics = new ArrayList<>();
		boolean unfillable = false;

		for (double buyOffset : appetite.getBuyOffsets())
		{
			int buyPrice = shift(screened.price.getLow(), buyOffset);
			for (double sellOffset : appetite.getSellOffsets())
			{
				int sellPrice = shift(screened.price.getHigh(), sellOffset);
				if (sellPrice <= buyPrice)
				{
					continue;
				}

				long marginPerItem = tax.netMarginPerItem(itemId, buyPrice, sellPrice);
				if (marginPerItem <= 0)
				{
					continue;
				}

				// Size the order to what can actually cross at these two prices within the horizon.
				//
				// The cheap screen had to guess from a five-minute bar multiplied by twelve, which
				// one large trade turns into a phantom torrent, and even the item's true overall
				// volume is the wrong number: what matters is the flow at the price we are quoting.
				// Both legs must clear, so the slower side sets the size.
				int fillable = quotableQuantity(curve, buyPrice, sellPrice, season, screened,
					spendableCoins, horizonHours);
				if (fillable <= 0)
				{
					// Almost always one leg, not both, and worth naming separately from a thin
					// margin. An order is sized against the time left after the wait for a first
					// counterparty, so once that wait reaches the horizon there is no time left to
					// fill in and the size is zero -- which drops the item even when it is trading
					// heavily. Measured live at the quoted bid and ask, Avantoe potion (unf) and
					// Grimy guam leaf both went to zero this way on five and nineteen thousand units
					// an hour of real volume.
					unfillable = true;
					continue;
				}

				// Fractional Kelly Sizing
				long netProfitFull = marginPerItem * fillable;
				long unwindLossFull = unwindCost(itemId, buyPrice, screened.price.getLow(), fillable, features, horizonHours);
				
				double pBuy = fillModel().estimateBuy(curve, buyPrice, fillable, horizonHours, season)
					.getProbability();
				double pSell = fillModel().estimateSell(curve, sellPrice, fillable, horizonHours, season)
					.getProbability();
				double p = pBuy * pSell;
				
				double b = unwindLossFull > 0 ? (double) netProfitFull / unwindLossFull : netProfitFull;

				// Offer the optimizer a choice of size, rather than deciding it here and telling it.
				//
				// Every price pair used to produce exactly one candidate, at one quantity, chosen by
				// a fixed fraction -- so the search ranged over prices and never over size. That is
				// the one dimension it should range over, because the objective it already maximises
				// is expected profit per hour of slot occupancy, and size is precisely the knob that
				// trades those two against each other: profit grows with the order, while the time
				// it ties a slot up grows more slowly, because a large part of the wait is waiting
				// for the first counterparty at all and that does not depend on size. Whether the
				// extra units are worth the extra hours is the question the objective exists to
				// answer, and it was being answered by a constant.
				//
				// Measured live, every order came out between 0.3% and 4.3% of the buy limit the
				// market would have taken, on a 128m account with seven of eight slots busy. Two
				// separate haircuts produce that: the capture share, which is already inside the
				// fill model's throughput, and then this fraction on top of it. The second one is
				// also the wrong shape -- a Kelly fraction says what share of a <em>bankroll</em> to
				// stake, and it is being applied to a quantity the market can absorb, which the buy
				// limit, the coins on hand and the optimizer's own exposure ceilings already bound.
				//
				// The fraction stays, as the middle of the grid: it is a reasonable guess and the
				// point is not to overrule it but to stop it being the only offer. Sizes that cost
				// more in fill probability than they earn in margin score worse and are dropped by
				// the sort below, which is the same gate every other tactic passes through.
				double kelly = kellyFraction(p, b);
				int lastQuantity = 0;
				for (double share : SIZE_GRID)
				{
					int quantity = (int) Math.max(1,
						Math.min(fillable, Math.round(fillable * kelly * share)));
					// Once the grid is against a ceiling -- the buy limit, the coins, or what the
					// market will absorb -- every larger share clamps to the same order. Emitting it
					// again would put identical candidates in front of the optimizer, which is pure
					// branching for no added choice, and the same reason the list is trimmed below.
					if (quantity == lastQuantity)
					{
						continue;
					}
					lastQuantity = quantity;

				FillEstimate buyFill = fillModel().estimateBuy(curve, buyPrice, quantity,
					horizonHours, season);
				FillEstimate sellFill = fillModel().estimateSell(curve, sellPrice, quantity,
					horizonHours, season);
					

				if (!buyFill.isPlausible() || !sellFill.isPlausible())
				{
					continue;
				}

				long netProfit = marginPerItem * quantity;
				long worstLoss = Math.max(1, (long) (buyPrice * stopDistance() * quantity));
				long unwindLoss = unwindCost(itemId, buyPrice, screened.price.getLow(),
					quantity, features, horizonHours);

				// Where everything that has been learned re-enters the decision -- which, until this
				// line was written, it did not. See the calibration field.
				//
				// Only the waiting is corrected. A predicted duration is a wait for the first
				// counterparty plus the time to work through the size ordered, and every observation
				// behind the multiplier was recorded while sizing was broken and orders were a single
				// unit. The throughput half of that history describes a market absorbing one item at
				// a time; the wait half is how long it takes anybody to turn up at the price, which
				// is the same whether the order is for one or two thousand.
				FillCalibration learned = learningDisabled ? FillCalibration.NEUTRAL : calibration;
				double buyHours = correctedHours(buyFill, learned.waitMultiplier(itemId));
				double sellHours = correctedHours(sellFill, learned.waitMultiplier(itemId));
				double buyProbability = buyFill.getProbability();
				double sellProbability = sellFill.getProbability();
				double displayBuyProbability = buyProbability;

				tactics.add(new PortfolioCandidate(itemId, screened.item.name, group,
					buyPrice, buyPrice, buyPrice, sellPrice, sellPrice, sellPrice,
					quantity, screened.buyLimitRemaining, netProfit, worstLoss,
					unwindLoss, buyProbability, sellProbability,
					buyHours, sellHours, horizonHours, expiresAt)
					.withDisplayProbability(displayBuyProbability));
				}
			}
		}

		// Keep only the strongest few. Near-duplicate tactics add branching to the optimizer's
		// search without adding any real choice.
		tactics.sort(Comparator.comparingDouble(PortfolioCandidate::expectedGpPerSlotHour).reversed());
		if (tactics.isEmpty())
		{
			// Two different answers, and telling them apart is the difference between a filter a
			// player can check and one they cannot. "Not expected to profit" is about the margin;
			// this other one is about the clock, and it lands on items whose margin is fine.
			veto(itemId, screened.item.name, unfillable
				? "Not expected to find a buyer or a seller for this within the time a flip is "
					+ "given. A longer \"how long a flip should take\" would let it through."
				: "No price and size combination is expected to profit.");
		}
		return tactics.size() > TACTICS_PER_ITEM ? tactics.subList(0, TACTICS_PER_ITEM) : tactics;
	}

	/**
	 * How many units are worth ordering: what can cross at both quoted prices inside the horizon,
	 * capped by the remaining buy limit and by what the player can actually pay for.
	 * <p>
	 * Only a fraction of the flow at a price is reachable — the rest belongs to everyone else queued
	 * there — and that discount is already inside the throughput figure.
	 */
	private int quotableQuantity(FillCurve curve, int buyPrice, int sellPrice, double season,
		Screened screened, long spendableCoins, double horizonHours)
	{
		FillEstimate buySide = fillModel().estimateBuy(curve, buyPrice, 1, horizonHours, season);
		FillEstimate sellSide = fillModel().estimateSell(curve, sellPrice, 1, horizonHours, season);

		// Size against the complete horizon. Reducing the horizon by expected counterparty wait
		// was measured on identical folds and reduced returns by 13.8%, because partial fills still
		// sell while consistently smaller orders give up more profit than occasional unwinds cost.
		double buyReach = buySide.getUnitsPerHour() * horizonHours;
		double sellReach = sellSide.getUnitsPerHour() * horizonHours;
		double reachable = Math.min(buyReach, sellReach);
		long affordable = spendableCoins / Math.max(1, buyPrice);
		long fullLimitCost = (long) buyPrice * screened.buyLimitRemaining;
		boolean capitalAbundant = spendableCoins >= fullLimitCost * 4.0;

		long byCapital = affordable;
		if (!capitalAbundant)
		{
			long capitalCeiling = (long) (spendableCoins * this.appetite.getItemExposureLimit());
			byCapital = (long) (capitalCeiling / Math.max(1, buyPrice));
		}

		long capped = Math.min(Math.min(screened.buyLimitRemaining, byCapital), (long) reachable);

		// A four-branch note naming which ceiling decided the size used to be recorded here, along
		// with both throughputs, and carried on every candidate. Nothing ever read them -- not the
		// panel, not the monitor, not the companion's own reader of stored plans -- and this runs once
		// per size fraction, per price, per item.
		return (int) Math.max(0, Math.min(Integer.MAX_VALUE, capped));
	}

	/**
	 * The share of what the market could absorb that is actually ordered.
	 * <p>
	 * Fractional Kelly: the full stake maximises long-run growth only if the probabilities are
	 * right, and these are estimates from noisy data, so a third of it is taken.
	 * <p>
	 *
	 * @param p the chance both legs complete
	 * @param b what the trade makes against what unwinding it would cost
	 */
	static double kellyFraction(double p, double b)
	{
		if (b <= 0 || p <= 0)
		{
			// No odds to compute a stake from. The floor, not everything: this used to default to
			// 1.0, which is the largest order the market will take, handed to the one case where
			// the arithmetic could say nothing at all about whether the trade was any good. The
			// screen guarantees a positive margin before this is reached, so nothing live took that
			// branch -- but it is not a default worth keeping now the sizing stands on its own.
			return MIN_KELLY_FRACTION;
		}
		return Math.max(MIN_KELLY_FRACTION,
			Math.min(1.0, KELLY_SHARE * ((p * b - (1.0 - p)) / b)));
	}

	/**
	 * What it costs to get out of a position whose sell leg did not fill.
	 * <p>
	 * You do not eat a stop; you re-list into the bid, which is roughly where you bought. So the
	 * cost is the tax paid on the way out plus however far the price wandered while you held —
	 * estimated from the item's own measured volatility over the holding period rather than a flat
	 * guess, because a stable staple and a jumpy niche item are not the same risk.
	 */
	private long unwindCost(int itemId, int buyPrice, int bid, int quantity, ItemFeatures features,
		double horizonHours)
	{
		// What giving up actually costs. A stranded position is sold into the bid, not liquidated at
		// a crash price, so most of the loss is structural -- the spread paid on the way in, plus tax
		// on the way out -- and only the remainder is however far the price drifted while we waited.
		//
		// The drift charged here is an expectation, not a worst case. This once charged a full
		// standard deviation, which treats a one-sigma adverse move as a certainty and then has
		// expectedProfit() multiply it by the probability of stranding: a tail scenario used as a
		// mean. The consequence was arithmetic rather than subtle. With an unwind cost k times the
		// margin, expected value is positive only when the sell-fill probability clears k/(1+k), and
		// on live data k sat around 3 -- so anything under a 0.75 sell-fill probability was rejected
		// no matter how good the margin was. Expensive items have lower sell-fill probabilities by
		// nature, so they were eliminated first, which is precisely backwards.
		double bucketsPerHour = 3600.0 / Math.max(1, bucketSeconds);
		double buckets = Math.max(1.0, horizonHours * bucketsPerHour);
		double sigma = Math.min(MAX_USABLE_VOLATILITY, features.getVolatility()) * Math.sqrt(buckets);
		double drift = Math.max(MIN_DRIFT_FRACTION, ADVERSE_DRIFT_SHARE * sigma);

		int exitPrice = Math.max(1, (int) Math.round(Math.min(buyPrice, bid) * (1 - drift)));
		long proceedsPerItem = exitPrice - tax.taxPerItem(itemId, exitPrice);
		long lossPerItem = Math.max(0, buyPrice - proceedsPerItem);
		return lossPerItem * quantity;
	}

	/**
	 * An estimate's own hours with the learned bias applied to its waiting, and to nothing else.
	 * <p>
	 * Never below the wait itself: the throughput term cannot be negative, and a multiplier that
	 * shortened the whole estimate below the time before the first trade would be describing an
	 * order that finishes before it starts.
	 */
	private static double correctedHours(FillEstimate estimate, double waitMultiplier)
	{
		double wait = estimate.getWaitHours();
		if (wait <= 0 || Double.isNaN(wait) || Double.isInfinite(wait))
		{
			return estimate.getExpectedHours();
		}
		double working = Math.max(0, estimate.getExpectedHours() - wait);
		return wait * waitMultiplier + working;
	}

	private static int shift(int price, double fraction)
	{
		int delta = (int) Math.round(price * fraction);
		if (delta == 0 && fraction != 0)
		{
			delta = fraction > 0 ? 1 : -1;
		}
		return Math.max(1, price + delta);
	}

	private static LatestPrice quote(JsonObject root, int itemId)
	{
		JsonObject object = root.getAsJsonObject(Integer.toString(itemId));
		if (object == null)
		{
			return null;
		}
		Integer high = boxed(object, "high");
		Integer low = boxed(object, "low");
		if (high == null || low == null)
		{
			return null;
		}
		return new LatestPrice(high, longOf(object, "highTime"), low, longOf(object, "lowTime"));
	}

	private static Candle bar(JsonObject root, int itemId)
	{
		JsonObject object = root.getAsJsonObject(Integer.toString(itemId));
		if (object == null)
		{
			return null;
		}
		return new Candle(0, boxed(object, "avgHighPrice"), boxed(object, "avgLowPrice"),
			intOf(object, "highPriceVolume"), intOf(object, "lowPriceVolume"));
	}

	private static Integer boxed(JsonObject object, String key)
	{
		return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : null;
	}

	private static Long longOf(JsonObject object, String key)
	{
		return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsLong() : null;
	}

	private static int intOf(JsonObject object, String key)
	{
		return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : 0;
	}

	/** One item with everything the screen needs, however it was obtained. */
	static final class QuotedItem
	{
		private final MarketIngestionService.Item item;
		private final LatestPrice quote;
		private final Candle fiveMinute;
		private final Candle hourly;

		QuotedItem(MarketIngestionService.Item item, LatestPrice quote, Candle fiveMinute, Candle hourly)
		{
			this.item = item;
			this.quote = quote;
			this.fiveMinute = fiveMinute;
			this.hourly = hourly;
		}
	}

	private static final class Screened
	{
		private final MarketIngestionService.Item item;
		private final LatestPrice price;
		private final int buyLimitRemaining;
		private final int fillable;
		private final int hourlyVolume;
		private final long throughput;

		Screened(MarketIngestionService.Item item, LatestPrice price, int buyLimitRemaining,
			int fillable, int hourlyVolume, long throughput)
		{
			this.item = item;
			this.price = price;
			this.buyLimitRemaining = buyLimitRemaining;
			this.fillable = fillable;
			this.hourlyVolume = hourlyVolume;
			this.throughput = throughput;
		}
	}
}
