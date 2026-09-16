package com.flippingfriend.model;

import com.google.gson.Gson;
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
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client for the local LSTM forecasting microservice.
 * <p>
 * The Python server runs as a background daemon on port 8000 and returns predicted percentage
 * changes for item prices. A positive value means the model expects the price to rise; a negative
 * value means it expects the price to fall.
 * <p>
 * All calls are best-effort: if the server is unreachable the client returns an empty map rather
 * than propagating the error, so the rest of the engine runs exactly as it did before the model
 * existed. The LSTM is an overlay, not a dependency.
 */
@Singleton
public class LstmForecasterClient
{
	private static final Logger log = LoggerFactory.getLogger(LstmForecasterClient.class);

	private static final HttpUrl BASE = HttpUrl.parse("http://localhost:8000");
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	private static final Type BULK_RESPONSE = new TypeToken<Map<String, Double>>()
	{
	}.getType();

	private final OkHttpClient httpClient;
	private final Gson gson;

	/** Tracks whether the server has been reachable recently, to avoid log spam. */
	private volatile boolean lastCallSucceeded = true;

	@Inject
	public LstmForecasterClient(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	/**
	 * Ask the LSTM service for predicted momentum on every item in the list.
	 *
	 * @param itemHistories the map of item-id to list of historical prices
	 * @return a map of item-id to predicted percentage change, or an empty map if the server is
	 * unreachable or returned an error
	 */
	public Map<Integer, Double> predictBulk(Map<Integer, List<Double>> itemHistories)
	{
		if (itemHistories == null || itemHistories.isEmpty() || BASE == null)
		{
			return Collections.emptyMap();
		}

		HttpUrl url = BASE.newBuilder().addPathSegment("predict_bulk").build();

		// Build the JSON body: {"item_histories": {"4151": [100.0, 105.0], ...}}
		Map<String, Map<Integer, List<Double>>> body = Collections.singletonMap("item_histories", itemHistories);
		String json = gson.toJson(body);

		Request request = new Request.Builder()
			.url(url)
			.post(RequestBody.create(JSON, json))
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful())
			{
				if (lastCallSucceeded)
				{
					log.warn("LSTM forecaster returned HTTP {}", response.code());
				}
				lastCallSucceeded = false;
				return Collections.emptyMap();
			}

			ResponseBody responseBody = response.body();
			if (responseBody == null)
			{
				lastCallSucceeded = false;
				return Collections.emptyMap();
			}

			// The bulk endpoint returns {"4151": 0.003, "1234": -0.001, ...} with string keys.
			Map<String, Double> raw;
			try (InputStreamReader reader = new InputStreamReader(
				responseBody.byteStream(), StandardCharsets.UTF_8))
			{
				raw = gson.fromJson(reader, BULK_RESPONSE);
			}

			if (raw == null)
			{
				lastCallSucceeded = false;
				return Collections.emptyMap();
			}

			Map<Integer, Double> result = new HashMap<>(raw.size());
			for (Map.Entry<String, Double> entry : raw.entrySet())
			{
				try
				{
					result.put(Integer.parseInt(entry.getKey()), entry.getValue());
				}
				catch (NumberFormatException e)
				{
					log.debug("Ignoring non-integer key from LSTM response: {}", entry.getKey());
				}
			}

			if (!lastCallSucceeded)
			{
				log.info("LSTM forecaster is back online — predictions available for {} items", result.size());
			}
			lastCallSucceeded = true;
			return result;
		}
		catch (IOException e)
		{
			if (lastCallSucceeded)
			{
				log.debug("LSTM forecaster unavailable ({}). Predictions disabled until it comes back.",
					e.getMessage());
			}
			lastCallSucceeded = false;
			return Collections.emptyMap();
		}
	}
}
