package com.flippingfriend.session;

import com.flippingfriend.data.PluginStorage;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Tracks how much of each item's 4-hour buy limit has been used.
 * <p>
 * The game does not tell us this, and getting it wrong is expensive in a way that is easy to miss:
 * an offer over the limit simply stops filling part-way with no error, tying up a Grand Exchange
 * slot and leaving a half position that has to be sold at whatever the market has moved to.
 * <p>
 * The window is anchored, not sliding. Buying an item starts a four hour window; everything bought
 * inside it counts against the same allowance, and the whole allowance returns when that window
 * ends. Modelling it as a sliding window — the intuitive guess — would let the plugin suggest
 * purchases the game will refuse.
 */
@Singleton
public class BuyLimitTracker
{
	public static final Duration WINDOW = Duration.ofHours(4);

	private static final Type WINDOW_LIST = new TypeToken<List<LimitWindow>>()
	{
	}.getType();
	private static final String FILE_NAME = "buy-limits.json";

	private final PluginStorage storage;
	private final Map<Integer, LimitWindow> windows = new ConcurrentHashMap<>();

	private Path loadedFrom;

	@Inject
	public BuyLimitTracker(PluginStorage storage)
	{
		this.storage = storage;
	}

	/** Loads the ledger for whichever account is now logged in. */
	public synchronized void load()
	{
		Path file = storage.accountDir().resolve(FILE_NAME);
		if (file.equals(loadedFrom))
		{
			return;
		}

		windows.clear();
		List<LimitWindow> saved = storage.readJson(file, WINDOW_LIST, new ArrayList<>());
		for (LimitWindow window : saved)
		{
			if (!window.isExpired(Instant.now()))
			{
				windows.put(window.itemId, window);
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
		prune(Instant.now());
		Path file = storage.accountDir().resolve(FILE_NAME);
		storage.writeJson(file, new ArrayList<>(windows.values()), WINDOW_LIST);
		loadedFrom = file;
	}

	/**
	 * Records items actually received from a buy offer.
	 *
	 * @param quantity number of items bought, not the offer size
	 */
	public void recordPurchase(int itemId, int quantity, Instant when)
	{
		if (quantity <= 0)
		{
			return;
		}

		windows.compute(itemId, (id, existing) ->
		{
			if (existing == null || existing.isExpired(when))
			{
				return new LimitWindow(id, quantity, when.getEpochSecond());
			}
			existing.quantity += quantity;
			return existing;
		});
	}



	public int purchasedInWindow(int itemId, Instant now)
	{
		LimitWindow window = windows.get(itemId);
		if (window == null || window.isExpired(now))
		{
			return 0;
		}
		return window.quantity;
	}

	/** How many more of this item the game will let us buy right now. */
	public int remaining(int itemId, int buyLimit, Instant now)
	{
		return Math.max(0, buyLimit - purchasedInWindow(itemId, now));
	}

	/** When the allowance for this item comes back, or null if it was never used. */
	public Instant resetsAt(int itemId, Instant now)
	{
		LimitWindow window = windows.get(itemId);
		if (window == null || window.isExpired(now))
		{
			return null;
		}
		return Instant.ofEpochSecond(window.startedAt).plus(WINDOW);
	}

	public synchronized void prune(Instant now)
	{
		Iterator<Map.Entry<Integer, LimitWindow>> it = windows.entrySet().iterator();
		while (it.hasNext())
		{
			if (it.next().getValue().isExpired(now))
			{
				it.remove();
			}
		}
	}

	public Map<Integer, Integer> activeWindows(Instant now)
	{
		Map<Integer, Integer> active = new HashMap<>();
		for (LimitWindow window : windows.values())
		{
			if (!window.isExpired(now))
			{
				active.put(window.itemId, window.quantity);
			}
		}
		return active;
	}

	/** Visible for testing: drops all state without touching disk. */
	synchronized void clear()
	{
		windows.clear();
		loadedFrom = null;
	}

	private static class LimitWindow
	{
		private int itemId;
		private int quantity;
		private long startedAt;

		LimitWindow()
		{
		}

		LimitWindow(int itemId, int quantity, long startedAt)
		{
			this.itemId = itemId;
			this.quantity = quantity;
			this.startedAt = startedAt;
		}

		boolean isExpired(Instant now)
		{
			return Instant.ofEpochSecond(startedAt).plus(WINDOW).isBefore(now);
		}
	}
}
