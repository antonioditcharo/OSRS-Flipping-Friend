package com.flippingfriend.session;

import com.flippingfriend.data.PluginStorage;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Items the plugin should stop suggesting, and until when.
 * <p>
 * Two kinds live here, because they mean different things. A skip from the panel is the player saying
 * "not this one, show me something else" — a preference about right now, which should not outlive the
 * session. Abandoning a part-filled buy is the player saying "I am done with this item", which is a
 * decision about the item and has to survive a restart, or an eight-hour cooldown means nothing the
 * first time RuneLite is closed.
 * <p>
 * So session skips are held in memory and dropped on restart exactly as before, and timed skips are
 * written to disk alongside the buy-limit ledger they closely resemble — same anchored-expiry shape,
 * same load/save/prune lifecycle, same per-account file.
 */
@Singleton
public class SkipList
{
	/**
	 * How long an abandoned buy keeps its item off the list.
	 * <p>
	 * Twice the four-hour buy-limit window, deliberately. A shorter cooldown would let the item come
	 * back the moment its limit reset, which is the exact circumstance in which the planner finds it
	 * attractive again — and the player has already said no.
	 */
	public static final Duration ABANDONED_BUY_COOLDOWN = Duration.ofHours(8);

	private static final Type ENTRY_LIST = new TypeToken<List<TimedSkip>>()
	{
	}.getType();
	private static final String FILE_NAME = "skipped.json";

	private final PluginStorage storage;
	/** Item id to the moment the skip lapses. */
	private final Map<Integer, Long> timed = new ConcurrentHashMap<>();
	/** Skipped from the panel: gone when the plugin stops. */
	private final Set<Integer> session = ConcurrentHashMap.newKeySet();

	private Path loadedFrom;

	@Inject
	public SkipList(PluginStorage storage)
	{
		this.storage = storage;
	}

	/** Loads the timed skips for whichever account is now logged in. */
	public synchronized void load()
	{
		Path file = storage.accountDir().resolve(FILE_NAME);
		if (file.equals(loadedFrom))
		{
			return;
		}

		timed.clear();
		Instant now = Instant.now();
		for (TimedSkip entry : storage.<List<TimedSkip>>readJson(file, ENTRY_LIST, new ArrayList<>()))
		{
			if (entry != null && !entry.hasLapsed(now))
			{
				timed.put(entry.itemId, entry.until);
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
		List<TimedSkip> entries = new ArrayList<>();
		for (Map.Entry<Integer, Long> entry : timed.entrySet())
		{
			entries.add(new TimedSkip(entry.getKey(), entry.getValue()));
		}
		Path file = storage.accountDir().resolve(FILE_NAME);
		storage.writeJson(file, entries, ENTRY_LIST);
		loadedFrom = file;
	}

	/**
	 * Whether cancelling a buy was a decision about the item, rather than a correction.
	 * <p>
	 * Something bought and the rest given up on is unambiguous: the player took what they wanted and
	 * walked away. Nothing bought is not, because cancelling in the first few minutes is how a
	 * mispriced or misplaced offer gets fixed, and taking the item away for eight hours over a slip
	 * would be a heavy price for a slip. Past the point where the plugin would itself call the offer
	 * stale, standing unfilled and then being pulled reads as a rejection.
	 *
	 * @param filled            how much had been bought when it was cancelled
	 * @param ordered           how much had been asked for
	 * @param minutesOpen       how long the offer had been standing
	 * @param staleAfterMinutes when an unfilled offer is old enough to be worth acting on
	 */
	public static boolean isRejection(int filled, int ordered, long minutesOpen,
		long staleAfterMinutes)
	{
		if (filled > 0)
		{
			// A cancel after the order completed is just how a finished offer is cleared, not a view.
			return filled < ordered;
		}
		return minutesOpen >= staleAfterMinutes;
	}

	/** The player passed on this one for now. Forgotten when the plugin stops. */
	public void skipForSession(int itemId)
	{
		session.add(itemId);
	}

	/**
	 * The player abandoned a buy on this item, so leave it alone for a while.
	 *
	 * @param now when the buy was abandoned
	 */
	public void skipUntilCooldownEnds(int itemId, Instant now)
	{
		timed.put(itemId, now.plus(ABANDONED_BUY_COOLDOWN).getEpochSecond());
	}

	/** Everything currently off the list, whichever kind. */
	public Set<Integer> skipped(Instant now)
	{
		prune(now);
		Set<Integer> all = new HashSet<>(session);
		all.addAll(timed.keySet());
		return all;
	}

	/** When this item's cooldown lapses, or null when it is not on a timed skip. */
	public Instant skippedUntil(int itemId)
	{
		Long until = timed.get(itemId);
		return until == null ? null : Instant.ofEpochSecond(until);
	}

	/**
	 * Drops the session skips only.
	 * <p>
	 * Changing the risk level or the blocklist reconsiders every trade, so a "show me something else"
	 * from the old settings no longer means anything. An abandoned buy is not a view about the
	 * settings, so it stays.
	 */
	public void clearSessionSkips()
	{
		session.clear();
	}

	public synchronized void prune(Instant now)
	{
		Iterator<Map.Entry<Integer, Long>> it = timed.entrySet().iterator();
		while (it.hasNext())
		{
			if (Instant.ofEpochSecond(it.next().getValue()).isBefore(now))
			{
				it.remove();
			}
		}
	}

	/** Visible for testing: drops all state without touching disk. */
	synchronized void clear()
	{
		timed.clear();
		session.clear();
		loadedFrom = null;
	}

	private static class TimedSkip
	{
		private int itemId;
		private long until;

		TimedSkip()
		{
		}

		TimedSkip(int itemId, long until)
		{
			this.itemId = itemId;
			this.until = until;
		}

		boolean hasLapsed(Instant now)
		{
			return Instant.ofEpochSecond(until).isBefore(now);
		}
	}
}
