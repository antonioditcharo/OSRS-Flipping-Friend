package com.flippingfriend;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility client to push notifications to a Discord Webhook.
 */
@Singleton
public class DiscordWebhookClient
{
	private static final Logger log = LoggerFactory.getLogger(DiscordWebhookClient.class);
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	private final OkHttpClient httpClient;
	private final Gson gson;

	@Inject
	public DiscordWebhookClient(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	public void sendMessage(String webhookUrl, String content)
	{
		if (webhookUrl == null || webhookUrl.trim().isEmpty())
		{
			return;
		}

		JsonObject payload = new JsonObject();
		payload.addProperty("content", content);

		RequestBody body = RequestBody.create(JSON, gson.toJson(payload));
		Request request = new Request.Builder()
			.url(webhookUrl.trim())
			.post(body)
			.build();

		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("Failed to send Discord webhook", e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				response.close();
			}
		});
	}
}
