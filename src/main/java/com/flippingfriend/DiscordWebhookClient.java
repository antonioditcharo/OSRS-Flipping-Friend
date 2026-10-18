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

		okhttp3.HttpUrl url=okhttp3.HttpUrl.parse(webhookUrl.trim());
		if(url==null || !"https".equals(url.scheme()) || !("discord.com".equals(url.host()) || "discordapp.com".equals(url.host()))
			|| !url.encodedPath().startsWith("/api/webhooks/")) {
			log.warn("Discord alert not sent: invalid webhook address"); return;
		}
		JsonObject payload = new JsonObject();
		payload.addProperty("content", content);
		JsonObject mentions = new JsonObject(); mentions.add("parse",new com.google.gson.JsonArray());
		payload.add("allowed_mentions",mentions);

		RequestBody body = RequestBody.create(JSON, gson.toJson(payload));
		Request request = new Request.Builder()
			.url(url)
			.post(body)
			.build();

		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				// Exception messages may include the webhook URL/token. Never log them.
				log.warn("Discord alert delivery failed (transport error)");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				if(!response.isSuccessful()) log.warn("Discord alert delivery failed (HTTP {})",response.code());
				response.close();
			}
		});
	}
}
