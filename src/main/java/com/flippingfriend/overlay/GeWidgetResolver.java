package com.flippingfriend.overlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/**
 * Finds the Grand Exchange widgets the walkthrough needs to point at.
 * <p>
 * The eight slots, the setup panel and the confirm button have stable generated ids, so those are
 * looked up directly. The quantity and price controls inside the setup panel do not — they are
 * dynamic children whose indices Jagex is free to renumber in any update. Hardcoding those indices
 * is the usual approach and it breaks silently: the plugin carries on drawing a highlight, just
 * around the wrong thing.
 * <p>
 * So they are found by what they <em>do</em> instead — a child offering an "Enter quantity" or
 * "Enter price" action — and if that fails, the resolver says so rather than guessing. The overlay
 * then highlights the whole setup panel, which is less precise but never wrong, and the instruction
 * card still shows the exact numbers to type.
 */
@Singleton
public class GeWidgetResolver
{
	private static final String[] QUANTITY_HINTS = {"enter quantity", "set quantity", "custom quantity"};
	private static final String[] PRICE_HINTS = {"enter price", "set price", "custom price"};
	/** Depth is bounded so a malformed interface cannot send the search spinning. */
	private static final int MAX_DEPTH = 4;

	private final Client client;
	private final net.runelite.client.game.ItemManager itemManager;

	@Inject
	public GeWidgetResolver(Client client, net.runelite.client.game.ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
	}

	/** True when the Grand Exchange interface is on screen. */
	public boolean isGeOpen()
	{
		Widget contents = client.getWidget(InterfaceID.GeOffers.CONTENTS);
		return contents != null && !contents.isHidden();
	}

	/** True when the buy/sell offer editor is on screen. */
	public boolean isSetupOpen()
	{
		Widget setup = getSetupPanel();
		return setup != null && !setup.isHidden();
	}

	public Widget getSetupPanel()
	{
		return visible(client.getWidget(InterfaceID.GeOffers.SETUP));
	}

	public Widget getConfirmButton()
	{
		return visible(client.getWidget(InterfaceID.GeOffers.SETUP_CONFIRM));
	}

	public Widget getCollectAllButton()
	{
		return visible(client.getWidget(InterfaceID.GeOffers.COLLECTALL));
	}

	public Widget getDetailsCollectButton()
	{
		return visible(client.getWidget(InterfaceID.GeOffers.DETAILS_COLLECT));
	}

	/**
	 * @param slot 0-7
	 */
	public Widget getSlot(int slot)
	{
		if (slot < 0 || slot > 7)
		{
			return null;
		}
		return visible(client.getWidget(InterfaceID.GeOffers.INDEX_0 + slot));
	}

	/** Quantity control inside the offer editor, or null when it cannot be identified. */
	public Widget getQuantityControl()
	{
		return findByAction(getSetupPanel(), QUANTITY_HINTS);
	}

	/** Price control inside the offer editor, or null when it cannot be identified. */
	public Widget getPriceControl()
	{
		return findByAction(getSetupPanel(), PRICE_HINTS);
	}

	/**
	 * The row in the search results matching an item, so the player can be shown which of several
	 * similarly named items to click.
	 */
	public Widget getSearchResult(int itemId)
	{
		Widget results = visible(client.getWidget(InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS));
		if (results == null)
		{
			return null;
		}

		Widget[] children = results.getDynamicChildren();
		if (children == null)
		{
			return null;
		}

		// The results are laid out as repeating groups; the widget carrying the item id is the one
		// worth pointing at, and its neighbours make up the same clickable row.
		for (Widget child : children)
		{
			if (child != null && child.getItemId() == itemId)
			{
				return child;
			}
		}
		return null;
	}

	/**
	 * The item in the side panel that a sell offer is started from. Selling begins by clicking the
	 * item itself rather than an empty slot, which is the step new players most often miss.
	 */
	public Widget getSideItem(int itemId)
	{
		Widget side = visible(client.getWidget(InterfaceID.GeOffersSide.ITEMS));
		if (side == null)
		{
			return null;
		}

		Widget[] children = side.getDynamicChildren();
		if (children == null)
		{
			return null;
		}
		// Compared on the canonical id, or a noted stack never matches.
		//
		// AccountMonitor folds notes into their unnoted form, so a position in notes carries the
		// unnoted id while the widget carries the noted one -- and this highlight has been silently
		// doing nothing for every noted holding, which on herbs and logs is most of them. The player
		// sees no highlight on the step they most often miss.
		int wanted = itemManager.canonicalize(itemId);
		for (Widget child : children)
		{
			// The id is checked before canonicalising it: an empty entry in the panel carries -1, and
			// asking the item manager to canonicalise a non-item is not a question it has an answer to.
			if (child == null || child.isHidden() || child.getItemId() <= 0)
			{
				continue;
			}
			if (itemManager.canonicalize(child.getItemId()) == wanted)
			{
				return child;
			}
		}
		return null;
	}

	/** Every slot with no offer in it, for the "click an empty slot" step. */
	public List<Widget> getEmptySlots(int totalSlots, boolean[] occupied)
	{
		List<Widget> empty = new ArrayList<>();
		for (int slot = 0; slot < totalSlots && slot < occupied.length; slot++)
		{
			if (occupied[slot])
			{
				continue;
			}
			Widget widget = getSlot(slot);
			if (widget != null)
			{
				empty.add(widget);
			}
		}
		return empty;
	}

	private Widget findByAction(Widget root, String[] hints)
	{
		if (root == null)
		{
			return null;
		}
		return search(root, hints, 0);
	}

	private Widget search(Widget widget, String[] hints, int depth)
	{
		if (widget == null || depth > MAX_DEPTH)
		{
			return null;
		}

		if (!widget.isHidden() && matches(widget, hints))
		{
			return widget;
		}

		Widget found = searchAll(widget.getStaticChildren(), hints, depth);
		if (found != null)
		{
			return found;
		}
		found = searchAll(widget.getDynamicChildren(), hints, depth);
		if (found != null)
		{
			return found;
		}
		return searchAll(widget.getNestedChildren(), hints, depth);
	}

	private Widget searchAll(Widget[] children, String[] hints, int depth)
	{
		if (children == null)
		{
			return null;
		}
		for (Widget child : children)
		{
			Widget found = search(child, hints, depth + 1);
			if (found != null)
			{
				return found;
			}
		}
		return null;
	}

	private static boolean matches(Widget widget, String[] hints)
	{
		String[] actions = widget.getActions();
		if (actions != null)
		{
			for (String action : actions)
			{
				if (action == null)
				{
					continue;
				}
				String normalised = action.toLowerCase(Locale.ROOT);
				for (String hint : hints)
				{
					if (normalised.contains(hint))
					{
						return true;
					}
				}
			}
		}

		String text = widget.getText();
		if (text != null)
		{
			String normalised = text.toLowerCase(Locale.ROOT);
			for (String hint : hints)
			{
				if (normalised.contains(hint))
				{
					return true;
				}
			}
		}
		return false;
	}

	private static Widget visible(Widget widget)
	{
		return widget == null || widget.isHidden() ? null : widget;
	}
}
