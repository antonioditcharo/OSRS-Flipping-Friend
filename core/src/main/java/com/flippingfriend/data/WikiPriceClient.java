package com.flippingfriend.data;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

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
	private static final HttpUrl BASE = HttpUrl.get("https://prices.runescape.wiki/api/v1/osrs");
	private static final String USER_AGENT =
		"OSRS-Flipping-Friend/1.0.0 - RuneLite plugin - https:" +
		"//github.com/antonioditcharo/OSRS-Flipping-Friend";

	private final OkHttpClient httpClient;
	private final Gson gson;

	@Inject
	public WikiPriceClient(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

    public static final class UnsupportedHistoryException extends IOException {
        public UnsupportedHistoryException(Throwable cause){super("Unsupported public history",cause);}
    }

	/** Static item facts. Large (~4k items) and changes only with game updates, so cache it hard. */
	public List<ItemMetadata> fetchMapping() throws IOException
	{
		try {
            List<ItemMetadata> mapping=PublicMarketNumbers.mapping(get(BASE.newBuilder().addPathSegment("mapping").build(),false),gson);
            if(mapping.isEmpty())throw new IOException("No supported public mapping rows");
            return mapping;
        }catch(RuntimeException invalid){throw new IOException("Invalid public mapping",invalid);}
	}

	/** Most recent instant-buy and instant-sell for every item. */
	public Map<Integer, LatestPrice> fetchLatest() throws IOException
	{
		JsonElement raw=get(BASE.newBuilder().addPathSegment("latest").build(),true);
        if(!raw.isJsonObject())throw new IOException("Public quote map required");
        return PublicMarketNumbers.quotes(raw.getAsJsonObject());
	}

	/**
	 * Volume-weighted averages over the most recent window.
	 *
	 * @param window one of {@code 5m}, {@code 1h} or {@code 24h}
	 */
	public Map<Integer, Candle> fetchAverages(String window) throws IOException
	{
        JsonElement response=get(BASE.newBuilder().addPathSegment(window).build(),false);
        try {
            JsonObject envelope=response.getAsJsonObject();
            if(!envelope.has("data") || !envelope.get("data").isJsonObject())throw new IllegalArgumentException("Public aggregate map required");
            long timestamp=envelope.has("timestamp")?PublicMarketNumbers.integer(envelope.get("timestamp")):0;
            if(timestamp<0)throw new IllegalArgumentException("Invalid public aggregate clock");
            return PublicMarketNumbers.candles(envelope.getAsJsonObject("data"),timestamp);
        }catch(RuntimeException invalid){throw new IOException("Invalid public aggregate",invalid);}
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
		try{return PublicMarketNumbers.history(get(url,true));}
        catch(RuntimeException invalid){throw new UnsupportedHistoryException(invalid);}
	}

	/**
	 * @param unwrapData the collection endpoints wrap their payload in a {@code data} object, but
	 *                   {@code /mapping} returns a bare array
	 */
	private JsonElement get(HttpUrl url, boolean unwrapData) throws IOException
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
					return gson.fromJson(reader, JsonElement.class);
				}

				JsonObject envelope = gson.fromJson(reader, JsonObject.class);
				if (envelope == null || !envelope.has("data"))
				{
					throw new IOException("response from " + url + " had no data field");
				}
				return envelope.get("data");
            }catch(RuntimeException invalid){throw new IOException("Malformed public response",invalid);}
        }
    }

}
