package com.flippingfriend.data;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
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
 * Client over the Official Jagex Grand Exchange API.
 * Pulls historical daily volume and pricing for a macro-level market trajectory analysis.
 */
@Singleton
public class JagexPriceClient
{
	private static final Logger log = LoggerFactory.getLogger(JagexPriceClient.class);
	private static final HttpUrl BASE = HttpUrl.get("https://services.runescape.com/m=itemdb_oldschool/api/graph");
	private static final String USER_AGENT = "OSRS-Flipping-Friend/1.0.0";

	private final OkHttpClient httpClient;
	private final Gson gson;

	@Inject
	public JagexPriceClient(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	/**
	 * Fetches the daily historical price map (timestamp -> average price).
	 */
	public Map<Long, Integer> fetchDailyTrend(int itemId) throws IOException
	{
		HttpUrl url = BASE.newBuilder().addPathSegment(itemId + ".json").build();
		Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", USER_AGENT)
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful())
			{
				throw new IOException("Jagex GE API returned " + response.code() + " for " + url);
			}

			ResponseBody body = response.body();
			if (body == null)
			{
				throw new IOException("empty response from " + url);
			}

			try (InputStreamReader reader = new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8))
			{
				JsonObject envelope = gson.fromJson(reader, JsonObject.class);
				if (envelope == null || !envelope.has("daily"))
				{
					return new TreeMap<>();
				}
				
				JsonObject daily = envelope.getAsJsonObject("daily");
				Map<Long, Integer> trend = new TreeMap<>();
				for (Map.Entry<String, com.google.gson.JsonElement> entry : daily.entrySet())
				{
					try
					{
						long timestamp = Long.parseLong(entry.getKey());
						int price = entry.getValue().getAsInt();
						trend.put(timestamp, price);
					}
					catch (NumberFormatException e)
					{
						// Ignore malformed keys
					}
				}
				return trend;
			}
		}
	}
}
