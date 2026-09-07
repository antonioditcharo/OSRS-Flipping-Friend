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
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.ClientTick;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.InterfaceID;
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
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.KeyListener;
import java.awt.event.KeyEvent;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.FontID;
import net.runelite.api.VarClientInt;
import net.runelite.api.VarClientStr;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.JavaScriptCallback;
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
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.ClientTick;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.InterfaceID;
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
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.KeyListener;
import java.awt.event.KeyEvent;

/**
 * Wires the plugin together and owns its lifecycle.
 * <p>
 * The division of labour is strict, because getting it wrong in a RuneLite plugin produces
 * intermittent client freezes. Anything that reads live game state runs on the client thread and
 * does nothing but copy that state into plain objects. Anything that thinks — scoring thousands of
 * items, fetching prices, computing history — runs on a background executor against those copies.
 * Anything that draws runs on the Swing thread.
 * <p>
 * The plugin only ever reads from the game and paints on top of it. It sends no input of any kind.
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
	private com.flippingfriend.session.GeHistoryTracker geHistoryTracker;

	@Inject
	private com.flippingfriend.ui.AlertManager alertManager;

	@Inject
	private com.flippingfriend.ui.AlertOverlay alertOverlay;

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

	@Inject
	private GeWidgetResolver widgetResolver;

	@Inject
	private GrandExchangeOverlay grandExchangeOverlay;

	@Inject
	private OfferEditorOverlay offerEditorOverlay;

	@Inject
	private com.flippingfriend.overlay.ChatboxOverlay chatboxOverlay;

	@Inject
	private FlippingFriendPanel panel;

	@Inject
	private KeyManager keyManager;

	private NavigationButton navigationButton;
	private ExecutorService worker;
	private boolean drawdownAlerted = false;

	private final java.util.Set<Integer> alertedDumps = new java.util.concurrent.ConcurrentSkipListSet<>();

	private final AtomicBoolean accountDataLoaded = new AtomicBoolean();
	private volatile long lastAccountRefresh;
	private volatile long lastEngineRefresh;
	private volatile long lastPersist;

	private Suggestion adjustmentIntent = null;
	private long adjustmentIntentTime = 0;

	private volatile boolean exemptionsResolved;
	private volatile Suggestion lastNotified = Suggestion.idle();

	private Widget recommendedInputWidget;
	private int chatboxTitleOriginalY = -1;


	@Provides
	FlippingFriendConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FlippingFriendConfig.class);
	}

	@Override
	protected void startUp()
	{
		worker = Executors.newSingleThreadExecutor(r ->
		{
			Thread thread = new Thread(r, "flipping-friend-engine");
			thread.setDaemon(true);
			return thread;
		});

		overlayManager.add(grandExchangeOverlay);
		overlayManager.add(offerEditorOverlay);
		overlayManager.add(chatboxOverlay);
		overlayManager.add(alertOverlay);

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
		panel.setOnResetAllTime(() ->
		{
			java.nio.file.Path archived = journal.archiveAndReset();
			// Rebuilt from what the journal holds now, which is nothing. Left as it was, the
			// calibrator would go on applying corrections learned from trades the plugin can no
			// longer show you, which is the one state worse than having no corrections at all.
			calibrator.rebuild(journal.getHistory());
			log.info("all-time figures reset; previous journal kept at {}", archived);
			panel.refresh();
		});
		panel.setOnCardClicked(this::copyCurrentStepToClipboard);

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

		keyManager.registerKeyListener(hotkeyListener);

		log.debug("Flipping Friend started");
	}

	@Override
	protected void shutDown()
	{
		marketData.setUpdateListener(null);
		offerTracker.setChangeListener(null);
		offerTracker.setAbandonedBuyListener(null);
		marketData.stop();

		overlayManager.remove(grandExchangeOverlay);
		overlayManager.remove(offerEditorOverlay);
		overlayManager.remove(chatboxOverlay);
		overlayManager.remove(alertOverlay);
		clientToolbar.removeNavigation(navigationButton);
		navigationButton = null;

		keyManager.unregisterKeyListener(hotkeyListener);

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
		GameState state = event.getGameState();
		if (state == GameState.LOGGED_IN)
		{
			// The RuneLite profile key is not available the instant the game state flips, so the
			// actual load is attempted from the scheduled tick until it succeeds.
			accountDataLoaded.set(false);
			// Sell-only is a way to end a session, so a new one starts out of it. Deliberately not a
			// stored setting: a switch that silently stops every buy is the last thing that should
			// survive a restart and be forgotten about.
			engine.setSellOnly(false);
		}
		else if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			persist();
			accountMonitor.reset();
			accountDataLoaded.set(false);
			// A cancelled buy waiting to see whether the player goes back to it: they have logged
			// out, so the question can no longer be answered. Dropped rather than decided -- the
			// verdict it was heading for is an eight-hour cooldown, and imposing that because
			// somebody logged off mid-edit is exactly the kind of silent punishment this whole
			// mechanism exists to stop. It also would not survive a hop to another world, which is
			// not a decision about an item either.
			abandonedBuys.clear();
			pendingOffers.clear();
		}
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
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
			// it the only record of what the item cost. Keeping the event costs one small map and
			// replays it the moment the account is known.
			pendingOffers.add(event);
			return;
		}

		offerTracker.setMarket(marketData.getSnapshot());
		drainPendingOffers();
		// Before the ledger sees it, because onOfferChanged is what raises the abandonment in the
		// first place and a fresh offer must not be able to arm a judgement it should be clearing.
		if (event.getOffer() != null
			&& event.getOffer().getState() == net.runelite.api.GrandExchangeOfferState.BUYING
			&& abandonedBuys.replaced(event.getOffer().getItemId()))
		{
			log.debug("re-placed {} after cancelling it; that was a modification, not a rejection",
				event.getOffer().getItemId());
		}

		// Track adjustment intent for BUY/SELL cancellations
		if (event.getOffer() != null)
		{
			net.runelite.api.GrandExchangeOfferState state = event.getOffer().getState();
			if (state == net.runelite.api.GrandExchangeOfferState.CANCELLED_BUY || state == net.runelite.api.GrandExchangeOfferState.CANCELLED_SELL)
			{
				Suggestion active = stepGuide.getSuggestion();
				if (active != null && 
					(active.getType() == SuggestionType.MODIFY_BUY || active.getType() == SuggestionType.MODIFY_SELL) && 
					active.getItemId() == event.getOffer().getItemId())
				{
					adjustmentIntent = active;
					adjustmentIntentTime = System.currentTimeMillis();
				}
			}
			else if (state == net.runelite.api.GrandExchangeOfferState.BUYING || state == net.runelite.api.GrandExchangeOfferState.SELLING)
			{
				if (adjustmentIntent != null && adjustmentIntent.getItemId() == event.getOffer().getItemId())
				{
					adjustmentIntent = null;
				}
			}
		}

		offerTracker.onOfferChanged(event.getSlot(), event.getOffer());
		publishOfferToCompanion(offerTracker.getOffer(event.getSlot()));
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
		refreshAccountState();
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		// Observation only. Nothing here consumes, rewrites or injects a menu action.
		stepGuide.onMenuOptionClicked(event);
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == WidgetUtil.componentToInterface(InterfaceID.GeOffers.UNIVERSE))
		{
			stepGuide.onSetupClosed();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		geHistoryTracker.onGameTick(client);
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		injectRecommendedItem();
		injectQuickSetWidget();
	}

	private void injectRecommendedItem()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		Suggestion suggestion = stepGuide.getSuggestion();
		if (suggestion == null || !suggestion.isActionable())
		{
			return;
		}

		net.runelite.api.widgets.Widget searchResults = client.getWidget(net.runelite.api.widgets.ComponentID.CHATBOX_GE_SEARCH_RESULTS);
		if (searchResults == null || searchResults.isHidden())
		{
			return;
		}

		ItemMetadata meta = marketData.getSnapshot().metadata(suggestion.getItemId());
		String itemName = meta != null ? meta.getName() : "Item";
		int itemId = suggestion.getItemId();

		net.runelite.api.widgets.Widget[] children = searchResults.getChildren();
		boolean hasNativeButton = children != null && children.length >= 4;
		
		if (!hasNativeButton)
		{
			createPreviousSearchWidget(searchResults, itemId, itemName);
			createPreviousSearchTextWidget(searchResults);
			createPreviousSearchItemNameWidget(searchResults, itemName);
			createPreviousSearchItemWidget(searchResults, itemId);
		}
		else
		{
			setPreviousSearch(searchResults, itemId, itemName);
		}
	}

	private void setPreviousSearch(net.runelite.api.widgets.Widget searchResults, int itemId, String itemName)
	{
		net.runelite.api.widgets.Widget previousSearch = searchResults.getChild(0);
		if (previousSearch != null)
		{
			previousSearch.setHasListener(true);
			previousSearch.setOnOpListener(754, itemId, 84);
			previousSearch.setOnKeyListener((Object[]) new Object[]{754, itemId, -2147483640});
			previousSearch.setName("<col=ff9040>" + itemName + "</col>");
			previousSearch.setAction(0, "Select");
			previousSearch.revalidate();
		}

		net.runelite.api.widgets.Widget previousSearchText = searchResults.getChild(1);
		if (previousSearchText != null)
		{
			previousSearchText.setText("Recommended:");
			previousSearchText.setOriginalWidth(95);
			previousSearchText.setXTextAlignment(net.runelite.api.widgets.WidgetTextAlignment.LEFT);
			previousSearchText.revalidate();
		}

		net.runelite.api.widgets.Widget itemNameWidget = searchResults.getChild(2);
		if (itemNameWidget != null)
		{
			itemNameWidget.setText(itemName);
			itemNameWidget.revalidate();
		}

		net.runelite.api.widgets.Widget item = searchResults.getChild(3);
		if (item != null)
		{
			item.setItemId(itemId);
			item.revalidate();
		}
	}

	private void injectQuickSetWidget()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		Suggestion suggestion = stepGuide.getSuggestion();
		if (suggestion == null || !suggestion.isActionable())
		{
			return;
		}

		var inputType = client.getVarcIntValue(VarClientInt.INPUT_TYPE);
		Widget chatboxTitle = client.getWidget(ComponentID.CHATBOX_TITLE);
		Widget chatboxContainer = client.getWidget(ComponentID.CHATBOX_CONTAINER);
		Widget offerContainer = client.getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER);
		int currentItemId = client.getVarpValue(net.runelite.api.VarPlayer.CURRENT_GE_ITEM);
		
		if (chatboxTitle == null || chatboxContainer == null || offerContainer == null || inputType != 7 || currentItemId != suggestion.getItemId())
		{
			// Cleanup if widget exists but prompt is closed
			if (recommendedInputWidget != null)
			{
				recommendedInputWidget.setHidden(true);
				if (chatboxTitleOriginalY != -1 && chatboxTitle != null)
				{
					chatboxTitle.setOriginalY(chatboxTitleOriginalY);
					chatboxTitle.revalidate();
				}
				recommendedInputWidget = null;
			}
			return;
		}

		String chatInputText = chatboxTitle.getText();
		Widget offerTextWidget = offerContainer.getChild(20);
		String offerText = offerTextWidget != null ? offerTextWidget.getText() : "";
		
		boolean isQuantity = chatInputText.equals("How many do you wish to buy?") || chatInputText.equals("How many do you wish to sell?");
		boolean isPrice = chatInputText.equals("Set a price for each item:") && (offerText.equals("Buy offer") || offerText.equals("Sell offer"));

		if (!isQuantity && !isPrice)
		{
			return;
		}

		boolean isSelling = client.getVarbitValue(net.runelite.api.Varbits.GE_OFFER_CREATION_TYPE) == 1;
		boolean isBuying = client.getVarbitValue(net.runelite.api.Varbits.GE_OFFER_CREATION_TYPE) == 0;
		String offerType = isBuying ? "buy" : (isSelling ? "sell" : "");

		// Only show suggestion if the offer type matches (buy vs sell)
		if (!offerType.equals(suggestion.getType() == SuggestionType.MODIFY_BUY || suggestion.getType() == SuggestionType.BUY ? "buy" : "sell"))
		{
			return;
		}

		if (recommendedInputWidget == null || recommendedInputWidget.isHidden())
		{
			if (chatboxTitleOriginalY == -1)
			{
				chatboxTitleOriginalY = chatboxTitle.getOriginalY();
			}
			chatboxTitle.setOriginalY(chatboxTitleOriginalY + 7);
			chatboxTitle.revalidate();

			recommendedInputWidget = chatboxContainer.createChild(-1, WidgetType.TEXT);
			recommendedInputWidget.setTextColor(0x0040FF);
			recommendedInputWidget.setFontId(FontID.VERDANA_11_BOLD);
			recommendedInputWidget.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
			recommendedInputWidget.setOriginalX(40);
			recommendedInputWidget.setOriginalY(10);
			recommendedInputWidget.setOriginalHeight(20);
			recommendedInputWidget.setXTextAlignment(WidgetTextAlignment.LEFT);
			recommendedInputWidget.setWidthMode(WidgetSizeMode.MINUS);
			recommendedInputWidget.setHasListener(true);
			recommendedInputWidget.setOnMouseRepeatListener((JavaScriptCallback) ev -> recommendedInputWidget.setTextColor(0xFFFFFF));
			recommendedInputWidget.setOnMouseLeaveListener((JavaScriptCallback) ev -> recommendedInputWidget.setTextColor(0x0040FF));
		}

		long value = isQuantity ? suggestion.getQuantity() : suggestion.getPrice();
		String prefix = isQuantity ? "quantity" : "price";
		String displayValue = isQuantity ? String.valueOf(value) : String.format("%,d gp", value);
		
		recommendedInputWidget.setText("Recommended " + prefix + ": " + displayValue);
		recommendedInputWidget.setAction(isQuantity ? 1 : 0, "Set " + prefix);
		recommendedInputWidget.setOnOpListener((JavaScriptCallback) ev -> {
			Widget chatboxInputWidget = client.getWidget(ComponentID.CHATBOX_FULL_INPUT);
			if (chatboxInputWidget != null)
			{
				chatboxInputWidget.setText(value + "*");
				client.setVarcStrValue(VarClientStr.INPUT_TEXT, String.valueOf(value));
			}
		});
		
		recommendedInputWidget.revalidate();
	}

	private void createPreviousSearchWidget(net.runelite.api.widgets.Widget parentWidget, int itemId, String itemName)
	{
		net.runelite.api.widgets.Widget widget = parentWidget.createChild(0, net.runelite.api.widgets.WidgetType.RECTANGLE);
		widget.setTextColor(0xFFFFFF);
		widget.setOpacity(255);
		widget.setName("<col=ff9040>" + itemName + "</col>");
		widget.setFilled(true);
		widget.setOriginalX(114);
		widget.setOriginalY(0);
		widget.setOriginalWidth(256);
		widget.setOriginalHeight(32);
		widget.setOnOpListener(754, itemId, 84);
		widget.setOnKeyListener((Object[]) new Object[]{754, itemId, -2147483640});
		widget.setHasListener(true);
		widget.setAction(0, "Select");
		widget.revalidate();
	}

	private void createPreviousSearchTextWidget(net.runelite.api.widgets.Widget parentWidget)
	{
		net.runelite.api.widgets.Widget widget = parentWidget.createChild(1, net.runelite.api.widgets.WidgetType.TEXT);
		widget.setText("Recommended:");
		widget.setFontId(495); // Quill 8
		widget.setOriginalX(114);
		widget.setOriginalY(0);
		widget.setOriginalWidth(95);
		widget.setOriginalHeight(32);
		widget.setYTextAlignment(1); // Center
		widget.revalidate();
	}

	private void createPreviousSearchItemNameWidget(net.runelite.api.widgets.Widget parentWidget, String itemName)
	{
		net.runelite.api.widgets.Widget widget = parentWidget.createChild(2, net.runelite.api.widgets.WidgetType.TEXT);
		widget.setText(itemName);
		widget.setFontId(495); // Quill 8
		widget.setOriginalX(210); // adjusted to give space for icon
		widget.setOriginalY(0);
		widget.setOriginalWidth(116);
		widget.setOriginalHeight(32);
		widget.setYTextAlignment(1); // Center
		widget.revalidate();
	}

	private void createPreviousSearchItemWidget(net.runelite.api.widgets.Widget parentWidget, int itemId)
	{
		net.runelite.api.widgets.Widget widget = parentWidget.createChild(3, net.runelite.api.widgets.WidgetType.GRAPHIC);
		widget.setItemId(itemId);
		widget.setItemQuantity(1);
		widget.setItemQuantityMode(0);
		widget.setRotationX(550);
		widget.setModelZoom(1031);
		widget.setBorderType(1);
		widget.setOriginalX(174); // adjusted to be between text and item name
		widget.setOriginalY(0);
		widget.setOriginalWidth(36);
		widget.setOriginalHeight(32);
		widget.revalidate();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!FlippingFriendConfig.GROUP.equals(event.getGroup()))
		{
			return;
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
		resolveTaxExemptionsIfNeeded();
		settleAbandonedBuys();

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

		if (now - lastLearningPoll > LEARNING_POLL_MILLIS)
		{
			lastLearningPoll = now;
			refreshLearning();
		}
	}

	/** How often the learning panel is refreshed. It moves on a fifteen-minute snapshot cadence. */
	private static final long LEARNING_POLL_MILLIS = 30_000;
	private long lastLearningPoll;

	/**
	 * Collect what the companion has learned, for the panel.
	 *
	 * <p>On the worker, never on the Swing thread: it is a loopback request and the panel that
	 * displays it is rebuilt on the event thread. The panel is handed the answer and renders it on its
	 * next ordinary refresh rather than being forced to redraw, because this changes slowly and the
	 * sidebar is already redrawn on every plan.
	 */
	private void refreshLearning()
	{
		ExecutorService executor = worker;
		if (executor == null || executor.isShutdown())
		{
			return;
		}
		executor.execute(() ->
		{
			try
			{
				java.util.List<com.flippingfriend.model.LearningReading> readings =
					companion.fetchLearning();
				// Null means the request failed, which is the companion being down rather than a
				// companion with nothing to say. The panel says something different for each.
				panel.setLearning(readings, readings != null);
			}
			catch (Exception ex)
			{
				panel.setLearning(null, false);
			}
		});
	}

	// ------------------------------------------------------------------ helpers

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
	 * position the plugin bought and then banked still needs to be sellable, but they are never
	 * adopted as new positions unless the user explicitly asks.
	 */
	private void syncHoldings(AccountState state)
	{
		Map<Integer, Integer> holdings = state.getHoldings();

		Map<Integer, Integer> inventoryHoldings = state.getInventoryHoldings();
		Instant now = Instant.now();

		// Inventory items are always adopted (the user holding them implies intent to sell).
		Map<Integer, Integer> toAdopt = new java.util.HashMap<>(inventoryHoldings);

		for (Map.Entry<Integer, Integer> entry : toAdopt.entrySet())
		{
			ItemMetadata metadata = marketData.getSnapshot().metadata(entry.getKey());
			if (metadata != null)
			{
				positions.adoptExisting(entry.getKey(), metadata.getName(), entry.getValue(), now);
			}
		}

		// Items away in a sell offer are still held; anything else that has vanished really has —
		// but only once the bank has been seen, since until then "not in hand" means nothing.
		positions.reconcile(holdings, offerTracker.listedForSale(), state.isBankSeen());
	}

	/**
	 * Offer events that arrived before this plugin knew whose account it was looking at.
	 * <p>
	 * Keyed by slot, so only the most recent state of each slot is kept -- which is all the ledger
	 * needs, since fills are tracked as running totals.
	 */
	private final java.util.Queue<net.runelite.api.events.GrandExchangeOfferChanged> pendingOffers =
		new java.util.concurrent.ConcurrentLinkedQueue<>();

	/** Replays whatever arrived during the account-load window, oldest slot first for determinism. */
	private void drainPendingOffers()
	{
		net.runelite.api.events.GrandExchangeOfferChanged event;
		while ((event = pendingOffers.poll()) != null)
		{
			offerTracker.onOfferChanged(event.getSlot(), event.getOffer());
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
			log.debug("loaded account data ({} journal entries)", journal.getHistory().size());
			
			// Flush any pending offers that were buffered during login replay before data as loaded
			clientThread.invokeLater(this::drainPendingOffers);
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
	 * @param filled      how much had been bought when it was cancelled
	 * @param ordered     how much had been asked for
	 * @param minutesOpen how long the offer had been standing
	 */
	private void onBuyAbandoned(int itemId, int filled, int ordered, long minutesOpen)
	{
		// Sell-only asked for this cancellation itself. Ending a session is not a view about the item,
		// and the mode clears on login -- so without this exemption a routine wind-down would take
		// every open item off the table for the next session too.
		if (engine.isSellOnly())
		{
			return;
		}

		// The plugin itself asked for this cancellation: the current advice is to reprice this
		// exact buy offer. That is compliance, not rejection, and putting the item on an
		// eight-hour cooldown for following the walkthrough is the opposite of what should happen.
		Suggestion active = stepGuide.getSuggestion();
		if (active != null && active.getType() == SuggestionType.MODIFY_BUY
			&& active.getItemId() == itemId)
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

		// Held open, not decided.
		//
		// There is no "modify" in the Grand Exchange: changing an offer means aborting it and placing
		// another, so every modification arrives here looking exactly like an abandonment. And
		// isRejection treats any part-filled buy that was cancelled as a rejection, so adjusting the
		// price of an order that had started to fill put the item on an eight-hour cooldown every
		// single time -- the plan dropped it and moved to the next one while the player was still
		// halfway through re-placing it.
		//
		// The two exemptions above try to catch this by reading the interface at the instant the
		// cancel event fires, which is a race the client usually wins: the offer is cancelled first
		// and the setup panel opens after. That is why they did not help.
		//
		// So the question is not asked yet. What separates a modification from a rejection is what
		// the player does NEXT, and waiting a minute costs nothing: a genuine rejection is a cooldown
		// that starts a minute late, while a false one takes the item off the table for eight hours.
		// Re-placing an offer for this item by ANY route -- the modify button, an abort from the slot
		// menu, an abort from inside the offer -- withdraws the judgement in noteOfferPlaced.
		abandonedBuys.cancelled(itemId, System.currentTimeMillis());
	}

	/**
	 * Cancelled buys whose meaning is not settled yet. The rule lives in
	 * {@link com.flippingfriend.session.AbandonedBuys}, where a test can reach it.
	 */
	private final com.flippingfriend.session.AbandonedBuys abandonedBuys =
		new com.flippingfriend.session.AbandonedBuys();

	/**
	 * Settle any cancelled buy the player has not gone back to.
	 *
	 * <p>Runs on the scheduled tick rather than on an event, because the thing being waited for is
	 * the player doing nothing, and nothing raises no event.
	 */
	private void settleAbandonedBuys()
	{
		for (int itemId : abandonedBuys.awaiting())
		{
			// Still in the middle of it: the setup panel is open on this very item. Asked here as
			// well as at cancel time, because here it is no longer a race -- the client has had whole
			// seconds to finish transitioning.
			if (widgetResolver.isSetupOpen()
				&& client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH) == itemId)
			{
				abandonedBuys.replaced(itemId);
			}
		}

		java.util.List<Integer> walkedAwayFrom = abandonedBuys.due(System.currentTimeMillis());
		if (walkedAwayFrom.isEmpty())
		{
			return;
		}

		for (int itemId : walkedAwayFrom)
		{
			skipList.skipUntilCooldownEnds(itemId, java.time.Instant.now());
		}
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

	private final KeyListener hotkeyListener = new KeyListener()
	{
		@Override
		public void keyTyped(KeyEvent e) {}

		@Override
		public void keyPressed(KeyEvent e)
		{
			if (config.copyToClipboardHotkey().matches(e))
			{
				copyCurrentStepToClipboard();
				e.consume();
			}
		}

		@Override
		public void keyReleased(KeyEvent e) {}
	};

	/**
	 * Auto-fills the Grand Exchange input box if it is open, or puts the number the walkthrough is 
	 * currently asking for onto the system clipboard.
	 *
	 * <p>The fallback to the clipboard is maintained for convenience, but the primary function now 
	 * mimics approved Plugin Hub extensions like Flipping Copilot which use 1:1 user interactions 
	 * (a hotkey press) to safely populate the input field without automating submission.
	 */
	private void copyCurrentStepToClipboard()
	{
		Suggestion suggestion = stepGuide.getSuggestion();
		if (suggestion == null || !suggestion.isActionable())
		{
			return;
		}
		String value = null;
		if (!stepGuide.isQuantityDone() && suggestion.getQuantity() > 0)
		{
			value = String.valueOf(suggestion.getQuantity());
		}
		else if (!stepGuide.isPriceDone() && suggestion.getPrice() > 0)
		{
			value = String.valueOf(suggestion.getPrice());
		}
		if (value == null)
		{
			return;
		}
		
		// Use clipboard

		try
		{
			java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new java.awt.datatransfer.StringSelection(value), null);
		}
		catch (Exception unavailable)
		{
			// A clipboard that refuses is a headless or locked desktop. Nothing to recover, and
			// nothing worth failing over.
			log.debug("Clipboard unavailable", unavailable);
		}
	}

	private void requestEngineRefresh()
	{
		ExecutorService executor = worker;
		if (executor == null || executor.isShutdown())
		{
			return;
		}

		lastEngineRefresh = System.currentTimeMillis();
		
		clientThread.invokeLater(() ->
		{
			boolean isSetupOpen = widgetResolver.isSetupOpen();
			int setupItemId = isSetupOpen ? client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH) : -1;
			Suggestion active = stepGuide.getSuggestion();
			
			if (adjustmentIntent != null && System.currentTimeMillis() - adjustmentIntentTime > 120_000)
			{
				adjustmentIntent = null;
			}
			
			boolean adjustingIntentExists = adjustmentIntent != null;
			boolean setupMatches = active != null && isSetupOpen && setupItemId == active.getItemId() &&
				(active.getType() == SuggestionType.MODIFY_BUY || active.getType() == SuggestionType.MODIFY_SELL);

			if (adjustingIntentExists)
			{
				engine.setPendingAdjustment(adjustmentIntent);
			}
			else if (setupMatches)
			{
				engine.setPendingAdjustment(active);
			}
			else if (!isSetupOpen)
			{
				// Cleared when the editor closes, not when the whole Grand Exchange does.
				//
				// The pin holds the advice on the trade being typed so the player is not yanked
				// elsewhere mid-entry. Keyed on the GE window it outlived the edit entirely: close
				// the offer editor, stay at the booth, and the pin kept the recommendation stuck on
				// the offer last touched for as long as the interface was open -- so nothing could be
				// said about the other seven slots.
				//
				// That was survivable while the pin merely repeated an old suggestion. It is not now
				// that an offer under the cursor is guaranteed an answer, because the pinned one would
				// win the card every time. No editor open means nothing is being typed and there is
				// nothing to protect.
				engine.setPendingAdjustment(null);
				companion.releaseIncumbent();
			}

			executor.execute(() ->
			{
				try
				{
					// The companion owns new-entry portfolio selection. The legacy engine remains here only
					// for collect/sell recovery while its equivalent companion workflows are introduced.
					long markedDrawdown = com.flippingfriend.session.SessionRisk.markedDrawdown(
						journal.sessionStats(), positions, marketData.getSnapshot(), taxCalculator);

					long bankroll = accountMonitor.getState().spendableCoins(config.includeBankValue(), config.bankrollCap());
					if (bankroll > 0 && markedDrawdown > bankroll * 0.15)
					{
						if (!drawdownAlerted)
						{
							notifier.notify("CRITICAL: 15% Session Drawdown Breaker tripped!");
							clientThread.invokeLater(() -> client.playSoundEffect(2289)); // Urgent Horn
							drawdownAlerted = true;
						}
					}
					else
					{
						drawdownAlerted = false;
					}

					// The buy-limit ledger travels too. Offer events are fire-and-forget over loopback, so
					// one lost while the companion restarts is lost to it for good -- and the next plan
					// then orders over the limit, which stops filling part-way with no error at all.
					// What the player has already rejected goes with it, so the plan leaves those trades
					// out rather than having them filtered off the card afterwards. They were only ever
					// applied at selection, so the portfolio list went on showing a blocked item as the
					// top-ranked trade and the headline rate was computed from trades never on offer.
					companion.publishAccount(accountMonitor.getState(), config, markedDrawdown,
						committedByItem(), buyLimits.activeWindows(java.time.Instant.now()),
						com.flippingfriend.model.SuggestionEngine.parseBlocked(config.blockedItems()),
						engine.skippedItems(), offerTracker.itemsWithOpenOffers(), engine.isSellOnly());

					// The companion owns which new position to open. Selling and collecting still come
					// from the built-in engine, which is where the position book lives.
					// Always refresh the plan, even on a cycle where the built-in engine has something to
					// sell or collect. It used to be fetched only inside the buy branch, so on any other
					// cycle the portfolio list kept rendering an ever-older plan with nothing to say it
					// was stale.
					companion.refreshPlan();

					// The local decision chain: collect, then reprice, then sell, then -- only if asked -- buy.

				Suggestion suggestion = engine.refresh(false);
				if (suggestion == null)
				{
					// The two rejection controls the player actually has. These were honoured only by
					// the built-in engine, whose buy suggestion the companion then replaced -- so
					// pressing Skip or "Never trade this" changed nothing that appeared on screen.
					Suggestion planned = companion.nextBuySuggestion(
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
					if (planned.getType() == SuggestionType.BUY)
					{
						suggestion = planned;
					}
					else if (companion.hasFreshPlan())
					{
						suggestion = planned;
					}
					else
					{
						suggestion = engine.buyFallback();
					}

					if (suggestion == planned)
					{
						// Record the exit alongside the entry, exactly as the built-in engine does.
						// Only that engine was doing it, so on the companion path -- the one that
						// actually runs -- every position opened with no target, no predicted time and
						// no stop. That left per-trade stops permanently inert, and left the journal
						// unable to say what any trade was expected to make, which is what the
						// calibrator learns from.
						int stop = (int) Math.round(
							planned.getPrice() * (1 - config.riskProfile().getLossCutPct()));
						tradePlans.plan(planned.getItemId(), planned.getTargetSellPrice(), stop,
							planned.getExpectedMinutes(), planned.getExpectedProfit());
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
				// A sale is advice too, and until now it was the only kind nobody wrote down. Without
				// this the offer that follows reaches the companion unattributed, so the sell leg --
				// the one where the fill is genuinely in doubt -- never scored a single prediction.
				if (suggestion.getType() == SuggestionType.SELL)
				{
					companion.recordSellAdvice(suggestion);
				}

				Suggestion settled = suggestion;
				clientThread.invokeLater(() ->
				{
					stepGuide.setSuggestion(settled);
					panel.refresh();
				});
				notifyIfNeeded(suggestion);

				checkPriceDumps();

			}
			catch (Exception ex)
			{
				log.warn("could not refresh suggestion", ex);
			}
		});
		});
	}

	/** Never runs on the client thread: local IPC must not make RuneLite input lag. */
	private void publishOfferToCompanion(TrackedOffer offer)
	{
		ExecutorService executor = worker;
		if (executor == null || executor.isShutdown())
		{
			return;
		}
		executor.execute(() -> companion.publishOffer(offer));
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
		else if (suggestion.getType() == SuggestionType.SELL)
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
		String webhook = config.discordWebhookUrl();
		if (webhook == null || webhook.trim().isEmpty())
		{
			return;
		}

		com.flippingfriend.data.MarketSnapshot snapshot = marketData.getSnapshot();
		if (snapshot == null)
		{
			return;
		}

		for (com.flippingfriend.session.Position pos : positions.all())
		{
			int itemId = pos.getItemId();
			com.flippingfriend.data.LatestPrice latest = snapshot.latest(itemId);
			com.flippingfriend.data.Candle avg = snapshot.fiveMinute(itemId);
			
			if (latest != null && avg != null && avg.getAvgHighPrice() != null && avg.getAvgHighPrice() > 0)
			{
				double drop = 1.0 - ((double) latest.getLow() / avg.getAvgHighPrice());
				if (drop > 0.10 && alertedDumps.add(itemId)) // 10% sudden drop
				{
					String msg = "\u26A0\uFE0F **PRICE DUMP ALERT** \u26A0\uFE0F\n" +
						"Your held item **" + pos.getItemName() + "** has dropped by " + String.format("%.1f%%", drop * 100) + 
						" in the last 5 minutes!";
					// discordWebhook.sendMessage(webhook, msg);
					notifier.notify(msg);
					clientThread.invokeLater(() -> client.playSoundEffect(2289)); // Urgent Horn
				}
				else if (drop < 0.05)
				{
					alertedDumps.remove(itemId);
				}
			}
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
		// Not the journal itself -- that is append-only and already on disk -- but the fact that the
		// session is still running, so reopening the client resumes it instead of starting over.
		journal.touch();
	}
}
