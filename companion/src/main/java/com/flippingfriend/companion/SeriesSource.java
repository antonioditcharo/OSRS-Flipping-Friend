package com.flippingfriend.companion;

import com.flippingfriend.data.Candle;
import java.util.List;

/**
 * Where per-item price history comes from.
 * <p>
 * This exists so that replay and live planning run the <em>same</em> decision code rather than two
 * implementations that are meant to agree. A backtest built against a parallel copy of the strategy
 * measures the copy, and the two drift apart silently — the copy keeps passing while the code that
 * actually trades changes underneath it. Putting the seam at the data source instead means the
 * simulator can only differ from production in where the bars came from and what time it thinks
 * it is.
 */
interface SeriesSource
{
	/**
	 * History for one item at the given resolution, oldest first, or empty when unavailable.
	 * Implementations used for replay must return only bars at or before the simulated instant.
	 */
	List<Candle> series(int itemId, String timestep);
}
