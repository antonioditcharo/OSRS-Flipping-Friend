package com.flippingfriend.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.data.TestStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Wanting the counter back at zero is not the same as wanting the evidence destroyed.
 *
 * <p>The journal is the only record of how this plugin's own predictions turned out — the calibrator
 * is built from it and nothing else, and a flip that was not recorded is gone for good. So clearing
 * the all-time figures renames the file with a timestamp rather than deleting it, and putting it back
 * is a rename.
 */
public class ResettingTheFiguresKeepsTheTradesTest
{
	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	private static FlipRecord flip(long profit)
	{
		long soldAt = Instant.now().getEpochSecond();
		return new FlipRecord(4151, "Abyssal whip", 100, 1000, 1050, 200, profit,
			soldAt - 600, soldAt, 30, profit, "MODERATE");
	}

	private static TradeJournal open(Path root)
	{
		TradeJournal journal = new TradeJournal(TestStorage.rootedAt(root, "profile-a"), null,
			new AccountMonitor(null, null, null));
		journal.load();
		return journal;
	}

	@Test
	public void resettingTheSessionLeavesTheAllTimeFiguresAlone()
	{
		Path root = folder.getRoot().toPath();
		TradeJournal journal = open(root);
		journal.record(flip(5_000));
		journal.record(flip(3_000));

		journal.startSession();

		assertEquals("the session starts again", 0, journal.sessionStats().getFlips());
		assertEquals("but nothing has been forgotten", 8_000L, journal.lifetimeStats().getProfit());
		assertEquals(2, journal.lifetimeStats().getFlips());
	}

	@Test
	public void resettingAllTimeZeroesTheFiguresAndKeepsTheFile()
	{
		Path root = folder.getRoot().toPath();
		TradeJournal journal = open(root);
		journal.record(flip(5_000));
		journal.record(flip(-2_000));
		assertEquals(3_000L, journal.lifetimeStats().getProfit());

		Path archived = journal.archiveAndReset();

		assertEquals("all time is back to zero", 0L, journal.lifetimeStats().getProfit());
		assertEquals(0, journal.lifetimeStats().getFlips());
		assertEquals("and so is the session", 0, journal.sessionStats().getFlips());
		assertEquals("nothing is left in memory", 0, journal.getHistory().size());

		assertNotNull("the trades must not be deleted", archived);
		assertTrue("the archive has to actually be there", Files.exists(archived));
	}

	@Test
	public void theArchivedTradesCanBePutBack()
	{
		// The whole reason for renaming rather than deleting. Restoring is a rename, because the
		// plugin reads whatever journal.jsonl holds at load and nothing else.
		Path root = folder.getRoot().toPath();
		TradeJournal journal = open(root);
		journal.record(flip(5_000));
		Path archived = journal.archiveAndReset();

		try
		{
			Files.move(archived, archived.getParent().resolve("journal.jsonl"));
		}
		catch (Exception failed)
		{
			throw new AssertionError("could not restore the archive", failed);
		}

		assertEquals("the trades come back", 5_000L,
			open(root).lifetimeStats().getProfit());
	}

	@Test
	public void tradingAfterAResetStartsCountingAgain()
	{
		// The reset must not leave the journal in a state where new flips go nowhere: loadedFrom has
		// to be cleared too, or a later load short-circuits on a path it thinks it has already read.
		Path root = folder.getRoot().toPath();
		TradeJournal journal = open(root);
		journal.record(flip(5_000));
		journal.archiveAndReset();

		journal.record(flip(1_200));

		assertEquals(1_200L, journal.sessionStats().getProfit());
		assertEquals(1_200L, journal.lifetimeStats().getProfit());
		assertEquals("and it survives a restart", 1_200L, open(root).lifetimeStats().getProfit());
	}
}
