package com.flippingfriend.companion;

import com.flippingfriend.data.Candle;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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
    private final java.util.concurrent.atomic.AtomicLong revision=new java.util.concurrent.atomic.AtomicLong();
    long revision(){return revision.get();}
	private static final String BASE = "https://prices.runescape.wiki/api/v1/osrs/timeseries";

	private static final Logger log = LoggerFactory.getLogger(SeriesCache.class);

	private static final Duration SHORT_TTL = Duration.ofMinutes(20);
	private static final Duration LONG_TTL = Duration.ofHours(3);
	/** A failed fetch is worth retrying soon; it is not a result worth keeping for three hours. */
	private static final Duration FAILURE_TTL = Duration.ofMinutes(2);
	private static final int MAX_ENTRIES = 600;
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

	private final OkHttpClient client;

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
		this(null, CompanionHttp.CLIENT);
	}

	SeriesCache(Path file)
	{
		this(file, CompanionHttp.CLIENT);
	}

	SeriesCache(Path file, OkHttpClient client)
	{
		this.file = file;
		this.client = client;
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
        return warm(itemIds,timestep,budget,false);
    }
    int warmMaintenance(List<Integer> itemIds,int budget) {
        // Oldest fetch attempt first, including failures. A fixed first page cannot monopolize a small budget.
        var ordered=new ArrayList<Integer>(new java.util.LinkedHashSet<>(itemIds));
        ordered.sort(java.util.Comparator.comparingLong((Integer item)->{
            var cached=cache.get(key(item,"5m"));return cached==null?Long.MIN_VALUE:cached.fetchedAt.getEpochSecond();
        }).thenComparingInt(Integer::intValue));
        return warm(ordered,"5m",budget,true);
    }
    private int warm(List<Integer> itemIds,String timestep,int budget,boolean maintenance) {
		int spent = 0;
		for (Integer itemId : itemIds)
		{
			if (spent >= budget || Thread.currentThread().isInterrupted())
			{
				break;
			}
			String key = key(itemId, timestep);
			Cached cached = cache.get(key);
			if (cached != null && !(maintenance?cached.isMaintenanceStale():cached.isStale(timestep)))
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
				cache.put(key, Cached.failed(Instant.now())); revision.incrementAndGet();
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
		cache.put(key(itemId, timestep), new Cached(candles, fetchedAt)); revision.incrementAndGet();
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

		try
		{
			Files.createDirectories(file.getParent());
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			// Gzipped: the honest shape of this is a few hundred series of a few hundred bars, which
			// is megabytes of very repetitive JSON and compresses to a fraction of it.
			int written=0;
			try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(
				new GZIPOutputStream(Files.newOutputStream(temp)), StandardCharsets.UTF_8)))
			{
				writer.setSerializeNulls(true);
				writer.beginObject().name("entries").beginArray();
				for (Map.Entry<String, Cached> entry : snapshot) {
					Cached cached=entry.getValue();
					if(cached.failed || cached.candles.isEmpty() || cached.isStale(timestepOf(entry.getKey())))continue;
					writer.beginObject().name("key").value(entry.getKey())
						.name("fetchedAt").value(cached.fetchedAt.getEpochSecond()).name("data").beginArray();
					for(Candle candle:cached.candles) {
						writer.beginObject().name("timestamp").value(candle.getTimestamp())
							.name("avgHighPrice").value(candle.getAvgHighPrice())
							.name("avgLowPrice").value(candle.getAvgLowPrice())
							.name("highPriceVolume").value(candle.getHighPriceVolume())
							.name("lowPriceVolume").value(candle.getLowPriceVolume()).endObject();
					}
					writer.endArray().endObject();written++;
				}
				writer.endArray().endObject();
			}
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			log.debug("wrote {} cached series to {}", written, file.getFileName());
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
		try (JsonReader reader = new JsonReader(new InputStreamReader(
			new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8)))
		{
			// Keep Gson's legacy lenient syntax and last-root-member semantics, but retain only
			// one history entry's JSON tree. A truncated container still discards the whole cache.
			reader.setLenient(true);reader.beginObject();
			while(reader.hasNext()) {
				if(!"entries".equals(reader.nextName())) {reader.skipValue();continue;}
				cache.clear();restored=0;expired=0;
				if(reader.peek()!=JsonToken.BEGIN_ARRAY) {reader.skipValue();continue;}
				var seenKeys=new java.util.HashSet<String>();reader.beginArray();
				while(reader.hasNext()) {
					JsonElement element=JsonParser.parseReader(reader);
					try {
						JsonObject stored = element.getAsJsonObject();
						String key = stored.get("key").getAsString();
						int split=key.indexOf('@');
						com.flippingfriend.data.PublicMarketNumbers.itemKey(key.substring(0,split));
						if(!List.of("5m","1h","6h","24h").contains(timestepOf(key)))continue;
						if(!seenKeys.add(key)){cache.remove(key);continue;}
						long fetched=com.flippingfriend.data.PublicMarketNumbers.integer(stored.get("fetchedAt"));
						if(fetched<=0 || fetched>Instant.now().getEpochSecond())continue;
						Cached cached = new Cached(parse(stored),Instant.ofEpochSecond(fetched));
						if (cached.isStale(timestepOf(key)))
						{
							expired++;
							continue;
						}
						cache.put(key, cached); revision.incrementAndGet();
						restored++;
					} catch(RuntimeException unsupported) {
						log.debug("ignored one unsupported public history cache entry");
					}
				}
				reader.endArray();
			}
			reader.endObject();
			if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IllegalStateException("Trailing public cache input");
		}
		catch (Throwable ex)
		{
			// Throwable, not Exception, and the difference killed a live session.
			//
			// This method's own promise is that nothing here is fatal, because the cache is
			// rebuildable by definition. Catching Exception did not keep that promise: the old
			// reader parsed the whole file into a JSON tree, and on a cache that
			// has grown past the heap the service died on OutOfMemoryError at startup -- an Error,
			// straight through the guard written to prevent exactly this. The companion never came
			// up, the plugin got no plans, and the only trace was a stack trace in a file nobody
			// reads.
			//
			// A partly built cache is dropped with it: half a cache is not better than none, and it
			// re-warms within minutes.
			cache.clear(); revision.incrementAndGet();
			log.warn("could not read the series cache at {}, starting cold: {}", file,
				String.valueOf(ex.toString()));
			return;
		}
		log.info("restored {} cached series from disk, {} had expired", restored, expired);
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
			.header("User-Agent", CompanionHttp.USER_AGENT)
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
		return com.flippingfriend.data.PublicMarketNumbers.history(root.get("data"));
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
        boolean isMaintenanceStale() {
            return failed?isStale("5m"):fetchedAt.plus(Duration.ofMinutes(2)).isBefore(Instant.now());
        }
	}
}
