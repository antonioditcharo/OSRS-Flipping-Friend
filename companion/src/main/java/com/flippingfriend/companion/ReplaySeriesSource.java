package com.flippingfriend.companion;

import com.flippingfriend.data.Candle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link SeriesSource} backed by stored history, which reveals only the bars a given moment could
 * have seen.
 *
 * <p>Recovered on 2 September 2026 from {@code flipping-friend-trainer.jar} and verified by bytecode
 * diff against the original. The javadoc is reconstructed; the code is exact.
 *
 * <p>The whole value of the class is in the split between {@link #series} and {@link #future}.
 * {@code series} is the {@link SeriesSource} the engine sees, and it binary-searches for the cutoff
 * set by {@link #seekTo}, so a replayed decision physically cannot read a bar from its own future —
 * lookahead is prevented by construction rather than by remembering to slice the input. {@code future}
 * is the deliberately-named other half, for the harness to resolve what actually happened afterwards.
 * A backtest that mixes those two up reports a strategy that cannot exist, which is the failure mode
 * this design removes.
 *
 * <p>Not thread-safe for loading, and does not need to be: history is loaded once, then
 * {@link #seekTo} advances a volatile cutoff as the replay walks forward.
 */
final class ReplaySeriesSource implements SeriesSource
{
	private final Map<String, List<Candle>> history = new HashMap<>();

	/** Volatile so the planner thread sees the advance the replay loop makes. */
	private volatile long cutoff;

	ReplaySeriesSource()
	{
	}

	/** Sorted on the way in so every later read is a binary search rather than a scan. */
	void load(int itemId, String timestep, List<Candle> bars)
	{
		List<Candle> sorted = new ArrayList<>(bars);
		sorted.sort((left, right) -> Long.compare(left.getTimestamp(), right.getTimestamp()));
		history.put(key(itemId, timestep), Collections.unmodifiableList(sorted));
	}

	/** Moves the visible horizon. Everything at or before {@code epochSeconds} becomes readable. */
	void seekTo(long epochSeconds)
	{
		cutoff = epochSeconds;
	}

	/**
	 * The engine-facing view: every bar at or before the cutoff, and nothing after it. Returns an
	 * empty list rather than a partial one when the cutoff predates all history, so an item with no
	 * usable past is indistinguishable from an item with no history — which is what the live engine
	 * sees too.
	 */
	@Override
	public List<Candle> series(int itemId, String timestep)
	{
		List<Candle> full = history.get(key(itemId, timestep));
		if (full == null || full.isEmpty())
		{
			return Collections.emptyList();
		}
		int low = 0;
		int high = full.size();
		while (low < high)
		{
			int mid = low + high >>> 1;
			if (full.get(mid).getTimestamp() <= cutoff)
			{
				low = mid + 1;
			}
			else
			{
				high = mid;
			}
		}
		return low <= 0 ? Collections.emptyList() : full.subList(0, low);
	}

	/** The bar at an exact timestamp, for pricing a simulated fill. Harness-only. */
	Candle barAt(int itemId, String timestep, long epochSeconds)
	{
		List<Candle> full = history.get(key(itemId, timestep));
		if (full == null)
		{
			return null;
		}
		for (Candle candle : full)
		{
			if (candle.getTimestamp() == epochSeconds)
			{
				return candle;
			}
		}
		return null;
	}

	/**
	 * Bars strictly after {@code after}, for resolving an outcome once it is known. Harness-only, and
	 * never reachable through {@link SeriesSource} — the interface the engine holds has no such method.
	 */
	List<Candle> future(int itemId, String timestep, long after, int limit)
	{
		List<Candle> full = history.get(key(itemId, timestep));
		if (full == null)
		{
			return Collections.emptyList();
		}
		List<Candle> ahead = new ArrayList<>();
		for (Candle candle : full)
		{
			if (candle.getTimestamp() > after)
			{
				ahead.add(candle);
				if (ahead.size() >= limit)
				{
					break;
				}
			}
		}
		return ahead;
	}

	List<Long> timestamps(int itemId, String timestep)
	{
		List<Candle> full = history.get(key(itemId, timestep));
		if (full == null)
		{
			return Collections.emptyList();
		}
		List<Long> stamps = new ArrayList<>(full.size());
		for (Candle candle : full)
		{
			stamps.add(candle.getTimestamp());
		}
		return stamps;
	}

	/**
	 * The earliest timestamp at or after {@code from} across every item at this timestep, so a replay
	 * can start at a bar that actually exists rather than at a clock time nothing was recorded at.
	 */
	long firstBarAtOrAfter(String timestep, long from)
	{
		long best = Long.MAX_VALUE;
		String suffix = "@" + timestep;
		for (Map.Entry<String, List<Candle>> entry : history.entrySet())
		{
			if (!entry.getKey().endsWith(suffix))
			{
				continue;
			}
			for (Candle candle : entry.getValue())
			{
				if (candle.getTimestamp() >= from)
				{
					// Bars are sorted, so the first at or after `from` is this item's best.
					best = Math.min(best, candle.getTimestamp());
					break;
				}
			}
		}
		return best == Long.MAX_VALUE ? from : best;
	}

	boolean has(int itemId, String timestep)
	{
		List<Candle> full = history.get(key(itemId, timestep));
		return full != null && !full.isEmpty();
	}

	private static String key(int itemId, String timestep)
	{
		return itemId + "@" + timestep;
	}
}
