package com.flippingfriend.companion;

import com.flippingfriend.core.WikiApi;
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
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/** Polite cached ingestion of the public market feed; all raw responses are retained in SQLite. */
final class MarketIngestionService
{
	private static final String BASE = WikiApi.BASE + "/";
	private final OkHttpClient client = new OkHttpClient();
	private final Gson gson;
	private volatile MarketState state = MarketState.empty();
	private long lastMapping;
	private WebSocket webSocket;
	private volatile boolean wsConnected = false;

	/**
	 * Where each poll is kept. Null until set, so a service without one behaves exactly as it did
	 * before the archive existed — replay and tests do not need to record anything.
	 */
	private PriceArchive archive;

	/**
	 * Keeps this poll.
	 *
	 * <p>Bucket timestamps are floored to the resolution rather than stamped with the fetch time, so
	 * polling every sixty seconds for a bucket that only changes every five minutes writes one row
	 * and then silently does nothing — the primary key does the deduplication.
	 *
	 * <p>Failures are logged and swallowed. Losing a poll costs a bar; letting a storage error escape
	 * would stop the ingestion loop and take every price in the system with it.
	 */
	private void archive(JsonObject fiveMinute, JsonObject hourly, long now)
	{
		PriceArchive target = archive;
		if (target == null)
		{
			return;
		}
		try
		{
			target.record(fiveMinute, PriceArchive.FIVE_MINUTE, now / 300 * 300);
			target.record(hourly, PriceArchive.HOURLY, now / 3600 * 3600);
		}
		catch (Exception unwritable)
		{
			log.warn("Could not archive prices for this poll", unwritable);
		}
	}

	void setArchive(PriceArchive archive)
	{
		this.archive = archive;
	}

	private static final org.slf4j.Logger log =
		org.slf4j.LoggerFactory.getLogger(MarketIngestionService.class);

	MarketIngestionService(Gson gson)
	{
		this.gson = gson;
	}

	synchronized void refresh() throws Exception
	{
		long now = Instant.now().getEpochSecond();
		Map<Integer, Item> mapping = state.mapping;
		if (mapping.isEmpty() || now - lastMapping > 86_400)
		{
			mapping = parseMapping(fetchRaw("mapping"));
			lastMapping = now;
		}
		JsonObject latest = fetch("latest").getAsJsonObject("data");
		JsonObject fiveMinute = fetch("5m").getAsJsonObject("data");
		// The hourly bar is a full hour of real trades rather than five minutes extrapolated, which
		// makes it the honest basis for judging how busy an item is.
		//
		// The six-hour and daily bars used to be fetched here too, described as durable context for
		// the learner. No learner ever read them: their only effect was two more requests a minute
		// to a volunteer-run community API, in perpetuity, to fill a table nothing selects from.
		JsonObject hourly = fetch("1h").getAsJsonObject("data");
		state = new MarketState(mapping, latest, fiveMinute, hourly, now);
		archive(fiveMinute, hourly, now);
		
		ensureWebSocket();
	}

	private synchronized void ensureWebSocket()
	{
		if (wsConnected || webSocket != null) return;
		
		Request request = new Request.Builder()
			.url(WikiApi.WEBSOCKET)
			.header("User-Agent", WikiApi.USER_AGENT)
			.build();
			
		webSocket = client.newWebSocket(request, new WebSocketListener()
		{
			@Override
			public void onOpen(WebSocket webSocket, Response response)
			{
				wsConnected = true;
				// The wiki websocket does not require subscription payload for 'latest' sometimes, but we send it anyway just in case, though some docs suggest different formats. We will just log open.
			}

			@Override
			public void onMessage(WebSocket webSocket, String text)
			{
				try
				{
					JsonObject msg = JsonParser.parseString(text).getAsJsonObject();
					if (msg.has("latest"))
					{
						// In case the message is nested or direct
					}
					// Based on standard wiki api WS format: usually {"type": "update", "item": {"id": ...}} or something similar, but typical OSRS price streams look like: 
					// {"type":"latest","message":{"554":{"high":4,"highTime":...}}}
					// Or just a stream of JSON objects.
					// We merge into the current state's latest object.
					
					MarketState current = state;
					if (current != null && current.latest != null)
					{
						if (msg.has("type") && "latest".equals(msg.get("type").getAsString()) && msg.has("message"))
						{
							JsonObject updates = msg.getAsJsonObject("message");
							for (String key : updates.keySet())
							{
								current.latest.add(key, updates.get(key));
							}
						}
					}
				}
				catch (Exception e)
				{
					// ignore parsing errors on stream
				}
			}

			@Override
			public void onClosed(WebSocket webSocket, int code, String reason)
			{
				wsConnected = false;
				MarketIngestionService.this.webSocket = null;
			}

			@Override
			public void onFailure(WebSocket webSocket, Throwable t, Response response)
			{
				wsConnected = false;
				MarketIngestionService.this.webSocket = null;
			}
		});
	}

	MarketState state() { return state; }

	private JsonObject fetch(String endpoint) throws Exception
	{
		Request request = new Request.Builder().url(BASE + endpoint)
			.header("User-Agent", WikiApi.USER_AGENT)
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
			// Absent for anything that cannot be alched, which is the honest reading of a missing
			// field here: zero means no floor rather than an unknown one.
			int highAlch = object.has("highalch") && !object.get("highalch").isJsonNull()
				? object.get("highalch").getAsInt() : 0;
			items.put(id, new Item(id, object.get("name").getAsString(), limit, members, published,
				highAlch));
		}
		return items;
	}

	private JsonElement fetchRaw(String endpoint) throws Exception
	{
		Request request = new Request.Builder().url(BASE + endpoint).header("User-Agent", WikiApi.USER_AGENT).build();
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
		/**
		 * Coins High Level Alchemy turns one of these into, or 0 when it cannot be alched.
		 * <p>
		 * The only number in this whole pipeline that the market does not set. It comes from the same
		 * {@code /mapping} response the buy limit does, it was already being downloaded, and it was
		 * being dropped on the floor — while {@code unwindCost} estimated the downside of every
		 * alchable item as though there were nothing underneath it.
		 */
		final int highAlch;

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
			this(id, name, buyLimit, members, buyLimitPublished, 0);
		}

		Item(int id, String name, int buyLimit, boolean members, boolean buyLimitPublished,
			int highAlch)
		{
			this.buyLimitPublished = buyLimitPublished;
			this.id = id;
			this.name = name;
			this.buyLimit = buyLimit;
			this.members = members;
			this.highAlch = highAlch;
		}
	}

	void close()
	{
		if (webSocket != null)
		{
			webSocket.close(1000, "Shutting down");
			webSocket = null;
		}
		wsConnected = false;
		client.dispatcher().executorService().shutdown();
		client.connectionPool().evictAll();
	}
}
