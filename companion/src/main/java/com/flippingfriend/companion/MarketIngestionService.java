package com.flippingfriend.companion;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Polite cached ingestion of the public market feed; all raw responses are retained in SQLite. */
final class MarketIngestionService
{
	private static final String BASE = "https://prices.runescape.wiki/api/v1/osrs/";
	private final OkHttpClient client;
	private final Gson gson;
	private volatile MarketState state = MarketState.empty();
	private long lastMapping;

	MarketIngestionService(Gson gson)
	{
		this(gson, CompanionHttp.CLIENT);
	}

	MarketIngestionService(Gson gson, OkHttpClient client)
	{
		this.gson = gson;
		this.client = client;
	}

	/**
	 * How long a five-minute bar is worth keeping before asking for it again.
	 * <p>
	 * The three feeds were all fetched every sixty seconds, which is what the loop runs at, but only
	 * one of them changes that often. The five-minute aggregate is republished every five minutes and
	 * the hourly one every hour, so we were asking for them five and sixty times more often than
	 * they could possibly differ -- two whole-universe requests a minute, in perpetuity, to a
	 * volunteer-run community API, for bytes we already had. Two captures 849 seconds apart confirmed
	 * it: the hourly payload was byte-identical.
	 * <p>
	 * Half the publication interval, so a bar is picked up within a couple of minutes of appearing
	 * without depending on our clock lining up with theirs.
	 */
	private static final long FIVE_MINUTE_TTL = 150;
	private static final long HOURLY_TTL = 900;

	private long lastFiveMinute;
	private long lastHourly;

	synchronized void refresh() throws Exception
	{
		long now = Instant.now().getEpochSecond();
		Map<Integer, Item> mapping = state.mapping;
		if (mapping.isEmpty() || now - lastMapping > 86_400)
		{
			mapping = parseMapping(fetchRaw("mapping"));
			lastMapping = now;
		}

		// The only one of the three that is worth a fresh request every cycle -- and even this one is
		// not fresh in the sense that matters. Across 4,184 quoted items, the newest trade the feed
		// knew about was a median 793 seconds old and <em>not one item</em> had a trade under sixty
		// seconds old. There is no latency here to win by polling harder; the delay is upstream, in
		// how the prices are published, and no cadence on our side reaches behind it.
		JsonObject latest = fetch("latest").getAsJsonObject("data");

		JsonObject fiveMinute = state.fiveMinute;
		if (fiveMinute == null || fiveMinute.size() == 0 || now - lastFiveMinute >= FIVE_MINUTE_TTL)
		{
			fiveMinute = fetch("5m").getAsJsonObject("data");
			lastFiveMinute = now;
		}

		// The hourly bar is a full hour of real trades rather than five minutes extrapolated, which
		// makes it the honest basis for judging how busy an item is.
		//
		// The six-hour and daily bars used to be fetched here too, described as durable context for
		// the learner. No learner ever read them: their only effect was two more requests a minute
		// to a volunteer-run community API, in perpetuity, to fill a table nothing selects from.
		JsonObject hourly = state.hourly;
		if (hourly == null || hourly.size() == 0 || now - lastHourly >= HOURLY_TTL)
		{
			hourly = fetch("1h").getAsJsonObject("data");
			lastHourly = now;
		}

		state = new MarketState(mapping, latest, fiveMinute, hourly, now);
	}

	/*
	 * A websocket client lived here and has been removed.
	 *
	 * It opened wss://prices.runescape.wiki/api/ws, which does not exist and answers 404. Its failure
	 * handler cleared the field that guards reconnection, and it was called from refresh(), so it
	 * retried once a minute for the life of the process -- about fourteen hundred failed handshakes a
	 * day -- and swallowed every failure, which is why the companion log never mentioned it once.
	 *
	 * Its message handler was written against a guessed payload format, by its own comments, and
	 * merged whatever arrived straight into the live MarketState's `latest` object from the
	 * websocket's thread while the planner was reading it. Had the endpoint existed, that is a data
	 * race on the market state every plan is built from.
	 *
	 * And the thing it was for is not available. The feed is published stale: across 4,184 quoted
	 * items not one had a trade under sixty seconds old, and the median was thirteen minutes. A live
	 * stream of it would arrive no sooner.
	 */

	MarketState state() { return state; }

	private JsonObject fetch(String endpoint) throws Exception
	{
		Request request = new Request.Builder().url(BASE + endpoint)
			.header("User-Agent", CompanionHttp.USER_AGENT)
			.build();
		try (Response response = client.newCall(request).execute())
		{
			if (!response.isSuccessful() || response.body() == null) { throw new IllegalStateException(endpoint + " returned " + response.code()); }
			String raw = response.body().string();
			JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
			return root;
		}
	}

	static Map<Integer, Item> parseMapping(JsonElement array)
	{
		Map<Integer, Item> items = new HashMap<>();
		for (JsonElement element : array.getAsJsonArray())
		{
			JsonObject object = element.getAsJsonObject();
			int id = object.get("id").getAsInt();
			boolean members = object.has("members") && !object.get("members").isJsonNull()
				&& object.get("members").getAsBoolean();
			boolean published = object.has("limit") && !object.get("limit").isJsonNull()
				&& object.get("limit").getAsInt() > 0;
			int value = object.has("value") && !object.get("value").isJsonNull()
				? object.get("value").getAsInt() : 0;
			// When the wiki does not publish one, use the same rule the plugin already applies in
			// ItemMetadata.getBuyLimit rather than a second, flatter guess. A hardcoded 8 here capped
			// every unpublished item at eight units however cheap it was, and erring low is only the
			// safe direction because an over-estimate produces an offer the game silently refuses to
			// fill -- so the guess is recorded as a guess.
			int limit = published ? object.get("limit").getAsInt() : (value >= 100_000 ? 8 : 100);
			items.put(id, new Item(id, object.get("name").getAsString(), limit, members, published));
		}
		return items;
	}

	private JsonElement fetchRaw(String endpoint) throws Exception
	{
		Request request = new Request.Builder().url(BASE + endpoint).header("User-Agent", CompanionHttp.USER_AGENT).build();
		try (Response response = client.newCall(request).execute())
		{
			if (!response.isSuccessful() || response.body() == null) { throw new IllegalStateException(endpoint + " returned " + response.code()); }
			String raw = response.body().string();
			return JsonParser.parseString(raw);
		}
	}

	static final class MarketState
	{
		final Map<Integer, Item> mapping;
		final JsonObject latest;
		final JsonObject fiveMinute;
		final JsonObject hourly;
		final long observedAt;
		MarketState(Map<Integer, Item> mapping, JsonObject latest, JsonObject fiveMinute,
			JsonObject hourly, long observedAt)
		{
			this.mapping = mapping;
			this.latest = latest;
			this.fiveMinute = fiveMinute;
			this.hourly = hourly;
			this.observedAt = observedAt;
		}
		static MarketState empty()
		{
			return new MarketState(Collections.emptyMap(), new JsonObject(), new JsonObject(),
				new JsonObject(), 0);
		}
	}

	static final class Item
	{
		final int id;
		final String name;
		final int buyLimit;
		/**
		 * Whether the wiki actually publishes this limit, or whether it is our own guess.
		 * <p>
		 * 508 of the 4,652 mapped items -- eleven per cent, and they include Bow of Faerdhinen, Nihil
		 * horn and Ancient hilt -- carry no limit at all. A guess that cannot be told apart from a
		 * published figure feeds the buy-limit ledger, the order-size cap and the model's
		 * order-versus-limit feature as if it were fact.
		 */
		final boolean buyLimitPublished;
		/** Members-only items cannot be traded at all on a free account, so they are not candidates. */
		final boolean members;

		Item(int id, String name, int buyLimit)
		{
			this(id, name, buyLimit, false);
		}

		Item(int id, String name, int buyLimit, boolean members)
		{
			this(id, name, buyLimit, members, true);
		}

		Item(int id, String name, int buyLimit, boolean members, boolean buyLimitPublished)
		{
			this.buyLimitPublished = buyLimitPublished;
			this.id = id;
			this.name = name;
			this.buyLimit = buyLimit;
			this.members = members;
		}
	}

	void close()
	{
		client.dispatcher().executorService().shutdown();
		client.connectionPool().evictAll();
	}
}
