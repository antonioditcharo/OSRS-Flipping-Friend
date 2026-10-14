package com.flippingfriend.session;

import com.flippingfriend.data.PluginStorage;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Legacy local journal/delivery cache. Runtime trading ownership comes from the durable event ledger. */
@Singleton
public class PositionBook
{
	private static final Logger log = LoggerFactory.getLogger(PositionBook.class);

	private static final Type POSITION_LIST = new TypeToken<List<Position>>()
	{
	}.getType();
	private static final String FILE_NAME = "positions.json";
	private static final String LEDGER_FILE_NAME = "buy-costs.json";
	private static final Type LEDGER_LIST = new TypeToken<List<BuyCost>>()
	{
	}.getType();
	/**
	 * How long a buy's price stays available to reprice a sale that arrives with nothing behind it.
	 * <p>
	 * Long enough to cover an overnight hold, short enough that a price from days ago is never used
	 * to value something bought since.
	 */
	private static final long LEDGER_RETENTION_SECONDS = 48 * 3600;

	private final PluginStorage storage;
	private final Map<Integer, Position> positions = new ConcurrentHashMap<>();

	/**
	 * What was actually paid for each item recently, kept separately from the positions.
	 * <p>
	 * The cost basis used to live in one place only -- the position -- so anything that lost the
	 * position also lost the ability to price the eventual sale, and the flip was dropped from the
	 * journal as unpriceable. That is how 90 Rune nails sold for 70,200 gp and left no record of
	 * having been traded at all. This is the second copy: written when a buy fills, read when a sale
	 * turns up with no position to account against.
	 */
	private final Map<Integer, BuyCost> costLedger = new ConcurrentHashMap<>();

	private Path loadedFrom;

	@Inject
	public PositionBook(PluginStorage storage)
	{
		this.storage = storage;
	}

	public synchronized void load()
	{
		Path file = storage.accountDir().resolve(FILE_NAME);
		if (file.equals(loadedFrom))
		{
			return;
		}

		positions.clear();
		for (Position position : storage.<List<Position>>readJson(file, POSITION_LIST, new ArrayList<>()))
		{
			if (position.getQuantity() > 0)
			{
				positions.put(position.getItemId(), position);
			}
		}

		costLedger.clear();
		long cutoff = Instant.now().getEpochSecond() - LEDGER_RETENTION_SECONDS;
		for (BuyCost cost : storage.<List<BuyCost>>readJson(
			storage.accountDir().resolve(LEDGER_FILE_NAME), LEDGER_LIST, new ArrayList<>()))
		{
			if (cost != null && cost.itemId > 0 && cost.quantity > 0 && cost.boughtAt >= cutoff)
			{
				costLedger.put(cost.itemId, cost);
			}
		}

		loadedFrom = file;
	}

	public synchronized void save()
	{
		if (!storage.hasAccount())
		{
			return;
		}
		Path file = storage.accountDir().resolve(FILE_NAME);
		storage.writeJson(file, new ArrayList<>(positions.values()), POSITION_LIST);

		long cutoff = Instant.now().getEpochSecond() - LEDGER_RETENTION_SECONDS;
		costLedger.values().removeIf(cost -> cost.boughtAt < cutoff);
		storage.writeJson(storage.accountDir().resolve(LEDGER_FILE_NAME),
			new ArrayList<>(costLedger.values()), LEDGER_LIST);

		loadedFrom = file;
	}

	public Collection<Position> all()
	{
		return positions.values();
	}

	public Position get(int itemId)
	{
		return positions.get(itemId);
	}

	public boolean isEmpty()
	{
		return positions.isEmpty();
	}

	public int size()
	{
		return positions.size();
	}

	/** Records a completed buy, blending into an existing position for the same item if there is one. */
	public synchronized Position recordBuy(int itemId, String itemName, int quantity, long totalCost,
		Instant when)
	{
		if (quantity <= 0)
		{
			return positions.get(itemId);
		}

		// The second copy of what this cost, written before the position so that even a position that
		// never survives leaves a price behind.
		costLedger.merge(itemId, new BuyCost(itemId, itemName, quantity, totalCost,
			when.getEpochSecond()), BuyCost::plus);

		return positions.compute(itemId, (id, existing) ->
		{
			if (existing == null)
			{
				Position created = new Position(id, itemName, quantity, totalCost, when.getEpochSecond(), true);
				return created;
			}
            // Keep an old unpriced adoption intact. A new purchase cannot price its personal units.
            // The acquisition cost cache records the observed purchase separately above.
            if (!existing.isCostKnown()) return existing;
			existing.addFill(quantity, totalCost);
			if (existing.getItemName() == null)
			{
				existing.setItemName(itemName);
			}
			return existing;
		});
	}

	/**
	 * What was recently paid per unit for an item, or 0 when nothing is known.
	 * <p>
	 * Deliberately an average of recent buys rather than an attempt to match lots: the Grand Exchange
	 * has no lots, and a blended price is what the position itself would have carried.
	 */
	public int recentUnitCost(int itemId)
	{
		BuyCost cost = usable(itemId);
		return cost == null ? 0 : (int) Math.max(1, cost.totalCost / cost.quantity);
	}

	/**
	 * Claims units the ledger can still vouch for, to price a sale the positions could not account for.
	 * <p>
	 * <b>It is an account, not a price list, and that distinction is the whole safety of it.</b> Every
	 * unit it hands out is spent, so it can only ever vouch for things that were actually bought and
	 * have not already been paid for by a position. Handing out a price on demand instead — which was
	 * the first attempt — valued 3,558 units sold against 500 units bought and turned a margin into
	 * proceeds, which is the shape of the worst bug this journal has ever had.
	 *
	 * @param quantity how many units need a price
	 * @return what the ledger could cover and what it cost; the remainder stays unpriced
	 */
	public synchronized Position.Removal claimUnaccounted(int itemId, int quantity)
	{
		if (quantity <= 0)
		{
			return Position.Removal.none(0);
		}
		BuyCost cost = usable(itemId);
		if (cost == null)
		{
			return Position.Removal.none(quantity);
		}

		int claimed = Math.min(quantity, cost.quantity);
		long unitCost = Math.max(1, cost.totalCost / cost.quantity);
		long claimedCost = unitCost * claimed;

		cost.quantity -= claimed;
		cost.totalCost = Math.max(0, cost.totalCost - claimedCost);
		if (cost.quantity <= 0)
		{
			costLedger.remove(itemId);
		}
		return new Position.Removal(claimed, claimedCost, quantity - claimed);
	}

	/**
	 * Spends ledger units against a sale the positions did account for.
	 * <p>
	 * Without this the same purchase could be paid for twice — once by its position and again by the
	 * ledger — which is how a reconstruction turns into invented profit.
	 */
	private void consumeLedger(int itemId, int quantity)
	{
		if (quantity > 0)
		{
			claimUnaccounted(itemId, quantity);
		}
	}

	/** The ledger entry for an item, if there is one and it is recent enough to trust. */
	private BuyCost usable(int itemId)
	{
		BuyCost cost = costLedger.get(itemId);
		if (cost == null || cost.quantity <= 0
			|| cost.boughtAt < Instant.now().getEpochSecond() - LEDGER_RETENTION_SECONDS)
		{
			return null;
		}
		return cost;
	}

	/**
	 * Records a completed sell.
	 *
	 * @return what the book could account for: the cost basis, and how many units it could not
	 */
	public synchronized Position.Removal recordSell(int itemId, int quantity)
	{
		Position position = positions.get(itemId);
		if (position == null)
		{
			return Position.Removal.none(quantity);
		}

		Position.Removal removal = position.removeQuantity(quantity);
		if (position.getQuantity() <= 0)
		{
			positions.remove(itemId);
		}
		// What the position paid for is now sold, so the ledger must not offer to pay for it again.
		consumeLedger(itemId, removal.getBackedQuantity());
		return removal;
	}

	/**
	 * Adds a holding the plugin did not buy itself, so items already in the bank get sell advice
	 * too. Existing positions are left alone, because a known cost basis is more valuable than a
	 * recount.
	 */
	public void adoptExisting(int itemId, String itemName, int quantity, Instant when)
	{
		if (quantity <= 0)
		{
			return;
		}
		positions.computeIfAbsent(itemId, id -> Position.preExisting(id, itemName, quantity, when.getEpochSecond()));
	}

	/**
	 * Reconciles against what the account actually holds. Offers and manual trades happen outside
	 * the plugin's view, so without this the book slowly drifts from reality.
	 */
	public void reconcile(Map<Integer, Integer> actualHoldings)
	{
		reconcile(actualHoldings, java.util.Collections.emptyMap());
	}

	/**
	 * @param listedForSale quantity of each item sitting in open sell offers
	 */
	public void reconcile(Map<Integer, Integer> actualHoldings, Map<Integer, Integer> listedForSale)
	{
		reconcile(actualHoldings, listedForSale, true);
	}

    /** Compatibility hook: container observations never change recorded acquisition quantities. */

	public void reconcile(Map<Integer, Integer> actualHoldings, Map<Integer, Integer> listedForSale,
		boolean holdingsAreComplete)
	{
        // Quantity changes require execution evidence. Extra physical units are personal stock;
        // missing units may be banked or uncollected. Keep legacy records intact.

	}

	/**
	 * Closes a position because the player says they no longer hold it.
	 * <p>
	 * The counterpart to no longer deleting on absence: something sold outside the plugin, dropped or
	 * alched would otherwise sit in the list for ever. This is the only way a position leaves the book
	 * without a sale, and it is deliberately a deliberate act.
	 *
	 * @return true if there was a position to close
	 */
	public boolean close(int itemId)
	{
		Position removed = positions.remove(itemId);
		if (removed == null)
		{
			return false;
		}
		log.info("closed {} x {} by hand, cost {} gp -- no sale accounted for it",
			removed.getQuantity(), removed.getItemName(), removed.getTotalCost());
		return true;
	}

	public synchronized void clear()
	{
		positions.clear();
		costLedger.clear();
		loadedFrom = null;
	}

	/** Recent purchases of one item, blended, so a sale with no position behind it can be priced. */
	static final class BuyCost
	{
		private int itemId;
		private String itemName;
		/** Units bought and not yet paid for by a sale. Spent down as sales account for them. */
		private int quantity;
		private long totalCost;
		private long boughtAt;

		BuyCost()
		{
		}

		BuyCost(int itemId, String itemName, int quantity, long totalCost, long boughtAt)
		{
			this.itemId = itemId;
			this.itemName = itemName;
			this.quantity = quantity;
			this.totalCost = totalCost;
			this.boughtAt = boughtAt;
		}

		/** Blends two buys of the same item, carrying the later timestamp so retention follows use. */
		static BuyCost plus(BuyCost first, BuyCost second)
		{
			return new BuyCost(first.itemId,
				second.itemName == null ? first.itemName : second.itemName,
				first.quantity + second.quantity,
				first.totalCost + second.totalCost,
				Math.max(first.boughtAt, second.boughtAt));
		}
	}
}
