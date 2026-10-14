package com.flippingfriend.overlay;

import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionType;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;

/**
 * Works out which single thing the player should click next, and where it is on screen.
 * <p>
 * Most of the answer is read straight from live game state — is the Grand Exchange open, is the
 * offer editor up, which item is loaded into it — because state that is derived fresh cannot drift
 * out of sync with what the player is actually looking at. Quantity and the 64-bit price are read
 * from committed GE variables. Clicks only identify
 * an input currently being edited; they never prove its value.
 * <p>
 * When the quantity and price controls cannot be identified in the interface, the guide degrades
 * deliberately: it highlights the whole editor and shows both numbers at once. Less precise, but it
 * never points at the wrong thing, and the player still has everything they need.
 */
@Singleton
public class StepGuide
{
	private final Client client;
	private final GeWidgetResolver resolver;

	private volatile Suggestion tracked = Suggestion.idle();
	/** Advice that arrived mid-entry and is waiting for the editor to close. */
	private volatile Suggestion pending;
    private volatile Suggestion editorQuote;
	private volatile boolean quantityDone;
	private volatile boolean priceDone;
    private volatile boolean entryStarted;
    private Widget editingControl;

	/** Whether the quantity field has been dealt with, so a hint for it can stop being drawn. */
	public boolean isQuantityDone()
	{
		refreshProgress();
		return quantityDone;
	}

	/** Whether the price field has been dealt with. */
	public boolean isPriceDone()
	{
		refreshProgress();
		return priceDone;
	}

	@Inject
	public StepGuide(Client client, GeWidgetResolver resolver)
	{
		this.client = client;
		this.resolver = resolver;
	}

	/**
	 * Takes new advice, unless doing so would pull the walkthrough out from under a half-entered
	 * offer.
	 * <p>
	 * Advice changes constantly and for good reasons — an offer completes and wants collecting, a
	 * held position comes due to sell, the market moves. Applying that instantly is right almost
	 * always, and badly wrong in one case: the player has the offer editor open, has already typed a
	 * quantity, and is reaching for the price. Swapping the target then moves every highlight to a
	 * different slot mid-keystroke, and the likeliest outcome is an offer placed at the wrong number
	 * for the wrong item — the plugin causing exactly the mistake it exists to prevent.
	 * <p>
	 * So a change that arrives mid-entry is <em>queued</em> rather than applied. The player finishes
	 * what they started, the panel tells them what is waiting, and the moment the editor closes the
	 * queued advice takes over. Nothing is ever more urgent than not fumbling the offer already being
	 * typed: a finished offer waits indefinitely, and a sale a few seconds later is a rounding error
	 * against placing the wrong trade entirely.
	 */
	public void setSuggestion(Suggestion suggestion)
	{
		Suggestion incoming = suggestion == null ? Suggestion.idle() : suggestion;
		if (incoming.sameAs(tracked))
		{
			pending = null;
			return;
		}

		// A correction to the trade already being typed is applied even mid-entry: the whole point of
		// holding advice back is to avoid placing the wrong offer, and letting a price go stale under
		// the player's fingers achieves exactly that by a different route. Only a genuinely different
		// trade waits.
		if (isMidEntry() && !incoming.isSameTradeAs(tracked))
		{
			pending = incoming;
			return;
		}

		boolean correction = incoming.isSameTradeAs(tracked);
		int oldPrice = tracked.getPrice();
		int oldQuantity = tracked.getQuantity();

		tracked = incoming;
		pending = null;
		
		if (!correction)
		{
			quantityDone = false;
			priceDone = false;
            entryStarted = false; editingControl = null;
		}
		else
		{
			if (incoming.getPrice() != oldPrice)
			{
				priceDone = false;
			}
			if (incoming.getQuantity() != oldQuantity)
			{
				quantityDone = false;
			}
		}
	}

	/**
	 * True while the player is partway through filling in an offer: the editor is open and at least
	 * one of the two fields has been set.
	 */
	private boolean isMidEntry()
	{
		// Nothing is being typed if nothing has been entered, and that half of the question needs no
		// widgets — so ask it first and usually avoid touching the client at all.
		if (!entryStarted)
		{
			return false;
		}
		// Widgets are client-thread only. A caller on the wrong thread used to take an AssertionError
		// straight out of setSuggestion and lose the recommendation entirely; deferring advice is a
		// small cost and losing it is a total one, so an unanswerable question means "not mid-entry".
		if (!client.isClientThread())
		{
			return false;
		}
		return resolver.isSetupOpen();
	}

	/**
	 * Advice that arrived while the player was mid-entry and is waiting its turn, or null.
	 * Surfaced by the panel so the interruption is visible rather than silently deferred.
	 */
	public Suggestion getPending()
	{
		return pending;
	}

	/** Applies whatever was queued, once it is safe to do so. */
	private void applyPending()
	{
		if (pending != null)
		{
			tracked = pending;
			pending = null;
		}
	}

    public void setEditorQuote(Suggestion quote) {
        if (quote != null && (editorQuote == null || !quote.sameAs(editorQuote))) {
            quantityDone = true; priceDone = false;
        }
        editorQuote = quote;
    }
    public Suggestion getSuggestion() { return editorQuote == null ? tracked : editorQuote; }

    /** Clicking a control starts entry; only committed values establish completion. */
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (!client.isClientThread() || !resolver.isSetupOpen()) return;
        Widget quantity = resolver.getQuantityControl(), price = resolver.getPriceControl();
        if (isSameWidget(event, quantity)) { entryStarted = true; editingControl = quantity; }
        else if (isSameWidget(event, price)) { entryStarted = true; editingControl = price; }
        refreshProgress();
    }

    private void refreshProgress() {
        if (!client.isClientThread()) return;
        GeEditorValues values = GeEditorValues.read(client, resolver);
        Suggestion suggestion = getSuggestion();
        boolean context = values != null && values.contextMatches(suggestion);
        quantityDone = context && values.quantity == suggestion.getQuantity()
            && !(values.typing && editingControl == resolver.getQuantityControl());
        priceDone = context && values.price == suggestion.getPrice()
            && !(values.typing && editingControl == resolver.getPriceControl());
    }
    GeEditorValues editorValues() { return GeEditorValues.read(client, resolver); }
    public void reset() {
        tracked = Suggestion.idle(); pending = null; onSetupClosed();
    }

	/** Clears progress when the editor closes, so re-opening it starts the walkthrough again. */
	public void onSetupClosed()
	{
        editorQuote = null;
		quantityDone = false;
		priceDone = false;
        entryStarted = false; editingControl = null;
		// The offer is placed or abandoned either way, so anything held back can now take over.
		applyPending();
	}

	/**
	 * The current step and what to draw around. Must be called on the client thread.
	 *
	 * @param occupiedSlots which Grand Exchange slots currently hold an offer
	 */
	public GuideState resolve(int totalSlots, boolean[] occupiedSlots)
	{
		Suggestion suggestion = getSuggestion();
		if (suggestion == null || !suggestion.isActionable())
		{
			return GuideState.none();
		}

		if (!resolver.isGeOpen())
		{
			return new GuideState(GuideStep.OPEN_GE, new ArrayList<>(), suggestion);
		}

		switch (suggestion.getType())
		{
			case COLLECT:
				return collectState(suggestion);
			case MODIFY_BUY:
			case MODIFY_SELL:
				return adjustState(suggestion);
			case BUY:
				return offerState(suggestion, true, totalSlots, occupiedSlots);
			case SELL:
				return offerState(suggestion, false, totalSlots, occupiedSlots);
			// Not a sell. CANCEL used to mean "cut a loss", which the player does by selling, so it
			// shared the sell walkthrough; it now means abandon the offer outright, and walking
			// someone through placing a sell would be the opposite of the instruction.
			case CANCEL:
				return abandonState(suggestion);
			default:
				return GuideState.none();
		}
	}

	private GuideState collectState(Suggestion suggestion)
	{
		List<Widget> targets = new ArrayList<>();

		Widget collectAll = resolver.getCollectAllButton();
		if (collectAll != null)
		{
			targets.add(collectAll);
		}

		Widget slot = resolver.getSlot(suggestion.getSlot());
		if (slot != null)
		{
			targets.add(slot);
		}

		return new GuideState(GuideStep.COLLECT, targets, suggestion);
	}

	private GuideState adjustState(Suggestion suggestion)
	{
        if (resolver.isSetupOpen()) return editorState(suggestion);

		List<Widget> targets = new ArrayList<>();
		Widget slot = resolver.getSlot(suggestion.getSlot());
		if (slot != null)
		{
			targets.add(slot);
		}
		return new GuideState(GuideStep.CANCEL_OFFER, targets, suggestion);
	}

	/** Points at the slot holding the offer. Nothing is placed afterwards, so the guide stops there. */
	private GuideState abandonState(Suggestion suggestion)
	{
		List<Widget> targets = new ArrayList<>();
		Widget slot = resolver.getSlot(suggestion.getSlot());
		if (slot != null)
		{
			targets.add(slot);
		}
		return new GuideState(GuideStep.ABANDON_OFFER, targets, suggestion);
	}

	private GuideState offerState(Suggestion suggestion, boolean buying, int totalSlots,
		boolean[] occupiedSlots)
	{
		if (!resolver.isSetupOpen())
		{
			if (buying)
			{
				List<Widget> slots = resolver.getEmptySlots(totalSlots, occupiedSlots);
				return new GuideState(GuideStep.PICK_SLOT, slots, suggestion);
			}

			// Selling starts from the item in the side panel, not from an empty slot.
			List<Widget> targets = new ArrayList<>();
			Widget item = resolver.getSideItem(suggestion.getItemId());
			if (item != null)
			{
				targets.add(item);
				return new GuideState(GuideStep.SELL_FROM_INVENTORY, targets, suggestion);
			}

			// If the item is not in the inventory, it must be in the collection box.
			if (suggestion.getSlot() >= 0)
			{
				Widget slot = resolver.getSlot(suggestion.getSlot());
				if (slot != null)
				{
					targets.add(slot);
					return new GuideState(GuideStep.COLLECT, targets, suggestion);
				}
			}

			return new GuideState(GuideStep.SELL_FROM_INVENTORY, targets, suggestion);
		}

        return editorState(suggestion);
    }

    private GuideState editorState(Suggestion suggestion) {
        GeEditorValues values = editorValues();
        if (values == null) return GuideState.none();
        if (values.item <= 0 && values.type == 0 && values.slot >= 0 && values.slot < 8
            && (suggestion.getType() == SuggestionType.BUY || suggestion.getType() == SuggestionType.MODIFY_BUY)) {
            Widget result = resolver.getSearchResult(suggestion.getItemId());
            return new GuideState(result == null ? GuideStep.SEARCH_ITEM : GuideStep.PICK_SEARCH_RESULT,
                single(result), suggestion);
        }
        if (!values.usable()) return GuideState.none();
        if (!values.contextMatches(suggestion)) {
            if (values.type == 0 && suggestion.getType() == SuggestionType.BUY) {
                Widget result = resolver.getSearchResult(suggestion.getItemId());
                return new GuideState(result == null ? GuideStep.SEARCH_ITEM : GuideStep.PICK_SEARCH_RESULT,
                    single(result), suggestion);
            }
            Widget back = client.getWidget(net.runelite.api.gameval.InterfaceID.GeOffers.BACK);
            return new GuideState(GuideStep.CHECK_OFFER, single(back), suggestion);
        }
        refreshProgress();
        if (values.matches(suggestion)) return new GuideState(GuideStep.CONFIRM, single(resolver.getConfirmButton()), suggestion);
        Widget quantity = resolver.getQuantityControl(), price = resolver.getPriceControl();
        List<Widget> targets = new ArrayList<>();
        if (!quantityDone && quantity != null) targets.add(quantity);
        if (!priceDone && price != null) targets.add(price);
        if (values.typing && editingControl != null && !targets.contains(editingControl)) targets.add(editingControl);
        if (targets.isEmpty()) targets.addAll(single(resolver.getSetupPanel()));
        boolean both = (!quantityDone && !priceDone) || quantity == null || price == null;
        return new GuideState(!quantityDone ? GuideStep.SET_QUANTITY : GuideStep.SET_PRICE, targets, suggestion, both);
    }

	private static boolean isSameWidget(MenuOptionClicked event, Widget widget)
	{
		if (widget == null)
		{
			return false;
		}
		Widget clicked = event.getWidget();
		if (clicked != null && clicked == widget)
		{
			return true;
		}
		// getParam1 carries the packed widget id of the clicked component.
		return widget.getId() > 0 && event.getParam1() == widget.getId()
            && event.getParam0() == widget.getIndex();
	}

	private static List<Widget> single(Widget widget)
	{
		List<Widget> targets = new ArrayList<>(1);
		if (widget != null)
		{
			targets.add(widget);
		}
		return targets;
	}

	/** The answer: which step, and which widgets to draw around. */
	public static final class GuideState
	{
		private static final GuideState NONE =
			new GuideState(GuideStep.NONE, new ArrayList<>(), Suggestion.idle());

		private final GuideStep step;
		private final List<Widget> targets;
		private final Suggestion suggestion;
		private final boolean showBothNumbers;

		GuideState(GuideStep step, List<Widget> targets, Suggestion suggestion)
		{
			this(step, targets, suggestion, false);
		}

		GuideState(GuideStep step, List<Widget> targets, Suggestion suggestion, boolean showBothNumbers)
		{
			this.step = step;
			this.targets = targets;
			this.suggestion = suggestion;
			this.showBothNumbers = showBothNumbers;
		}

		static GuideState none()
		{
			return NONE;
		}

		public GuideStep getStep()
		{
			return step;
		}

		public List<Widget> getTargets()
		{
			return targets;
		}

		public Suggestion getSuggestion()
		{
			return suggestion;
		}

		/** True when the editor controls could not be told apart and both numbers should be shown. */
		public boolean isShowBothNumbers()
		{
			return showBothNumbers;
		}

		public boolean hasTargets()
		{
			return !targets.isEmpty();
		}
	}
}
