package com.flippingfriend.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns every piece of market data the plugin holds, and all the network traffic that produces it.
 * <p>
 * Nothing here ever runs on the RuneLite client thread. Consumers read {@link #getSnapshot()},
 * which is swapped atomically, so the game loop never blocks on a request and never observes a
 * half-updated market.
 * <p>
 * Screening works as a funnel to keep request volume tiny: the three all-item endpoints are enough
 * to rank several thousand items, and only the handful that survive that first pass ever cost a
 * per-item {@code /timeseries} call.
 */
@Singleton
public class MarketDataService
{
	private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

	private static final Duration MAPPING_TTL = Duration.ofHours(24);
	private static final Duration LATEST_INTERVAL = Duration.ofSeconds(30);
	private static final Duration FIVE_MIN_INTERVAL = Duration.ofMinutes(5);
	private static final Duration HOURLY_INTERVAL = Duration.ofMinutes(30);
	/**
	 * How long each timestep's history stays fresh. A five-minute series is stale within the hour;
	 * a fortnight of hourly buckets barely moves, so refetching it often would waste requests on the
	 * wiki for no new information.
	 */
	private static final Duration SERIES_TTL = Duration.ofMinutes(15);
	private static final Duration LONG_SERIES_TTL = Duration.ofHours(2);
	/**
	 * How long a failed fetch is remembered before trying again.
	 * <p>
	 * Short, because a failure is not an answer. Storing one under the normal time-to-live meant a
	 * single rejected request blanked an item's hourly history for two hours, and every surface then
	 * read that as a warm-up still in progress. The companion's own cache already draws this
	 * distinction; the plugin's copy never did.
	 */
	private static final Duration FAILURE_TTL = Duration.ofMinutes(2);
	private static final int SERIES_CACHE_SIZE = 256;

	private static final Type MAPPING_LIST = new TypeToken<List<ItemMetadata>>()
	{
	}.getType();

	private final WikiPriceClient client;
	private final Gson gson;
	private final Path cacheDir;

	private final AtomicReference<MarketSnapshot> snapshot = new AtomicReference<>(MarketSnapshot.empty());
	private final AtomicBoolean running = new AtomicBoolean();

	/**
	 * Held while a map is being replaced and while a snapshot is being taken from them.
	 * <p>
	 * The maps are concurrent, so nothing here can corrupt them — but replacing one is a clear
	 * followed by a putAll, and that pair is not atomic. On a single scheduler thread it did not
	 * matter, because whoever was writing was also the one publishing. Now that the polls run in
	 * parallel, a snapshot taken during another poll's clear would carry an empty price map, which
	 * reads downstream as "market data not usable yet" and flashes the loading card back onto the
	 * screen for no reason. Three locks a minute, held for microseconds.
	 */
	private final Object publishLock = new Object();

	private final Map<Integer, ItemMetadata> metadata = new ConcurrentHashMap<>();
	private final Map<Integer, LatestPrice> latest = new ConcurrentHashMap<>();
	private final Map<Integer, Candle> fiveMinute = new ConcurrentHashMap<>();
	private final Map<Integer, Candle> hourly = new ConcurrentHashMap<>();

	/**
	 * Access-ordered so eviction drops whatever the engine has stopped looking at. Keyed by item
	 * <em>and</em> timestep: the engine asks for both a five-minute and an hourly series per item,
	 * and a key of item alone would make each request evict the other and refetch forever.
	 */
	private final Map<String, CachedSeries> seriesCache =
		Collections.synchronizedMap(new LinkedHashMap<String, CachedSeries>(64, 0.75f, true)
		{
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, CachedSeries> eldest)
			{
				return size() > SERIES_CACHE_SIZE;
			}
		});

	private static String cacheKey(int itemId, String timestep)
	{
		return itemId + "@" + timestep;
	}

	private ScheduledExecutorService scheduler;
	private volatile Runnable updateListener;

	/**
	 * The local companion, when there is one. Never required: everything here works without it, just
	 * slower, which is exactly how it worked before.
	 */
	private volatile com.flippingfriend.companion.CompanionClient companion;

	@Inject
	public MarketDataService(WikiPriceClient client, Gson gson, PluginStorage storage)
	{
		this.client = client;
		this.gson = gson;
		this.cacheDir = storage.sharedDir();
	}

	/**
	 * Lets this service start from the companion's warm feed instead of the internet.
	 * <p>
	 * Set rather than injected, because the dependency only runs one way for construction: the
	 * companion client is a plain object with no need of market data, and wiring it in the
	 * constructor would tie the two together for a relationship that is entirely optional.
	 */
	public void setCompanion(com.flippingfriend.companion.CompanionClient companion)
	{
		this.companion = companion;
	}

	public MarketSnapshot getSnapshot()
	{
		return snapshot.get();
	}

	/** Called after every successful poll so the UI can refresh without polling us in turn. */
	public void setUpdateListener(Runnable listener)
	{
		this.updateListener = listener;
	}

	public void start()
	{
		if (!running.compareAndSet(false, true))
		{
			return;
		}

		ThreadFactory threads = r ->
		{
			Thread t = new Thread(r, "flipping-friend-market");
			t.setDaemon(true);
			return t;
		};
		// Three threads, not one.
		//
		// On one, everything queued behind whatever ran first: the four-thousand-item mapping
		// download, then the price poll that was scheduled for delay zero and could not start, then
		// every per-item history request -- sixty of them, one at a time. The plugin was refusing to
		// advise for the whole of that, and describing it as "usually takes a few seconds".
		scheduler = Executors.newScheduledThreadPool(3, threads);

		// Before anything is asked of the internet. The companion has been running the whole time and
		// is holding a feed no more than a minute old; taking it turns the "getting the latest
		// prices" wait into a loopback read. Costs nothing when the companion is not running.
		scheduler.execute(this::seedFromCompanion);
		scheduler.execute(this::refreshMetadata);
		scheduler.scheduleWithFixedDelay(this::refreshLatest, 0, LATEST_INTERVAL.getSeconds(), TimeUnit.SECONDS);
		scheduler.scheduleWithFixedDelay(this::refreshFiveMinute, 2, FIVE_MIN_INTERVAL.getSeconds(), TimeUnit.SECONDS);
		scheduler.scheduleWithFixedDelay(this::refreshHourly, 4, HOURLY_INTERVAL.getSeconds(), TimeUnit.SECONDS);
	}

	public void stop()
	{
		if (!running.compareAndSet(true, false))
		{
			return;
		}
		if (scheduler != null)
		{
			scheduler.shutdownNow();
			scheduler = null;
		}
		updateListener = null;
	}

	/**
	 * History for one item, fetched on demand and cached. Returns an empty list rather than blocking
	 * if nothing is cached yet; the caller is expected to skip the item this pass and pick it up on
	 * the next one, once the background fetch has landed.
	 */
	public List<Candle> getSeries(int itemId, String timestep)
	{
		CachedSeries cached = seriesCache.get(cacheKey(itemId, timestep));
		if (cached != null && !cached.isExpired())
		{
			return cached.candles;
		}

		requestSeries(itemId, timestep);
		return cached == null ? Collections.emptyList() : cached.candles;
	}

	/** Warms the series cache for a batch of items the engine is about to look at closely. */
	public void prefetchSeries(List<Integer> itemIds, String timestep)
	{
		for (int itemId : itemIds)
		{
			CachedSeries cached = seriesCache.get(cacheKey(itemId, timestep));
			if (cached == null || cached.isExpired())
			{
				requestSeries(itemId, timestep);
			}
		}
	}

	private void requestSeries(int itemId, String timestep)
	{
		ScheduledExecutorService exec = scheduler;
		if (exec == null || exec.isShutdown())
		{
			return;
		}

		// Reserve the slot immediately so a burst of scoring passes cannot queue the same item
		// dozens of times while the first request is still in flight.
		String key = cacheKey(itemId, timestep);
		CachedSeries placeholder = seriesCache.get(key);
		if (placeholder != null && placeholder.inFlight)
		{
			return;
		}
		seriesCache.put(key, CachedSeries.pending(timestep, placeholder));

		exec.execute(() ->
		{
			boolean settled = false;
			try
			{
				// The companion first. It keeps a persistent per-item cache and warms it continuously,
				// so this is usually a loopback read of something already fetched -- against a
				// throttled round trip to a volunteer-run API. Sixty of those, one at a time, is what
				// "checking the market" was actually waiting on.
				com.flippingfriend.companion.CompanionClient local = companion;
				List<Candle> candles = local == null ? null : local.fetchSeries(itemId, timestep);
				if (candles == null)
				{
					candles = client.fetchTimeseries(itemId, timestep);
				}
				seriesCache.put(key, CachedSeries.loaded(timestep, candles));
				settled = true;
			}
			catch (Exception ex)
			{
				// Not just IOException. A malformed body throws JsonSyntaxException, which is a
				// RuntimeException: it escaped this handler, killed the task, and left the entry marked
				// in flight. Since an in-flight entry never expires and the guard above returns early
				// for one, that item's history was dead for the rest of the session -- silently.
				log.debug("could not fetch history for item {}: {}", itemId, ex.getMessage());
				// Keep whatever was already loaded rather than replacing it with nothing: a transient
				// failure should not destroy good data, and an empty list is indistinguishable from an
				// item that genuinely has no history.
				seriesCache.put(key, CachedSeries.failed(timestep, placeholder));
				settled = true;
			}
			finally
			{
				if (!settled)
				{
					// An Error is not an Exception, and one escaping here would leave the slot reserved
					// for the rest of the session -- the same shape as the failure that stopped the
					// companion ingesting market data for two hours. Whatever happened, the reservation
					// is released.
					seriesCache.put(key, CachedSeries.failed(timestep, placeholder));
				}
			}
		});
	}

	private void refreshMetadata()
	{
		Path cacheFile = cacheDir.resolve("item-mapping.json");

		if (loadMappingFromDisk(cacheFile))
		{
			publish();
			return;
		}

		try
		{
			List<ItemMetadata> mapping = client.fetchMapping();
			applyMapping(mapping);
			writeMappingToDisk(cacheFile, mapping);
			publish();
			log.debug("loaded {} items from the wiki mapping", mapping.size());
		}
		catch (IOException ex)
		{
			log.warn("could not load item mapping: {}", ex.getMessage());
		}
	}

	private boolean loadMappingFromDisk(Path cacheFile)
	{
		try
		{
			if (!Files.isRegularFile(cacheFile))
			{
				return false;
			}
			Instant modified = Files.getLastModifiedTime(cacheFile).toInstant();
			if (modified.plus(MAPPING_TTL).isBefore(Instant.now()))
			{
				return false;
			}
			try (Reader reader = Files.newBufferedReader(cacheFile, StandardCharsets.UTF_8))
			{
				List<ItemMetadata> mapping = gson.fromJson(reader, MAPPING_LIST);
				if (mapping == null || mapping.isEmpty())
				{
					return false;
				}
				applyMapping(mapping);
				return true;
			}
		}
		catch (Exception ex)
		{
			log.debug("ignoring unreadable mapping cache: {}", ex.getMessage());
			return false;
		}
	}

	private void writeMappingToDisk(Path cacheFile, List<ItemMetadata> mapping)
	{
		try
		{
			Files.createDirectories(cacheFile.getParent());
			try (Writer writer = Files.newBufferedWriter(cacheFile, StandardCharsets.UTF_8))
			{
				gson.toJson(mapping, MAPPING_LIST, writer);
			}
		}
		catch (Exception ex)
		{
			log.debug("could not cache item mapping: {}", ex.getMessage());
		}
	}

	private void applyMapping(List<ItemMetadata> mapping)
	{
		synchronized (publishLock)
		{
			metadata.clear();
			for (ItemMetadata item : mapping)
			{
				metadata.put(item.getId(), item);
			}
		}
	}

	/**
	 * Fills the price maps from the companion, if it can.
	 * <p>
	 * The payloads are the wiki's own, unwrapped, so they are parsed with the same types the wiki
	 * responses are -- there is no second format to keep in step. Nothing is overwritten if a poll
	 * has already landed, and a failure of any kind is silent: this is a shortcut, and the long way
	 * round is still running.
	 */
	void seedFromCompanion()
	{
		com.flippingfriend.companion.CompanionClient local = companion;
		if (local == null)
		{
			return;
		}

		com.flippingfriend.companion.CompanionClient.MarketFeed feed = local.fetchMarketFeed();
		if (feed == null || feed.getLatest() == null)
		{
			return;
		}

		boolean seeded = false;
		try
		{
			Map<Integer, LatestPrice> prices = keyById(feed.getLatest(), LatestPrice.class);
			Map<Integer, Candle> five = keyById(feed.getFiveMinute(), Candle.class);
			Map<Integer, Candle> hour = keyById(feed.getHourly(), Candle.class);

			// Parsed outside the lock, filled inside it. A real poll may have landed while this was
			// being fetched and decoded, and a poll is fresher than a seed -- so "is it still empty"
			// has to be asked at the moment of filling, not before.
			synchronized (publishLock)
			{
				if (!prices.isEmpty() && latest.isEmpty())
				{
					latest.putAll(prices);
					seeded = true;
				}
				if (!five.isEmpty() && fiveMinute.isEmpty())
				{
					fiveMinute.putAll(five);
					seeded = true;
				}
				if (!hour.isEmpty() && hourly.isEmpty())
				{
					hourly.putAll(hour);
					seeded = true;
				}
			}
		}
		catch (Exception ex)
		{
			log.debug("could not read the companion's market feed: {}", ex.getMessage());
			return;
		}

		if (seeded)
		{
			log.debug("started from the companion's feed, observed {}s ago",
				Instant.now().getEpochSecond() - feed.getObservedAt());
			publish();
		}
	}

	/** The wiki keys its all-item payloads by item id as a string. */
	private <T> Map<Integer, T> keyById(com.google.gson.JsonObject payload, Class<T> type)
	{
		Map<Integer, T> byId = new java.util.HashMap<>();
		if (payload == null)
		{
			return byId;
		}
		for (Map.Entry<String, com.google.gson.JsonElement> entry : payload.entrySet())
		{
			try
			{
				byId.put(Integer.parseInt(entry.getKey()), gson.fromJson(entry.getValue(), type));
			}
			catch (RuntimeException ex)
			{
				// One malformed row must not cost the whole feed, which is the difference between a
				// warm start and a cold one.
			}
		}
		return byId;
	}

	private void refreshLatest()
	{
		try
		{
			Map<Integer, LatestPrice> prices = client.fetchLatest();
			if (!prices.isEmpty())
			{
				synchronized (publishLock)
				{
					latest.clear();
					latest.putAll(prices);
				}
				publish();
			}
		}
		catch (IOException ex)
		{
			log.debug("latest price poll failed: {}", ex.getMessage());
		}
	}

	private void refreshFiveMinute()
	{
		try
		{
			Map<Integer, Candle> candles = client.fetchAverages("5m");
			if (!candles.isEmpty())
			{
				synchronized (publishLock)
				{
					fiveMinute.clear();
					fiveMinute.putAll(candles);
				}
				publish();
			}
		}
		catch (IOException ex)
		{
			log.debug("5m poll failed: {}", ex.getMessage());
		}
	}

	private void refreshHourly()
	{
		try
		{
			Map<Integer, Candle> candles = client.fetchAverages("1h");
			if (!candles.isEmpty())
			{
				synchronized (publishLock)
				{
					hourly.clear();
					hourly.putAll(candles);
				}
				publish();
			}
		}
		catch (IOException ex)
		{
			log.debug("1h poll failed: {}", ex.getMessage());
		}
	}

	private void publish()
	{
		MarketSnapshot taken;
		synchronized (publishLock)
		{
			taken = new MarketSnapshot(
				Collections.unmodifiableMap(metadata),
				Collections.unmodifiableMap(latest),
				Collections.unmodifiableMap(fiveMinute),
				Collections.unmodifiableMap(hourly),
				Instant.now());
		}
		snapshot.set(taken);

		Runnable listener = updateListener;
		if (listener != null)
		{
			try
			{
				listener.run();
			}
			catch (Exception ex)
			{
				log.debug("market update listener threw", ex);
			}
		}
	}

	/** Package-private so the expiry rules can be exercised without a network client. */
	static final class CachedSeries
	{
		private final String timestep;
		private final List<Candle> candles;
		private final Instant fetchedAt;
		private final boolean inFlight;
		/** A fetch that failed, kept only long enough to stop hammering a failing item. */
		private final boolean failed;

		private CachedSeries(String timestep, List<Candle> candles, Instant fetchedAt, boolean inFlight)
		{
			this(timestep, candles, fetchedAt, inFlight, false);
		}

		CachedSeries(String timestep, List<Candle> candles, Instant fetchedAt, boolean inFlight,
			boolean failed)
		{
			this.timestep = timestep;
			this.candles = candles;
			this.fetchedAt = fetchedAt;
			this.inFlight = inFlight;
			this.failed = failed;
		}

		/** Records that the fetch failed, keeping any candles already held. */
		static CachedSeries failed(String timestep, CachedSeries previous)
		{
			List<Candle> existing = previous == null ? Collections.emptyList() : previous.candles;
			return new CachedSeries(timestep, existing, Instant.now(), false, true);
		}

		static CachedSeries loaded(String timestep, List<Candle> candles)
		{
			return new CachedSeries(timestep, Collections.unmodifiableList(candles), Instant.now(), false);
		}

		/** Keeps any previously loaded candles visible while a refresh is in flight. */
		static CachedSeries pending(String timestep, CachedSeries previous)
		{
			List<Candle> existing = previous == null ? Collections.emptyList() : previous.candles;
			return new CachedSeries(timestep, existing, Instant.now(), true);
		}

		List<Candle> candles()
		{
			return candles;
		}

		boolean isExpired()
		{
			Duration ttl = failed ? FAILURE_TTL : ("5m".equals(timestep) ? SERIES_TTL : LONG_SERIES_TTL);
			return !inFlight && fetchedAt.plus(ttl).isBefore(Instant.now());
		}
	}
}
