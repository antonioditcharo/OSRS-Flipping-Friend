package com.flippingfriend.model;

import com.flippingfriend.data.Candle;
import java.util.Arrays;
import java.util.List;

/**
 * A precomputed view of one item's history, arranged so that "how much trades at or beyond this
 * price?" is a binary search instead of a scan.
 * <p>
 * The scorer evaluates a grid of roughly fifty price combinations per item, and each one previously
 * walked the whole candle series twice. Since the answer only depends on how many buckets sit either
 * side of a price, sorting the prices once and keeping a running volume total answers every query in
 * logarithmic time. For a thirty-item shortlist that turns hundreds of thousands of inner iterations
 * into a few thousand, which is the difference between a scoring pass the player notices and one
 * they do not.
 * <p>
 * Immutable, so a curve built on the background thread can be reused freely.
 */
public final class FillCurve
{
	// The prefix arrays are always one longer than the price arrays, so that a prefix sum of "the
	// first zero elements" is addressable. The empty curve has to honour that invariant too, or any
	// query against an item with no history walks off the end of a zero-length array.
	private static final FillCurve EMPTY = new FillCurve(new int[0], new long[1], new int[0], new long[1], 0);

	/** Instant-sell prices, ascending, with volumes accumulated in the same order. */
	private final int[] lowPrices;
	private final long[] lowVolumePrefix;
	/** Instant-buy prices, ascending, with volumes accumulated in the same order. */
	private final int[] highPrices;
	private final long[] highVolumePrefix;
	private final long windowSeconds;
	/**
	 * Half the typical bid-ask spread, used as the within-bucket price dispersion.
	 * <p>
	 * A bucket's recorded price is the volume-weighted <em>mean</em> of everything that traded on
	 * that side during those five minutes, not a price every one of those trades achieved. Treating
	 * it as an all-or-nothing threshold throws away a whole bucket because its average landed a
	 * gp the wrong side of our quote, including the trades inside it that did happen at our price.
	 * Measured against a share-weighted count that error ran to 2.1x on Dragon claws and 1.9x on
	 * grimy irit leaf, and it grows as items get sparser -- which is to say it is worst on exactly
	 * the expensive items that can absorb real capital.
	 * <p>
	 * The spread is the right scale for it: within one bucket, instant-sells are scattered across
	 * roughly the distance between the bid and the ask.
	 */
	private final int dispersion;

	private FillCurve(int[] lowPrices, long[] lowVolumePrefix, int[] highPrices, long[] highVolumePrefix,
		long windowSeconds)
	{
		this.lowPrices = lowPrices;
		this.lowVolumePrefix = lowVolumePrefix;
		this.highPrices = highPrices;
		this.highVolumePrefix = highVolumePrefix;
		this.windowSeconds = windowSeconds;
		this.dispersion = spreadHalfWidth(lowPrices, highPrices);
	}

	/**
	 * Half the gap between the typical instant-buy and typical instant-sell price. Medians rather
	 * than means, because a handful of outlier buckets should not set the scale for every query.
	 */
	private static int spreadHalfWidth(int[] lowPrices, int[] highPrices)
	{
		if (lowPrices.length == 0 || highPrices.length == 0)
		{
			return 0;
		}
		int medianLow = lowPrices[lowPrices.length / 2];
		int medianHigh = highPrices[highPrices.length / 2];
		return Math.max(0, (medianHigh - medianLow) / 2);
	}

	public static FillCurve empty()
	{
		return EMPTY;
	}

	/**
	 * How far back {@link #overRecentHistory} looks.
	 * <p>
	 * <b>This histogram is asked a relative question and was being answered with an absolute one.</b>
	 * What the model wants to know is "if I quote at the bid, how much flow comes to me" -- a
	 * question about where a price sits in the book. What it was given was every bar in the cache,
	 * thirty hours of them, and asked how much volume had traded at or below one absolute number of
	 * gold pieces. Over thirty hours prices move.
	 * <p>
	 * So any item that had drifted had one of its two legs scored as very nearly untradeable, and it
	 * did not matter which way it drifted: whichever way the price went, the quote on one side of the
	 * book fell outside almost all of the history on that side. Measured on a live board, Dragonstone
	 * dragon bolts -- thirteen thousand units an hour, both sides busy -- had drifted eight per cent,
	 * which left 5.6% of the recorded instant-sell volume at or below the current bid, and the model
	 * believed the buy leg could take <em>fifteen units an hour</em>. Cooked moonlight antelope,
	 * Bracelet of ethereum and Unicorn horn dust showed the same shape, one leg a hundred times the
	 * other.
	 * <p>
	 * Both ends of the strategy compound it: order size is the smaller of the two legs, and the score
	 * multiplies the two fill probabilities, so the drifted leg sets the size and then halves the
	 * value. On a 128m account the plan that came out was seven trades of <em>one unit each</em>.
	 * <p>
	 * Four hours is not a compromise between evidence and freshness; it is the window the question is
	 * about. The plan asks what will fill within its horizon, and the longest horizon this system
	 * plans over is four hours, so the flow of the last four hours is the evidence that bears on it.
	 * Swept across a live board at 30, 12, 6, 4 and 2 hours, the binding side of every liquid item
	 * rose as the window shortened and peaked here -- ten-fold on Dragonstone dragon bolts and
	 * Bracelet of ethereum, seven-fold on Cooked moonlight antelope -- before two hours began costing
	 * more in evidence than it bought in freshness. On the same captured board it moved the best
	 * eight trades available from 68,995 to 84,965 gp per slot-hour while asking for nineteen per
	 * cent less capital.
	 * <p>
	 * Rescaling old bars into today's money was tried first and is worse: it flattens the histogram
	 * onto a single level, so the natural width of quotes around the mid disappears and the query
	 * falls off one end. It moved the degeneracy rather than removing it.
	 */
	private static final long RECENT_WINDOW_SECONDS = 4 * 3600;

	/**
	 * Never fewer bars than this, however sparse the history.
	 * <p>
	 * Guards two cases at once: a series recorded at a coarser resolution than five minutes, where
	 * four hours is a handful of bars, and an item so quiet that four hours of it is no evidence at
	 * all. Both are better served by more history than by a window they cannot fill.
	 */
	private static final int MIN_BARS = 24;

	/**
	 * A curve over the history that bears on the next few hours, rather than over everything cached.
	 * <p>
	 * Separate from {@link #from} on purpose. That one is an exact, faster restatement of scanning
	 * the series it is given -- a property {@code FillCurveTest} pins by comparing the two -- and
	 * quietly handing it different data would make the comparison a lie while leaving it green.
	 * Which evidence bears on the question is a modelling decision, so it is made here, by name,
	 * where a caller can see it. See {@link #RECENT_WINDOW_SECONDS} for why the answer is four hours.
	 */
	public static FillCurve overRecentHistory(List<Candle> series)
	{
		if (series == null || series.size() < 2)
		{
			return from(series);
		}

		long last = series.get(series.size() - 1).getTimestamp();
		long earliest = last - RECENT_WINDOW_SECONDS;
		int start = series.size();
		while (start > 0 && series.get(start - 1).getTimestamp() >= earliest)
		{
			start--;
		}
		start = Math.min(start, Math.max(0, series.size() - MIN_BARS));
		return from(start <= 0 ? series : series.subList(start, series.size()));
	}

	/*
	 * Restating the window at today's price level was tried twice and is worse both times. Recorded
	 * here because it is the obvious next idea and it does not work.
	 *
	 * The residual after windowing is a lopsidedness between the legs -- measured at the quoted bid
	 * and ask, Bracelet of ethereum ran 4,965 units an hour on the buy leg against 152 on the sell,
	 * Avantoe potion (unf) 4,536 against 213 -- and since order size is the smaller leg, that is what
	 * sets it. Scaling every bar so its own mid lands on today's looks like the cure.
	 *
	 * It is not, and the reason is that the spread of bar prices is doing real work. A bar's own high
	 * and low sit a half-spread either side of its mid, so once every mid is moved to the same place
	 * the histogram is only as wide as one spread. The query then lands at an end of it rather than
	 * inside it, and the answer saturates at nothing or everything.
	 *
	 * Both attempts show it. Scaling onto the last bar's mid took Dragonstone dragon bolts from
	 * 15/343 units an hour to 328/1 -- the lopsidedness moved, it did not go. Scaling onto the live
	 * mid, which also fixes the source mismatch between a tick and a run of five-minute means, was
	 * measured end to end on a captured board: the best eight trades fell from 141,788 to 48,258 gp
	 * per slot-hour, expected profit from 325,210 to 145,609, and the median order from 3.7% of the
	 * buy limit to 1.4%.
	 *
	 * So the drift inside the window is not noise sitting on top of the measurement. It is most of
	 * the width the measurement has. Shortening the window until the drift is small enough to live
	 * with is the lever that works; removing the drift removes the estimate with it.
	 */

	public static FillCurve from(List<Candle> series)
	{
		if (series == null || series.size() < 2)
		{
			return EMPTY;
		}

		int size = series.size();
		long[] lows = new long[size];
		long[] highs = new long[size];
		int lowCount = 0;
		int highCount = 0;

		// Price and volume are packed into one long so a single primitive sort keeps them together;
		// sorting an array of objects here would allocate on a hot path for no benefit.
		for (Candle candle : series)
		{
			Integer low = candle.getAvgLowPrice();
			if (low != null && low > 0)
			{
				lows[lowCount++] = pack(low, candle.getLowPriceVolume());
			}
			Integer high = candle.getAvgHighPrice();
			if (high != null && high > 0)
			{
				highs[highCount++] = pack(high, candle.getHighPriceVolume());
			}
		}

		long first = series.get(0).getTimestamp();
		long last = series.get(size - 1).getTimestamp();
		long window = Math.max(0, last - first);

		int[] lowPrices = new int[lowCount];
		long[] lowPrefix = new long[lowCount + 1];
		unpackSorted(lows, lowCount, lowPrices, lowPrefix);

		int[] highPrices = new int[highCount];
		long[] highPrefix = new long[highCount + 1];
		unpackSorted(highs, highCount, highPrices, highPrefix);

		return new FillCurve(lowPrices, lowPrefix, highPrices, highPrefix, window);
	}

	private static long pack(int price, int volume)
	{
		// Price in the high bits so the natural long ordering sorts by price.
		return ((long) price << 32) | (volume & 0xFFFFFFFFL);
	}

	private static void unpackSorted(long[] packed, int count, int[] prices, long[] volumePrefix)
	{
		Arrays.sort(packed, 0, count);
		for (int i = 0; i < count; i++)
		{
			prices[i] = (int) (packed[i] >>> 32);
			volumePrefix[i + 1] = volumePrefix[i] + (packed[i] & 0xFFFFFFFFL);
		}
	}

	public boolean isEmpty()
	{
		return lowPrices.length == 0 && highPrices.length == 0;
	}

	public long getWindowSeconds()
	{
		return windowSeconds;
	}

	/** Buckets that traded down to our buy price, out of those that traded on that side at all. */
	public int buyReachableBuckets(int price)
	{
		return upperBound(lowPrices, price);
	}

	public long buyVolumeAtOrBelow(int price)
	{
		if (dispersion <= 0)
		{
			return lowVolumePrefix[upperBound(lowPrices, price)];
		}
		// Buckets well below our price count in full and stay a single prefix lookup; only the few
		// straddling it need their share worked out, so the fast path survives.
		int settled = upperBound(lowPrices, price - dispersion);
		int beyond = upperBound(lowPrices, price + dispersion);
		long total = lowVolumePrefix[settled];
		for (int i = settled; i < beyond; i++)
		{
			long volume = lowVolumePrefix[i + 1] - lowVolumePrefix[i];
			total += (long) (volume * shareAtOrBelow(price, lowPrices[i]));
		}
		return total;
	}

	/** The within-bucket price dispersion this curve is using. Exposed so tests can verify it. */
	int dispersion()
	{
		return dispersion;
	}

	/** Fraction of a bucket centred on {@code mean} that traded at or below {@code price}. */
	private double shareAtOrBelow(int price, int mean)
	{
		double lowest = mean - dispersion;
		return clampShare((price - lowest) / (2.0 * dispersion));
	}

	/** Fraction of a bucket centred on {@code mean} that traded at or above {@code price}. */
	private double shareAtOrAbove(int price, int mean)
	{
		double highest = mean + dispersion;
		return clampShare((highest - price) / (2.0 * dispersion));
	}

	private static double clampShare(double share)
	{
		return share < 0 ? 0 : share > 1 ? 1 : share;
	}

	public int buyScoredBuckets()
	{
		return lowPrices.length;
	}

	/** Buckets that traded up to our sell price. */
	public int sellReachableBuckets(int price)
	{
		return highPrices.length - lowerBound(highPrices, price);
	}

	public long sellVolumeAtOrAbove(int price)
	{
		long everything = highVolumePrefix[highPrices.length];
		if (dispersion <= 0)
		{
			return everything - highVolumePrefix[lowerBound(highPrices, price)];
		}
		int settled = lowerBound(highPrices, price + dispersion);
		int below = lowerBound(highPrices, price - dispersion);
		long total = everything - highVolumePrefix[settled];
		for (int i = below; i < settled; i++)
		{
			long volume = highVolumePrefix[i + 1] - highVolumePrefix[i];
			total += (long) (volume * shareAtOrAbove(price, highPrices[i]));
		}
		return total;
	}

	public int sellScoredBuckets()
	{
		return highPrices.length;
	}

	/** Index of the first element strictly greater than {@code value}. */
	private static int upperBound(int[] sorted, int value)
	{
		int low = 0;
		int high = sorted.length;
		while (low < high)
		{
			int mid = (low + high) >>> 1;
			if (sorted[mid] <= value)
			{
				low = mid + 1;
			}
			else
			{
				high = mid;
			}
		}
		return low;
	}

	/** Index of the first element greater than or equal to {@code value}. */
	private static int lowerBound(int[] sorted, int value)
	{
		int low = 0;
		int high = sorted.length;
		while (low < high)
		{
			int mid = (low + high) >>> 1;
			if (sorted[mid] < value)
			{
				low = mid + 1;
			}
			else
			{
				high = mid;
			}
		}
		return low;
	}
}
