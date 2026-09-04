package com.flippingfriend.companion;

import com.flippingfriend.core.PlanDiagnostics;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.data.Candle;
import com.flippingfriend.data.ItemMetadata;
import com.flippingfriend.data.LatestPrice;

import com.flippingfriend.model.AlchemyFloor;
import com.flippingfriend.model.FeatureEngine;
import com.flippingfriend.model.FillCurve;
import com.flippingfriend.model.FillEstimate;
import com.flippingfriend.model.FillModel;
import com.flippingfriend.model.FilterResult;
import com.flippingfriend.model.ItemFeatures;
import com.flippingfriend.model.ItemGroups;
import com.flippingfriend.model.ManipulationFilter;
import com.flippingfriend.learning.FlipFeatures;
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
	 * "Conservative pricing": place offers one step further inside the spread, filling sooner for
	 * slightly less. The built-in engine has always honoured this by widening its price grid; on this
	 * path the setting did nothing at all, because prices come from the appetite's own offsets.
	 */
	private volatile boolean conservativePricing;
	/**
	 * "Learn from my trades", inverted. Turning it off left the companion learning regardless, which
	 * made the setting a statement about the engine that no longer chooses anything.
	 */
	private volatile boolean learningDisabled;

	void setConservativePricing(boolean conservative)
	{
		this.conservativePricing = conservative;
	}

	void setLearningDisabled(boolean disabled)
	{
		this.learningDisabled = disabled;
	}

	/** The same step the built-in engine takes when the setting is on. */
	private static final double CONSERVATIVE_STEP = 1.6;
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
	/** Everything learned so far; never null, and neutral until something has been learned. */

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
	/**
	 * Whether order size is reduced by the expected counterparty wait.
	 * <p>
	 * <b>Off, because it was measured and it lost money.</b> The argument for it looked airtight:
	 * duration is the wait plus quantity over rate, so sizing at rate times the whole horizon yields
	 * an order whose own predicted duration exceeds the horizon — guaranteed to be cut short and
	 * unwound. Run against its absence on identical folds it cost 13.8%, and left the profit and time
	 * estimates no better calibrated than before.
	 * <p>
	 * The flaw was in what a partial fill is worth. Being cut short is not the disaster the reasoning
	 * assumed: a partially filled position still sells, and the profit given up by ordering small
	 * every time is larger than the occasional cost of unwinding a remainder. Ordering into the whole
	 * horizon is a bet that the market will be generous, and it pays more often than not.
	 * <p>
	 * Kept switchable so the comparison can be repeated rather than re-argued.
	 */
	private boolean waitAwareSizing = false;

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

	/** What the current risk appetite claims of a market, before any measurement corrects it. */
	double assumedCaptureShare()
	{
		return appetite.getCaptureShare();
	}

	/** Fill model reflecting the current appetite; falls back to the injected one before any is set. */
	private FillModel fillModel()
	{
		FillModel model = riskModel;
		return model == null ? fillModel : model;
	}

	/**
	 * The fill model to use for one item, with the capture share this item has actually shown.
	 * <p>
	 * The appetite's number is a policy about how much of a market we are willing to claim; the
	 * learned one is a measurement of how much we get. They are not the same question, and until now
	 * only the first had an answer — every order in the system was sized against a constant chosen by
	 * a dropdown. An item where we win a fifth of the flow and one where we win four fifths were
	 * given identical orders.
	 * <p>
	 * With no evidence {@link CaptureRates#rateFor} returns the appetite's own number, so this is the
	 * previous behaviour exactly until fills say otherwise.
	 * <p>
	 * Models are cached by rounded rate rather than rebuilt per item. {@link FillModel} is immutable
	 * and this runs once per item per plan across thousands of items, so a hundred distinct models
	 * are shared instead of thousands allocated — and rounding to a hundredth is well inside the
	 * precision the measurement supports.
	 */
	private FillModel fillModel(int itemId)
	{
		CaptureRates rates = captureRates;
		FillModel base = fillModel();
		if (rates == null || itemId <= 0)
		{
			return base;
		}
		double learned = rates.rateFor(itemId, appetite.getCaptureShare());
		int key = (int) Math.round(learned * 100);
		if (key == (int) Math.round(appetite.getCaptureShare() * 100))
		{
			return base;
		}
		return captureModels.computeIfAbsent(key, rounded -> base.withCaptureRate(rounded / 100.0));
	}

	private volatile FillModel riskModel;

	/** Measured capture shares. Null until wired, which leaves the appetite's number in charge. */
	private volatile CaptureRates captureRates;

	/**
	 * How the odds of filling change with how long an offer has already waited. Null until wired,
	 * and inert until it has both enough observations and something to say.
	 */
	private volatile FillHazard fillHazard;

	/**
	 * Hands the factory the measured fill hazard.
	 * <p>
	 * Wired rather than merely offered, and read in {@link #holdValue} rather than stored and
	 * forgotten -- audit item 11 is a list of setters that exist, compile and are never called.
	 */
	void setFillHazard(FillHazard hazard)
	{
		this.fillHazard = hazard;
	}

	/** Nature rune. Consumed by every High Level Alchemy cast, so its price is the floor's cost. */
	private static final int NATURE_RUNE = 561;

	/**
	 * Nature runes, as the market last quoted them, because every alch consumes one.
	 * <p>
	 * Read from the same quote sweep everything else uses rather than hardcoded: the rune is one of
	 * the most actively traded items in the game, and a stale figure here would sit underneath every
	 * downside estimate in the system.
	 */
	private volatile int natureRunePrice = AlchemyFloor.DEFAULT_NATURE_RUNE_PRICE;

	/** Rebuilt only when the rune price actually moves, since it is consulted per candidate. */
	private volatile AlchemyFloor cachedFloor;

	private AlchemyFloor alchemyFloor()
	{
		AlchemyFloor floor = cachedFloor;
		if (floor == null || floor.natureRunePrice() != natureRunePrice)
		{
			floor = new AlchemyFloor(tax, natureRunePrice);
			cachedFloor = floor;
		}
		return floor;
	}

	/** The live nature rune quote, from the sweep that is already running. */
	void setNatureRunePrice(int price)
	{
		if (price > 0)
		{
			this.natureRunePrice = price;
		}
	}

	/** One model per distinct rounded capture rate, shared across every item that lands on it. */
	private final Map<Integer, FillModel> captureModels = new ConcurrentHashMap<>();

	/**
	 * Hands the factory what our own fills have shown about capture.
	 * <p>
	 * Wired rather than merely offered — audit item 11 is a list of setters that exist, compile, and
	 * are never called. A measurement nothing consults is the same bug in a new place.
	 */
	void setCaptureRates(CaptureRates rates)
	{
		this.captureRates = rates;
		this.captureModels.clear();
	}

	/** Working from the most recent sizing decision, attached to the candidate it produced. */

	/** Names shown per veto reason. Three is enough to spot-check a filter, few enough to read. */
	private static final int VETO_EXAMPLES_PER_REASON = 3;

	/**
	 * Corrections learned from this account's own fills.
	 *
	 * <p>Defaults to a fresh instance rather than null because a fresh {@link FillCalibration} is
	 * inert by construction — no observations means no correction — so an unwired factory, such as
	 * the one the backtester builds, behaves exactly as it did before calibration existed, with no
	 * null handling on the hot path.
	 */
	private FillCalibration calibration = new FillCalibration();

	/**
	 * Paper-trades the items the screen rejects. Null until wired, because unlike calibration there
	 * is no useful inert default — an unwired factory should do no shadow work at all rather than
	 * accumulate positions nobody will ever read.
	 */
	private ShadowTrader shadow;

	/**
	 * The learned correction, which is inert until it has beaten {@code FillModel} out of sample.
	 * A fresh instance has no model and weight zero, so an unwired factory ranks exactly as it did
	 * before any of this existed.
	 */
	private LearnedFillModel learned = new LearnedFillModel();

	void setLearnedFillModel(LearnedFillModel learned)
	{
		if (learned != null)
		{
			this.learned = learned;
		}
	}

	LearnedFillModel learnedFillModel()
	{
		return learned;
	}

	/**
	 * The buy-limit windows, so a spent limit can say when it frees.
	 *
	 * <p>{@link BuyLimitLedger#resetsAt} has existed, tested, with no production caller: the system
	 * knew to the second when every item became available again and never told anyone. Null in replay,
	 * where there is no live ledger and the wait is not a thing a backtest can act on.
	 */
	private BuyLimitLedger buyLimits;

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
	/**
	 * A judgement: this item could have been traded and was not worth it.
	 *
	 * <p>Recorded for the funnel and handed to {@link ShadowTrader}, which paper-trades it so the
	 * refusal can be scored later. That is the whole point of a veto — it is a decision, and a
	 * decision is something that can turn out to have been wrong.
	 */
	private void veto(int itemId, String itemName, String reason)
	{
		lastVeto.put(itemId, reason);
		vetoedNames.put(itemId, itemName == null ? "" : itemName);
	}

	/**
	 * An item that was never on the table: unactionable, or unpriceable.
	 *
	 * <p>Counted in the funnel exactly like a veto, and deliberately <b>not</b> paper-traded. A
	 * members item on a free account is not a trade this system passed up, it is one the player
	 * could not have placed; scoring it as a missed opportunity would fill the counterfactual channel
	 * with trades that never existed and quietly inflate the cost of every real refusal.
	 *
	 * <p>Until now these left through a bare {@code continue}. Nothing landed anywhere, so the funnel
	 * could report "3,088 priced → 412 liquid enough → 90 studied → 0 cleared" and not account for a
	 * single item that fell out between those numbers — which is audit item 67, and is also why a
	 * player looking at an empty plan had no way to find out why.
	 */
	private void dropped(int itemId, String itemName, String reason)
	{
		lastDrop.put(itemId, reason);
		droppedNames.put(itemId, itemName == null ? "" : itemName);
	}
	private boolean exemptionsResolved;

	/** Items the screen wants history for, published so the warmer knows what to fetch next. */
	private volatile List<Integer> shortlistIds = new ArrayList<>();

	/** Items that were never actionable, kept apart from vetoes so the shadow channel stays honest. */
	private final Map<Integer, String> lastDrop = new ConcurrentHashMap<>();
	private final Map<Integer, String> droppedNames = new ConcurrentHashMap<>();

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
	/**
	 * Supplies the live calibration. Called by {@link PortfolioPlanner}; without it the factory keeps
	 * its own inert instance and applies no correction.
	 */
	void setCalibration(FillCalibration calibration)
	{
		if (calibration != null)
		{
			this.calibration = calibration;
		}
	}

	/** Visible for testing that the calibration actually arrives, rather than only that it compiles. */
	FillCalibration calibration()
	{
		return calibration;
	}

	/**
	 * What a resting offer is still worth per hour of the slot it is holding.
	 *
	 * <p>The question a hold decision should ask, and the one nothing asked before: not "has this been
	 * open a while" but "would this slot earn more doing something else". Repricing on a staleness
	 * timer answers neither — it fires on an offer that is about to fill and stays silent on one that
	 * never will.
	 *
	 * <p>Scored with the same {@link FillModel} and the same curve the entry decision used, so a hold
	 * and a fresh trade are compared on one scale rather than by two rules that happen to disagree.
	 *
	 * @param remainingHours slot-time this offer will still consume before its horizon expires
	 * @return expected gp per slot-hour from letting it stand, or a negative value when there is not
	 *         enough history to judge — never zero, which would read as "worthless" rather than
	 *         "unknown"
	 */
	double holdValue(int itemId, int price, int remainingQuantity, boolean buying, long marginPerItem,
		double remainingHours)
	{
		return holdValue(itemId, price, remainingQuantity, buying, marginPerItem, remainingHours, 0);
	}

	/**
	 * @param waitedMinutes how long this offer has already been sitting there unfilled, which the
	 *                      analytical model has no way to use and which is the whole of what
	 *                      {@link FillHazard} adds
	 */
	double holdValue(int itemId, int price, int remainingQuantity, boolean buying, long marginPerItem,
		double remainingHours, double waitedMinutes)
	{
		if (itemId <= 0 || price <= 0 || remainingQuantity <= 0 || remainingHours <= 0)
		{
			return -1;
		}
		List<Candle> shortSeries = series.series(itemId, shortStep);
		if (shortSeries == null || shortSeries.isEmpty())
		{
			return -1;
		}
		FillCurve curve = FillCurve.from(shortSeries);
		FillEstimate estimate = buying
			? fillModel(itemId).estimateBuy(curve, price, remainingQuantity, remainingHours)
			: fillModel(itemId).estimateSell(curve, price, remainingQuantity, remainingHours);
		if (!estimate.isPlausible())
		{
			return -1;
		}
		// Only the profit still ahead counts. What the offer has already cost in slot-time is spent
		// either way, and charging it again would keep an offer alive purely because it has been
		// expensive so far.
		double probability = estimate.getProbability()
			* waitPenalty(itemId, buying, waitedMinutes, remainingHours * 60);
		double expected = (double) marginPerItem * remainingQuantity * probability;
		return expected / Math.max(1.0 / 60.0, remainingHours);
	}

	/**
	 * How much less likely this offer is to fill because it has already waited.
	 *
	 * <p>A ratio, not a replacement. {@link FillModel} reads the book — the volume at our price, the
	 * within-bucket dispersion, the counterparty wait — and its answer is sensitive to price and size
	 * in ways nothing here could reproduce. What it structurally cannot know is that <em>this</em>
	 * order has been sitting unfilled for forty minutes, because that is a fact about our order and
	 * not about the market, and the price feed has never seen it.
	 *
	 * <p>Under the Poisson arrivals the analytical model assumes, the answer is always 1: a memoryless
	 * process does not care how long you have waited. So this correction is inert exactly when that
	 * assumption holds, and bites only to the extent the measured hazard actually declines — which
	 * makes it safe to apply before anyone knows whether it will.
	 *
	 * <p>Floored, because the late buckets are always the thinnest and a handful of unlucky offers in
	 * one of them should not be able to declare a perfectly good offer worthless.
	 */
	private double waitPenalty(int itemId, boolean buying, double waitedMinutes,
		double horizonMinutes)
	{
		FillHazard hazard = fillHazard;
		if (hazard == null || waitedMinutes <= 0 || horizonMinutes <= 0 || !hazard.isUsable())
		{
			return 1.0;
		}
		double fresh = hazard.completionWithin(itemId, buying, 0, horizonMinutes);
		if (fresh <= 0)
		{
			return 1.0;
		}
		double waited = hazard.completionWithin(itemId, buying, waitedMinutes, horizonMinutes);
		// Never above one. The hazard is here to say that waiting has cost something, not to talk a
		// stale offer up past what the book says about it.
		return Math.max(MIN_WAIT_PENALTY, Math.min(1.0, waited / fresh));
	}

	/**
	 * Floor on the wait penalty.
	 * <p>
	 * A quarter. Past that the correction stops being a correction and starts overriding the model
	 * that reads the book, on the strength of the thinnest buckets in the sample.
	 */
	private static final double MIN_WAIT_PENALTY = 0.25;

	/**
	 * The hold value of a resting <em>buy</em>, net of the tax the eventual sale will pay.
	 *
	 * <p>Buys only, deliberately. Cancelling a resting buy frees the slot and costs nothing — no
	 * capital has changed hands. Cancelling a resting sell leaves you holding the item, so the slot is
	 * not really freed and the decision is about exit pricing rather than opportunity cost. Applying
	 * one hurdle to both would recommend abandoning positions to chase a better entry.
	 */
	double buyHoldValue(int itemId, int offerPrice, int remainingQuantity, int marketSellPrice,
		double remainingHours)
	{
		return buyHoldValue(itemId, offerPrice, remainingQuantity, marketSellPrice, remainingHours, 0);
	}

	/**
	 * @param waitedMinutes how long the offer has already been open unfilled
	 */
	double buyHoldValue(int itemId, int offerPrice, int remainingQuantity, int marketSellPrice,
		double remainingHours, double waitedMinutes)
	{
		if (marketSellPrice <= offerPrice)
		{
			return -1;
		}
		long margin = tax.netMarginPerItem(itemId, offerPrice, marketSellPrice);
		if (margin <= 0)
		{
			// The spread no longer covers tax, so letting it stand earns nothing whatever it does.
			return 0;
		}
		return holdValue(itemId, offerPrice, remainingQuantity, true, margin, remainingHours,
			waitedMinutes);
	}

	/** The reason this item was turned away on the last pass, or null if it was not. */
	String lastVetoFor(int itemId)
	{
		return lastVeto.get(itemId);
	}

	void setBuyLimitLedger(BuyLimitLedger buyLimits)
	{
		this.buyLimits = buyLimits;
	}

	/**
	 * How long until this item's four-hour window resets, or null when it is not waiting on one.
	 * <p>
	 * Rounded to whole minutes: the ledger knows the second, and a second is not a number anyone acts
	 * on.
	 */
	private String resetWait(int itemId, Instant now)
	{
		if (buyLimits == null)
		{
			return null;
		}
		Instant resets = buyLimits.resetsAt(itemId, now);
		if (resets == null || !resets.isAfter(now))
		{
			return null;
		}
		long minutes = Math.max(1, (resets.getEpochSecond() - now.getEpochSecond()) / 60);
		if (minutes < 60)
		{
			return minutes + "m";
		}
		return minutes / 60 + "h " + minutes % 60 + "m";
	}

	void setShadowTrader(ShadowTrader shadow)
	{
		this.shadow = shadow;
	}

	ShadowTrader shadowTrader()
	{
		return shadow;
	}

	List<PortfolioCandidate> build(MarketIngestionService.MarketState market, double horizonHours,
		Map<Integer, Integer> buyLimitRemaining, long spendableCoins, boolean members, Instant now)
	{
		resolveExemptions(market.mapping.values());
		// Every alch costs a rune, and the rune has a live price sitting in the sweep we are already
		// holding. Read here rather than injected, so a setter cannot be left uncalled: the alch
		// floor under every downside estimate below is only as current as this line.
		LatestPrice runeQuote = quote(market.latest, NATURE_RUNE);
		if (runeQuote != null && runeQuote.getHigh() != null && runeQuote.getHigh() > 0)
		{
			setNatureRunePrice(runeQuote.getHigh());
		}
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
		// Both records, every build. A discard map that survives a pass accumulates for the life of
		// the process and the funnel starts reporting a history rather than a plan.
		lastDrop.clear();
		droppedNames.clear();
		itemsInFeed.set(universe.size());
		itemsQuoted.set(0);
		itemsAnalysed.set(0);

		List<Screened> shortlist = screen(universe, buyLimitRemaining, spendableCoins, horizonHours, now);
		itemsShortlisted.set(shortlist.size());
		shadowRejected(universe, horizonHours, now);

		// Published in screen order, so the warmer spends its budget on the most promising items
		// first and the shortlist becomes usable from the top down rather than all at once.
		List<Integer> ids = new ArrayList<>(shortlist.size());
		for (Screened screened : shortlist)
		{
			ids.add(screened.item.id);
		}
		shortlistIds = ids;

		// A momentum inference over the whole shortlist ran here every planning pass, on a tensor
		// that was allocated and never populated -- new float[n][12][4], all zeros -- so every item
		// got the model's output for an all-zero input: a constant -0.052978. It was written to this
		// module's FeatureEngine, which nothing in the companion reads; the only caller of
		// getPredictedMomentum() is Scorer, in the plugin, fed by its own separate instance. So it
		// cost an inference per pass, moved no price, and would have applied a flat -5.3% haircut to
		// every sell the moment anyone read it. Removed rather than repaired: a momentum signal
		// should arrive with the features that justify it.

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

	void setWaitAwareSizing(boolean enabled)
	{
		this.waitAwareSizing = enabled;
	}



	/** What the screen would like history for, most promising first. */
	List<Integer> shortlistIds()
	{
		return shortlistIds;
	}

	/**
	 * Why this item is not in the plan, from either record, or null if it is.
	 *
	 * <p>The two are separate because the shadow channel must only see judgements, but a player
	 * asking "where did my item go" does not care which kind it was — and the property worth
	 * guaranteeing is that <em>something</em> answers, for every item in the feed.
	 */
	String lastReasonFor(int itemId)
	{
		String veto = lastVeto.get(itemId);
		return veto != null ? veto : lastDrop.get(itemId);
	}

	/** A few names for one reason, from whichever record holds them. */
	private static void collectExamples(Map<Integer, String> reasons, Map<Integer, String> names,
		String reason, List<String> into)
	{
		for (Map.Entry<Integer, String> entry : reasons.entrySet())
		{
			if (into.size() >= VETO_EXAMPLES_PER_REASON)
			{
				return;
			}
			if (!reason.equals(entry.getValue()))
			{
				continue;
			}
			String name = names.get(entry.getKey());
			if (name != null && !name.isEmpty())
			{
				into.add(name);
			}
		}
	}

	/**
	 * The funnel from the last build. Capital figures are left at zero here because only the
	 * optimizer knows what was finally allocated.
	 */
	PlanDiagnostics lastFunnel(int tacticsGenerated, long capitalAvailable)
	{
		// Both kinds, so the funnel adds up. The shadow channel still reads lastVeto alone: what a
		// player needs is a complete account of where four thousand items went, and what the
		// counterfactual needs is only the ones that were genuinely passed up.
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (String reason : lastVeto.values())
		{
			counts.merge(reason, 1, Integer::sum);
		}
		for (String reason : lastDrop.values())
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
			collectExamples(lastVeto, vetoedNames, reason, names);
			collectExamples(lastDrop, droppedNames, reason, names);
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
		Map<Integer, Integer> buyLimitRemaining, long spendableCoins, double horizonHours, Instant now)
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
				dropped(itemId, item.name, "Members item, which this account cannot trade.");
				continue;
			}

			// Believe the volume-weighted average over a single transaction when they disagree: one
			// misclick should not put an item on the shortlist.
			LatestPrice anchored = PriceAnchor.anchor(quoted.quote, bar);
			if (anchored == null || !anchored.isComplete())
			{
				dropped(itemId, item.name, "Only one side of this item has a recent price.");
				continue;
			}
			itemsQuoted.incrementAndGet();

			int buy = anchored.getLow();
			int sell = anchored.getHigh();
			// Three different situations, and they were one silent branch. A player staring at an
			// empty plan is owed the difference between "nobody is trading this", "there is no gap to
			// capture" and "you cannot afford one of these".
			if (buy <= 0)
			{
				dropped(itemId, item.name, "No buy price to work from.");
				continue;
			}
			if (sell <= buy)
			{
				veto(itemId, item.name, "No gap between the buy and sell price.");
				continue;
			}
			if (buy > spendableCoins)
			{
				dropped(itemId, item.name, "Costs more than the whole bankroll.");
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
				// Say when it frees. "Spent" alone reads as permanent, and the player's response to a
				// forty-minute wait is different from their response to three hours.
				String wait = resetWait(itemId, now);
				veto(itemId, item.name, wait == null
					? "Four-hour buy limit is spent."
					: "Four-hour buy limit is spent; resets in " + wait + ".");
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
				dropped(itemId, item.name, "Nothing of this is affordable or available right now.");
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

		FillCurve curve = FillCurve.from(shortSeries);
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

		// The labelled row this decision will become.
		//
		// Built once per analysed item, from the same ItemFeatures and MarketContext the filter and
		// the fill model just used, so the features recorded are exactly the ones the decision saw.
		// Computing them later from stored prices would reconstruct an approximation of a moment that
		// has passed, and a training set assembled that way teaches a model to predict the
		// reconstruction.
		int hourOfDay = now.atZone(ZoneOffset.UTC).getHour();
		double[] featureVector = FlipFeatures.of(
			(double) (screened.price.getHigh() - screened.price.getLow())
				/ Math.max(1, screened.price.getLow()),
			screened.price.getLow(), screened.fillable, screened.item.buyLimit,
			features, context, hourOfDay).values();

		// What FillModel says about this same trade, at this same moment, recorded beside the
		// features. Without it a trained model can only be compared with the base rate, and the
		// question that decides whether it ships is whether it beats the analytical model that reads
		// the actual book. Computing it later would compare against a reconstruction.
		double analyticalCompletion =
			fillModel(itemId).estimateBuy(curve, screened.price.getLow(),
					Math.max(1, screened.fillable), horizonHours, season).getProbability()
				* fillModel(itemId).estimateSell(curve, screened.price.getHigh(),
					Math.max(1, screened.fillable), horizonHours, season).getProbability();


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
					continue;
				}

				// Fractional Kelly Sizing
				long netProfitFull = marginPerItem * fillable;
				long unwindLossFull = unwindCost(itemId, screened.item.highAlch, buyPrice,
					screened.price.getLow(), fillable, features, horizonHours);

				// Sizing is driven by FillModel, which reads the actual book: volume at or beyond the
				// quote, the within-bucket dispersion, and the counterparty-wait term. Until
				// 2 September 2026 an ONNX model overrode this whenever it returned above zero, and
				// that model responded only to a coarse season bucket -- a 150gp order for 1,000 units
				// and a 2,000,000gp order for 5 both scored 0.329689. It was setting deployed capital
				// from a two-valued lookup. See OnnxInferenceEngineTest.
				double pBuy = fillModel(itemId).estimateBuy(curve, buyPrice, fillable, horizonHours, season).getProbability();
				double pSell = fillModel(itemId).estimateSell(curve, sellPrice, fillable, horizonHours, season).getProbability();
				double p = pBuy * pSell;
				
				double b = unwindLossFull > 0 ? (double) netProfitFull / unwindLossFull : netProfitFull;
				
				double fStar = 1.0;
				if (b > 0 && p > 0)
				{
					fStar = 0.35 * ((p * b - (1.0 - p)) / b);
				}
				fStar = Math.max(0.1, Math.min(1.0, fStar));
				
				int quantity = (int) Math.max(1, fillable * fStar);

				FillEstimate buyFill = fillModel(itemId).estimateBuy(curve, buyPrice, quantity,
					horizonHours, season);
				FillEstimate sellFill = fillModel(itemId).estimateSell(curve, sellPrice, quantity,
					horizonHours, season);

				// The ONNX fill and wait models replaced both estimates here whenever they returned
				// anything non-zero. `> 0` is not a validity test: it cannot tell a genuine low
				// probability from the zero every failure path in OnnxInferenceEngine returns, so a
				// model that failed to load and one that was confident looked identical. They return
				// as a residual correction with an earned weight, on features that include liquidity,
				// rather than as a replacement for a model that reads the book. See T2.6.

				if (!buyFill.isPlausible() || !sellFill.isPlausible())
				{
					continue;
				}

				long netProfit = marginPerItem * quantity;
				long worstLoss = Math.max(1, (long) (buyPrice * stopDistance() * quantity));
				long unwindLoss = unwindCost(itemId, screened.item.highAlch, buyPrice,
					screened.price.getLow(),
					quantity, features, horizonHours);

				// Where everything that has been learned re-enters the decision.
				//
				// Both corrections come from this account's own settled offers, and both are inert
				// until there is enough evidence to justify them: a cold IsotonicCalibrator returns
				// its input unchanged, and an unseen item's duration multiplier is 1.0. So an engine
				// that has never traded ranks exactly as it did before this was wired.
				//
				// Correcting here rather than at the objective means expectedProfit(),
				// expectedSlotHours() and expectedGpPerSlotHour() all see one consistent set of
				// numbers -- the alternative is a candidate whose stated probability disagrees with
				// the one it was ranked on.
				double durationMultiplier = calibration.durationMultiplier(itemId);
				double buyHours = buyFill.getExpectedHours() * durationMultiplier;
				double sellHours = sellFill.getExpectedHours() * durationMultiplier;
				double sellProbability = calibration.calibrate(false, sellFill.getProbability());


				// Rank on a draw, show the mean.
				//
				// Ranking on the argmax means only trades the model already rates highly are ever
				// attempted, so the evidence it learns from is censored by the policy that produced
				// it: an item it has quietly underrated is never tried and therefore never corrected.
				// The draw lets such an item win a slot in proportion to how uncertain its estimate
				// is, and that uncertainty shrinks on its own as fills accumulate.
				//
				// The player sees the mean. A confidence that jumped around because it was a random
				// draw would be unreadable, and worse, dishonest -- it is not what the system
				// believes. withDisplayProbability carries exactly this split.
				// Three corrections, in the order their evidence justifies. The analytical estimate
				// reads the book; the learned model adjusts it only by as much as it has earned;
				// calibration then corrects whatever remains wrong about the magnitude; and the draw
				// is the exploration. Each is inert until it has grounds, so an engine that has never
				// traded produces the same ranking as before any of them existed.
				double adjustedBuy = learned.adjust(buyFill.getProbability(), featureVector);
				double displayBuyProbability = calibration.calibrate(true, adjustedBuy);
				double buyProbability = calibration.explore(itemId, displayBuyProbability);

				tactics.add(new PortfolioCandidate(itemId, screened.item.name, group,
					buyPrice, buyPrice, buyPrice, sellPrice, sellPrice, sellPrice,
					quantity, screened.buyLimitRemaining, netProfit, worstLoss,
					unwindLoss, buyProbability, sellProbability,
					buyHours, sellHours, horizonHours, expiresAt)
					.withDisplayProbability(displayBuyProbability));
			}
		}

		// Keep only the strongest few. Near-duplicate tactics add branching to the optimizer's
		// search without adding any real choice.
		tactics.sort(Comparator.comparingDouble(PortfolioCandidate::expectedGpPerSlotHour).reversed());
		if (tactics.isEmpty())
		{
			veto(itemId, screened.item.name, "No price and size combination is expected to profit.");
		}
		// Paper-trade this item whatever the outcome, carrying the features that produced it.
		//
		// Recorded for accepted and rejected alike. A model trained only on trades the engine chose to
		// take learns the engine's existing opinion back; the counterfactuals are what let it learn
		// that an opinion was wrong, and this is the only place both are visible with their features.
		ShadowTrader trader = shadow;
		if (trader != null)
		{
			String outcome = tactics.isEmpty() ? lastVeto.get(itemId) : null;
			trader.open(itemId, screened.item.name, outcome,
				screened.price.getLow(), screened.price.getHigh(),
				Math.max(1, screened.fillable), now.getEpochSecond(), horizonHours, featureVector,
				analyticalCompletion);
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
		FillEstimate buySide =
			fillModel(screened.item.id).estimateBuy(curve, buyPrice, 1, horizonHours, season);
		FillEstimate sellSide =
			fillModel(screened.item.id).estimateSell(curve, sellPrice, 1, horizonHours, season);

		// Size against the time left after the wait, not against the whole horizon.
		//
		// The two have to agree or the order is doomed by arithmetic before the market gets a vote.
		// Duration is the wait plus quantity divided by rate; sizing at rate times the full horizon
		// therefore produces an order whose own predicted duration is the horizon *plus* the wait —
		// always too long, always partially filled, always unwinding the remainder at a loss. The
		// wait was added to the duration model without being carried through to here, and the visible
		// symptom was profit coming in around three quarters of what was promised.
		double buyTrading = waitAwareSizing ? buySide.tradingHoursWithin(horizonHours) : horizonHours;
		double sellTrading = waitAwareSizing ? sellSide.tradingHoursWithin(horizonHours) : horizonHours;
		double buyReach = buySide.getUnitsPerHour() * buyTrading;
		double sellReach = sellSide.getUnitsPerHour() * sellTrading;
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
		return unwindCost(itemId, 0, buyPrice, bid, quantity, features, horizonHours);
	}

	/**
	 * @param highAlch the item's high alchemy value, which puts a floor under how bad this can get
	 */
	private long unwindCost(int itemId, int highAlch, int buyPrice, int bid, int quantity,
		ItemFeatures features, double horizonHours)
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

		// Below a certain price the item stops being worth less, because it can be turned into a
		// fixed number of coins instead of sold. That is the one number here the market does not
		// set, and for an alchable item it converts the drift term above from an estimate into a
		// bound: the price can wander as far as it likes and the recovery cannot fall past the
		// furnace. Alching pays no tax, which is why this is not simply a floor on the exit price --
		// the two routes are compared in coins recovered, not in prices quoted.
		long recovered = alchemyFloor().unwindValue(itemId, highAlch, exitPrice, quantity,
			horizonHours);
		return Math.max(0, (long) buyPrice * quantity - recovered);
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
	/**
	 * Opens a notional position for every item the screen turned away, and resolves whatever has come
	 due since the last pass.
	 *
	 * <p>Priced at the market: buy at the instant-sell quote, sell at the instant-buy quote. That is
	 * the trade the engine would have been choosing between had the veto not fired, so it is the
	 * right counterfactual — not a hypothetical at some price nobody was offering.
	 *
	 * <p>Sized at the item's buy limit, which is the largest a real order could be.
	 * {@link ShadowTrader#report} then filters by what a given bankroll could actually have afforded,
	 * so sizing here does not have to guess at capital and the same recorded trade can be scored
	 * against several bankrolls.
	 */
	private void shadowRejected(Collection<QuotedItem> universe, double horizonHours, Instant now)
	{
		ShadowTrader trader = shadow;
		if (trader == null || lastVeto.isEmpty())
		{
			return;
		}
		long at = now.getEpochSecond();
		for (QuotedItem quoted : universe)
		{
			String reason = lastVeto.get(quoted.item.id);
			if (reason == null)
			{
				continue;
			}
			Integer low = quoted.quote == null ? null : quoted.quote.getLow();
			Integer high = quoted.quote == null ? null : quoted.quote.getHigh();
			if (low == null || high == null || low <= 0 || high <= low)
			{
				// No two-sided quote means there is no trade to have missed.
				continue;
			}
			int quantity = Math.max(1, quoted.item.buyLimit);
			trader.open(quoted.item.id, quoted.item.name, reason, low, high, quantity, at, horizonHours);
		}
		// Resolving here rather than on a timer keeps the whole channel on the planner thread, which
		// already owns the series cache and runs often enough that nothing waits long past its
		// horizon.
		trader.resolve(series, shortStep, at);
	}

	static final class QuotedItem
	{
		/** For tests that need to ask about an item by id without reaching into the wrapper. */
		int itemId()
		{
			return item.id;
		}

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
