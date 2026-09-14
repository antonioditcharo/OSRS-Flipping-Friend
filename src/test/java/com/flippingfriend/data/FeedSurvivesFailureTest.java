package com.flippingfriend.data;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The price feed has to survive a bad answer, and say so when it cannot.
 * <p>
 * This is the fault that stopped a live session dead. The three price polls are scheduled with
 * {@code scheduleWithFixedDelay}, which <b>cancels a task the moment it throws</b> and puts the
 * throwable in a {@code Future} nobody reads. They caught {@code IOException} and nothing else — so
 * one malformed number, one changed field type, one unexpected null, and the feed was over for the
 * rest of the session. The item mapping was worse: a single {@code execute} with no retry at all,
 * running while the client is still starting, and nothing in the plugin works without it.
 * <p>
 * None of it was visible. Every failure on that path logged at {@code debug} and RuneLite runs at
 * {@code info}, so the evidence was: an idle scheduler, an empty engine queue, a reachable wiki, no
 * log lines, and a sidebar that said "Getting the latest prices" for ever.
 */
public class FeedSurvivesFailureTest
{
	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	private final WikiPriceClient wiki = Mockito.mock(WikiPriceClient.class);

	private MarketDataService serviceAt(Path root)
	{
		return new MarketDataService(wiki, new Gson(), TestStorage.rootedAt(root, "p"));
	}

	private static List<ItemMetadata> mapping()
	{
		return Arrays.asList(new ItemMetadata(2361, "Adamant bar", false, 30_000, 1_000));
	}

	@Test
	public void aPollThatThrowsIsRunAgain() throws Exception
	{
		MarketDataService service = serviceAt(folder.newFolder("guard").toPath());
		AtomicInteger runs = new AtomicInteger();

		Runnable explodes = service.guarded("test feed", () ->
		{
			runs.incrementAndGet();
			throw new IllegalStateException("the far end said something unexpected");
		});

		// The scheduler calls the wrapper. If anything escapes it, the periodic task is cancelled and
		// never runs again -- so the whole contract is that this does not throw.
		explodes.run();
		explodes.run();
		explodes.run();

		assertEquals("every scheduled run has to happen", 3, runs.get());
	}

	@Test
	public void anErrorIsNotAllowedToEndTheFeedEither() throws Exception
	{
		MarketDataService service = serviceAt(folder.newFolder("error").toPath());
		// Catching Exception would leave this one to cancel the task. The far end can produce a
		// StackOverflowError out of a deeply nested payload, and the feed still has to come back.
		service.guarded("test feed", () ->
		{
			throw new StackOverflowError("nested too deep");
		}).run();
	}

	@Test
	public void theItemMappingKeepsBeingAskedForUntilItArrives() throws Exception
	{
		MarketDataService service = serviceAt(folder.newFolder("mapping").toPath());
		Mockito.when(wiki.fetchMapping())
			.thenThrow(new IOException("connection reset while the client was starting"))
			.thenReturn(mapping());

		service.refreshMetadataIfNeeded();
		assertTrue("the first attempt failed, so there is nothing yet",
			service.getSnapshot().getMetadata().isEmpty());

		service.refreshMetadataIfNeeded();
		assertEquals("and the retry is what saves the session", 1,
			service.getSnapshot().getMetadata().size());

		service.refreshMetadataIfNeeded();
		Mockito.verify(wiki, Mockito.times(2)).fetchMapping();
	}

	@Test
	public void aStuckFeedSaysWhatIsMissingRatherThanLoadingForEver()
	{
		MarketDataService service = serviceAt(folder.getRoot().toPath());

		assertNull("during the first seconds, the loading card is the honest answer",
			service.unavailableReason());

		service.guarded("latest prices", () ->
		{
			throw new IllegalStateException("wiki prices API returned 503");
		}).run();
		// A failure is reported immediately, because a failing feed is not a warm-up.
		String reason = service.unavailableReason();
		assertNotNull("once it is failing, the player has to be told", reason);
		assertTrue(reason, reason.contains("item list") || reason.contains("prices"));
	}
}
