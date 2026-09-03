package com.flippingfriend.data;

import com.flippingfriend.core.WikiApi;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin client over the Old School RuneScape Wiki real-time prices API.
 * <p>
 * The wiki asks for exactly one thing in return for the data: a descriptive User-Agent so they can
 * identify traffic and contact the author. They explicitly block the default agents of common HTTP
 * libraries, so this is not optional.
 *
 * @see <a href="https://oldschool.runescape.wiki/w/RuneScape:Real-time_Prices">Real-time Prices</a>
 */
@Singleton
public class WikiPriceClient
{
	private static final Logger log = LoggerFactory.getLogger(WikiPriceClient.class);

	private static final HttpUrl BASE = HttpUrl.get(WikiApi.BASE);
	/** Shared with every other client, so the wiki sees one identity for this product. */
	private static final String USER_AGENT = WikiApi.USER_AGENT;

	private static final Type LATEST_MAP = new TypeToken<Map<String, LatestPrice>>()
	{
	}.getType();
	private static final Type CANDLE_MAP = new TypeToken<Map<String, Candle>>()
	{
	}.getType();
	private static final Type CANDLE_LIST = new TypeToken<List<Candle>>()
	{
	}.getType();
	private static final Type METADATA_LIST = new TypeToken<List<ItemMetadata>>()
	{
	}.getType();

	private final OkHttpClient httpClient;
	private final Gson gson;

	@Inject
	public WikiPriceClient(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	/** Static item facts. Large (~4k items) and changes only with game updates, so cache it hard. */
	public List<ItemMetadata> fetchMapping() throws IOException
	{
		return get(BASE.newBuilder().addPathSegment("mapping").build(), METADATA_LIST, false);
	}

	/** Most recent instant-buy and instant-sell for every item. */
	public Map<Integer, LatestPrice> fetchLatest() throws IOException
	{
		Map<String, LatestPrice> raw = get(BASE.newBuilder().addPathSegment("latest").build(), LATEST_MAP, true);
		return keyById(raw);
	}

	/**
	 * Volume-weighted averages over the most recent window.
	 *
	 * @param window one of {@code 5m}, {@code 1h} or {@code 24h}
	 */
	public Map<Integer, Candle> fetchAverages(String window) throws IOException
	{
		HttpUrl url = BASE.newBuilder().addPathSegment(window).build();
		Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", USER_AGENT)
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful())
			{
				throw new IOException("wiki prices API returned " + response.code() + " for " + url);
			}

			ResponseBody body = response.body();
			if (body == null)
			{
				throw new IOException("empty response from " + url);
			}

			try (InputStreamReader reader = new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8))
			{
				JsonObject envelope = gson.fromJson(reader, JsonObject.class);
				if (envelope == null || !envelope.has("data"))
				{
					throw new IOException("response from " + url + " had no data field");
				}
				
				long timestamp = 0;
				if (envelope.has("timestamp"))
				{
					timestamp = envelope.get("timestamp").getAsLong();
				}
				
				Map<String, Candle> raw = gson.fromJson(envelope.get("data"), CANDLE_MAP);
				if (timestamp > 0 && raw != null)
				{
					for (Candle c : raw.values())
					{
						if (c != null)
						{
							c.setTimestamp(timestamp);
						}
					}
				}
				return keyById(raw);
			}
		}
	}

	/**
	 * Up to 365 buckets of history for a single item.
	 *
	 * @param timestep one of {@code 5m}, {@code 1h}, {@code 6h} or {@code 24h}
	 */
	public List<Candle> fetchTimeseries(int itemId, String timestep) throws IOException
	{
		HttpUrl url = BASE.newBuilder()
			.addPathSegment("timeseries")
			.addQueryParameter("id", Integer.toString(itemId))
			.addQueryParameter("timestep", timestep)
			.build();
		List<Candle> series = get(url, CANDLE_LIST, true);
		return series == null ? Collections.emptyList() : series;
	}

	/**
	 * @param unwrapData the collection endpoints wrap their payload in a {@code data} object, but
	 *                   {@code /mapping} returns a bare array
	 */
	private <T> T get(HttpUrl url, Type type, boolean unwrapData) throws IOException
	{
		Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", USER_AGENT)
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful())
			{
				throw new IOException("wiki prices API returned " + response.code() + " for " + url);
			}

			ResponseBody body = response.body();
			if (body == null)
			{
				throw new IOException("empty response from " + url);
			}

			try (InputStreamReader reader = new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8))
			{
				if (!unwrapData)
				{
					return gson.fromJson(reader, type);
				}

				JsonObject envelope = gson.fromJson(reader, JsonObject.class);
				if (envelope == null || !envelope.has("data"))
				{
					throw new IOException("response from " + url + " had no data field");
				}
				return gson.fromJson(envelope.get("data"), type);
			}
		}
	}

	/**
	 * The feed keys everything by item id rendered as a JSON string. Converting once here keeps the
	 * rest of the plugin working in ints.
	 */
	private static <T> Map<Integer, T> keyById(Map<String, T> raw)
	{
		if (raw == null)
		{
			return Collections.emptyMap();
		}

		Map<Integer, T> byId = new HashMap<>(raw.size());
		for (Map.Entry<String, T> entry : raw.entrySet())
		{
			try
			{
				byId.put(Integer.parseInt(entry.getKey()), entry.getValue());
			}
			catch (NumberFormatException ex)
			{
				log.debug("skipping non-numeric item key {}", entry.getKey());
			}
		}
		return byId;
	}
}
