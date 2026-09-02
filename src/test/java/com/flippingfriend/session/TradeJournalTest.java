package com.flippingfriend.session;

import com.flippingfriend.data.PluginStorage;
import com.flippingfriend.data.TestStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Trade history is the one thing this plugin holds that cannot be regenerated: the price data comes
 * back from the wiki on demand, but a flip that was not recorded is gone, and with it the profit
 * figures and everything the calibrator learned. So these tests actually write to disk and read it
 * back rather than trusting the in-memory path.
 */
public class TradeJournalTest
{
	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	private static FlipRecord flip(int itemId, String name, long profit, long boughtAt, long soldAt)
	{
		return new FlipRecord(itemId, name, 100, 1000, 1050, 200, profit, boughtAt, soldAt,
			30, profit, "MODERATE");
	}

	@Test
	public void survivesARestart() throws Exception
	{
		Path root = folder.newFolder("data").toPath();
		PluginStorage storage = TestStorage.rootedAt(root, "profile-a");
		AccountMonitor monitor = new AccountMonitor(null, null, null);

		TradeJournal writing = new TradeJournal(storage, null, monitor);
		writing.load();
		writing.startSession();
		writing.record(flip(4151, "Abyssal whip", 5000, 1000, 2800));
		writing.record(flip(561, "Nature rune", 2500, 3000, 4200));

		// A completely fresh journal, as if the client had been closed and reopened.
		TradeJournal reloaded = new TradeJournal(TestStorage.rootedAt(root, "profile-a"), null, monitor);
		reloaded.load();

		assertEquals(2, reloaded.getHistory().size());
		assertEquals(7500L, reloaded.lifetimeStats().getProfit());
		assertEquals(2, reloaded.lifetimeStats().getFlips());
		assertTrue(reloaded.hasHistory());
	}

	@Test
	public void sessionAndLifetimeAreCountedSeparately() throws Exception
	{
		Path root = folder.newFolder("data").toPath();
		PluginStorage storage = TestStorage.rootedAt(root, "profile-a");
		AccountMonitor monitor = new AccountMonitor(null, null, null);

		TradeJournal first = new TradeJournal(storage, null, monitor);
		first.load();
		first.startSession();
		first.record(flip(4151, "Abyssal whip", 5000, 1000, 2800));

		TradeJournal second = new TradeJournal(TestStorage.rootedAt(root, "profile-a"), null, monitor);
		second.load();
		second.startSession();
		second.record(flip(561, "Nature rune", 2500, 3000, 4200));

		// The new session only knows about its own flip, but the lifetime figure carries both.
		assertEquals(2500L, second.sessionStats().getProfit());
		assertEquals(1, second.sessionStats().getFlips());
		assertEquals(7500L, second.lifetimeStats().getProfit());
		assertEquals(2, second.lifetimeStats().getFlips());
	}

	@Test
	public void accountsAreKeptApart() throws Exception
	{
		Path root = folder.newFolder("data").toPath();
		AccountMonitor monitor = new AccountMonitor(null, null, null);

		TradeJournal alt = new TradeJournal(TestStorage.rootedAt(root, "profile-b"), null, monitor);
		alt.load();
		alt.startSession();
		alt.record(flip(4151, "Abyssal whip", 9999, 1000, 2800));

		TradeJournal main = new TradeJournal(TestStorage.rootedAt(root, "profile-a"), null, monitor);
		main.load();

		assertEquals("one account must not see another's trades", 0, main.getHistory().size());
	}

	@Test
	public void aCorruptLineDoesNotDestroyTheRest() throws Exception
	{
		Path root = folder.newFolder("data").toPath();
		PluginStorage storage = TestStorage.rootedAt(root, "profile-a");
		AccountMonitor monitor = new AccountMonitor(null, null, null);

		TradeJournal writing = new TradeJournal(storage, null, monitor);
		writing.load();
		writing.record(flip(4151, "Abyssal whip", 5000, 1000, 2800));

		// Simulate a torn write, which an append-only file can suffer on a hard crash.
		Path journal = storage.accountDir().resolve("journal.jsonl");
		Files.write(journal, "{ this is not valid json".getBytes(), java.nio.file.StandardOpenOption.APPEND);

		TradeJournal reloaded = new TradeJournal(TestStorage.rootedAt(root, "profile-a"), null, monitor);
		reloaded.load();

		assertEquals("the good record should still load", 1, reloaded.getHistory().size());
		assertEquals(5000L, reloaded.lifetimeStats().getProfit());
	}

	@Test
	public void winsAndLossesAreBothCounted() throws Exception
	{
		Path root = folder.newFolder("data").toPath();
		AccountMonitor monitor = new AccountMonitor(null, null, null);
		TradeJournal journal = new TradeJournal(TestStorage.rootedAt(root, "profile-a"), null, monitor);
		journal.load();
		journal.startSession();

		journal.record(flip(4151, "Abyssal whip", 5000, 1000, 2800));
		journal.record(flip(561, "Nature rune", -1500, 3000, 4200));
		journal.record(flip(2, "Cannonball", 800, 5000, 6000));

		SessionStats stats = journal.sessionStats();
		assertEquals(3, stats.getFlips());
		assertEquals(2, stats.getWins());
		assertEquals(4300L, stats.getProfit());
		assertEquals(600L, stats.getTaxPaid());
	}

	@Test
	public void recentFlipsComeBackNewestFirst() throws Exception
	{
		Path root = folder.newFolder("data").toPath();
		AccountMonitor monitor = new AccountMonitor(null, null, null);
		TradeJournal journal = new TradeJournal(TestStorage.rootedAt(root, "profile-a"), null, monitor);
		journal.load();

		journal.record(flip(1, "Oldest", 100, 1000, 2000));
		journal.record(flip(2, "Middle", 200, 3000, 4000));
		journal.record(flip(3, "Newest", 300, 5000, 6000));

		List<FlipRecord> recent = journal.recentFlips(2);
		assertEquals(2, recent.size());
		assertEquals("Newest", recent.get(0).getItemName());
		assertEquals("Middle", recent.get(1).getItemName());
	}

	@Test
	public void lifetimeStatsTracksAllFlipsEvenWhenHistoryIsTruncated() throws Exception
	{
		Path root = folder.newFolder("data").toPath();
		PluginStorage storage = TestStorage.rootedAt(root, "profile-a");
		AccountMonitor monitor = new AccountMonitor(null, null, null);

		TradeJournal writing = new TradeJournal(storage, null, monitor);
		writing.load();

		// MAX_LOADED is 2000. Write 2005 records.
		int numRecords = 2005;
		for (int i = 0; i < numRecords; i++)
		{
			writing.record(flip(1, "Item", 10, 1000 + i, 2000 + i));
		}

		TradeJournal reloaded = new TradeJournal(TestStorage.rootedAt(root, "profile-a"), null, monitor);
		reloaded.load();

		assertEquals("history list should be truncated to MAX_LOADED", 2000, reloaded.getHistory().size());
		assertEquals("lifetimeStats should reflect all " + numRecords + " flips", numRecords, reloaded.lifetimeStats().getFlips());
		assertEquals(numRecords * 10L, reloaded.lifetimeStats().getProfit());
	}
}
