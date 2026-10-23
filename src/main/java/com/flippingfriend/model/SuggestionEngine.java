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
	private final com.flippingfriend.model.arbitrage.ArbitrageRegistry arbitrageRegistry;

	private final AtomicReference<Suggestion> current = new AtomicReference<>(Suggestion.idle());
    private final java.util.concurrent.ConcurrentMap<String,Long> maintenanceRetryAfter=new java.util.concurrent.ConcurrentHashMap<>();
    /** Back off one unsavable ordinary workflow so other exits/entries can still run. */
    public boolean deferUnsavedMaintenance(Suggestion declined,long now) {
        if(declined==null || declined.isLossCut() || sellOnly)return false;
        var offer=offers.getOffer(declined.getSlot());
        if(offer==null || offer.getItemId()!=declined.getItemId())return false;
        maintenanceRetryAfter.put(offer.getContinuationIdentity(),now+30);
        return true;
    }
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
    @Inject private com.flippingfriend.companion.CompanionClient companion;
    @Inject private com.flippingfriend.session.SellRelistIntent sellRelist;
    @Inject private com.flippingfriend.session.BuyRepriceIntent buyReprice;
    private volatile String observedAccount;
    private volatile SaleDiagnostics saleDiagnostics;
    private static final class SaleDiagnostics {
        final String account,settings;
        final com.flippingfriend.core.AccountLedgerSnapshot ledger;
        final MarketSnapshot market;
        final Map<Integer,com.flippingfriend.session.SaleMaintenanceDiagnostic> rows;
        SaleDiagnostics(String account,String settings,com.flippingfriend.core.AccountLedgerSnapshot ledger,
                MarketSnapshot market,Map<Integer,com.flippingfriend.session.SaleMaintenanceDiagnostic> rows) {
            this.account=account;this.settings=settings;this.ledger=ledger;this.market=market;
            this.rows=Collections.unmodifiableMap(rows);
        }
    }
    private String saleDiagnosticSettings() {
        return config.riskProfile()+":"+config.checkInterval()+":"+config.targetHoldMinutes()+":"+sellOnly;
    }
    /** Worker only. This never persists a workflow, stabilizes a price, or requests final authority. */
    private void evaluateSaleDiagnostics(MarketSnapshot market,Instant now) {
        String account=observedAccount,settings=saleDiagnosticSettings();
        var ledger=companion==null?null:companion.tradingLedger();
        Map<Integer,com.flippingfriend.session.SaleMaintenanceDiagnostic> rows=new HashMap<>();
        var horizon=TradingHorizon.of(config.riskProfile(),config.checkInterval(),config.targetHoldMinutes());
        for(var live:offers.getOffers()) {
            var offer=live.copyObservation();
            if(offer.isBuying() || !"SELLING".equals(offer.getState()) || offer.getRemaining()<=0)continue;
            var currentLedger=companion==null?null:saleLedger(offer.getItemId(),now);
            Position position=currentLedger==null?null:salePosition(currentLedger,offer.getItemId());
            var price=market.latest(offer.getItemId());
            String unavailable=currentLedger==null?"Managed scope unavailable: waiting for acknowledged, fresh canonical acquisition evidence.":null;
            var evaluation=unavailable!=null?null:new com.flippingfriend.session.SaleMaintenancePricing().evaluate(
                offer.getItemId(),offer.getPrice(),offer.getRemaining(),position,price,
                marketData.getSeries(offer.getItemId(),TIMESTEP),horizon,now,sellOnly,sellTiming,taxCalculator,
                offer.getFirstSeen(),offer.getLastFillObservedAt());
            rows.put(offer.getSlot(),new com.flippingfriend.session.SaleMaintenanceDiagnostic(offer,evaluation,unavailable,
                price,now.getEpochSecond(),currentLedger==null?now.getEpochSecond()+30:currentLedger.getObservedAt()+151,
                companion==null?Double.NaN:companion.originalSellExpectedMinutes(offer),currentLedger!=null && position==null));
        }
        if(java.util.Objects.equals(account,observedAccount) && settings.equals(saleDiagnosticSettings()))
            saleDiagnostics=new SaleDiagnostics(account,settings,ledger,market,rows);
    }
    /** UI reads only immutable results; changed evidence withdraws them until the worker evaluates it. */
    public List<com.flippingfriend.session.SaleMaintenanceDiagnostic> saleMaintenanceDiagnostics() {
        if(!accountMonitor.getState().isLoggedIn())return Collections.emptyList();
        var snapshot=saleDiagnostics;long now=Instant.now().getEpochSecond();
        boolean valid=snapshot!=null && java.util.Objects.equals(snapshot.account,observedAccount)
            && snapshot.settings.equals(saleDiagnosticSettings()) && snapshot.market==marketData.getSnapshot()
            // Publication clears this reference before replay; a new ledger also invalidates the
            // result. UI must not reread the durable outbox or perform model work.
            && (companion==null || snapshot.ledger==companion.lastLedger());
        List<com.flippingfriend.session.SaleMaintenanceDiagnostic> rows=new ArrayList<>();
        for(var live:offers.getOffers()) {
            var offer=live.copyObservation();
            if(offer.isBuying() || !"SELLING".equals(offer.getState()) || offer.getRemaining()<=0)continue;
            var row=valid?snapshot.rows.get(offer.getSlot()):null;
            if(row==null || !row.matches(offer,now))row=com.flippingfriend.session.SaleMaintenanceDiagnostic.unavailable(offer,now);
            if(!row.personal)rows.add(row);
        }
        rows.sort(Comparator.comparingInt(row->row.offer().getSlot()));
        return Collections.unmodifiableList(rows);
    }
    private volatile Suggestion continuationWait;
    private volatile List<Suggestion> replacementCandidates=Collections.emptyList();
    private volatile List<Suggestion> saleCandidates=Collections.emptyList();
    private List<Suggestion> stockSaleCandidates=Collections.emptyList(),listedStopCandidates=Collections.emptyList();
    private final Set<Integer> locallyDeclinedReplacements=java.util.concurrent.ConcurrentHashMap.newKeySet();
    public List<Suggestion> replacementCandidates(){return replacementCandidates;}
    /** Ordered proposals only. Each exact order still requires final companion authority. */
    public List<Suggestion> saleCandidates(){return saleCandidates;}
    /** A retained continuation's refusal must not become an account-wide entry or exit lock. */
    public boolean isContinuationWait(Suggestion suggestion) { return suggestion!=null && suggestion==continuationWait; }
    public void setObservedAccount(String account) {
        if(!java.util.Objects.equals(observedAccount,account)) {
            saleDiagnostics=null;
            advisedPrices.clear();reprice=null;maintenanceRetryAfter.clear();replacementCandidates=Collections.emptyList();locallyDeclinedReplacements.clear();
            saleCandidates=Collections.emptyList();stockSaleCandidates=Collections.emptyList();listedStopCandidates=Collections.emptyList();
        }
        observedAccount=account;
    }
    /** The saved account/episode commitment survives a changed card or reconnect before refresh. */
    public boolean isGuidedBuyCancellation(com.flippingfriend.session.TrackedOffer offer,String account,long now) {
        return buyReprice!=null && buyReprice.isGuidedCancellation(offer,account,now);
    }
    /** Client-thread acceptance only: a worker proposal is not a promise shown to the player. */
    public boolean recordPresentedMaintenance(Suggestion accepted,String expectedAccount,long now) {
        if(accepted==null || expectedAccount==null || !expectedAccount.equals(observedAccount))return false;
        if(accepted.getContinuationOriginIdentity()!=null && (accepted.getType()==SuggestionType.BUY || accepted.getType()==SuggestionType.SELL)) {
            var proof=accepted.getType()==SuggestionType.BUY
                ?buyReprice==null?null:buyReprice.acceptSuccessor(accepted.getContinuationOriginIdentity(),accepted.getItemId(),accepted.getPrice(),accepted.getQuantity(),offers,expectedAccount,now)
                :sellRelist==null?null:sellRelist.acceptSuccessor(accepted.getContinuationOriginIdentity(),accepted.getItemId(),accepted.getPrice(),accepted.getQuantity(),offers,expectedAccount,now);
            if(proof==null)return false;
            accepted.bindContinuationSuccessorProof(proof);
        }
        if(accepted.getType()==SuggestionType.SELL && accepted.isProfitKnown())
            return clearBuyPromise(accepted.getItemId(),Instant.ofEpochSecond(now));
        if(accepted.getType()==SuggestionType.CANCEL) {
            var cancelled=offers.getOffer(accepted.getSlot());
            if(cancelled!=null && cancelled.isBuying() && "BUYING".equals(cancelled.getState())
                && cancelled.getItemId()==accepted.getItemId() && cancelled.getPrice()==accepted.getPrice()
                && cancelled.getRemaining()==accepted.getQuantity() && cancelled.getRemaining()>0
                && accepted.getMaintenanceOfferIdentity()!=null
                && accepted.getMaintenanceOfferIdentity().equals(cancelled.getContinuationIdentity()))
                return clearBuyPromise(accepted.getItemId(),Instant.ofEpochSecond(now));
            return false;
        }
        boolean buy=accepted.getType()==SuggestionType.MODIFY_BUY;
        if(!buy && accepted.getType()!=SuggestionType.MODIFY_SELL)return true;
        var offer=offers.getOffer(accepted.getSlot());
        if(offer==null || offer.isBuying()!=buy || offer.getItemId()!=accepted.getItemId()
            || offer.getRemaining()!=accepted.getQuantity() || accepted.getMaintenanceOfferIdentity()==null
            || !accepted.getMaintenanceOfferIdentity().equals(offer.getContinuationIdentity())
            || !(buy?"BUYING":"SELLING").equals(offer.getState()))return false;
        return buy ? buyReprice!=null && buyReprice.advise(offer,expectedAccount,offers,now)
            : sellRelist!=null && sellRelist.advise(offer,expectedAccount,accepted.isProfitKnown(),offers,accepted.getSaleRepriceSource(),accepted.getSalePolicyReceipt(),now);
    }

    public java.util.List<com.flippingfriend.core.AcceptedSalePolicyProof> acceptedSalePolicies(String account) {
        return sellRelist==null?null:sellRelist.acceptedPolicies(account);
    }
    public boolean hasTradeEvidence() {
        var ledger = companion == null ? null : companion.tradingLedger();
        return ledger != null && ledger.getTradingPositions() != null && !ledger.hasUnboundedReconciliation()
            && (!ledger.getTradingPositions().isEmpty() || ledger.getMissedFlips() == null
                || ledger.getMissedFlips().stream().noneMatch(e -> "RECONCILE".equals(e.getType())));
    }
    public Collection<Position> tradingPositions()
    {
        // The local book remains a delivery/journal cache. Runtime exits use durable acquisition evidence.
        if (companion == null) {
            List<Position> fixtures = new ArrayList<>(positions.all());
            fixtures.removeIf(p -> !p.isCostKnown());
            return fixtures;
        }
        var ledger = companion.tradingLedger();
        if (ledger == null || ledger.getTradingPositions() == null || ledger.hasUnboundedReconciliation())
            return Collections.emptyList();
        List<Position> held = new ArrayList<>();
        for (var p : ledger.getTradingPositions()) {
            Position position = new Position(p.getItemId(), p.getItemName(), p.getQuantity(),
                p.getTotalCost(), p.getOpenedAt(), true);
            position.applyExitContexts(p.getExitContexts());
            held.add(position);
        }
        return held;
    }
    private Position tradingPosition(int itemId) {
        for (Position p : tradingPositions()) if (p.getItemId() == itemId) return p;
        return null;
    }
    public boolean reviewedOutOfTrading(int itemId)
    { return tradingPosition(itemId) == null; }

	@Inject
	public SuggestionEngine(MarketDataService marketData, FeatureEngine featureEngine,
		ManipulationFilter filter, Scorer scorer, Explainer explainer, Calibrator calibrator,
		TaxCalculator taxCalculator, AccountMonitor accountMonitor, BuyLimitTracker buyLimits,
		PositionBook positions, OfferTracker offers, SellTimingEngine sellTiming, TradePlans tradePlans,
		FlippingFriendConfig config, SkipList skipped,
		com.flippingfriend.model.arbitrage.ArbitrageRegistry arbitrageRegistry)
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
        if(sellOnly){reprice=null;if(buyReprice!=null)buyReprice.clear(observedAccount);}
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
		continuationWait=null;
        replacementCandidates=Collections.emptyList();
        saleCandidates=Collections.emptyList();stockSaleCandidates=Collections.emptyList();listedStopCandidates=Collections.emptyList();
        locallyDeclinedReplacements.clear();
		AccountState account = accountMonitor.getState();
		MarketSnapshot market = marketData.getSnapshot();
		Instant now = Instant.now();
        maintenanceRetryAfter.entrySet().removeIf(e->e.getValue()<=now.getEpochSecond());

		if (!account.isLoggedIn())
		{
			saleDiagnostics=null;
			return Suggestion.waiting("Log in to start",
				"Once you are logged in, the plugin will look at what you have and suggest a trade.");
		}

        evaluateSaleDiagnostics(market,now);
		if (account.isIronman())
		{
			return Suggestion.waiting("Ironman accounts cannot flip",
				"Ironman accounts can only buy bonds on the Grand Exchange, so there is nothing "
					+ "for this plugin to suggest.");
		}

		if(buyReprice!=null && companion!=null)
            buyReprice.observeRecordedSales(companion.tradingLedger(),observedAccount,now.getEpochSecond());

		// Collection and an explicit wind-down need current offers, not a market price.
		// StepGuide alone protects mid-entry presentation; every engine pass revalidates live work.
		Suggestion collect = collectSuggestion(market);
		if (collect != null) return collect;
		if (sellOnly)
		{
			Suggestion abandon = abandonBuySuggestion(market);
			if (abandon != null) return abandon;
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
        Suggestion deferredWait=replace!=null && replace.getType()==SuggestionType.WAIT?replace:null;
        Suggestion relist=null;
        List<Suggestion> relists=new ArrayList<>();Set<Integer> relistItems=new HashSet<>();

        if (sellRelist != null) {
            for(var remaining:sellRelist.collectedRequests(offers,observedAccount))if(account.getFreeSlots()>0) {
                relistItems.add(remaining.getItemId());
                Suggestion candidate=relistSuggestion(remaining, market, horizon, account, now);
                if(candidate!=null && candidate.getType()!=SuggestionType.WAIT) {
                    relists.add(candidate);
                    if(relist==null || candidate.isLossCut() && !relist.isLossCut())relist=candidate;
                } else if(candidate!=null)deferredWait=candidate;
            }
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
        List<Suggestion> stockSales=new ArrayList<>(stockSaleCandidates);
        // A generic holding exit cannot enlarge the exact cancelled-sale remainder.
        stockSales.removeIf(candidate->relistItems.contains(candidate.getItemId()));
        if(sell!=null && sell.getType()==SuggestionType.SELL)sell=stockSales.isEmpty()?null:stockSales.get(0);
        Suggestion listedStop=adjustSuggestion(market,horizon,now,true);
        List<Suggestion> orderedSales=new ArrayList<>();
        for(var candidate:relists)if(candidate.isLossCut())orderedSales.add(candidate);
        for(var candidate:stockSales)if(candidate.isLossCut())orderedSales.add(candidate);
        orderedSales.addAll(listedStopCandidates);
        for(var candidate:relists)if(!candidate.isLossCut())orderedSales.add(candidate);
        for(var candidate:stockSales)if(!candidate.isLossCut())orderedSales.add(candidate);
        saleCandidates=Collections.unmodifiableList(orderedSales);
        // Complete ordinary relists, but never let one hide a freshly supported stop elsewhere.
        if(relist!=null && relist.isLossCut())return relist;
        if(sell!=null && sell.isLossCut())return sell;
        if(listedStop!=null)return listedStop;
        if(relist!=null)return relist;
		if (sell != null)
		{
			return sell;
		}
        // awaitingReplacement still protects this accumulation's own partial fills. Other
        // ready holdings must exit before another purchase, including a promised replacement.
        if(replace!=null && replace.getType()!=SuggestionType.WAIT)return replace;
        if(deferredWait!=null) {
            var partialExit=exitAfterReplacementRefusal(locallyDeclinedReplacements);
            if(partialExit!=null)return partialExit;
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
        if(deferredWait!=null){continuationWait=deferredWait;return deferredWait;}

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
        return adjustSuggestion(market,horizon,now,false);
    }
    private Suggestion adjustSuggestion(MarketSnapshot market,TradingHorizon horizon,Instant now,boolean stopsOnly) {
        return adjustSuggestion(market,horizon,now,stopsOnly,false,Collections.emptySet());
    }
    private Suggestion adjustSuggestion(MarketSnapshot market,TradingHorizon horizon,Instant now,boolean stopsOnly,
            boolean buysOnly,Set<String> excluded)
	{
        List<Suggestion> stops=new ArrayList<>(),routineSales=new ArrayList<>();
        if(stopsOnly)listedStopCandidates=Collections.emptyList();
		for (TrackedOffer offer : mostStrandedFirst(offers.getOffers(), market))
		{
            if(buysOnly && (!offer.isBuying() || excluded.contains(offer.getContinuationIdentity())))continue;
            if(stopsOnly && offer.isBuying())continue;
            // Preserve the original first buy-vs-sale selection. Once a sale wins, collect
            // other eligible sales without creating an unseen buy cancellation promise.
            if(!stopsOnly && !routineSales.isEmpty() && offer.isBuying())continue;
            if(!stopsOnly && maintenanceRetryAfter.getOrDefault(offer.getContinuationIdentity(),0L)>now.getEpochSecond())continue;
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
			if (offer.isBuying())
			{
				ItemMetadata metadata = market.metadata(offer.getItemId());
				int limit = metadata != null ? metadata.getBuyLimit() : 0;
				if (limit > 0 && buyLimits.remaining(offer.getItemId(), limit, now) <= 0)
				{
					return Suggestion.builder(SuggestionType.CANCEL)
						.maintenanceSource(offer.getContinuationIdentity())
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
            if(offer.isBuying()) {
                long deadline=companion==null?offers.freezeBuyMaintenanceDeadline(offer.getSlot(),offer.getContinuationIdentity(),0,
                    horizon.legHorizonHours()*60,now.getEpochSecond(),observedAccount)
                    :companion.freezeBuyMaintenanceDeadline(offer,offers,horizon.legHorizonHours()*60,now.getEpochSecond());
                if(com.flippingfriend.session.BuyRepricePricing.overdue(offer.getFirstSeen(),offer.getLastFillObservedAt(),
                    deadline,price,horizon,now.getEpochSecond())) {
                    return Suggestion.builder(SuggestionType.CANCEL).maintenanceSource(offer.getContinuationIdentity())
                        .item(offer.getItemId(),name).slot(offer.getSlot()).price(offer.getPrice()).quantity(remaining)
                        .headline("Review the stalled "+name+" buy")
                        .detail("The original buy waiting budget has elapsed without recent observed fills. Cancel this remainder and collect its coins and any acquired units. A fresh plan will reassess the returned capital; this is not a promise to replace the same order.")
                        .build();
                }
            }

			if (horizon.isOutbid(offer.isBuying(), offer.getPrice(), price, trend))
			{
				if (offer.isBuying())
				{
					if(price.getLow()>=Integer.MAX_VALUE)continue;
					// Settled before it is judged, so the profitability gate below and the card the
					// player reads are talking about the same price. See advisedPrice.
					int newPrice = advisedPrices.buyReprice(offer.getItemId(), offer.getPrice(), price.getLow() + 1,
						now.getEpochSecond());
                    var buyQuote=com.flippingfriend.session.BuyRepricePricing.quote(offer.getItemId(),offer.getPrice(),remaining,
                        offer.getFirstSeen(),offer.getLastFillObservedAt(),price,trend,horizon,taxCalculator,now.getEpochSecond(),newPrice,config.minProfitPerFlip());
                    if(buyQuote==null)continue;
                    int targetSellPrice=buyQuote.target;
                    long expectedProfit=buyQuote.profit;

					// Chasing the price up is only worth doing while the trade is still worth doing.
					//
					// This branch worked out what the repriced flip would earn, put the figure on the
					// card, and then never looked at it -- so when the spread had closed while the
					// offer sat there, the advice was to cancel a working offer and re-place it at a
					// price where the round trip loses money. Reported from a live session, and it is
					// hard to argue with: nothing recommends entering a trade at a loss before it has
					// begun. The offer is being skipped over *and* it is no longer worth having, so
					// the honest answer is the slot, not a better price for a bad trade.
					if (!buyQuote.replace)
					{
						return Suggestion.builder(SuggestionType.CANCEL)
							.maintenanceSource(offer.getContinuationIdentity())
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
                    if (buyReprice == null && !buysOnly) reprice = new Reprice(offer.getItemId(), remaining, now.getEpochSecond());

					return Suggestion.builder(SuggestionType.MODIFY_BUY)
						.maintenanceSource(offer.getContinuationIdentity())
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
				if (!freshSaleQuote(price, now)) continue;
				var saleLedger = companion == null ? null : saleLedger(offer.getItemId(), now);
				if (companion != null && saleLedger == null) continue;
                Position position = companion == null ? tradingPosition(offer.getItemId())
                    : salePosition(saleLedger, offer.getItemId());
                var proposal = new com.flippingfriend.session.SaleMaintenancePricing().quote(
                    offer.getItemId(), offer.getPrice(), remaining, position, price,
                    marketData.getSeries(offer.getItemId(), TIMESTEP), horizon, now, sellOnly,
                    sellTiming, taxCalculator,offer.getFirstSeen(),offer.getLastFillObservedAt());
                if (proposal == null) continue;
                int newPrice = proposal.price, breakEven = proposal.floor;
                boolean costKnown = proposal.costKnown, isCut = proposal.lossCut;
                if(stopsOnly && !isCut)continue;
                long allocatedCost = proposal.allocatedCost;
				// Settled through noise, and then floored again: stickiness must never be the thing
				// that walks a price below what the position cost.
				newPrice = advisedPrice(offer.getItemId(), false, newPrice, now.getEpochSecond());
				if (!isCut && costKnown)
				{
					newPrice = Math.max(newPrice, breakEven);
				}
                if(offer.getPrice()-(long)newPrice<horizon.outbidDeadband(offer.getPrice()))continue;

				// What the new price is actually worth. The reprice card carried no figure at all, so a
				long change = costKnown ? taxCalculator.netProceeds(offer.getItemId(),newPrice,remaining)-allocatedCost : 0;
				Suggestion candidate=Suggestion.builder(SuggestionType.MODIFY_SELL)
                    .maintenanceSource(offer.getContinuationIdentity())
					.item(offer.getItemId(), name)
					.slot(offer.getSlot())
					.price(newPrice)
					.quantity(remaining)
					.expectedProfit(change)
                    .profitKnown(costKnown)
					.lossCut(isCut)
					.headline(isCut ? "Cut your losses on " + name : "Your " + name + " offer is too high")
					.detail(proposal.reason
						+ "\n\nThe Grand Exchange cannot change the price of an offer that is already "
						+ "running, so cancel this one, collect the items back, and list them again at "
						+ explainer.formatNumber(newPrice) + " gp.")
					.build();
                if(stopsOnly){stops.add(candidate);continue;}
                routineSales.add(candidate);
			}
		}
		if(stopsOnly){listedStopCandidates=Collections.unmodifiableList(stops);return stops.isEmpty()?null:stops.get(0);}
        if(!routineSales.isEmpty()) {
            // Each exact proposal must still pass final authority and durable presentation.
            saleCandidates=Collections.unmodifiableList(routineSales);
            return routineSales.get(0);
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
        if(buyReprice!=null) {
            if(!buyReprice.isPending(itemId,offers,observedAccount,now.getEpochSecond())) return false;
            if(replacementRejected(itemId,marketData.getSnapshot())) {buyReprice.clearItem(observedAccount,itemId);return false;}
            return true;
        }
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
        if(buyReprice!=null) return replaceDurableBuy(market,account,now);
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
		if (companion == null) tradePlans.plan(pending.itemId, targetSell,
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

    private boolean replacementRejected(int item,MarketSnapshot market) {
        return sellOnly || skippedItems().contains(item)
            || parseBlocked(config.blockedItems()).contains(market.getItemName(item).toLowerCase(java.util.Locale.ROOT));
    }
    private boolean clearBuyPromise(int item,Instant now) {
        boolean saved=buyReprice==null || buyReprice.clearItem(observedAccount,item);
        if(reprice!=null && reprice.itemId==item)reprice=null;
        return saved;
    }
    private Suggestion replaceDurableBuy(MarketSnapshot market,AccountState account,Instant now) {
        if(account.getFreeSlots()<=0)return null;
        List<Suggestion> candidates=new ArrayList<>();
        Suggestion waiting=null;var risk=companion==null?null:companion.currentRisk();
        for(var pending:buyReprice.collectedRequests(offers,observedAccount,now.getEpochSecond())) {
            int item=pending.itemId;
            if(replacementRejected(item,market)){buyReprice.clearItem(observedAccount,item);continue;}
            if(offers.itemsWithOpenOffers().contains(item))continue;
            locallyDeclinedReplacements.add(item);
            if(risk!=null && risk.isAvailable() && observedAccount.equals(risk.getAccountId())
                && risk.getValidUntil()>=now.getEpochSecond() && risk.getValuationPausedItems().contains(item)) {
                waiting=Suggestion.waiting("Replacement retained","This item's valuation is uncertain. Its exact cancelled remainder is retained while unrelated trades are checked.");continue;
            }
            LatestPrice price=market.latest(item);
            if(!freshSaleQuote(price,now) || price.getLow()>=Integer.MAX_VALUE) {
                waiting=Suggestion.waiting("Checking the replacement price","A current two-sided quote is required before replacing the retained remainder.");continue;
            }
            int quoted=price.getLow()+1;var metadata=market.metadata(item);
            int limit=metadata==null?0:metadata.getBuyLimit();
            int allowed=limit>0?buyLimits.remaining(item,limit,now):0;
            int quantity=replacementQuantity(pending.quantity,allowed,account.spendableCoins(config.includeBankValue(),config.bankrollCap()),quoted);
            if(quantity<=0) {
                waiting=Suggestion.waiting("Replacement waiting for funds or buy limit","The cancelled remainder is retained; fresh account evidence must support the next order.");continue;
            }
            // Only a bounded request: fresh companion authority still owns the displayed order.
            candidates.add(Suggestion.builder(SuggestionType.BUY).item(item,market.getItemName(item)).price(quoted).quantity(quantity)
                .continuationOrigin(pending.originIdentity)
                .headline("Recheck your "+market.getItemName(item)+" replacement").build());
            locallyDeclinedReplacements.remove(item);
        }
        replacementCandidates=Collections.unmodifiableList(candidates);
        return candidates.isEmpty()?waiting:candidates.get(0);
    }

    /** A declined entry continuation must not hide independent maintenance on the working board. */
    public Suggestion maintenanceAfterReplacementRefusal() {
        var account=accountMonitor.getState();var market=marketData.getSnapshot();
        if(account==null || !account.isLoggedIn() || account.isIronman() || !market.isUsable())return null;
        var now=Instant.now();var horizon=TradingHorizon.of(config.riskProfile(),config.checkInterval(),config.targetHoldMinutes());
        var stop=adjustSuggestion(market,horizon,now,true);
        if(stop!=null){saleCandidates=listedStopCandidates;return stop;}
        // This pass evaluates existing offers only. It cannot recreate/re-authorize a declined buy.
        return adjustSuggestion(market,horizon,now);
    }

    /** Only working BUY episodes; rejected sale proposals cannot hide independent maintenance. */
    public Suggestion buyMaintenanceAfterSaleRefusal(Set<String> excluded) {
        var account=accountMonitor.getState();var market=marketData.getSnapshot();
        if(account==null || !account.isLoggedIn() || account.isIronman() || !market.isUsable())return null;
        return adjustSuggestion(market,TradingHorizon.of(config.riskProfile(),config.checkInterval(),config.targetHoldMinutes()),
            Instant.now(),false,true,new HashSet<>(excluded));
    }

    /** After fresh entry authority refuses every proposed remainder, finish its acquired stock. */
    public Suggestion exitAfterReplacementRefusal(Set<Integer> declined) {
        var account=accountMonitor.getState();var market=marketData.getSnapshot();
        if(declined==null || !account.isLoggedIn() || !market.isUsable() || buyReprice==null)return null;
        Set<Integer> refused=new HashSet<>(declined);refused.addAll(locallyDeclinedReplacements);
        if(refused.isEmpty())return null;
        long now=Instant.now().getEpochSecond();Set<Integer> collected=new HashSet<>();
        for(var pending:buyReprice.collectedRequests(offers,observedAccount,now))
            if(refused.contains(pending.itemId))collected.add(pending.itemId);
        if(collected.isEmpty())return null;
        var exit=sellSuggestion(market,TradingHorizon.of(config.riskProfile(),config.checkInterval(),config.targetHoldMinutes()),
            account,Instant.ofEpochSecond(now),collected);
        saleCandidates=stockSaleCandidates;
        return exit;
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
			tradingPositions().isEmpty()
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
			.maintenanceSource(biggest.getContinuationIdentity())
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

    private final com.flippingfriend.session.AdvisedPriceTracker advisedPrices =
        new com.flippingfriend.session.AdvisedPriceTracker();

    /** Stable while still inside final authority's price budget; exact cost is rechecked by callers. */
    private int advisedPrice(int itemId,boolean buying,int fresh,long nowSeconds) {
        return advisedPrices.price(itemId,buying,fresh,nowSeconds);
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
        var account=accountMonitor.getState();
        java.util.List<Position> visible=new java.util.ArrayList<>();
        if(account!=null)for(var position:tradingPositions())if(account.quantityHeld(position.getItemId())>0)visible.add(position);
		return awaitingExit(visible, offers.listedForSale(), offers.itemsBeingBought());
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
			.maintenanceSource(give.getContinuationIdentity())
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
			.maintenanceSource(buy.getContinuationIdentity())
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

    private com.flippingfriend.core.AccountLedgerSnapshot saleLedger(int item, Instant now) {
        var ledger = companion == null ? null : companion.tradingLedger();
        if (ledger == null || !ledger.isValid() || ledger.getTradingPositions() == null
            || !java.util.Objects.equals(observedAccount, ledger.getAccountId())
            || ledger.getObservedAt() > now.getEpochSecond() || now.getEpochSecond()-ledger.getObservedAt()>150
            || ledger.hasUnboundedReconciliation())
            return null;
        for (var gap : ledger.getMissedFlips()) if ("RECONCILE".equals(gap.getType())) {
            var scope = ledger.getReconciliationScope();
            if (gap.getItemId() == item || scope == null || scope.get(gap.getEventId()) == null
                || scope.get(gap.getEventId()).contains(item))
                return null;
        }
        return ledger;
    }
    private Position salePosition(com.flippingfriend.core.AccountLedgerSnapshot ledger, int item) {
        for (var p : ledger.getTradingPositions()) if (p.getItemId()==item) {
            var position = new Position(item,p.getItemName(),p.getQuantity(),p.getTotalCost(),p.getOpenedAt(),true);
            position.applyExitContexts(p.getExitContexts()); return position;
        }
        return null;
    }
    private static boolean freshSaleQuote(LatestPrice price, Instant now) {
        return price != null && price.isComplete() && price.secondsSinceHigh(now)>=0
            && price.secondsSinceHigh(now)<=300 && price.secondsSinceLow(now)>=0 && price.secondsSinceLow(now)<=300;
    }
    private Suggestion relistSuggestion(com.flippingfriend.session.SellRelistIntent.CollectedRemainder remaining,
        MarketSnapshot market, TradingHorizon horizon,
        AccountState account, Instant now)
    {
        int item = remaining.getItemId();
        var ledger = saleLedger(item,now);
        if (ledger == null) return Suggestion.waiting("Waiting to verify the cancelled sale",
            "Fresh, consistent account trade evidence is required before relisting. See Missed flips for recorded gaps.");
        var price = market.latest(item);
        int quantity = Math.min(remaining.getQuantity(), account.getInventoryHoldings().getOrDefault(item, 0));
        Position position = salePosition(ledger,item);
        if (quantity<=0) return Suggestion.waiting("Collect the cancelled sale's items", "No returned quantity is currently visible in inventory.");
        if (position == null) {
            if (remaining.requiresManagedCost())
                return Suggestion.waiting("Waiting to verify remaining stock", "The managed sale's acquisition evidence is unavailable.");
            if (!freshSaleQuote(price,now))
                return Suggestion.waiting("Waiting for a current sale price", "The cancelled sale will be repriced when a fresh quote is available.");
            var personal=ManualSalePrice.quote(item,quantity,quantity,market,now);
            if(personal!=null && personal.getType()==SuggestionType.SELL)personal.bindContinuationOrigin(remaining.getOriginIdentity());
            return personal;
        }
        if (offers.itemsBeingBought().contains(item))
            return Suggestion.waiting("Purchase still in progress", "Finish the purchase before relisting the managed stock.");
        quantity = Math.min(quantity, Math.max(0,position.getQuantity()-offers.listedForSale().getOrDefault(item,0)));
        if (quantity<=0) return Suggestion.waiting("Stock already listed", "No verified unlisted quantity remains for this sale.");
        var series = marketData.getSeries(item,TIMESTEP);
        var decision = sellTiming.evaluateManagedExit(position,price,featureEngine.compute(item,series,BUCKET_SECONDS),
            series,horizon,now,config.minProfitPerFlip(),true,sellOnly,false);
        var relist=remaining.getSaleRepriceSource()==null?null:new com.flippingfriend.session.SaleMaintenancePricing().evaluateRelist(
            item,remaining.getOriginalPrice(),quantity,position,price,series,horizon,now,sellOnly,sellTiming,taxCalculator,
            remaining.getFirstSeenAt(),remaining.getProgressAt(),remaining.getAcceptedSalePolicyProof()==null?null:remaining.getAcceptedSalePolicyProof().getReceipt()).quote;
        if(remaining.getSaleRepriceSource()!=null && relist==null)
            return Suggestion.waiting("Rechecking the cancelled sale",
                "Current prices and market evidence do not support this relist. Its exact remainder and accepted reduction are retained; the old target is not restored.");
        if(relist!=null)decision=new com.flippingfriend.session.SellDecision(relist.lossCut?com.flippingfriend.session.SellDecision.Action.CUT
            :com.flippingfriend.session.SellDecision.Action.SELL,relist.price,relist.reason,Double.NaN,
            taxCalculator.netProceeds(item,relist.price,quantity)-relist.allocatedCost);
        if (!decision.isSell()) return Suggestion.waiting("Waiting for a supported sell price",decision.getReason());
        boolean cut = decision.getAction()==SellDecision.Action.CUT;
        long cost = com.flippingfriend.core.SaleAccounting.allocateCost(position.getTotalCost(),position.getQuantity(),quantity);
        int quoted = advisedPrice(item,false,decision.getPrice(),now.getEpochSecond());
        if (!cut && (relist!=null || !sellOnly || !freshSaleQuote(price,now))) {
            int floor = taxCalculator.breakEvenSellPriceForCost(item,cost,quantity);
            if (floor<=0) return Suggestion.waiting("No non-loss sell price is available", "No legal price covers the recorded cost after tax.");
            quoted = Math.max(quoted,relist==null?SellTimingEngine.protectedPrice(position,horizon,now,floor):floor);
        }
        var forecast = sellTiming.forecastForOrder(item,quoted,quantity,series,horizon);
        double minutes = forecast==null?Double.NaN:forecast.getExpectedTotalSeconds()/60;
        return Suggestion.builder(SuggestionType.SELL).item(item,position.getItemName()).quantity(quantity)
            .price(quoted).lossCut(cut).profitKnown(true).expectedProfit(taxCalculator.netProceeds(item,quoted,quantity)-cost)
            .saleRepriceSource(remaining.getSaleRepriceSource())
            .continuationOrigin(remaining.getOriginIdentity())
            .expectedMinutes(minutes).fillMinutes(0,minutes).executionForecast(forecast)
            .legCompletionProbability(forecast==null?null:forecast.getCompletionProbability())
            .headline((cut?"Cut your losses on ":"Relist your ")+position.getItemName()).detail(decision.getReason()).build();
    }

    /** Exact collected workflow context for a player-selected sale; final authority still rechecks it. */
    public com.flippingfriend.session.SellRelistIntent.CollectedRemainder selectedSaleReprice(int item,int quantity) {
        return selectedSaleReprice(item,quantity,null);
    }
    public boolean hasPendingSaleContinuation(int item) {
        return sellRelist!=null && observedAccount!=null && sellRelist.isPending(item,offers,observedAccount);
    }
    public com.flippingfriend.session.SellRelistIntent.CollectedRemainder selectedSaleReprice(int item,int quantity,String preferredOrigin) {
        if(quantity<=0 || sellRelist==null || observedAccount==null)return null;
        int inventory=accountMonitor.getState().getInventoryHoldings().getOrDefault(item,0);
        if(quantity>inventory)return null;
        var candidates=new ArrayList<com.flippingfriend.session.SellRelistIntent.CollectedRemainder>();
        for(var request:sellRelist.collectedRequests(offers,observedAccount))
            if(request.getItemId()==item && quantity<=request.getQuantity()) {
                if(preferredOrigin!=null && preferredOrigin.equals(request.getOriginIdentity()))return request;
                candidates.add(request);
            }
        return preferredOrigin==null && candidates.size()==1?candidates.get(0):null;
    }

	private Suggestion sellSuggestion(MarketSnapshot market, TradingHorizon horizon, AccountState account,
		Instant now)
	{ return sellSuggestion(market,horizon,account,now,null); }
    private Suggestion sellSuggestion(MarketSnapshot market,TradingHorizon horizon,AccountState account,
        Instant now,Set<Integer> declinedReplacements)
	{
		stockSaleCandidates=Collections.emptyList();
		if (tradingPositions().isEmpty())
		{
			positionStatuses.set(Collections.emptyMap());
			return null;
		}

		Map<Integer, PositionStatus> statuses = new HashMap<>();
		Map<Integer, Integer> listedForSale = offers.listedForSale();
		java.util.Set<Integer> stillBuying = offers.itemsBeingBought();
		SellCandidate best = null;
        List<SellCandidate> candidates=new ArrayList<>();

		for (Position position : tradingPositions())
		{
			int itemId = position.getItemId();
            if(declinedReplacements!=null && !declinedReplacements.contains(itemId))continue;
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
            int inventoryQuantity=account.getInventoryHoldings().getOrDefault(itemId,0);
            if(held>0 && !inInventory && !stillBuying.contains(itemId)) {
                int equipped=account.getEquipmentHoldings().getOrDefault(itemId,0);
                int banked=account.getBankHoldings().getOrDefault(itemId,0);
                String location=equipped>0?"Equipped stock is retained; move it to inventory before selling."
                    :banked>0?"Banked stock is retained; withdraw the units you want to sell to inventory."
                    :"Recorded stock is retained; collect it to inventory before selling.";
                statuses.put(itemId,new PositionStatus(SellDecision.hold(location,0),false,false));continue;
            }

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
                if(!stillBuying.contains(itemId)) {
                    statuses.put(itemId,new PositionStatus(SellDecision.hold(
                        "Recorded purchase retained; location is unconfirmed. No sale or disposal is assumed.",0),true,false));
                    continue;
                }
                // Filled units may still be uncollected. Keep the active-buy stop/cancel workflow;
                // the stillBuying branch below never proposes selling around the working buy.
                held=toSell;unconfirmed=true;
			}

			LatestPrice price = market.latest(itemId);
			List<Candle> series = marketData.getSeries(itemId, TIMESTEP);
			ItemFeatures features = featureEngine.compute(itemId, series, BUCKET_SECONDS);

			boolean isSkipped = skippedItems().contains(itemId);
			SellDecision decision = sellTiming.evaluateManagedExit(position, price, features, series, horizon, now,
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
			if (decision.getAction()!=SellDecision.Action.CUT && (declinedReplacements==null || !declinedReplacements.contains(itemId))
                && awaitingReplacement(itemId, account, now))
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

			int quantity = Math.min(inventoryQuantity, toSell);
            if(quantity<=0)continue;
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
			long remainderProfit = taxCalculator.netProceeds(itemId,decision.getPrice(),quantity)
                -(position.isCostKnown()?com.flippingfriend.core.SaleAccounting.allocateCost(position.getTotalCost(),position.getQuantity(),quantity):0);
			if (!remainderWarrantsItsOwnOffer(listed, remainderProfit, config.minProfitPerFlip()))
			{
				statuses.put(itemId, sellingStatus(itemId, quantity));
				continue;
			}
			SellCandidate candidate = new SellCandidate(position, decision, quantity, unconfirmed, inInventory);
            candidates.add(candidate);
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

        candidates.sort(SellCandidate.MOST_URGENT_FIRST);
        List<Suggestion> ready=new ArrayList<>();Suggestion waiting=null;
        for(var candidate:candidates) {
            var next=stockSaleSuggestion(candidate,market,horizon,now);
            if(next.getType()==SuggestionType.SELL)ready.add(next);else if(waiting==null)waiting=next;
        }
        stockSaleCandidates=Collections.unmodifiableList(ready);
        return ready.isEmpty()?waiting:ready.get(0);
    }
    private Suggestion stockSaleSuggestion(SellCandidate candidate,MarketSnapshot market,TradingHorizon horizon,Instant now) {
		Position position = candidate.position;
		SellDecision decision = candidate.decision;
		int itemId = position.getItemId();
		boolean cut = decision.getAction() == SellDecision.Action.CUT;

		// Held still through noise. See advisedPrice: the quote moves on every feed update, and a
		// card whose number changes while the player is typing it reads as the plugin changing its
		// mind about a trade it has not changed its mind about.
		int sellPrice = advisedPrice(itemId, false, decision.getPrice(), now.getEpochSecond());
        long allocatedCost=position.isCostKnown()?com.flippingfriend.core.SaleAccounting.allocateCost(
            position.getTotalCost(),position.getQuantity(),candidate.quantity):0;
        if(position.isCostKnown() && !cut && (!sellOnly || !freshSaleQuote(market.latest(itemId),now))) {
            int minimum=taxCalculator.breakEvenSellPriceForCost(itemId,allocatedCost,candidate.quantity);
            if(minimum<=0)return Suggestion.waiting("No non-loss sell price is available",
                "No legal Grand Exchange price covers the recorded cost of these units after tax.");
            sellPrice=Math.max(sellPrice,SellTimingEngine.protectedPrice(position,horizon,now,minimum));
        }
        var forecast = sellTiming.forecastForOrder(itemId,sellPrice,candidate.quantity,marketData.getSeries(itemId,TIMESTEP),horizon);
        double sellMinutes = forecast == null ? Double.NaN : forecast.getExpectedTotalSeconds()/60;

		long profit = taxCalculator.netProceeds(itemId,sellPrice,candidate.quantity)-allocatedCost;

		int collectionSlot = -1;
		if (!candidate.inInventory)
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
			.quantity(candidate.quantity)
			.slot(collectionSlot)
			.expectedProfit(profit)
            .profitKnown(position.isCostKnown())
			.expectedMinutes(sellMinutes)
			.fillMinutes(0, sellMinutes)
            .executionForecast(forecast)
            .legCompletionProbability(forecast == null ? null : forecast.getCompletionProbability())
			.headline((cut ? "Cut your losses on " : "Sell your ") + position.getItemName())
            .detail(decision.getReason());


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

		for (Position position : tradingPositions())
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
