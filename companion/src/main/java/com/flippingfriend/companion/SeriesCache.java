package com.flippingfriend.companion;

import com.flippingfriend.core.WikiApi;
import com.flippingfriend.data.Candle;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Per-item price history, fetched on demand and cached hard.
 * <p>
 * The all-item endpoints give one bar per item per poll, which is enough to rank thousands of items
 * but not to judge any of them: volatility, regime, manipulation and fill probability all need a
 * series. That costs one request per item, so it is spent only on the shortlist that survives the
 * cheap screen, and the results are held long enough that a planning cycle every few minutes does
 * not re-fetch anything.
 * <p>
 * Cache lifetimes differ by resolution because the data does. A five-minute series is stale within
 * the hour; a fortnight of hourly bars barely moves, and refetching it often would spend requests on
 * a free community API for no new information.
 * <p>
 * <b>It survives a restart.</b> Held only in memory, every restart threw away up to three hours of
 * collected history and re-fetched the whole shortlist at sixty requests a cycle -- minutes during
 * which every candidate is vetoed for want of history and the companion looks like it is stuck
 * checking the market. Entries carry the time they were fetched, so a restart inside their lifetime
 * starts warm and one outside re-fetches exactly as it did before: nothing past its own time-to-live
 * is ever restored, so no decision is made on prices older than it would have accepted anyway.
 */
final class SeriesCache implements SeriesSource
{
	private static final String BASE = WikiApi.BASE + "/timeseries";
	/** Shared with every other client, so the wiki sees one identity for this product. */
	private static final String USER_AGENT = WikiApi.USER_AGENT;

	private static final Logger log = LoggerFactory.getLogger(SeriesCache.class);

	private static final Duration SHORT_TTL = Duration.ofMinutes(20);
	private static final Duration LONG_TTL = Duration.ofHours(3);
	/** A failed fetch is worth retrying soon; it is not a result worth keeping for three hours. */
	private static final Duration FAILURE_TTL = Duration.ofMinutes(2);
	/**
	 * Room for every shortlisted item at both resolutions, and then some.
	 *
	 * <p>This was a flat 600 while the shortlist grew to 600 ITEMS, and each item needs two entries.
	 * So the cache could hold half of what the planner was asking for, and being access-ordered it
	 * evicted precisely what the warmer had just fetched: the warm count oscillated between 250 and
	 * 340 of 600 for as long as it was left running, never converging, re-fetching the same history
	 * from a volunteer-run API for ever and getting no further. The planner meanwhile skipped a
	 * different arbitrary third of the market on every pass.
	 *
	 * <p>Derived from the shortlist rather than set beside it, because a number that has to agree
	 * with another number will not, and the failure is silent. The spare capacity absorbs the churn
	 * as items enter and leave the shortlist, so a brief change of mind does not evict history that
	 * is about to be wanted again.
	 */
	private static final int MAX_ENTRIES = CandidateFactory.DEEP_ANALYSIS_LIMIT * 2 + 400;
	/**
	 * Shortest gap between writes of the cache file.
	 * <p>
	 * Slower than the planning cycle on purpose. What a lost write costs is one cold start, which is
	 * the behaviour this replaces, so it is not worth rewriting megabytes every minute to narrow the
	 * window.
	 */
	private static final Duration SAVE_INTERVAL = Duration.ofMinutes(5);
	/** Spacing between per-item requests, so a planning cycle stays a polite trickle. */
	private static final long SPACING_MILLIS = 220;

	private final OkHttpClient client = new OkHttpClient();

	/** Access-ordered, so eviction drops whatever the planner has stopped looking at. */
	private final Map<String, Cached> cache =
		Collections.synchronizedMap(new LinkedHashMap<String, Cached>(128, 0.75f, true)
		{
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest)
			{
				return size() > MAX_ENTRIES;
			}
		});

	/** Where the cache is kept between runs, or null to hold it only in memory. */
	private final Path file;
	private volatile Instant lastSavedAt = Instant.EPOCH;
	/** Set when history has been collected that is not on disk yet. */
	private volatile boolean dirty;

	/** In-memory only. Used by the replay and test harnesses, which must not touch a real cache. */
	SeriesCache()
	{
		this(null);
	}

	SeriesCache(Path file)
	{
		this.file = file;
		load();
	}

	/**
	 * History for one item, from cache only. Never fetches, never blocks.
	 * <p>
	 * This used to fetch on a miss, which quietly made the size of the shortlist a latency budget:
	 * every extra item analysed added a throttled HTTP request to the planning cycle, so the number
	 * of items the plugin could consider was capped at whatever fitted inside one cycle — forty-five,
	 * against roughly three hundred and fifty that qualified. Deciding and fetching are now separate
	 * jobs. Planning reads whatever {@link #warm} has already collected and skips the rest, which
	 * costs a newly interesting item one cycle of delay and buys the freedom to look at all of them.
	 */
	@Override
	public List<Candle> series(int itemId, String timestep)
	{
		Cached cached = cache.get(key(itemId, timestep));
		return cached == null ? Collections.emptyList() : cached.candles;
	}

	/**
	 * Fetches history for items that have none or whose copy has gone stale, oldest need first.
	 * <p>
	 * Runs on its own thread and trickles deliberately: this is a free community API and the whole
	 * universe is worth a few hundred requests spread over the cache lifetime, not a burst. Returns
	 * once the budget for this pass is spent, so the caller controls the pace.
	 *
	 * @param budget how many requests this pass may make
	 * @return how many were actually made
	 */
	int warm(List<Integer> itemIds, String timestep, int budget)
	{
		int spent = 0;
		for (Integer itemId : itemIds)
		{
			if (spent >= budget || Thread.currentThread().isInterrupted())
			{
				break;
			}
			String key = key(itemId, timestep);
			Cached cached = cache.get(key);
			if (cached != null && !cached.isStale(timestep))
			{
				continue;
			}

			try
			{
				remember(itemId, timestep, fetch(itemId, timestep), Instant.now());
				spent++;
				Thread.sleep(SPACING_MILLIS);
			}
			catch (InterruptedException ex)
			{
				Thread.currentThread().interrupt();
				break;
			}
			catch (Exception ex)
			{
				// Back off on a failing item rather than retrying it every pass -- but back off on a
				// failure clock, not the success one. An empty result stored with a full time-to-live
				// blackholes an item's history for up to three hours, and if the cause is systemic
				// (a 429, a rejected User-Agent, an endpoint moving) then every item goes dark at
				// once and every surface reads as a warm-up in progress that never finishes. That is
				// indistinguishable from starting up, which is why it could run indefinitely without
				// anyone noticing. Now it says so, and retries within the minute.
				log.warn("history fetch failed for item {} at {}: {}", itemId, timestep,
					String.valueOf(ex.getMessage()));
				cache.put(key, Cached.failed(Instant.now()));
				spent++;
			}
		}
		// Every pass, not only the ones that fetched something. Warming goes quiet once the shortlist
		// is covered, and gating the save on this pass having spent would leave whatever the last
		// spending pass collected sitting in memory until the process ended.
		saveIfDue();
		return spent;
	}

	/**
	 * The one place a successful fetch enters the cache.
	 * <p>
	 * Package-private and taking the fetch time explicitly, so the disk round trip can be exercised
	 * against known history and a known age without a network call.
	 */
	void remember(int itemId, String timestep, List<Candle> candles, Instant fetchedAt)
	{
		cache.put(key(itemId, timestep), new Cached(candles, fetchedAt));
		dirty = true;
	}

	/**
	 * Writes the cache out, if enough time has passed since the last write.
	 * <p>
	 * Called after a warming pass rather than on a timer, so a companion that has stopped collecting
	 * stops writing too.
	 */
	private void saveIfDue()
	{
		if (file == null || !dirty
			|| Duration.between(lastSavedAt, Instant.now()).compareTo(SAVE_INTERVAL) < 0)
		{
			return;
		}
		save();
	}

	/**
	 * Writes the cache out now. Called on shutdown, so the last pass before a restart is not lost.
	 * <p>
	 * Synchronised because two threads can reach it: the warmer, and the shutdown hook, which stops
	 * the warmer without waiting for it. Both would write the same temp file.
	 */
	synchronized void save()
	{
		if (file == null)
		{
			return;
		}
		lastSavedAt = Instant.now();
		// Cleared before the snapshot is taken, so anything collected during the write is still seen
		// as unsaved and goes out on the next pass rather than being lost.
		dirty = false;

		// Copied out under the lock and encoded outside it: encoding a few hundred series takes long
		// enough that doing it while holding the map would stall the planner reading from it.
		List<Map.Entry<String, Cached>> snapshot;
		synchronized (cache)
		{
			snapshot = new ArrayList<>(cache.entrySet());
		}

		JsonArray entries = new JsonArray();
		for (Map.Entry<String, Cached> entry : snapshot)
		{
			Cached cached = entry.getValue();
			// Failures and empties are not worth a restart's memory, and anything already past its
			// lifetime would only be dropped again on the way back in.
			if (cached.failed || cached.candles.isEmpty()
				|| cached.isStale(timestepOf(entry.getKey())))
			{
				continue;
			}
			JsonObject stored = new JsonObject();
			stored.addProperty("key", entry.getKey());
			stored.addProperty("fetchedAt", cached.fetchedAt.getEpochSecond());
			stored.add("data", encode(cached.candles));
			entries.add(stored);
		}

		JsonObject root = new JsonObject();
		root.add("entries", entries);

		try
		{
			Files.createDirectories(file.getParent());
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			// Gzipped: the honest shape of this is a few hundred series of a few hundred bars, which
			// is megabytes of very repetitive JSON and compresses to a fraction of it.
			try (Writer writer = new OutputStreamWriter(
				new GZIPOutputStream(Files.newOutputStream(temp)), StandardCharsets.UTF_8))
			{
				writer.write(root.toString());
			}
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			log.debug("wrote {} cached series to {}", entries.size(), file.getFileName());
		}
		catch (Exception ex)
		{
			// A cache, so a failed write costs one cold start and nothing else. Said out loud anyway,
			// because a cold start is exactly the symptom this exists to prevent.
			log.warn("could not write the series cache to {}: {}", file,
				String.valueOf(ex.getMessage()));
		}
	}

	/**
	 * Reads back whatever the last run left, keeping only what is still within its own lifetime.
	 * <p>
	 * Nothing here is fatal: the cache is rebuildable by definition, so a missing, truncated or
	 * unreadable file simply means starting cold, which is what every run did before this.
	 */
	private void load()
	{
		if (file == null || !Files.isRegularFile(file))
		{
			return;
		}

		int restored = 0;
		int expired = 0;
		try (Reader reader = new InputStreamReader(
			new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8))
		{
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			if (!root.has("entries") || !root.get("entries").isJsonArray())
			{
				return;
			}
			for (JsonElement element : root.getAsJsonArray("entries"))
			{
				JsonObject stored = element.getAsJsonObject();
				String key = stored.get("key").getAsString();
				Cached cached = new Cached(parse(stored),
					Instant.ofEpochSecond(stored.get("fetchedAt").getAsLong()));
				if (cached.isStale(timestepOf(key)))
				{
					expired++;
					continue;
				}
				cache.put(key, cached);
				restored++;
			}
		}
		catch (Exception ex)
		{
			log.warn("could not read the series cache at {}, starting cold: {}", file,
				String.valueOf(ex.getMessage()));
			return;
		}
		log.info("restored {} cached series from disk, {} had expired", restored, expired);
	}

	private static JsonArray encode(List<Candle> candles)
	{
		JsonArray data = new JsonArray();
		for (Candle candle : candles)
		{
			JsonObject bar = new JsonObject();
			bar.addProperty("timestamp", candle.getTimestamp());
			// Written under the API's own field names and read back through the same parser the live
			// response goes through. One format, one parser, nothing to drift apart.
			bar.addProperty("avgHighPrice", candle.getAvgHighPrice());
			bar.addProperty("avgLowPrice", candle.getAvgLowPrice());
			bar.addProperty("highPriceVolume", candle.getHighPriceVolume());
			bar.addProperty("lowPriceVolume", candle.getLowPriceVolume());
			data.add(bar);
		}
		return data;
	}

	private static String timestepOf(String key)
	{
		int at = key.indexOf('@');
		return at < 0 ? "" : key.substring(at + 1);
	}

	/** How many items have usable history at this resolution, for reporting warm-up progress. */
	int warmCount(String timestep)
	{
		String suffix = "@" + timestep;
		int count = 0;
		synchronized (cache)
		{
			for (Map.Entry<String, Cached> entry : cache.entrySet())
			{
				if (entry.getKey().endsWith(suffix) && !entry.getValue().candles.isEmpty())
				{
					count++;
				}
			}
		}
		return count;
	}

	private static String key(int itemId, String timestep)
	{
		return itemId + "@" + timestep;
	}

	private List<Candle> fetch(int itemId, String timestep) throws Exception
	{
		Request request = new Request.Builder()
			.url(BASE + "?id=" + itemId + "&timestep=" + timestep)
			.header("User-Agent", USER_AGENT)
			.build();

		try (Response response = client.newCall(request).execute())
		{
			if (!response.isSuccessful() || response.body() == null)
			{
				throw new IllegalStateException("timeseries returned " + response.code());
			}
			String raw = response.body().string();
			return parse(JsonParser.parseString(raw).getAsJsonObject());
		}
	}

	private static List<Candle> parse(JsonObject root)
	{
		List<Candle> candles = new ArrayList<>();
		if (!root.has("data") || !root.get("data").isJsonArray())
		{
			return candles;
		}

		JsonArray data = root.getAsJsonArray("data");
		for (JsonElement element : data)
		{
			JsonObject bar = element.getAsJsonObject();
			candles.add(new Candle(
				number(bar, "timestamp"),
				boxed(bar, "avgHighPrice"),
				boxed(bar, "avgLowPrice"),
				(int) number(bar, "highPriceVolume"),
				(int) number(bar, "lowPriceVolume")));
		}
		return candles;
	}

	private static long number(JsonObject object, String key)
	{
		return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsLong() : 0;
	}

	private static Integer boxed(JsonObject object, String key)
	{
		return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : null;
	}

	private static final class Cached
	{
		private final List<Candle> candles;
		private final Instant fetchedAt;
		/** Set when this entry records a failed fetch rather than a genuinely empty history. */
		private boolean failed;

		Cached(List<Candle> candles, Instant fetchedAt)
		{
			this.candles = Collections.unmodifiableList(candles);
			this.fetchedAt = fetchedAt;
		}

		static Cached failed(Instant at)
		{
			Cached cached = new Cached(Collections.emptyList(), at);
			cached.failed = true;
			return cached;
		}

		boolean isStale(String timestep)
		{
			if (failed)
			{
				return FAILURE_TTL.minus(Duration.between(fetchedAt, Instant.now())).isNegative();
			}
			Duration ttl = "5m".equals(timestep) ? SHORT_TTL : LONG_TTL;
			return fetchedAt.plus(ttl).isBefore(Instant.now());
		}
	}
}
