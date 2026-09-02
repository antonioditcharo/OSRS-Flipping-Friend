package com.flippingfriend.session;

import com.flippingfriend.data.TestStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Items the plugin has been told to leave alone.
 * <p>
 * The two kinds mean different things and must not be confused. "Show me something else" is about
 * right now; abandoning a part-filled buy is a decision about the item, and an eight-hour cooldown
 * that evaporates the first time RuneLite closes is not a cooldown at all.
 */
public class SkipListTest
{
	private static final int SAPPHIRE_NECKLACE = 6575;
	private static final int GRAPES = 1987;

	private SkipList listAt(Path root) throws Exception
	{
		Files.createDirectories(root);
		return new SkipList(TestStorage.rootedAt(root, "rsprofile_test"));
	}

	private Path tempRoot() throws Exception
	{
		return Files.createTempDirectory("flipping-friend-skips");
	}

	@Test
	public void anAbandonedBuySurvivesARestart() throws Exception
	{
		Path root = tempRoot();
		Instant now = Instant.now();

		SkipList before = listAt(root);
		before.skipUntilCooldownEnds(SAPPHIRE_NECKLACE, now);
		before.save();

		// A fresh instance, as after closing and reopening the client.
		SkipList after = listAt(root);
		after.load();

		assertTrue("eight hours has to mean eight hours, not until the client closes",
			after.skipped(now).contains(SAPPHIRE_NECKLACE));
		assertNotNull(after.skippedUntil(SAPPHIRE_NECKLACE));
	}

	@Test
	public void aSessionSkipDoesNot() throws Exception
	{
		Path root = tempRoot();
		Instant now = Instant.now();

		SkipList before = listAt(root);
		before.skipForSession(GRAPES);
		before.save();

		SkipList after = listAt(root);
		after.load();

		assertTrue("'show me something else' is about right now, not about the item",
			!after.skipped(now).contains(GRAPES));
	}

	@Test
	public void theCooldownLapsesOnItsOwn() throws Exception
	{
		SkipList list = listAt(tempRoot());
		Instant abandonedAt = Instant.now().minus(SkipList.ABANDONED_BUY_COOLDOWN).minusSeconds(60);

		list.skipUntilCooldownEnds(SAPPHIRE_NECKLACE, abandonedAt);

		assertTrue("past the cooldown the item is available again",
			!list.skipped(Instant.now()).contains(SAPPHIRE_NECKLACE));
	}

	@Test
	public void theCooldownOutlastsTheBuyLimitWindowThatWouldOtherwiseRevive() throws Exception
	{
		// The point of eight hours: a shorter cooldown would return the item exactly when its
		// four-hour buy limit resets, which is when the planner finds it attractive again.
		assertTrue("a cooldown inside the buy-limit window would hand the item straight back",
			SkipList.ABANDONED_BUY_COOLDOWN.compareTo(BuyLimitTracker.WINDOW) > 0);
	}

	/** Eight minutes at the player's settings: max(8, checkInterval). */
	private static final long STALE_AFTER = 8;

	@Test
	public void walkingAwayFromAPartFilledBuyIsARejection()
	{
		// The live case: 6,318 of a 13,883 order taken, the rest abandoned.
		assertTrue("you took what you wanted and left the rest",
			SkipList.isRejection(6_318, 13_883, 2, STALE_AFTER));
	}

	@Test
	public void clearingAFinishedOfferIsNotARejection()
	{
		// A completed buy still reports CANCELLED_BUY when it is cleared out of the slot. Treating
		// that as "I do not want this item" would blacklist every item the plugin traded successfully.
		assertTrue("a completed order is not a complaint",
			!SkipList.isRejection(13_883, 13_883, 200, STALE_AFTER));
	}

	@Test
	public void aQuickCancelWithNothingBoughtIsTreatedAsACorrection()
	{
		assertTrue("cancelling within a couple of minutes is how a mispriced offer gets fixed",
			!SkipList.isRejection(0, 17_937, 2, STALE_AFTER));
		assertTrue("but one left standing and then pulled is a decision",
			SkipList.isRejection(0, 17_937, STALE_AFTER, STALE_AFTER));
	}

	@Test
	public void changingSettingsForgetsPreferencesButNotDecisions() throws Exception
	{
		SkipList list = listAt(tempRoot());
		Instant now = Instant.now();
		list.skipForSession(GRAPES);
		list.skipUntilCooldownEnds(SAPPHIRE_NECKLACE, now);

		list.clearSessionSkips();

		assertTrue("a new risk level reconsiders what to show next", !list.skipped(now).contains(GRAPES));
		assertTrue("but it is not a change of mind about a buy you walked away from",
			list.skipped(now).contains(SAPPHIRE_NECKLACE));
		assertEquals(1, list.skipped(now).size());
	}
}
