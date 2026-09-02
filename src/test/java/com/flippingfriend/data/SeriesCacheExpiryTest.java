package com.flippingfriend.data;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * How long a failed history fetch is allowed to stand in for an answer.
 * <p>
 * A failure was stored under the normal time-to-live, so one rejected request blanked an item's hourly
 * history for two hours — and every surface reads an empty history as a warm-up still in progress
 * rather than as a problem. The companion's own cache has drawn this distinction since it was fixed
 * there; the plugin's copy never did.
 */
public class SeriesCacheExpiryTest
{
	private static final List<Candle> BARS = Arrays.asList(
		new Candle(1_700_000_000L, 110, 100, 50, 50));

	private static MarketDataService.CachedSeries at(Instant when, boolean failed, List<Candle> bars)
	{
		return new MarketDataService.CachedSeries("1h", bars, when, false, failed);
	}

	@Test
	public void aFailedFetchIsRetriedWithinMinutes()
	{
		Instant fiveMinutesAgo = Instant.now().minusSeconds(300);

		assertTrue("a failure must not stand for two hours",
			at(fiveMinutesAgo, true, Collections.emptyList()).isExpired());
		assertFalse("a real answer of the same age is still good",
			at(fiveMinutesAgo, false, BARS).isExpired());
	}

	@Test
	public void aFailureKeepsWhateverWasAlreadyKnown()
	{
		// Replacing good candles with an empty list on a transient failure destroys data the plugin
		// already paid to fetch, and an empty list is indistinguishable from an item that genuinely
		// has no history.
		MarketDataService.CachedSeries previous = at(Instant.now(), false, BARS);
		MarketDataService.CachedSeries failed =
			MarketDataService.CachedSeries.failed("1h", previous);

		assertEquals("the bars already held must survive a failed refresh", 1, failed.candles().size());
	}
}
