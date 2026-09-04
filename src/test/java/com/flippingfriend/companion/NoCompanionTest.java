package com.flippingfriend.companion;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.data.TestStorage;
import com.flippingfriend.model.Explainer;
import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionType;
import com.flippingfriend.model.TaxCalculator;
import com.flippingfriend.session.TrackedOffer;
import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * What happens to somebody who installs this from the Plugin Hub and never runs the companion.
 *
 * <p>Which is nearly all of them. The companion is a second Java process started from a batch file;
 * a player who clicks Install in the client's plugin list has none of that, and if the plugin needs
 * it the plugin is broken for almost everyone who ever sees it. This is the largest single risk to a
 * first release, and it was being carried on the strength of a code path that <em>looked</em> like it
 * would work.
 *
 * <p>{@link com.flippingfriend.data.CompanionSeedTest} already covers the data half, with a mocked
 * client returning null. That proves the handling and not the client: a mock cannot show whether the
 * real thing returns null, throws, or sits on a socket for three quarters of a second on the game
 * thread. So everything here uses a real {@link CompanionClient} with nothing behind it — which in a
 * test is not a simulation of the Hub user's situation, it <em>is</em> it.
 *
 * <p>The plugin's decision chain ends in three lines: if the companion's answer is a BUY use it,
 * else if there is a fresh plan use that, else run the built-in engine. A Hub user has to land on the
 * third. What is pinned below is each condition that sends them there, because if any one of them
 * came out the other way the player would be looking at <em>&ldquo;Companion unavailable — no new buy
 * will be suggested&rdquo;</em> for ever, on a plugin that works perfectly well without it.
 */
public class NoCompanionTest
{
	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	private CompanionClient clientAt(Path root)
	{
		return new CompanionClient(TestStorage.rootedAt(root, "profile"), new Gson(),
			new SuggestionLedger(), new TaxCalculator());
	}

	/**
	 * The Hub user: the plugin installed, the companion never run, so no token was ever written.
	 *
	 * <p>A fresh directory each time, because two clients in one test would otherwise collide on the
	 * folder name — and because a client that has never seen a companion must behave the same on its
	 * first call as on its hundredth.
	 */
	private int roots;

	private CompanionClient neverRun() throws IOException
	{
		return clientAt(folder.newFolder("never-run-" + roots++).toPath());
	}

	/**
	 * The other absence, and the worse one for latency: the companion has run at some point, so a
	 * token exists, but nothing is listening now. Every call attempts a real connection.
	 */
	private CompanionClient ranOnceThenStopped() throws IOException
	{
		Path root = folder.newFolder("stopped-" + roots++).toPath();
		Path companion = root.resolve("profile").resolve("companion");
		Files.createDirectories(companion);
		Files.write(companion.resolve("companion.properties"),
			"token=abc123\n".getBytes(StandardCharsets.UTF_8));
		return clientAt(root);
	}

	private static Suggestion askForABuy(CompanionClient client)
	{
		return client.nextBuySuggestion(new Explainer(), Collections.<String>emptySet(),
			Collections.<Integer>emptySet(), Collections.<Integer>emptySet());
	}

	// --- the three conditions the plugin branches on ---

	@Test
	public void thereIsNoFreshPlan()
	{
		// The second branch. If this were true the plugin would show the companion's answer, which
		// with no companion is a "waiting" message, and would never reach its own engine.
		assertFalse(new CompanionClient(null, new Gson(), new SuggestionLedger(), new TaxCalculator())
			.hasFreshPlan());
	}

	@Test
	public void askingForAPlanReturnsNothingRatherThanThrowing() throws IOException
	{
		assertNull("no companion means no plan, not an exception on the game thread",
			neverRun().refreshPlan());
		assertNull(ranOnceThenStopped().refreshPlan());
	}

	@Test
	public void askingForABuyReturnsSomethingThatIsNotABuy() throws IOException
	{
		// The first branch. The answer has to be a real Suggestion — a null here would be a
		// NullPointerException in the plugin one line later, where getType() is called on it — and it
		// has to not be a BUY, or the plugin would act on advice that came from nothing.
		for (CompanionClient client : new CompanionClient[]{neverRun(), ranOnceThenStopped()})
		{
			Suggestion answer = askForABuy(client);

			assertNotNull("the plugin calls getType() on this without a null check", answer);
			assertNotEquals("a companion that is not running must not appear to recommend a trade",
				SuggestionType.BUY, answer.getType());
		}
	}

	@Test
	public void everySuggestionPathAgreesSoTheFallbackIsReached() throws IOException
	{
		// The three conditions together. This is the whole property: with no companion, the plugin's
		// chain necessarily arrives at its own engine.
		CompanionClient client = neverRun();

		assertNotEquals(SuggestionType.BUY, askForABuy(client).getType());
		assertFalse(client.hasFreshPlan());
		// ... therefore engine.buyFallback(), which must still be there to be reached.
		assertTrue("the escape hatch must remain a public entry point",
			java.util.Arrays.stream(
					com.flippingfriend.model.SuggestionEngine.class.getDeclaredMethods())
				.anyMatch(m -> m.getName().equals("buyFallback")
					&& java.lang.reflect.Modifier.isPublic(m.getModifiers())));
	}

	// --- nothing may throw, and nothing may hang ---

	@Test
	public void publishingFromAGameEventNeverThrows() throws IOException
	{
		// publishOffer is called from onGrandExchangeOfferChanged. An exception escaping there is not
		// a missing feature, it is an error dialog every time the player touches the Exchange.
		for (CompanionClient client : new CompanionClient[]{neverRun(), ranOnceThenStopped()})
		{
			TrackedOffer offer = new TrackedOffer(1, 4151, true, 1_000_000, 10, 1_700_000_000L);
			client.publishOffer(offer);
			client.publishOffer(null);
			client.clearIncumbent();
			client.recordSellAdvice(null);
		}
	}

	@Test
	public void theMarketFeedIsAbsentRatherThanBroken() throws IOException
	{
		assertNull(neverRun().fetchMarketFeed());
		assertNull(ranOnceThenStopped().fetchMarketFeed());
	}

	@Test
	public void noHistoryFromTheCompanionIsNullRatherThanEmpty() throws IOException
	{
		// Null on purpose, and it is what routes a Hub user to the wiki. MarketDataService reads
		// `if (candles == null) candles = client.fetchTimeseries(...)`, so null means "no answer, ask
		// the internet" while an empty list would mean "this item genuinely has no history" and would
		// be cached as such. Two different facts, and collapsing them would leave every item silently
		// historyless for anyone without a companion — which is everyone installing from the Hub.
		assertNull(neverRun().fetchSeries(4151, "5m"));
		assertNull(ranOnceThenStopped().fetchSeries(4151, "5m"));
	}

	@Test
	public void theWholeCycleIsFastEnoughToRunOnTheGameThread() throws IOException
	{
		// The failure a mock cannot show. These calls happen on the client thread, and a connect
		// timeout of three quarters of a second each would be a visible stutter every cycle — worse
		// on a machine where a firewall drops the packet instead of refusing it.
		//
		// Generous by an order of magnitude, because this is a wall-clock assertion on a shared
		// machine and a flaky test is worse than none. What it is really guarding against is somebody
		// raising the timeout, or a retry loop appearing.
		CompanionClient client = ranOnceThenStopped();

		long start = System.nanoTime();
		client.refreshPlan();
		askForABuy(client);
		client.fetchMarketFeed();
		client.publishOffer(new TrackedOffer(1, 4151, true, 1_000_000, 10, 1_700_000_000L));
		long millis = (System.nanoTime() - start) / 1_000_000;

		assertTrue("a full cycle against a dead companion took " + millis + "ms", millis < 10_000);
	}
}
