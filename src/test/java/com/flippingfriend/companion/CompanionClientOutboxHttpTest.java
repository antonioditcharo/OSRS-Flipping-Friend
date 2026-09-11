package com.flippingfriend.companion;

import com.flippingfriend.data.PluginStorage;
import com.flippingfriend.data.TestStorage;
import com.flippingfriend.session.TrackedOffer;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Black-box HTTP verification of durable companion delivery.
 */
public class CompanionClientOutboxHttpTest
{
	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	private final Gson gson = new Gson();
	private final List<String> receivedBodies = new ArrayList<>();
	private final AtomicInteger requests = new AtomicInteger();

	private HttpServer server;
	private java.util.concurrent.ExecutorService serverExecutor;
	private PluginStorage storage;
	private CompanionEventOutbox outbox;
	private CompanionClient client;

	@Before
	public void setUp() throws Exception
	{
		Path root = folder.newFolder("client-http").toPath();
		storage = TestStorage.rootedAt(root, "profile-a");
		outbox = new CompanionEventOutbox(storage);
		client = new CompanionClient(
			storage, gson, new SuggestionLedger(), outbox);

		Path tokenFile = storage.root()
			.resolve("companion")
			.resolve("companion.properties");
		Files.createDirectories(tokenFile.getParent());
		Files.write(
			tokenFile,
			"token=test-token\n".getBytes(StandardCharsets.UTF_8));

		server = HttpServer.create(
			new InetSocketAddress("127.0.0.1", 37_777), 0);
		server.createContext("/v1/events/ge-offer", this::handleOffer);

		serverExecutor = Executors.newSingleThreadExecutor();
		server.setExecutor(serverExecutor);
		server.start();
	}

	@After
	public void tearDown()
	{
		if (server != null)
		{
			server.stop(0);
		}
		if (serverExecutor != null)
		{
			serverExecutor.shutdownNow();
		}
	}

	@Test
	public void failedPostIsRetriedWithTheSameEventIdBeforeTheNextEvent()
	{
		client.publishOffer(offer(1, 100));
		assertEquals("the rejected event must remain queued", 1, outbox.size());

		client.publishOffer(offer(2, 200));

		assertEquals("the retry and new event must both be acknowledged", 0, outbox.size());
		assertEquals(
			"one rejected attempt, its retry, then the new event",
			3,
			receivedBodies.size());

		JsonObject rejected = gson.fromJson(receivedBodies.get(0), JsonObject.class);
		JsonObject retried = gson.fromJson(receivedBodies.get(1), JsonObject.class);
		JsonObject next = gson.fromJson(receivedBodies.get(2), JsonObject.class);

		String rejectedId = rejected.get("correlationId").getAsString();
		String retriedId = retried.get("correlationId").getAsString();
		String nextId = next.get("correlationId").getAsString();

		assertEquals(
			"a retry must preserve the event identifier",
			rejectedId,
			retriedId);
		assertNotEquals(
			"a genuinely new event must receive a new identifier",
			retriedId,
			nextId);

		assertEquals(1, rejected.get("slot").getAsInt());
		assertEquals(1, retried.get("slot").getAsInt());
		assertEquals(2, next.get("slot").getAsInt());
	}

	private void handleOffer(HttpExchange exchange) throws java.io.IOException
	{
		String token = exchange.getRequestHeaders()
			.getFirst("X-Flipping-Friend-Token");

		if (!"test-token".equals(token))
		{
			respond(exchange, 401);
			return;
		}

		String body = new String(
			exchange.getRequestBody().readAllBytes(),
			StandardCharsets.UTF_8);

		synchronized (receivedBodies)
		{
			receivedBodies.add(body);
		}

		respond(exchange, requests.incrementAndGet() == 1 ? 503 : 202);
	}

	private static void respond(HttpExchange exchange, int status)
		throws java.io.IOException
	{
		byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(status, body.length);
		exchange.getResponseBody().write(body);
		exchange.close();
	}

	private static TrackedOffer offer(int slot, int price)
	{
		TrackedOffer offer =
			new TrackedOffer(slot, 1603, true, price, 100, 1_000);
		offer.setItemName("Ruby");
		offer.setState("BUYING");
		offer.setQuantityFilled(10);
		offer.setSpent((long) price * 10);
		return offer;
	}
}
