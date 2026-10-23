package com.flippingfriend.companion;

import com.flippingfriend.core.AccountSnapshot;
import com.flippingfriend.core.OfferEvent;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.google.gson.Gson;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;

/** Authenticated loopback-only HTTP boundary. It intentionally exposes no control endpoint. */
final class ApiServer implements AutoCloseable
{
	static final int PORT = 37_777;
	private final HttpServer server;
	private final ExecutorService executor = Executors.newFixedThreadPool(2);
	// Slow scoped reads must not occupy the transport workers needed by the starter's health probe.
	// Keep account work at its existing concurrency, and reject overload without acknowledging events.
	private final ThreadPoolExecutor accountExecutor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
		new ArrayBlockingQueue<>(64));
	// Historical reports and their potentially slow response bodies cannot occupy event/authority workers.
	private final ThreadPoolExecutor reportExecutor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
		new ArrayBlockingQueue<>(2));
	private final String token;
	private final Gson gson;
	private final CompanionService service;
	private final CompanionAccounts accounts;

	ApiServer(String token, Gson gson, CompanionService service, CompanionAccounts accounts) throws IOException
	{
		this(token, gson, service, accounts, new InetSocketAddress("127.0.0.1", PORT));
	}

	ApiServer(String token, Gson gson, CompanionService service, CompanionAccounts accounts,
		InetSocketAddress address) throws IOException
	{
		this.token = token; this.gson = gson; this.service = service;
		this.accounts = accounts;
		if (!address.getAddress().isLoopbackAddress()) throw new IllegalArgumentException("loopback required");
		server = HttpServer.create(address, 0);
		server.createContext("/v1/health", exchange -> {
			if (exchange.getRequestHeaders().getFirst(com.flippingfriend.core.AccountIdentity.HEADER) == null)
				health(exchange);
			else dispatch(exchange, this::health);
		});
		accountContext("/v1/portfolio/current", this::portfolio);
		accountContext("/v1/action", this::action);
		accountContext("/v1/policy/entry", this::entryDecision);
        accountContext("/v1/policy/replacement",this::replacementEntry);
		accountContext("/v1/policy/buy-maintenance", this::buyMaintenanceDecision);
		// Read-only, and serving nothing this process did not already fetch for itself. They exist so
		// the plugin can start from a warm feed instead of rebuilding one from the internet on every
		// launch.
		accountContext("/v1/market/snapshot", this::marketSnapshot);
		accountContext("/v1/market/series", this::marketSeries);
		accountContext("/v1/events/account-state", this::account);
		accountContext("/v1/events/ge-offer", this::offer);
		accountContext("/v1/accounts/", this::accountRoute);
		server.setExecutor(executor);
	}

	void start() { server.start(); }
	int port() { return server.getAddress().getPort(); }

	private void accountContext(String path, HttpHandler handler)
	{
		server.createContext(path, exchange -> dispatch(exchange, handler));
	}

	private void dispatch(HttpExchange exchange, HttpHandler handler) throws IOException
	{
		if (!authorized(exchange)) return;
		boolean report = reportRequest(exchange);
		try
		{
			(report ? reportExecutor : accountExecutor).execute(() -> {
				try { handler.handle(exchange); }
				catch (IOException | RuntimeException error) { exchange.close(); }
			});
		}
		catch (RejectedExecutionException busy)
		{
			respond(exchange, 503, report
                ? "{\"error\":\"Companion reports are busy; retry the read later.\"}"
                : "{\"error\":\"Companion account requests are busy; retry without changing event identity.\"}");
		}
	}
    private static boolean reportRequest(HttpExchange exchange) {
        if(!"GET".equals(exchange.getRequestMethod()))return false;
        String prefix="/v1/accounts/",path=exchange.getRequestURI().getPath();
        if(!path.startsWith(prefix))return false;
        String[] scoped=path.substring(prefix.length()).split("/",2);
        return scoped.length==2 && java.util.Set.of("performance/current","trade-review/current","learning/current").contains(scoped[1]);
    }

	private void accountRoute(HttpExchange exchange) throws IOException
	{
		if (!authorized(exchange)) return;
		String[] path = exchange.getRequestURI().getPath().substring("/v1/accounts/".length()).split("/", 2);
		if (path.length != 2 || !path[0].equals(exchange.getRequestHeaders().getFirst(
			com.flippingfriend.core.AccountIdentity.HEADER)))
		{
			respond(exchange, 400, "{\"error\":\"account path and header must match\"}"); return;
		}
		switch (path[1])
		{
			case "portfolio/current": portfolio(exchange); break;
			case "ledger/current": ledger(exchange); break;
			case "learning/current": learning(exchange); break;
			case "performance/current": performance(exchange); break;
			case "performance/session": performanceSession(exchange); break;
            case "trade-review/current": tradeReview(exchange,false);break;
            case "trade-review": tradeReview(exchange,true);break;
			case "action": action(exchange); break;
			case "policy/entry": entryDecision(exchange); break;
            case "policy/replacement": replacementEntry(exchange);break;
			case "policy/buy-maintenance": buyMaintenanceDecision(exchange); break;
			case "policy/sale": saleDecision(exchange); break;
            case "policy/sale-maintenance": saleMaintenanceDecision(exchange);break;
			case "events/account-state": account(exchange); break;
			case "events/ge-offer": offer(exchange); break;
			default: respond(exchange, 404, "{\"error\":\"unknown account endpoint\"}");
		}
	}

	private CompanionService accountService(HttpExchange exchange) throws IOException
	{
		try
		{
			String accountId = com.flippingfriend.core.AccountIdentity.require(
				exchange.getRequestHeaders().getFirst(com.flippingfriend.core.AccountIdentity.HEADER));
			exchange.getResponseHeaders().set(com.flippingfriend.core.AccountIdentity.HEADER, accountId);
			return accounts.forAccount(accountId);
		}
		catch (Exception error) { respond(exchange, 400, error(error)); return null; }
	}

	private void requirePayloadAccount(HttpExchange exchange, String accountId)
	{
		com.flippingfriend.core.AccountIdentity.require(accountId);
		if (!accountId.equals(exchange.getRequestHeaders().getFirst(com.flippingfriend.core.AccountIdentity.HEADER)))
			throw new IllegalArgumentException("payload account identity does not match request");
	}

	private void health(HttpExchange exchange) throws IOException
	{
		if (!authorized(exchange)) return;
		CompanionService scoped = exchange.getRequestHeaders().getFirst(
			com.flippingfriend.core.AccountIdentity.HEADER) == null ? service : accountService(exchange);
		if (scoped != null) respond(exchange, 200, gson.toJson(scoped.health()));
	}
	private void portfolio(HttpExchange exchange) throws IOException
	{
		if (!authorized(exchange)) return;
		CompanionService service = accountService(exchange);
		if (service == null) return;
		respond(exchange, 200, gson.toJson(service.plan()));
	}

	private void ledger(HttpExchange exchange) throws IOException
	{
		if (!authorized(exchange)) return;
		if(!"GET".equals(exchange.getRequestMethod())){respond(exchange,405,"{\"error\":\"GET required\"}");return;}
		CompanionService scoped = accountService(exchange);
		if (scoped == null) return;
		try { respond(exchange, 200, gson.toJson(scoped.ledger())); }
		catch (Exception failure) { respond(exchange, 500, error(failure)); }
	}

	private void action(HttpExchange exchange) throws IOException
	{
		if (!authorized(exchange)) return;
		CompanionService service = accountService(exchange);
		if (service == null) return;
		respond(exchange, 200, gson.toJson(service.action()));
	}
	private void performance(HttpExchange exchange) throws IOException {
		if(!authorized(exchange))return;
		if(!"GET".equals(exchange.getRequestMethod())){respond(exchange,405,"{\"error\":\"GET required\"}");return;}
		var scoped=accountService(exchange);if(scoped==null)return;
		try {respond(exchange,200,gson.toJson(scoped.performance(queryOf(exchange).getOrDefault("scope","SESSION"))));}
		catch(Exception failure){respond(exchange,400,error(failure));}
	}
	private void performanceSession(HttpExchange exchange) throws IOException {
		if(!authorized(exchange))return;
		if(!"POST".equals(exchange.getRequestMethod())){respond(exchange,405,"{\"error\":\"POST required\"}");return;}
		var scoped=accountService(exchange);if(scoped==null)return;
		try {
			var body=gson.fromJson(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8),com.google.gson.JsonObject.class);
			scoped.resetPerformanceSession(body.get("requestId").getAsString());respond(exchange,200,"{\"accepted\":true}");
		}catch(Exception failure){respond(exchange,400,error(failure));}
	}
    private void tradeReview(HttpExchange exchange,boolean write)throws IOException {
        if(!authorized(exchange))return;
        if(!(write?"POST":"GET").equals(exchange.getRequestMethod())){respond(exchange,405,"{\"error\":\"Unsupported method\"}");return;}
        var scoped=accountService(exchange);if(scoped==null)return;
        try {
            if(!write){respond(exchange,200,gson.toJson(scoped.tradeReview()));return;}
            byte[] bytes=exchange.getRequestBody().readNBytes(256*1024+1);
            if(bytes.length>256*1024)throw new IllegalArgumentException("Review request too large");
            var request=gson.fromJson(new String(bytes,StandardCharsets.UTF_8),com.flippingfriend.core.TradeReviewSnapshot.Request.class);
            if(request==null)throw new IllegalArgumentException("Review request required");
            requirePayloadAccount(exchange,request.accountId);
            respond(exchange,200,gson.toJson(scoped.reviewTrades(request)));
        }catch(Exception failure){respond(exchange,400,error(failure));}
    }

    private void learning(HttpExchange exchange) throws IOException {
        if(!authorized(exchange))return;
        if(!"GET".equals(exchange.getRequestMethod())) {respond(exchange,405,"{\"error\":\"GET required\"}");return;}
        CompanionService scoped=accountService(exchange);
        if(scoped!=null)respond(exchange,200,gson.toJson(scoped.learningEvidence()));
    }

	private void buyMaintenanceDecision(HttpExchange exchange) throws IOException
	{
		if (!authorized(exchange)) return;
		CompanionService service = accountService(exchange);
		if (service == null) return;
		respond(exchange, 200, gson.toJson(service.buyMaintenanceDecision(
			java.time.Instant.now().getEpochSecond())));
	}
    private void saleMaintenanceDecision(HttpExchange exchange)throws IOException {
        if(!authorized(exchange))return;
        if(!"POST".equals(exchange.getRequestMethod())){respond(exchange,405,"{\"error\":\"POST required\"}");return;}
        var scoped=accountService(exchange);if(scoped==null)return;
        try {
            byte[] bytes=exchange.getRequestBody().readNBytes(16385);
            if(bytes.length>16384)throw new IllegalArgumentException("Sale request is too large");
            var body=gson.fromJson(new String(bytes,StandardCharsets.UTF_8),com.google.gson.JsonObject.class);
            if(body==null || !body.keySet().equals(java.util.Set.of("requestId","item","price","quantity","source","cut","slot")))
                throw new IllegalArgumentException("Invalid sale request");
            for(String key:new String[]{"item","price","quantity","slot"}) {
                var value=body.getAsJsonPrimitive(key);
                if(value==null || !value.isNumber() || !value.getAsString().matches("0|[1-9][0-9]*")
                    || new java.math.BigInteger(value.getAsString()).compareTo(java.math.BigInteger.valueOf(Integer.MAX_VALUE))>0)throw new IllegalArgumentException("Invalid sale quantity");
            }
            if(!body.getAsJsonPrimitive("requestId").isString() || !body.getAsJsonPrimitive("source").isString()
                || !body.getAsJsonPrimitive("cut").isBoolean())throw new IllegalArgumentException("Invalid sale identity");
            respond(exchange,200,gson.toJson(scoped.saleMaintenanceDecision(body.get("requestId").getAsString(),body.get("item").getAsInt(),
                body.get("price").getAsInt(),body.get("quantity").getAsInt(),body.get("source").getAsString(),body.get("cut").getAsBoolean(),
                body.get("slot").getAsInt(),java.time.Instant.now().getEpochSecond())));
        }catch(RuntimeException malformed){respond(exchange,400,"{\"error\":\"invalid sale maintenance request\"}");}
    }
	private void saleDecision(HttpExchange exchange) throws IOException {
		if(!authorized(exchange))return;
		if(!"GET".equals(exchange.getRequestMethod())){respond(exchange,405,"{\"error\":\"GET required\"}");return;}
		CompanionService scoped=accountService(exchange);if(scoped==null)return;
		try {
			java.util.Map<String,String> query=new java.util.HashMap<>();
			String raw=exchange.getRequestURI().getRawQuery();
			if(raw!=null)for(String part:raw.split("&")) {
				String[] pair=part.split("=",2);if(pair.length!=2 || query.put(pair[0],java.net.URLDecoder.decode(pair[1],StandardCharsets.UTF_8))!=null)throw new IllegalArgumentException("invalid sale query");
			}
            var keys=new java.util.HashSet<>(query.keySet());keys.remove("repriceSource");
			if(!java.util.Set.of("item","price","quantity","source","cut","slot").equals(keys)
				|| !("true".equals(query.get("cut")) || "false".equals(query.get("cut"))))throw new IllegalArgumentException("invalid sale query");
			respond(exchange,200,gson.toJson(scoped.saleDecision(Integer.parseInt(query.get("item")),Integer.parseInt(query.get("price")),
				Integer.parseInt(query.get("quantity")),query.get("source"),Boolean.parseBoolean(query.get("cut")),Integer.parseInt(query.get("slot")),java.time.Instant.now().getEpochSecond(),query.get("repriceSource"))));
		}catch(RuntimeException invalid){respond(exchange,400,"{\"error\":\"invalid sale query\"}");}
	}

    private void replacementEntry(HttpExchange exchange) throws IOException {
        if(!authorized(exchange))return;
        if(!"GET".equals(exchange.getRequestMethod())){respond(exchange,405,"{\"error\":\"GET required\"}");return;}
        CompanionService scoped=accountService(exchange);if(scoped==null)return;
        try {
            var query=queryOf(exchange);
            if(query.size()!=2) throw new IllegalArgumentException("Invalid bounded entry query");
            int item=Integer.parseInt(query.get("item")),cap=Integer.parseInt(query.get("quantityCap"));
            if(item<=0 || cap<=0)throw new IllegalArgumentException("Invalid bounded entry query");
            respond(exchange,200,gson.toJson(scoped.replacementEntry(item,cap,java.time.Instant.now().getEpochSecond())));
        }catch(RuntimeException invalid){respond(exchange,400,"{\"error\":\"positive item and quantityCap are required\"}");}
    }
	private void entryDecision(HttpExchange exchange) throws IOException
	{
		if (!authorized(exchange)) return;
		CompanionService service = accountService(exchange);
		if (service == null) return;
		java.util.Map<String, String> query = queryOf(exchange);
		String planId = query.get("planId");
		String rank = query.get("rank");
		if (planId == null || planId.isBlank() || rank == null)
		{
			respond(exchange, 400, "{\"error\":\"planId and rank are required\"}");
			return;
		}
		try
		{
			respond(exchange, 200, gson.toJson(service.entryDecision(planId,
				Integer.parseInt(rank), java.time.Instant.now().getEpochSecond())));
		}
		catch (NumberFormatException ex)
		{
			respond(exchange, 400, "{\"error\":\"rank must be a number\"}");
		}
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
			AccountSnapshot snapshot = gson.fromJson(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), AccountSnapshot.class);
			if (snapshot == null) throw new IllegalArgumentException("snapshot is required");
			requirePayloadAccount(exchange, snapshot.getAccountId());
			CompanionService service = accountService(exchange);
			if (service == null) return;
			service.account(snapshot);
			respond(exchange, 202, "{\"accepted\":true}");
		}
		catch (Exception ex) { respond(exchange, 400, error(ex)); }
	}
	private void offer(HttpExchange exchange) throws IOException
	{
		if (!authorized(exchange)) return;
		try
		{
			OfferEvent event = gson.fromJson(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), OfferEvent.class);
			if (event == null) throw new IllegalArgumentException("offer is required");
			requirePayloadAccount(exchange, event.getAccountId());
			CompanionService service = accountService(exchange);
			if (service == null) return;
			SqliteStore.EventAcceptance acceptance = service.offer(event);
			respond(exchange, 202, gson.toJson(OfferEventAcknowledgement.of(event, acceptance)));
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
		exchange.getResponseHeaders().set(com.flippingfriend.core.AccountIdentity.PROTOCOL_HEADER,
			com.flippingfriend.core.AccountIdentity.PROTOCOL);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.sendResponseHeaders(code, bytes.length);
		exchange.getResponseBody().write(bytes);
		exchange.close();
	}
	@Override public void close() { server.stop(0); executor.shutdownNow(); accountExecutor.shutdownNow(); reportExecutor.shutdownNow(); }
}
