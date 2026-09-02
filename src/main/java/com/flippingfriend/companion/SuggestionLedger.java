package com.flippingfriend.companion;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Singleton;

/**
 * Remembers what the plugin last advised, so an offer the player then places can be attributed to
 * the advice that prompted it.
 * <p>
 * This is the join that makes the event log worth keeping. The companion can already see that an
 * offer was placed and later filled; what it cannot see, without this, is whether that offer was the
 * one it recommended — and therefore whether its own fill estimates, prices and sizes were any good.
 * Every calibration question downstream reduces to comparing a recommendation with its outcome.
 * <p>
 * Attribution is deliberately loose on price and quantity but strict on item and side. Players round
 * prices, buy half as many as suggested, or take the advice ten minutes late, and none of that means
 * the trade was unprompted — it means the advice was followed imperfectly, which is itself worth
 * recording. What is <em>not</em> assumed is that a matching offer proves the player was following
 * advice at all; a stale recommendation is dropped rather than credited with a coincidence, which is
 * what {@link #ATTRIBUTION_WINDOW_SECONDS} is for.
 */
@Singleton
public class SuggestionLedger
{
	/**
	 * How long advice stays attributable. Long enough to cover a player reading the panel, walking
	 * to a booth and typing an offer; short enough that an unrelated trade in the same item an hour
	 * later is not credited to it.
	 */
	static final long ATTRIBUTION_WINDOW_SECONDS = 600;
	/** Bounded so a long session cannot grow this without limit. */
	private static final int MAX_ENTRIES = 64;

	private final Map<Long, Advice> outstanding =
		new LinkedHashMap<Long, Advice>(32, 0.75f, false)
		{
			@Override
			protected boolean removeEldestEntry(Map.Entry<Long, Advice> eldest)
			{
				return size() > MAX_ENTRIES;
			}
		};

	private long sequence;



	public synchronized void recorded(String recommendationId, int itemId, boolean buying, int price,
		int quantity, long quoteAgeSeconds, double predictedMinutes)
	{
		if (recommendationId == null || recommendationId.isEmpty() || itemId <= 0)
		{
			return;
		}
		long key = key(itemId, buying);
		Advice previous = outstanding.get(key);
		long now = Instant.now().getEpochSecond();

		// The clock starts when the advice appears, not when it was last repeated. The panel
		// re-records the same recommendation every refresh -- every thirty seconds by default -- and
		// stamping it afresh each time meant the age was permanently near zero, so the attribution
		// window below could never elapse for anything still on screen. A trade made an hour after
		// seeing the advice was credited to it, and a trade made unprompted on an item that happened
		// to be top-ranked was credited too. Both then fed the calibrator as if the plan had called
		// them, which is marking work nobody did.
		// Sameness is the trade, not the id it arrived under. The id is the plan's correlation id,
		// which the plugin generates fresh with UUID.randomUUID() on every account-state POST -- so
		// comparing on it was never true even for advice that had not changed by a single gp, and the
		// timestamp went on being restamped every cycle exactly as before. The window still could not
		// elapse; the check only looked like it was doing something.
		long since = previous != null && previous.isSameTradeAs(price, quantity)
			? previous.suggestedAt
			: now;
		outstanding.put(key, new Advice(recommendationId, price, quantity,
			quoteAgeSeconds, predictedMinutes, since));
	}

	/** The advice this offer most plausibly acts on, or null when the player acted unprompted. */
	public synchronized Advice attribute(int itemId, boolean buying, long observedAt)
	{
		Advice advice = outstanding.get(key(itemId, buying));
		if (advice == null)
		{
			return null;
		}
		if (observedAt - advice.suggestedAt > ATTRIBUTION_WINDOW_SECONDS)
		{
			outstanding.remove(key(itemId, buying));
			return null;
		}
		return advice;
	}

	/** Monotonic ordering for outgoing events, since local IPC does not guarantee arrival order. */
	public synchronized long nextSequence()
	{
		return ++sequence;
	}

	public synchronized void clear()
	{
		outstanding.clear();
	}

	private static long key(int itemId, boolean buying)
	{
		return ((long) itemId << 1) | (buying ? 1 : 0);
	}

	/** One recommendation the panel showed. */
	public static final class Advice
	{
		private final String recommendationId;
		private final int price;
		private final int quantity;
		private final long quoteAgeSeconds;
		private final double predictedMinutes;
		private final long suggestedAt;
		Advice(String recommendationId, int price, int quantity, long quoteAgeSeconds,
			double predictedMinutes, long suggestedAt)
		{
			this.recommendationId = recommendationId;
			this.price = price;
			this.quantity = quantity;
			this.quoteAgeSeconds = quoteAgeSeconds;
			this.predictedMinutes = predictedMinutes;
			this.suggestedAt = suggestedAt;
		}

		/** Whether this is the same recommendation repeated, rather than a genuinely new one. */
		boolean isSameTradeAs(int otherPrice, int otherQuantity)
		{
			return price == otherPrice && quantity == otherQuantity;
		}

		public String getRecommendationId() { return recommendationId; }
		public int getPrice() { return price; }
		public int getQuantity() { return quantity; }
		public long getQuoteAgeSeconds() { return quoteAgeSeconds; }
		public double getPredictedMinutes() { return predictedMinutes; }
		public long getSuggestedAt() { return suggestedAt; }
	}
}
