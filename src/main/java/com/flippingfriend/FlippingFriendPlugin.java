package com.flippingfriend;

import com.flippingfriend.data.ItemMetadata;
import com.flippingfriend.data.MarketDataService;
import com.flippingfriend.companion.CompanionClient;
import com.flippingfriend.model.Calibrator;
import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionEngine;
import com.flippingfriend.model.SuggestionType;
import com.flippingfriend.model.TaxCalculator;
import com.flippingfriend.overlay.GeWidgetResolver;
import com.flippingfriend.overlay.GrandExchangeOverlay;
import com.flippingfriend.overlay.OfferEditorOverlay;
import com.flippingfriend.overlay.StepGuide;
import com.flippingfriend.session.AccountMonitor;
import com.flippingfriend.session.AccountState;
import com.flippingfriend.session.BuyLimitTracker;
import com.flippingfriend.session.OfferTracker;
import com.flippingfriend.session.OfferObservation;
import com.flippingfriend.session.PositionBook;
import com.flippingfriend.session.TradeJournal;
import com.flippingfriend.session.TradePlans;
import com.flippingfriend.session.TrackedOffer;
import com.flippingfriend.ui.FlippingFriendPanel;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wires the plugin together and owns its lifecycle.
 * <p>
 * The division of labour is strict, because getting it wrong in a RuneLite plugin produces
 * intermittent client freezes. Anything that reads live game state runs on the client thread and
 * does nothing but copy that state into plain objects. Anything that thinks — scoring thousands of
 * items, fetching prices, computing history — runs on a background executor against those copies.
 * Anything that draws runs on the Swing thread.
 * <p>
 * Offer actions remain player-operated. A configured player key may fill the already open GE
 * numeric prompt; it never accepts the input or places, cancels or collects an offer.
 */
@PluginDescriptor(
	name = "Flipping Friend",
	description = "Live Grand Exchange flipping assistant: what to buy, at what price, and when to sell",
	tags = {"grand", "exchange", "ge", "flip", "flipping", "merch", "money", "profit", "trade"}
)
public class FlippingFriendPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(FlippingFriendPlugin.class);

	/** Account state is cheap to read but not free; once a second is plenty. */
	private static final long ACCOUNT_REFRESH_MILLIS = 1000;
	/**
	 * Positions and the buy-limit ledger are otherwise only written when an offer changes, which can
	 * be a long time on a quiet session. Saving periodically means a crash costs minutes, not hours.
	 * The journal is append-only and already safe.
	 */
	private static final long PERSIST_INTERVAL_MILLIS = 60_000;

	/**
	 * How many refreshes in a row may fail before the panel says so.
	 * <p>
	 * Three. One is a blip -- a market fetch that timed out, a snapshot swapped underneath the pass
	 * -- and blanking the card for that would be worse than the fault itself. Three in a row is not a
	 * blip, and the alternative is a stale card the player has no way to recognise as stale.
	 */
	private static final int REFRESH_FAILURES_BEFORE_SAYING_SO = 3;

    private java.util.function.LongSupplier adviceClock=System::nanoTime;
	private final java.util.concurrent.atomic.AtomicInteger consecutiveRefreshFailures =
		new java.util.concurrent.atomic.AtomicInteger();

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Notifier notifier;

	@Inject
	private FlippingFriendConfig config;

	@Inject
	private MarketDataService marketData;

	@Inject
	private CompanionClient companion;

	@Inject
	private SuggestionEngine engine;

	@Inject
	private AccountMonitor accountMonitor;

	@Inject
	private OfferTracker offerTracker;

	@Inject
	private PositionBook positions;

	@Inject
	private BuyLimitTracker buyLimits;

	@Inject
	private com.flippingfriend.session.SkipList skipList;

	@Inject
	private TradeJournal journal;

	@Inject
	private TradePlans tradePlans;

	@Inject
	private Calibrator calibrator;



	@Inject
	private TaxCalculator taxCalculator;

	@Inject
	private StepGuide stepGuide;

    @Inject private com.flippingfriend.overlay.TradeConfirmGuard tradeConfirmGuard;
    @Inject private com.flippingfriend.overlay.GeInputAssist geInputAssist;

	@Inject
	private GeWidgetResolver widgetResolver;

	@Inject
	private GrandExchangeOverlay grandExchangeOverlay;

	@Inject
	private OfferEditorOverlay offerEditorOverlay;

	@Inject
	private FlippingFriendPanel panel;

	private NavigationButton navigationButton;
	private ExecutorService worker;
	private boolean drawdownAlerted = false;
    private String drawdownAlertWindow;

	private final com.flippingfriend.model.HeldStockDropAlerts heldDropAlerts = new com.flippingfriend.model.HeldStockDropAlerts();
	@Inject private DiscordWebhookClient discordWebhook;

	private final AtomicBoolean accountDataLoaded = new AtomicBoolean();
	private final java.util.concurrent.atomic.AtomicLong offerObservationGeneration = new java.util.concurrent.atomic.AtomicLong();
	private final java.util.concurrent.atomic.AtomicLong adviceObservationRevision = new java.util.concurrent.atomic.AtomicLong();
	// Client-thread snapshot boundary. LOGGED_IN alone can precede server-populated slot data.
	private boolean offerSnapshotReady;
	private boolean restoreOffersPending;
	private long pendingRestoreGeneration;
	private String pendingRestoreProfile;
	private final boolean[] nonemptyOfferSeen = new boolean[8];
	private volatile long lastAccountRefresh;
	private volatile long lastEngineRefresh;
	private final AtomicBoolean engineRefreshInFlight=new AtomicBoolean(), engineRefreshRequested=new AtomicBoolean();
	private final java.util.concurrent.atomic.AtomicLong engineRefreshEpoch=new java.util.concurrent.atomic.AtomicLong();
	private volatile long lastPersist;
	private volatile boolean exemptionsResolved;
	private volatile Suggestion lastNotified = Suggestion.idle();

	@Provides
	FlippingFriendConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FlippingFriendConfig.class);
	}

	@Override
	protected void startUp()
	{
        engineRefreshEpoch.incrementAndGet();engineRefreshInFlight.set(false);engineRefreshRequested.set(false);
        geInputAssist.start();
		worker = Executors.newSingleThreadExecutor(r ->
		{
			Thread thread = new Thread(r, "flipping-friend-engine");
			thread.setDaemon(true);
			return thread;
		});

		overlayManager.add(grandExchangeOverlay);
		overlayManager.add(offerEditorOverlay);

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/com/flippingfriend/icon.png");
		navigationButton = NavigationButton.builder()
			.tooltip("Flipping Friend")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);

		// Skipping or blocking has to reach the walkthrough, and only this loop writes to it.
		panel.setOnRejection(this::requestEngineRefresh);

		// Before start(), so the very first thing the market service does is ask next door for a feed
		// rather than the internet for one. The companion runs whether or not the game is open and
		// holds prices no more than a minute old, so this is the difference between advising in
		// seconds and advising in minutes.
		marketData.setCompanion(companion);
		marketData.setUpdateListener(this::onMarketUpdated);
		offerTracker.setChangeListener(this::onOffersChanged);
		offerTracker.setAbandonedBuyListener(this::onBuyAbandoned);
		offerTracker.setRiskProfileName(config.riskProfile().name());
		marketData.start();

		journal.startSession();


		log.debug("Flipping Friend started");
	}

	@Override
	protected void shutDown()
	{
        engineRefreshEpoch.incrementAndGet();engineRefreshInFlight.set(false);engineRefreshRequested.set(false);
        geInputAssist.stop();
        stepGuide.reset(); tradeConfirmGuard.reset();
		offerObservationGeneration.incrementAndGet();
		offerSnapshotReady = false;
		restoreOffersPending = false;
		java.util.Arrays.fill(nonemptyOfferSeen, false);
		synchronized (pendingOffers) { pendingOffers.clear(); }
		companion.pauseOfferReplay();
		marketData.setUpdateListener(null);
		offerTracker.setChangeListener(null);
		offerTracker.setAbandonedBuyListener(null);
		marketData.stop();

		overlayManager.remove(grandExchangeOverlay);
		overlayManager.remove(offerEditorOverlay);
		clientToolbar.removeNavigation(navigationButton);
        panel.closeWindows();
        stepGuide.setEditorQuote(null);
		navigationButton = null;


		persist();

		if (worker != null)
		{
			worker.shutdownNow();
			worker = null;
		}

		accountDataLoaded.set(false);
		exemptionsResolved = false;
		engine.clearSkipped();
		companion.clearIncumbent();


		log.debug("Flipping Friend stopped");
	}

	// ------------------------------------------------------------------- events

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
        if (geInputAssist != null) geInputAssist.reset();
        if (stepGuide != null) stepGuide.reset();
        if (tradeConfirmGuard != null) tradeConfirmGuard.reset();
		GameState state = event.getGameState();
		if (state == GameState.LOGGED_IN)
		{
			offerObservationGeneration.incrementAndGet();
			offerSnapshotReady = false;
			restoreOffersPending = false;
			java.util.Arrays.fill(nonemptyOfferSeen, false);
			// The RuneLite profile key is not available the instant the game state flips, so the
			// actual load is attempted from the scheduled tick until it succeeds.
			accountDataLoaded.set(false);
			// Sell-only is a way to end a session, so a new one starts out of it. Deliberately not a
			// stored setting: a switch that silently stops every buy is the last thing that should
			// survive a restart and be forgotten about.
			engine.setSellOnly(false);
		}
		else if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING
			|| state == GameState.LOGGING_IN || state == GameState.CONNECTION_LOST)
		{
			offerObservationGeneration.incrementAndGet();
			offerSnapshotReady = false;
			restoreOffersPending = false;
			java.util.Arrays.fill(nonemptyOfferSeen, false);
			synchronized (pendingOffers) { pendingOffers.clear(); }
			companion.pauseOfferReplay();
			engine.setObservedAccount(null);
			persist();
			accountMonitor.reset();
			accountDataLoaded.set(false);
		}
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		// Login/logout resets are not evidence that the player collected an offer.
		if (client.getGameState() != GameState.LOGGED_IN || event == null || event.getOffer() == null) return;
		GrandExchangeOfferChanged captured = new GrandExchangeOfferChanged();
		captured.setSlot(event.getSlot()); captured.setOffer(OfferObservation.copyOf(event.getOffer()));
		event = captured;
		int observedSlot = event.getSlot();
		if (observedSlot < 0 || observedSlot >= nonemptyOfferSeen.length) return;
		// RuneLite initializes all slots with EMPTY callbacks on login before server data arrives.
		// A terminal/nonempty callback followed by EMPTY in this same login is still real evidence.
		if (event.getOffer().getState() == net.runelite.api.GrandExchangeOfferState.EMPTY)
		{
			if (!offerSnapshotReady && !nonemptyOfferSeen[observedSlot]) return;
		}
		else nonemptyOfferSeen[observedSlot] = true;
		invalidateAccountAdvice();
		accountMonitor.observeOffer(event.getSlot(),event.getOffer());
		// The game replays every slot on login, and it does so before this plugin knows which account
		// it is looking at. Booking those fills against an offer book that has not been read off disk
		// yet made every replayed offer look brand new, so its entire running total was counted as a
		// fresh fill -- one sale of 4,337 mithril bars was recorded six times over. Load first; if the
		// profile is not available yet, skip the event rather than guess. Nothing is lost by skipping,
		// because fills are tracked as a running total and the next update for the slot catches up.
		loadAccountDataIfNeeded();
		if (!accountDataLoaded.get())
		{
			// Held, not dropped.
			//
			// The claim above -- that nothing is lost because the next update catches up -- is only
			// true while another update is still coming. It is not true of the login replay: if the
			// offer finished while the plugin was away and the player collects it, the next update for
			// that slot is EMPTY, which carries no fill to catch up on. The buy is then gone, and with
			// it the only record of what the item cost. Keeping copied observations in order
			// replays it the moment the account is known.
			synchronized (pendingOffers) { pendingOffers.addLast(event); }
			return;
		}

		offerTracker.setMarket(marketData.getSnapshot());
		drainPendingOffers();
		offerTracker.onOfferChanged(event.getSlot(), event.getOffer());
		if (!offerTracker.hasCompleteOfferEvidence())
		{
			withdrawUnsupportedPriceAdvice();
			return;
		}
		if (event.getOffer().getState() == net.runelite.api.GrandExchangeOfferState.EMPTY)
		{
			publishOfferClearedToCompanion(event.getSlot());
		}
		else
		{
			publishOfferToCompanion(offerTracker.getOffer(event.getSlot()));
		}
		persist();

		// The account refresh runs on the client thread, so it has to complete before the engine
		// recomputes. Firing both at once meant the new suggestion was calculated against the slot
		// count and committed capital from *before* the offer was placed — which is how the panel
		// ended up repeating an instruction the player had already carried out.
		refreshAccountState(this::requestEngineRefresh);
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		invalidateAccountAdvice();
        accountMonitor.observeBankContainer(event.getContainerId(),event.getItemContainer());
		refreshAccountState(this::requestEngineRefresh);
	}

    @Subscribe
    public void onGameTick(net.runelite.api.events.GameTick event) {
        if(client.getGameState()==GameState.LOGGED_IN) {
            offerSnapshotReady=true;
            if(restoreOffersPending) {
                restoreOffersPending=false;
                restoreCurrentOffers(pendingRestoreGeneration,pendingRestoreProfile);
            }
        }
        refreshSellSetupQuote();
    }
    @Subscribe
    public void onVarbitChanged(net.runelite.api.events.VarbitChanged event) { refreshSellSetupQuote(); }

    private void refreshSellSetupQuote() {
        int[] setup = client.getGameState() == GameState.LOGGED_IN && accountDataLoaded.get() && hasProfile() ? widgetResolver.sellSetup() : null;
        Suggestion quote = setup == null || !offerTracker.hasCompleteOfferEvidence() ? null
            : com.flippingfriend.model.ManualSalePrice.quote(setup[0], setup[1],
                accountMonitor.getState().getInventoryHoldings().getOrDefault(setup[0], 0),
                marketData.getSnapshot(), Instant.now());
        Suggestion previous = stepGuide.getSuggestion();
        stepGuide.setEditorQuote(quote);
        recordVisibleAdvice();
        if (panel != null && !stepGuide.getSuggestion().sameAs(previous)) panel.refresh();
    }
    private void recordVisibleAdvice() {
        if (client.getGameState() != GameState.LOGGED_IN || !accountDataLoaded.get()
            || !hasProfile() || !offerTracker.hasCompleteOfferEvidence() || stepGuide.isPaused()) return;
        Suggestion accepted=stepGuide.getSuggestion();
        int stop=accepted==null?0:(int)Math.round(accepted.getPrice()*(1-config.riskProfile().getLossCutPct()));
        String account=com.flippingfriend.core.AccountIdentity.fromProfileKey(configManager.getRSProfileKey());
        boolean saved=engine.recordPresentedMaintenance(accepted,account,java.time.Instant.now().getEpochSecond());
        if(accepted!=null && (accepted.getType()==SuggestionType.MODIFY_BUY || accepted.getType()==SuggestionType.MODIFY_SELL
                || accepted.getType()==SuggestionType.CANCEL) && !saved) {
            stepGuide.withdrawAdvice(Suggestion.waiting("Saving this trade workflow",
                "The cancellation/replacement sequence could not be committed for the current offer. Keep the offer working; existing records are retained and guidance will retry."));
            if(engine.deferUnsavedMaintenance(accepted,java.time.Instant.now().getEpochSecond()))requestEngineRefresh();
            return;
        }
        companion.recordPresentedAdvice(accepted,account,stop);
    }
    @Subscribe
    public void onMenuEntryAdded(net.runelite.api.events.MenuEntryAdded event) { tradeConfirmGuard.onMenuEntryAdded(event); }
    @Subscribe
    public void onMenuOpened(net.runelite.api.events.MenuOpened event) { tradeConfirmGuard.onMenuOpened(event); }
    @Subscribe
    public void onBeforeRender(net.runelite.api.events.BeforeRender event) {
        tradeConfirmGuard.beforeRender();
        refreshInputAssist();
    }
    @Subscribe
    public void onVarClientIntChanged(net.runelite.api.events.VarClientIntChanged event) { refreshInputAssist(); }
    @Subscribe
    public void onScriptPostFired(net.runelite.api.events.ScriptPostFired event) {
        if (event.getScriptId() == net.runelite.api.ScriptID.GE_ITEM_SEARCH
            || event.getScriptId() == net.runelite.api.ScriptID.GE_OFFERS_SETUP_BUILD) refreshInputAssist();
    }
    private void refreshInputAssist() {
        geInputAssist.refresh(accountDataLoaded.get() && offerTracker.hasCompleteOfferEvidence());
    }

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
        // Confirm remains a player click. Input assistance is a separate configured key press.
        tradeConfirmGuard.onMenuOptionClicked(event);
        if (!event.isConsumed()) stepGuide.onMenuOptionClicked(event);
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == WidgetUtil.componentToInterface(InterfaceID.GeOffers.UNIVERSE))
		{
			stepGuide.onSetupClosed();
            recordVisibleAdvice();
            if (panel != null) panel.refresh();
            tradeConfirmGuard.reset();
            geInputAssist.reset();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!FlippingFriendConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		invalidateAccountAdvice();

        if ("fillRecommendedInputKey".equals(event.getKey()) || "showSuggestedSearchItem".equals(event.getKey())) {
            geInputAssist.reset();
        }

		offerTracker.setRiskProfileName(config.riskProfile().name());
		if ("riskProfile".equals(event.getKey()) || "blockedItems".equals(event.getKey()))
		{
			engine.clearSkipped();
			// A held recommendation belongs to the settings it was chosen under.
			companion.clearIncumbent();
		}
		requestEngineRefresh();
		panel.refresh();
	}

	/**
	 * The heartbeat. Runs off the client thread, so it must never touch the client directly — the
	 * one thing it does need from the game is pushed to it by {@link #refreshAccountState()}.
	 */
	@Schedule(period = 2, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void tick()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		loadAccountDataIfNeeded();
		requestPendingOfferReplay();
		resolveTaxExemptionsIfNeeded();

		long now = System.currentTimeMillis();
		if (now - lastAccountRefresh > ACCOUNT_REFRESH_MILLIS)
		{
			lastAccountRefresh = now;
			refreshAccountState();
		}

		if (now - lastEngineRefresh > config.refreshSeconds() * 1000L)
		{
			requestEngineRefresh();
		}

		if (now - lastPersist > PERSIST_INTERVAL_MILLIS)
		{
			lastPersist = now;
			persist();
		}
	}

	// ------------------------------------------------------------------ helpers


        private void requestPendingOfferReplay()
        {
                if (!accountDataLoaded.get()) return;
                ExecutorService executor = worker;
                if (executor == null || executor.isShutdown()) return;
                companion.requestPendingOfferReplay(executor);
        }

	private void refreshAccountState()
	{
		refreshAccountState(null);
	}

	/**
	 * @param whenDone run once the client thread has actually published the new state, for callers
	 *                 whose next step depends on it being current
	 */
	private void refreshAccountState(Runnable whenDone)
	{
		clientThread.invokeLater(() ->
		{
			AccountState state = accountMonitor.refresh();
			if (state.isLoggedIn())
			{
				syncHoldings(state);
			}
			if (whenDone != null)
			{
				whenDone.run();
			}
		});
	}

	/**
	 * Keeps the position book in step with what the account actually holds.
	 * <p>
	 * Only what the plugin bought is tracked. Opening your bank must not turn its contents into
	 * flipping stock — a bank is gear, supplies and long-term holdings, and quietly deciding to
	 * sell it would be both wrong and alarming. Bank contents are still <em>read</em>, because a
	 * position the plugin bought and then banked still needs to be sellable, but they never
	 * enroll pre-existing belongings as new trade positions.
	 */
	private void syncHoldings(AccountState state)
	{
		Map<Integer, Integer> holdings = state.getHoldings();

		// Physical holdings never enroll trade stock, including an old saved adoption setting.

		// Positive observations can reconcile ownership; missing container data is not a disposal.
		positions.reconcile(holdings, offerTracker.listedForSale(), state.isBankSeen());
	}

	/**
	 * Offer events that arrived before this plugin knew whose account it was looking at.
	 * <p>
	 * Preserve arrival order, including a terminal fill followed by EMPTY in the same slot.
	 */
	private final java.util.Deque<GrandExchangeOfferChanged> pendingOffers = new java.util.ArrayDeque<>();

	/** Client thread only: replay copied observations in arrival order, publishing every transition. */
	private void drainPendingOffers()
	{
		if (!accountDataLoaded.get() || client.getGameState() != GameState.LOGGED_IN
			|| !offerTracker.hasCompleteOfferEvidence())
		{
			return;
		}
		while (true)
		{
			GrandExchangeOfferChanged event;
			synchronized (pendingOffers) { event = pendingOffers.pollFirst(); }
			if (event == null) return;
			offerTracker.onOfferChanged(event.getSlot(), event.getOffer());
			if (!offerTracker.hasCompleteOfferEvidence()) { withdrawUnsupportedPriceAdvice(); return; }
			if (event.getOffer().getState() == net.runelite.api.GrandExchangeOfferState.EMPTY)
				publishOfferClearedToCompanion(event.getSlot());
			else publishOfferToCompanion(offerTracker.getOffer(event.getSlot()));
			persist();
		}
	}

	/** Read authenticated slots after loading; pre-login resets were intentionally ignored. */
	private void restoreCurrentOffers(long generation, String profile)
	{
		if (generation != offerObservationGeneration.get() || !accountDataLoaded.get()
			|| client.getGameState() != GameState.LOGGED_IN
			|| !java.util.Objects.equals(profile, configManager.getRSProfileKey())) return;
		if (!offerSnapshotReady) {
			restoreOffersPending = true;
			pendingRestoreGeneration = generation;
			pendingRestoreProfile = profile;
			return;
		}
		restoreOffersPending = false;
		drainPendingOffers();
		net.runelite.api.GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null) return;
		for (int slot = 0; slot < offers.length; slot++)
		{
			if (offers[slot] == null) continue;
			GrandExchangeOfferChanged event = new GrandExchangeOfferChanged();
			event.setSlot(slot); event.setOffer(offers[slot]); onGrandExchangeOfferChanged(event);
		}
	}

	private void loadAccountDataIfNeeded()
	{
		if (accountDataLoaded.get())
		{
			return;
		}

		// Returns false until RuneLite has established which account is logged in.
		if (!hasProfile())
		{
			return;
		}

		// Loaded under the lock and the gate opened afterwards.
		//
		// This used to set the flag first and then read the files, on a background thread, while the
		// client thread was free to walk straight past the gate and record a buy into a book that
		// PositionBook.load() was about to clear. The window is milliseconds and the loss is total, so
		// the order matters more than the contention does.
		synchronized (accountLoadLock)
		{
			if (accountDataLoaded.get())
			{
				return;
			}
			buyLimits.load();
			skipList.load();
			positions.load();
			tradePlans.load();
			offerTracker.load();
			journal.load();
			calibrator.rebuild(journal.getHistory());
			accountDataLoaded.set(true);
			companion.resumeOfferReplay();
			log.debug("loaded account data ({} journal entries)", journal.getHistory().size());

			long generation = offerObservationGeneration.get();
			String profile = configManager.getRSProfileKey();
			clientThread.invokeLater(() -> restoreCurrentOffers(generation, profile));
		}
	}

	private final Object accountLoadLock = new Object();

	/**
	 * A buy the player cancelled: decide whether that was a rejection of the item.
	 * <p>
	 * Here rather than in the offer ledger because it takes two things only this class can see: what
	 * the plugin itself was asking for, and how long an unfilled offer has to have stood before
	 * cancelling it means anything.
	 *
	 * @param cancelled   copied exact episode and cumulative progress at cancellation
	 * @param minutesOpen how long the offer had been standing
	 */
	private void onBuyAbandoned(com.flippingfriend.session.TrackedOffer cancelled, long minutesOpen)
	{
        int itemId=cancelled.getItemId(),filled=cancelled.getQuantityFilled(),ordered=cancelled.getTotalQuantity();
		// Sell-only asked for this cancellation itself. Ending a session is not a view about the item,
		// and the mode clears on login -- so without this exemption a routine wind-down would take
		// every open item off the table for the next session too.
		if (engine.isSellOnly())
		{
			return;
		}

        String profile=configManager.getRSProfileKey();
        if(profile==null || profile.isEmpty())return;
        // The displayed card may already have changed. Only a committed exact-episode promise
        // establishes that this cancellation follows the walkthrough.
		if (engine.isGuidedBuyCancellation(cancelled,com.flippingfriend.core.AccountIdentity.fromProfileKey(profile),
            java.time.Instant.now().getEpochSecond()))
		{
			return;
		}

		// If the user manually clicks the "Modify" button, the client immediately transitions the
		// interface to the setup panel loaded with the same item. The player is not abandoning
		// the offer, they are just tweaking it.
		if (widgetResolver.isSetupOpen() && client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH) == itemId)
		{
			return;
		}

		long stale = TradingHorizon.of(config.riskProfile(), config.checkInterval()).staleOfferMinutes();
		if (!com.flippingfriend.session.SkipList.isRejection(filled, ordered, minutesOpen, stale))
		{
			return;
		}

		skipList.skipUntilCooldownEnds(itemId, java.time.Instant.now());
		// Written now rather than at the next scheduled save: the whole point of the cooldown is that
		// it survives the client being closed, and a crash between here and then would lose it.
		skipList.save();
		companion.clearIncumbent();
		requestEngineRefresh();
	}

	private boolean hasProfile()
	{
		String key = configManager.getRSProfileKey();
		return key != null && !key.isEmpty();
	}

	private void resolveTaxExemptionsIfNeeded()
	{
		if (exemptionsResolved)
		{
			return;
		}
		Map<Integer, ItemMetadata> metadata = marketData.getSnapshot().getMetadata();
		if (metadata.isEmpty())
		{
			return;
		}
		taxCalculator.resolveExemptions(metadata.values());
		exemptionsResolved = true;
		log.debug("resolved {} tax-exempt items", taxCalculator.exemptItemCount());
	}

	private void onMarketUpdated()
	{
		offerTracker.setMarket(marketData.getSnapshot());
		requestEngineRefresh();
	}

	private void onOffersChanged()
	{
		calibrator.rebuild(journal.getHistory());
		panel.refresh();
	}

	/** Client-thread boundary: withdraw stale entries before deferred recomputation. */
	private void invalidateAccountAdvice()
	{
		adviceObservationRevision.incrementAndGet();
		companion.invalidateEntryAdvice();
		Suggestion shown = stepGuide == null ? null : stepGuide.getSuggestion();
		if (shown != null && shown.getType() == SuggestionType.BUY)
		{
			stepGuide.setSuggestion(Suggestion.waiting("Checking current account state",
				"Cash, offers or preferences changed. Waiting for a fresh recommendation."));
			if (panel != null) panel.refresh();
		}
	}

	private void requestEngineRefresh()
	{
		if(worker==null || worker.isShutdown())return;
		engineRefreshRequested.set(true);
		if(!engineRefreshInFlight.compareAndSet(false,true))return;
		engineRefreshRequested.set(false);
		beginEngineRefresh(engineRefreshEpoch.get());
	}
	private void finishEngineRefresh(long epoch)
	{
		if(epoch!=engineRefreshEpoch.get())return;
		engineRefreshInFlight.set(false);
		if(engineRefreshRequested.get())requestEngineRefresh();
	}
	private void beginEngineRefresh(long epoch)
	{
		long guidanceStarted=System.nanoTime();
		ExecutorService executor = worker;
		if (executor == null || executor.isShutdown())
		{
			finishEngineRefresh(epoch);
			return;
		}

		lastEngineRefresh = System.currentTimeMillis();

		clientThread.invokeLater(() ->
		{
			boolean submitted=false;
			try {
            if(epoch!=engineRefreshEpoch.get())return;
			engineRefreshRequested.set(false);
            if(!offerTracker.hasCompleteOfferEvidence()){withdrawUnsupportedPriceAdvice();return;}
            if(client.getGameState()!=GameState.LOGGED_IN || !accountDataLoaded.get() || !hasProfile())return;
            if(!offerSnapshotReady)return;
            com.flippingfriend.session.AccountState observedState=accountMonitor.refresh();
			boolean isSetupOpen = widgetResolver.isSetupOpen();
			int setupItemId = isSetupOpen ? client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH) : -1;
			Suggestion active = stepGuide.getSuggestion();

			// Hold the reprice card still while the player is in the editor for that same item, and
			// let it go the instant they are not.
			//
			// This used to be released only when the whole Grand Exchange was closed, and the engine
			// returns a held card ahead of everything else -- so closing the offer editor while
			// leaving the Exchange open left the plugin frozen on a reprice for an offer that no
			// longer existed. Every recommendation behind it was dropped for as long as the window
			// stayed open, which is the whole session for anyone who flips with it up. Cancelling an
			// offer is precisely the action that closes the editor and leaves the Exchange open, so
			// the card most likely to wedge the engine was the one the player had just acted on.
			boolean midAdjustment = active != null && isSetupOpen
				&& setupItemId == active.getItemId() && active.getType().isModify();
			engine.setPendingAdjustment(midAdjustment ? active : null);
			java.util.List<TrackedOffer> observedOffers = new java.util.ArrayList<>();
			for (TrackedOffer offer : offerTracker.getOffers()) observedOffers.add(offer.copyObservation());
			String observedProfile = configManager.getRSProfileKey();
			String observedAccount = observedProfile == null || observedProfile.isBlank() ? null
				: com.flippingfriend.core.AccountIdentity.fromProfileKey(observedProfile);
			long observedGeneration = offerObservationGeneration.get();
            long observedRevision=adviceObservationRevision.get();
            var planningObservation=com.flippingfriend.session.PlanningAccountObservation.capture(observedAccount,
                observedState,client.getGrandExchangeOffers(),observedOffers,java.time.Instant.now().getEpochSecond());
            if(planningObservation==null) {
                companion.invalidateEntryAdvice();
                stepGuide.setSuggestion(Suggestion.waiting("Waiting for complete account observations",
                    "Cash and all Grand Exchange slots must agree before new advice is shown."));
                panel.refresh();return;
            }

			executor.execute(() ->
			{
				try
				{
                    if(epoch!=engineRefreshEpoch.get())return;
					if (observedGeneration != offerObservationGeneration.get() || observedRevision!=adviceObservationRevision.get()) {
                        requestEngineRefresh();return;
                    }
					if (!offerTracker.hasCompleteOfferEvidence())
					{
						clientThread.invokeLater(this::withdrawUnsupportedPriceAdvice);
						return;
					}

					// The companion owns new-entry portfolio selection. The legacy engine remains here only
					// for collect/sell recovery while its equivalent companion workflows are introduced.
					// The buy-limit ledger travels too. Offer events are fire-and-forget over loopback, so
					// one lost while the companion restarts is lost to it for good -- and the next plan
					// then orders over the limit, which stops filling part-way with no error at all.
					// What the player has already rejected goes with it, so the plan leaves those trades
					// out rather than having them filtered off the card afterwards. They were only ever
					// applied at selection, so the portfolio list went on showing a blocked item as the
					// top-ranked trade and the headline rate was computed from trades never on offer.
					companion.publishAccount(observedState, config, 0,
						committedByItem(), buyLimits.activeWindows(java.time.Instant.now()),
						com.flippingfriend.model.SuggestionEngine.parseBlocked(config.blockedItems()),
						engine.skippedItems(), offerTracker.itemsWithOpenOffers(), engine.isSellOnly(), positions.all(), observedOffers, observedAccount, planningObservation);
					long publicationFinished=System.nanoTime();

					// The companion owns new-position selection and gets first chance to supply collect advice.
					// The built-in engine remains as the collect fallback and still owns reprice and sell decisions.
					// Always refresh the plan, even on a cycle where the built-in engine has something to
					// sell or collect. It used to be fetched only inside the buy branch, so on any other
					// cycle the portfolio list kept rendering an ever-older plan with nothing to say it
					// was stale.
					companion.refreshPlan();
					long planFinished=System.nanoTime();
                    var risk=companion.currentRisk();
                    if(observedGeneration!=offerObservationGeneration.get() || observedRevision!=adviceObservationRevision.get())return;
                    if(risk!=null) {
                        if(!java.util.Objects.equals(drawdownAlertWindow,risk.getWindowId())) {
                            drawdownAlertWindow=risk.getWindowId();drawdownAlerted=false;
                        }
                        if(risk.getDrawdownUpper()>0 && risk.getRemainingLossBudget()<=0) {
                            if(!drawdownAlerted) {
                                notifier.notify("New buys are paused. " + risk.getReason());
                                drawdownAlerted=true;
                            }
                        }else drawdownAlerted=false;
                    }

					// The decision chain: companion collect, then built-in recovery, then companion buy, then fallback buy.

                engine.setObservedAccount(observedAccount);
                Suggestion continuationRefusal=null;
                Suggestion rejectedPriceAdvice=null;
                Suggestion suggestion = companion.nextAction();
				if (suggestion == null)
				{
					suggestion = engine.refresh(false);
                    if (suggestion != null && suggestion.getType() == SuggestionType.BUY)
                    {
                        suggestion = authorizeReplacement(suggestion);
                        if(suggestion.getType()==SuggestionType.WAIT){continuationRefusal=suggestion;suggestion=null;}
                    }
                    else if(engine.isContinuationWait(suggestion)){continuationRefusal=suggestion;suggestion=null;}
				}
				if (suggestion != null && (suggestion.getType() == SuggestionType.MODIFY_BUY
					|| suggestion.getType() == SuggestionType.CANCEL))
				{
                    Suggestion proposed=suggestion;
					suggestion = companion.selectBuyMaintenanceCancellation(suggestion);
                    if(suggestion==null || suggestion.getType()==SuggestionType.WAIT)rejectedPriceAdvice=proposed;
				}
				if(suggestion!=null && (suggestion.getType()==SuggestionType.MODIFY_SELL
                    || suggestion.getType()==SuggestionType.SELL && suggestion.isProfitKnown()))
                {
                    Suggestion proposed=suggestion;
                    suggestion=companion.authorizeSale(proposed);
                    if(suggestion!=proposed)rejectedPriceAdvice=proposed;
                }
				if (suggestion == null)
				{
					// The two rejection controls the player actually has. These were honoured only by
					// the built-in engine, whose buy suggestion the companion then replaced -- so
					// pressing Skip or "Never trade this" changed nothing that appeared on screen.
					Suggestion planned = companion.nextEntrySuggestion(
						new com.flippingfriend.model.Explainer(),
						com.flippingfriend.model.SuggestionEngine.parseBlocked(config.blockedItems()),
						engine.skippedItems(),
						offerTracker.itemsWithOpenOffers());
					// Fall back only when the companion has genuinely gone quiet -- no plan, or one so old
					// it has expired. A plan that says "every slot is occupied" or "nothing clears the
					// bars" is an answer, and answering it by running the built-in engine instead put a
					// trade chosen on different rules in front of the player seconds after one chosen
					// on these. With three slots, a single offer changing state was enough to trigger
					// that, which is what reads as the system changing its mind.
					if (planned!=null && planned.getType() == SuggestionType.BUY)
					{
						suggestion = planned;
					}
					else if (planned!=null && companion.hasFreshPlan())
					{
						suggestion = planned;
					}
					else if(continuationRefusal!=null)suggestion=continuationRefusal;
					else
					{
						suggestion = Suggestion.waiting("Companion entry decision unavailable",
				"No new buy will be suggested until the companion returns a current, valid entry decision.");
					}

				}
				// The walkthrough reads Grand Exchange widgets to decide whether the player is
				// halfway through typing an offer, and widgets may only be touched on the client
				// thread. Calling this from the engine thread threw AssertionError out of every
				// refresh, which meant the suggestion was never applied at all: the panel simply
				// stopped producing trades, with the failure visible only in the client log.
				//
				// The panel repaint has to follow the guide update, not race it. The panel renders
				// stepGuide.getSuggestion(), so posting the guide to the client thread and the
				// repaint to the EDT separately let the EDT win: the sidebar drew the *previous*
				// recommendation while the overlay, which repaints every frame, already had the new
				// one. Nothing repainted the panel afterwards, so the two disagreed for a full
				// refresh cycle -- which is what a player sees as the card and the walkthrough
				// naming different items. Chaining them makes the order explicit.
				consecutiveRefreshFailures.set(0);
				Suggestion settled = suggestion;
                Suggestion declined = rejectedPriceAdvice;
                long validatedAt=adviceClock.getAsLong();
				clientThread.invokeLater(() ->
				{
                    if(epoch!=engineRefreshEpoch.get())return;
                    if (observedGeneration != offerObservationGeneration.get() || observedRevision!=adviceObservationRevision.get()
                        || !java.util.Objects.equals(observedProfile, configManager.getRSProfileKey())) {
                        requestEngineRefresh();return;
                    }
					if (!offerTracker.hasCompleteOfferEvidence())
					{
						withdrawUnsupportedPriceAdvice();
						return;
					}
                    if (observedGeneration != offerObservationGeneration.get() || observedRevision!=adviceObservationRevision.get()) return;
                    Suggestion current = settled;
                    long deliveryAge=adviceClock.getAsLong()-validatedAt;
                    if(deliveryAge<0 || deliveryAge>=com.flippingfriend.overlay.StepGuide.MAX_ADVICE_AGE_NANOS
                        || current.getAdviceExpiresAt()>0 && current.getAdviceExpiresAt()<=java.time.Instant.now().getEpochSecond()
                        || current.getAdviceAccountId()!=null && !current.getAdviceAccountId().equals(observedAccount)) {
                        stepGuide.withdrawAdvice(Suggestion.waiting("Refreshing this order","The queued quote is no longer current. Its workflow and purchase records are retained."));
                        panel.refresh();requestEngineRefresh();return;
                    }

                    stepGuide.withdrawTrade(declined,Suggestion.waiting("Refreshing this trade",
                        "The previous instruction was declined by current validation. Existing offers and purchase records are retained."));
					stepGuide.setSuggestion(current);
                    recordVisibleAdvice();
					panel.refresh();
                    if(!stepGuide.isPaused())notifyIfNeeded(stepGuide.getSuggestion());
					log.info("Guidance delivered: {} ms from refresh request; capture/durable publication {} ms, plan/ledger {} ms, decision/delivery {} ms; {}",
						(System.nanoTime()-guidanceStarted)/1_000_000,(publicationFinished-guidanceStarted)/1_000_000,
						(planFinished-publicationFinished)/1_000_000,(System.nanoTime()-planFinished)/1_000_000,stepGuide.getSuggestion().getType());
				});
				if (epoch==engineRefreshEpoch.get() && observedRevision==adviceObservationRevision.get() && !stepGuide.isPaused())checkPriceDumps();

			}
			catch (Exception ex)
			{
				// A failure here used to be a log line and nothing else, and the panel draws the last
				// suggestion the walkthrough was given -- so a refresh that kept throwing left a card
				// on screen that looked live and was minutes or hours old. That is the worst version
				// of a lost recommendation: the player is not told to do the next thing, and has no
				// way to tell they are not being told.
				//
				// One failure is a blip and is ridden out, because blanking the card on a momentary
				// network fault would be worse than the fault. A run of them is a fault that is not
				// going away, and then it is said out loud.
				log.warn("could not refresh suggestion", ex);
				if (consecutiveRefreshFailures.incrementAndGet() >= REFRESH_FAILURES_BEFORE_SAYING_SO)
				{
					Suggestion problem = Suggestion.waiting("The plugin has stopped being able to "
							+ "work out your next trade",
						"Something went wrong while it was thinking: " + describe(ex)
							+ ". It will keep trying. If this does not clear, the RuneLite log has "
							+ "the details.");
					clientThread.invokeLater(() ->
					{
						if (epoch!=engineRefreshEpoch.get() || observedGeneration != offerObservationGeneration.get()
							|| observedRevision != adviceObservationRevision.get()
							|| !java.util.Objects.equals(observedProfile, configManager.getRSProfileKey())) return;
						stepGuide.withdrawAdvice(problem);
						panel.refresh();
					});
				}
			}
			finally { finishEngineRefresh(epoch); }
		});
		submitted=true;
		} catch(java.util.concurrent.RejectedExecutionException unavailable) {
            log.debug("Guidance worker is unavailable; the next heartbeat will retry",unavailable);
        } finally { if(!submitted)finishEngineRefresh(epoch); }
		});
	}

	/** Called on the client thread, including at the final advice-delivery boundary. */
	private void withdrawUnsupportedPriceAdvice()
	{
		stepGuide.withdrawAdvice(Suggestion.waiting("Offer data requires reconciliation",
			"An observed offer price exceeds this plugin's supported range. Advice is paused; "
				+ "the observation and prior accounting are retained for a compatible update."));
		panel.refresh();
	}

    /** A replacement spends cash again; its old reservation is not current entry authority. */
    private Suggestion authorizeReplacement(Suggestion proposed)
    {
        if (proposed.getItemId() <= 0 || proposed.getQuantity() <= 0)
            return Suggestion.waiting("Replacement unavailable", "The cancelled remainder is not valid.");
        Suggestion authorized = companion.replacementSuggestion(proposed.getItemId(),proposed.getQuantity(),
            new com.flippingfriend.model.Explainer(),
            com.flippingfriend.model.SuggestionEngine.parseBlocked(config.blockedItems()),
            engine.skippedItems(), offerTracker.itemsWithOpenOffers());
        if (authorized != null && authorized.getType() == SuggestionType.WAIT) return authorized;
        if (authorized == null || authorized.getType() != SuggestionType.BUY
            || authorized.getItemId() != proposed.getItemId() || authorized.getPrice() <= 0
            || authorized.getQuantity() <= 0 || authorized.getQuantity() > proposed.getQuantity())
            return Suggestion.waiting("Replacement needs a current companion plan",
                "No eligible entry fits this item's cancelled remainder. Waiting for a fresh plan; the old order will not be reused.");
        return authorized;
    }
	/** A short, readable description of a failure, for a card rather than a log. */
	private static String describe(Exception ex)
	{
		String message = ex.getMessage();
		return message == null || message.isEmpty()
			? ex.getClass().getSimpleName() : message;
	}

	/** Never runs on the client thread: local IPC must not make RuneLite input lag. */
	private void publishOfferToCompanion(TrackedOffer offer)
	{
		ExecutorService executor = worker;
		if (offer == null || executor == null || executor.isShutdown())
		{
			return;
		}
		TrackedOffer observation = offer.copyObservation();
		String profile = configManager.getRSProfileKey();
		if (profile == null || profile.isBlank()) return;
		String account = com.flippingfriend.core.AccountIdentity.fromProfileKey(profile);
		long generation = offerObservationGeneration.get();
        long observedAt=java.time.Instant.now().getEpochSecond();
		executor.execute(() -> {
			if (generation == offerObservationGeneration.get()) companion.publishObservedOffer(observation, account, observedAt);
		});
	}

	private void publishOfferClearedToCompanion(int slot)
	{
		ExecutorService executor = worker;
		if (executor == null || executor.isShutdown())
		{
			return;
		}
		String profile = configManager.getRSProfileKey();
		if (profile == null || profile.isBlank()) return;
		String account = com.flippingfriend.core.AccountIdentity.fromProfileKey(profile);
		long generation = offerObservationGeneration.get();
        long observedAt=java.time.Instant.now().getEpochSecond();
		executor.execute(() -> {
			if (generation == offerObservationGeneration.get()) companion.publishObservedOfferCleared(slot, account, observedAt);
		});
	}

	private void notifyIfNeeded(Suggestion suggestion)
	{
		if (suggestion == null || suggestion.sameAs(lastNotified) || !suggestion.isActionable())
		{
			return;
		}
		lastNotified = suggestion;

		if (suggestion.getType() == SuggestionType.COLLECT)
		{
			notifier.notify(config.notifyOnFill(), "Your " + suggestion.getItemName() + " offer has finished.");
		}
		else if (!stepGuide.isPaused() && suggestion.getType() == SuggestionType.SELL)
		{
			notifier.notify(config.notifyOnSellSignal(),
				"Time to sell your " + suggestion.getItemName() + ".");
			if (config.notifyOnSellSignal() != net.runelite.client.config.Notification.OFF)
			{
				clientThread.invokeLater(() -> client.playSoundEffect(3924)); // Distinct Chime
			}
		}
		else if (suggestion.getType().isModify())
		{
			notifier.notify(config.notifyOnStaleOffer(),
				"Your " + suggestion.getItemName() + " offer is not filling.");
			if (config.notifyOnStaleOffer() != net.runelite.client.config.Notification.OFF)
			{
				clientThread.invokeLater(() -> client.playSoundEffect(2277)); // Double Tick
			}
		}
	}

	private void checkPriceDumps()
	{
		String webhook=config.discordWebhookUrl();
		boolean local=config.notifyOnPriceDrop()!=net.runelite.client.config.Notification.OFF;
		if (!local && (webhook==null || webhook.trim().isEmpty())) return;
		var state=accountMonitor.getState();
		var ledger=companion.tradingLedger();
		var messages=heldDropAlerts.update(state!=null && state.isLoggedIn() && ledger!=null?ledger.getAccountId():null,
			engine.tradingPositions(),new com.flippingfriend.model.MarketDropDetector().detect(marketData.getSnapshot(),Instant.now()));
		for(String message:messages) {
			if(local) { notifier.notify(config.notifyOnPriceDrop(),message); clientThread.invokeLater(()->client.playSoundEffect(2289)); }
			discordWebhook.sendMessage(webhook,message);
		}
	}

	/**
	 * Capital already tied up in each item: what is held, at cost, plus what an open buy offer has
	 * reserved.
	 * <p>
	 * The companion has never known this. Its exposure ceilings started from zero on every plan, so
	 * they constrained a plan against itself and never against the book -- and the guard that stops an
	 * item being suggested twice only covers offers that are still open, not the position left behind
	 * when one is collected.
	 */
	private Map<Integer, Long> committedByItem()
	{
		Map<Integer, Long> committed = new HashMap<>();
		for (com.flippingfriend.session.Position position : positions.all())
		{
			if (position.getQuantity() > 0 && position.isCostKnown())
			{
				committed.merge(position.getItemId(), position.getTotalCost(), Long::sum);
			}
		}
		for (com.flippingfriend.session.TrackedOffer offer : offerTracker.getOffers())
		{
			// Only a buy still working has coins reserved against it; a sell is inventory, already
			// counted above, and a finished buy has become a position.
			if (offer.isBuying() && "BUYING".equals(offer.getState()))
			{
				long reserved = (long) offer.getPrice() * offer.getRemaining();
				if (reserved > 0)
				{
					committed.merge(offer.getItemId(), reserved, Long::sum);
				}
			}
		}
		return committed;
	}

	private void persist()
	{
		if (!accountDataLoaded.get())
		{
			return;
		}
		buyLimits.save();
		skipList.save();
		positions.save();
		tradePlans.save();
		offerTracker.save();
	}
}
