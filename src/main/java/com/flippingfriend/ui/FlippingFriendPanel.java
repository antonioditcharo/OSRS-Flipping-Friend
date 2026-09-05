package com.flippingfriend.ui;

import com.flippingfriend.CheckInterval;
import com.flippingfriend.FlippingFriendConfig;
import com.flippingfriend.RiskProfile;
import com.flippingfriend.companion.CompanionClient;
import com.flippingfriend.data.MarketDataService;
import com.flippingfriend.model.Explainer;
import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionEngine;
import com.flippingfriend.model.TaxCalculator;
import com.flippingfriend.session.AccountMonitor;
import com.flippingfriend.session.AccountState;
import com.flippingfriend.session.PositionBook;
import com.flippingfriend.session.SessionStats;
import com.flippingfriend.session.TradeJournal;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.Locale;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JButton;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The side panel: the current instruction, what is held, recent trades, and how things are going.
 * <p>
 * The content lives in a scrollable column that declares it tracks the viewport width, which is what
 * makes text wrap into the panel instead of running off the right-hand edge. Nothing inside sets a
 * fixed height, so a card grows to fit its content rather than cropping it.
 * <p>
 * Every update arrives from a background thread, so all of them are marshalled onto the event
 * dispatch thread here rather than at each call site. Swing is not thread safe and the symptoms of
 * getting that wrong are intermittent and awful to diagnose.
 */
public class FlippingFriendPanel extends PluginPanel
{
	private static final org.slf4j.Logger log =
		org.slf4j.LoggerFactory.getLogger(FlippingFriendPanel.class);

	private final SuggestionEngine engine;
	private final TradeJournal journal;
	private final AccountMonitor accountMonitor;
	private final FlippingFriendConfig config;
	private final ConfigManager configManager;
	private final Explainer explainer;
	private final CompanionClient companion;
	private Runnable onRejection = () -> { };

	/** Our own handle on the scrolled column; PluginPanel keeps a private one of its own. */
	private JScrollPane scrolledColumn;

	private final SuggestionCard suggestionCard;
	/**
	 * The walkthrough, so the panel shows the trade actually being guided.
	 * <p>
	 * Reading the engine directly would let the panel and the overlay disagree: the guide holds its
	 * advice while an offer is half-entered, and a panel that had already moved on would be telling
	 * the player to do one thing while every highlight in the game pointed at another.
	 */
	private final com.flippingfriend.overlay.StepGuide stepGuide;
	private final PositionsPanel positionsPanel;
	private final HistoryPanel historyPanel;
	private final StatsPanel statsPanel;
	private final LearningPanel learningPanel;

	/**
	 * The companion's latest learning readings, or null when it has not answered.
	 *
	 * <p>Volatile and set from outside because fetching them is a network call and this panel is
	 * rebuilt on the Swing thread. Nothing here reaches for the companion; the plugin hands it over
	 * from its worker and this only ever renders what it was given.
	 */
	private volatile java.util.List<com.flippingfriend.model.LearningReading> learning;
	private volatile boolean companionReachable;
	private final PortfolioPanel portfolioPanel;
	private final BankTrajectoryPanel bankTrajectoryPanel;

	private final JPanel onboardingSlot = new JPanel(new BorderLayout());
	private final JButton sellOnlyButton = new JButton();
	private final JComboBox<RiskProfile> riskSelector = new JComboBox<>();
	private final JComboBox<CheckInterval> intervalSelector = new JComboBox<>();
	private final JLabel bankrollLabel = new JLabel();
	private final JTextArea sellOnlyNote;
	private final JTextArea riskDescription;
	private final JTextArea intervalDescription;

	private boolean suppressSelectorEvents;

	@Inject
	public FlippingFriendPanel(SuggestionEngine engine, PositionBook positions, TradeJournal journal,
		AccountMonitor accountMonitor, MarketDataService marketData, ItemManager itemManager,
		Explainer explainer, TaxCalculator taxCalculator,
		FlippingFriendConfig config, ConfigManager configManager,
		CompanionClient companion, com.flippingfriend.overlay.StepGuide stepGuide)
	{
		super(false);

		this.engine = engine;
		this.stepGuide = stepGuide;
		this.journal = journal;
		this.accountMonitor = accountMonitor;
		this.config = config;
		this.configManager = configManager;
		this.explainer = explainer;
		this.companion = companion;

		this.sellOnlyNote = UiUtils.wrappedText("", UiUtils.MUTED,
			FontManager.getRunescapeSmallFont(), UiUtils.CONTENT_WIDTH);
		this.riskDescription = UiUtils.wrappedText("", UiUtils.MUTED,
			FontManager.getRunescapeSmallFont(), UiUtils.CONTENT_WIDTH);
		this.intervalDescription = UiUtils.wrappedText("", UiUtils.MUTED,
			FontManager.getRunescapeSmallFont(), UiUtils.CONTENT_WIDTH);

		this.suggestionCard = new SuggestionCard(itemManager, explainer);
		this.positionsPanel = new PositionsPanel(positions, marketData, itemManager, explainer, taxCalculator,
			engine::getPositionStatuses);
		// Dismissing a holding is the only way one leaves the book without a sale, so it goes through
		// the same immediate-recompute path as Skip and Block rather than waiting for the next poll.
		this.positionsPanel.setOnClose(itemId ->
		{
			if (positions.close(itemId))
			{
				rejected();
			}
		});
		this.historyPanel = new HistoryPanel(journal, itemManager, explainer);
		this.statsPanel = new StatsPanel(explainer);
		this.learningPanel = new LearningPanel();
		this.portfolioPanel = new PortfolioPanel(companion, explainer);
		this.bankTrajectoryPanel = new BankTrajectoryPanel(journal, explainer);

		setLayout(new BorderLayout());
		setBackground(UiUtils.BACKGROUND);
		setBorder(BorderFactory.createEmptyBorder(UiUtils.PANEL_BORDER, UiUtils.PANEL_BORDER,
			UiUtils.PANEL_BORDER, UiUtils.PANEL_BORDER));

		UiUtils.ScrollableColumn content = new UiUtils.ScrollableColumn();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

		content.add(buildHeader());

		onboardingSlot.setBackground(UiUtils.BACKGROUND);
		onboardingSlot.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(UiUtils.gap(UiUtils.SPACE_M));
		content.add(onboardingSlot);

		// The session line only moves where this session is counted from -- every flip stays on
		// record -- so the panel can do it itself. Clearing the all-time figures also has to rebuild
		// the calibrator from the emptied history, and that lives with the plugin.
		statsPanel.setOnResetSession(() ->
		{
			journal.startSession();
			refresh();
		});
		statsPanel.setOnResetAllTime(() -> onResetAllTime.run());

		suggestionCard.setOnSkip(this::skipItem);
		suggestionCard.setOnBlock(this::blockItem);
		content.add(UiUtils.gap(UiUtils.SPACE_M));
		content.add(new CollapsibleSection("Recommendation", suggestionCard, false));

		content.add(new CollapsibleSection("Portfolio", portfolioPanel, false));
		content.add(new CollapsibleSection("Holding", positionsPanel, false));
		content.add(new CollapsibleSection("Recent trades", historyPanel, false));
		content.add(new CollapsibleSection("Performance", statsPanel, false));
		// Collapsed by default. It is a diagnostic, not something to read every trade -- but it has to
		// be reachable, because the one time it matters is when the advice has gone strange and the
		// reason is a number in here.
		content.add(new CollapsibleSection("Learning", learningPanel, true));
		content.add(new CollapsibleSection("Bank Trajectory", bankTrajectoryPanel, false));


		content.add(UiUtils.gap(UiUtils.SPACE_L));

		scrolledColumn = new JScrollPane(content,
			ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrolledColumn.setBackground(UiUtils.BACKGROUND);
		scrolledColumn.getViewport().setBackground(UiUtils.BACKGROUND);
		scrolledColumn.setBorder(BorderFactory.createEmptyBorder());
		scrolledColumn.getVerticalScrollBar().setUnitIncrement(16);
		scrolledColumn.getVerticalScrollBar().setPreferredSize(new Dimension(9, 0));

		add(scrolledColumn, BorderLayout.CENTER);

		refreshOnboarding();
	}

	private JPanel buildHeader()
	{
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(UiUtils.BACKGROUND);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel title = UiUtils.label("Flipping Friend", UiUtils.TEXT, FontManager.getRunescapeBoldFont());
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(title);

		bankrollLabel.setFont(FontManager.getRunescapeSmallFont());
		bankrollLabel.setForeground(UiUtils.MUTED);
		bankrollLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(UiUtils.gap(2));
		header.add(bankrollLabel);

		riskSelector.setModel(new DefaultComboBoxModel<>(RiskProfile.values()));
		riskSelector.setSelectedItem(config.riskProfile());
		riskSelector.addActionListener(e ->
		{
			if (suppressSelectorEvents)
			{
				return;
			}
			RiskProfile selected = (RiskProfile) riskSelector.getSelectedItem();
			if (selected != null)
			{
				configManager.setConfiguration(FlippingFriendConfig.GROUP, "riskProfile", selected);
				UiUtils.setWrappedText(riskDescription, selected.getDescription());
			}
		});

		// On the panel rather than buried in settings: this changes what gets suggested as much as
		// the risk level does, and it is the one setting that legitimately varies session to session
		// depending on what the player is actually doing.
		intervalSelector.setModel(new DefaultComboBoxModel<>(CheckInterval.values()));
		intervalSelector.setSelectedItem(config.checkInterval());
		intervalSelector.addActionListener(e ->
		{
			if (suppressSelectorEvents)
			{
				return;
			}
			CheckInterval selected = (CheckInterval) intervalSelector.getSelectedItem();
			if (selected != null)
			{
				configManager.setConfiguration(FlippingFriendConfig.GROUP, "checkInterval", selected);
				UiUtils.setWrappedText(intervalDescription, selected.getDescription());
			}
		});

		UiUtils.setWrappedText(riskDescription, config.riskProfile().getDescription());
		UiUtils.setWrappedText(intervalDescription, config.checkInterval().getDescription());

		// Above the risk selector, because when it is on it is the single most important thing about
		// what the plugin is going to say, and a mode that quietly stops every buy has to be visible
		// without scrolling or hunting through settings.
		sellOnlyButton.addActionListener(e ->
		{
			engine.setSellOnly(!engine.isSellOnly());
			// The companion holds the last buy it put in front of you, so that a momentary gap in the
			// plan does not blink the card. That hold has to be broken here or switching sell-only
			// back off re-serves the buy from before you started winding down -- chosen under the
			// old intent, and possibly minutes stale.
			companion.clearIncumbent();
			// Through rejected(), not a bare refresh(). The card draws what the walkthrough is
			// guiding and only the plugin's worker writes that, so repainting the panel changed the
			// button and left the advice showing a buy for up to a full refresh interval -- thirty
			// seconds by default, five minutes at the top of the range. Skip and Block were converted
			// to this long ago; the mode switch is the one control that was missed, and it is the one
			// where a stale answer costs the most.
			rejected();
		});
		sellOnlyButton.setFocusPainted(false);
		sellOnlyButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		sellOnlyButton.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 26));
		header.add(UiUtils.gap(UiUtils.SPACE_M));
		header.add(sellOnlyButton);
		header.add(UiUtils.gap(UiUtils.SPACE_XS));
		header.add(sellOnlyNote);

		header.add(UiUtils.gap(UiUtils.SPACE_M));
		header.add(selectorBlock("Risk level", riskSelector, riskDescription));
		header.add(UiUtils.gap(UiUtils.SPACE_M));
		header.add(selectorBlock("How often you check the GE", intervalSelector, intervalDescription));

		return header;
	}

	private JPanel selectorBlock(String label, JComboBox<?> selector, JTextArea description)
	{
		JPanel block = new JPanel();
		block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
		block.setBackground(UiUtils.BACKGROUND);
		block.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel caption = UiUtils.small(label);
		caption.setAlignmentX(Component.LEFT_ALIGNMENT);
		block.add(caption);
		block.add(UiUtils.gap(UiUtils.SPACE_XS));

		selector.setFocusable(false);
		selector.setAlignmentX(Component.LEFT_ALIGNMENT);
		selector.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		block.add(selector);

		description.setAlignmentX(Component.LEFT_ALIGNMENT);
		block.add(UiUtils.gap(UiUtils.SPACE_XS));
		block.add(description);

		return block;
	}

	/**
	 * Keeps the sell-only control showing the truth.
	 * <p>
	 * Driven from the engine rather than from the button's own state, because the engine clears the
	 * mode on login and the button would otherwise go on claiming it was still on.
	 */
	private void updateSellOnlyControl()
	{
		boolean on = engine.isSellOnly();
		sellOnlyButton.setText(on ? "Sell-only mode: ON — click to resume buying"
			: "Switch to sell-only");
		sellOnlyButton.setForeground(on ? UiUtils.WARNING : UiUtils.TEXT);
		UiUtils.setWrappedText(sellOnlyNote, on
			? "No new trades will be suggested. Open buys are offered for cancellation, and what you "
				+ "hold is sold on the usual rules. This clears when you next log in."
			: "Winding down? This stops new buys and helps you close out what you are holding.");
	}

	/**
	 * Hand over what the companion has learned. Safe to call from any thread; renders on the next
	 * refresh rather than forcing one, since this changes on a slow cadence and the panel is redrawn
	 * often anyway.
	 */
	public void setLearning(java.util.List<com.flippingfriend.model.LearningReading> readings,
		boolean reachable)
	{
		this.learning = readings;
		this.companionReachable = reachable;
	}

	private Runnable onResetAllTime = () -> { };

	/**
	 * What to do when the all-time figures are cleared. Set by the plugin, which owns the calibrator
	 * that has to be rebuilt afterwards -- otherwise it would go on correcting from a history the
	 * journal no longer holds.
	 */
	public void setOnResetAllTime(Runnable onResetAllTime)
	{
		this.onResetAllTime = onResetAllTime;
	}

	/** Safe to call from any thread. */
	public void refresh()
	{
		SwingUtilities.invokeLater(() ->
		{
			try
			{
				refreshOnEdt();
			}
			catch (Exception ex)
			{
				// One bad section must not take the window with it. Several of these panels clear
				// themselves before rebuilding, so an exception halfway through leaves the sidebar
				// part-empty and skips everything after it -- on every refresh from then on, because
				// nothing here was ever going to recover on its own.
				log.warn("could not refresh the panel", ex);
			}
		});
	}

	private void refreshOnEdt()
	{
		updateSellOnlyControl();

		// Several sections rebuild themselves wholesale, so the column's height changes underneath the
		// scrollbar and the viewport is clamped to whatever still fits. Holding the offset across the
		// rebuild keeps the player where they were reading.
		java.awt.Point scrolledTo = scrolledColumn == null ? null
			: scrolledColumn.getViewport().getViewPosition();

		// What the guide is actually guiding, which during a half-entered offer is not the newest
		// advice — plus whatever is queued behind it, so the deferral is visible rather than silent.
		Suggestion suggestion = stepGuide == null ? engine.getCurrent() : stepGuide.getSuggestion();
		suggestionCard.update(suggestion,
			stepGuide == null ? null : stepGuide.getPending());
		positionsPanel.refresh();
		historyPanel.refresh();

		SessionStats session = journal.sessionStats();
		SessionStats lifetime = journal.lifetimeStats();
		statsPanel.update(session, lifetime);
		if (!companionReachable)
		{
			learningPanel.clear("The companion is not running, so nothing is being learned.");
		}
		else
		{
			learningPanel.update(learning);
		}
		portfolioPanel.refresh();
		bankTrajectoryPanel.repaint();

		updateBankroll();
		syncSelectors();
		revalidate();
		repaint();

		if (scrolledTo != null)
		{
			// After the layout has settled, or the position is clamped against the old height.
			SwingUtilities.invokeLater(() ->
			{
				java.awt.Dimension extent = scrolledColumn.getViewport().getExtentSize();
				java.awt.Dimension full = scrolledColumn.getViewport().getViewSize();
				int highest = Math.max(0, full.height - extent.height);
				scrolledTo.y = Math.min(scrolledTo.y, highest);
				scrolledColumn.getViewport().setViewPosition(scrolledTo);
			});
		}
	}

	private void updateBankroll()
	{
		AccountState account = accountMonitor.getState();
		if (!account.isLoggedIn())
		{
			bankrollLabel.setText("Not logged in");
			return;
		}

		long spendable = account.spendableCoins(config.includeBankValue(), config.bankrollCap());
		String mode = account.isFreeToPlay() ? "  ·  free-to-play" : "";
		bankrollLabel.setText(explainer.formatGp(spendable) + "  ·  " + account.getFreeSlots()
			+ " of " + account.getTotalSlots() + " slots free" + mode);
	}

	/** Keeps the panel in step when settings are changed from RuneLite's own config screen. */
	private void syncSelectors()
	{
		RiskProfile profile = config.riskProfile();
		CheckInterval interval = config.checkInterval();

		if (riskSelector.getSelectedItem() != profile || intervalSelector.getSelectedItem() != interval)
		{
			suppressSelectorEvents = true;
			riskSelector.setSelectedItem(profile);
			UiUtils.setWrappedText(riskDescription, profile.getDescription());
			intervalSelector.setSelectedItem(interval);
			UiUtils.setWrappedText(intervalDescription, interval.getDescription());
			suppressSelectorEvents = false;
		}
	}

	private void refreshOnboarding()
	{
		onboardingSlot.removeAll();
		if (config.showOnboarding())
		{
			onboardingSlot.add(new OnboardingCard(() ->
			{
				config.setShowOnboarding(false);
				refreshOnboarding();
			}), BorderLayout.CENTER);
		}
		onboardingSlot.revalidate();
		onboardingSlot.repaint();
	}


	private void skipItem(Suggestion suggestion)
	{
		if (suggestion != null && suggestion.getItemId() > 0)
		{
			engine.skip(suggestion.getItemId());
			rejected();
		}
	}

	/** Adds the item to the permanent blocklist in config, so it survives restarts. */
	private void blockItem(Suggestion suggestion)
	{
		if (suggestion == null || suggestion.getItemId() <= 0 || suggestion.getItemName() == null)
		{
			return;
		}

		String existing = config.blockedItems() == null ? "" : config.blockedItems().trim();
		String name = suggestion.getItemName();
		if (existing.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)))
		{
			return;
		}

		String updated = existing.isEmpty() ? name : existing + ", " + name;
		configManager.setConfiguration(FlippingFriendConfig.GROUP, "blockedItems", updated);
		engine.skip(suggestion.getItemId());
		rejected();
	}

	/**
	 * Acts on a rejection immediately instead of waiting for the next poll.
	 * <p>
	 * Both buttons used to update only the built-in engine and repaint, but the card renders what the
	 * walkthrough is guiding, and only the plugin's refresh loop writes that. At the default thirty
	 * second interval the card sat unchanged for up to half a minute after a click, which reads as a
	 * broken button.
	 */
	private void rejected()
	{
		// No engine call here. This runs on the Swing thread, and refreshing the engine from it did
		// cache-miss HTTP on the event thread for a result that was discarded anyway -- the update the
		// player sees comes from onRejection, which goes through the worker like every other refresh.
		refresh();
		onRejection.run();
	}

	/** Lets the plugin re-run its full refresh, which is the only thing that updates the walkthrough. */
	public void setOnRejection(Runnable onRejection)
	{
		this.onRejection = onRejection == null ? () -> { } : onRejection;
	}

	public void setOnCardClicked(Runnable onCardClicked)
	{
		suggestionCard.setOnCardClicked(onCardClicked);
	}
}
