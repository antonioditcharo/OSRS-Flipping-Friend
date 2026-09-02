package com.flippingfriend.companion;

import com.flippingfriend.core.AccountSnapshot;
import com.flippingfriend.core.OfferEvent;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.google.gson.Gson;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Authenticated loopback-only HTTP boundary. It intentionally exposes no control endpoint. */
final class ApiServer implements AutoCloseable
{
	static final int PORT = 37_777;
	private final HttpServer server;
	private final ExecutorService executor = Executors.newFixedThreadPool(2);
	private final String token;
	private final Gson gson;
	private final CompanionService service;

	ApiServer(String token, Gson gson, CompanionService service) throws IOException
	{
		this.token = token; this.gson = gson; this.service = service;
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
		server.createContext("/v1/health", this::health);
		server.createContext("/v1/portfolio/current", this::portfolio);
		// Read-only, and serving nothing this process did not already fetch for itself. They exist so
		// the plugin can start from a warm feed instead of rebuilding one from the internet on every
		// launch.
		server.createContext("/v1/market/snapshot", this::marketSnapshot);
		server.createContext("/v1/market/series", this::marketSeries);
		server.createContext("/v1/events/account-state", this::account);
		server.createContext("/v1/events/ge-offer", this::offer);
		server.setExecutor(executor);
	}

	void start() { server.start(); }

	private void health(HttpExchange exchange) throws IOException
	{
		if (!authorized(exchange)) return;
		respond(exchange, 200, gson.toJson(service.health()));
	}
	private void portfolio(HttpExchange exchange) throws IOException
	{
		if (!authorized(exchange)) return;
		respond(exchange, 200, gson.toJson(service.plan()));
	}
	private void marketSnapshot(HttpExchange exchange) throws IOException
	{
		if (!authorized(exchange)) return;
		respond(exchange, 200, gson.toJson(service.marketFeed()));
	}

	private void marketSeries(HttpExchange exchange) throws IOException
	{
		if (!authorized(exchange)) return;
		java.util.Map<String, String> query = queryOf(exchange);
		String id = query.get("id");
		String timestep = query.get("timestep");
		if (id == null || timestep == null)
		{
			respond(exchange, 400, "{\"error\":\"id and timestep are required\"}");
			return;
		}
		try
		{
			respond(exchange, 200, gson.toJson(service.series(Integer.parseInt(id), timestep)));
		}
		catch (NumberFormatException ex)
		{
			respond(exchange, 400, "{\"error\":\"id must be a number\"}");
		}
	}

	/** Query parameters, decoded. Absent or malformed pairs are skipped rather than throwing. */
	private static java.util.Map<String, String> queryOf(HttpExchange exchange)
	{
		java.util.Map<String, String> values = new java.util.HashMap<>();
		String query = exchange.getRequestURI().getRawQuery();
		if (query == null)
		{
			return values;
		}
		for (String pair : query.split("&"))
		{
			int equals = pair.indexOf('=');
			if (equals <= 0)
			{
				continue;
			}
			values.put(
				java.net.URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
				java.net.URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
		}
		return values;
	}

	private void account(HttpExchange exchange) throws IOException
	{
		if (!authorized(exchange)) return;
		try
		{
			service.account(gson.fromJson(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), AccountSnapshot.class));
			respond(exchange, 202, "{\"accepted\":true}");
		}
		catch (Exception ex) { respond(exchange, 400, error(ex)); }
	}
	private void offer(HttpExchange exchange) throws IOException
	{
		if (!authorized(exchange)) return;
		try
		{
			service.offer(gson.fromJson(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), OfferEvent.class));
			respond(exchange, 202, "{\"accepted\":true}");
		}
		catch (Exception ex) { respond(exchange, 400, error(ex)); }
	}

	private boolean authorized(HttpExchange exchange) throws IOException
	{
		if (!token.equals(exchange.getRequestHeaders().getFirst("X-Flipping-Friend-Token")))
		{
			respond(exchange, 401, "{\"error\":\"unauthorized\"}"); return false;
		}
		return true;
	}
	/**
	 * The error body, built without assuming the exception had anything to say.
	 * <p>
	 * This called getMessage().replace(...) directly, and an NPE -- which is exactly what Gson binding
	 * throws on a malformed body -- carries a null message. The secondary NPE escaped the handler, so
	 * the exchange was never closed and the plugin saw a dropped connection instead of a 400.
	 */
	private static String error(Exception ex)
	{
		String message = ex == null || ex.getMessage() == null
			? String.valueOf(ex == null ? "unknown" : ex.getClass().getSimpleName())
			: ex.getMessage();
		return "{\"error\":\"" + message.replace("\"", "'") + "\"}";
	}
	private static void respond(HttpExchange exchange, int code, String body) throws IOException
	{
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.sendResponseHeaders(code, bytes.length);
		exchange.getResponseBody().write(bytes);
		exchange.close();
	}
	@Override public void close() { server.stop(0); executor.shutdownNow(); }
}
