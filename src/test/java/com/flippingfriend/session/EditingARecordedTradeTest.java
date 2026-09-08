package com.flippingfriend.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.data.PluginStorage;
import com.flippingfriend.data.TestStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Correcting a trade by hand, which the history and positions panels both offer.
 *
 * <p>The Grand Exchange does not always tell the plugin the truth. An offer collected on another
 * client, a sale the tracker priced against a cost basis it had lost, a holding adopted at login
 * with no cost at all -- each leaves a row the player can see is wrong. These are the operations
 * that let them say so.
 *
 * <p>Worth testing rather than trusting, because the obvious implementation of both is wrong in a
 * way that stays hidden for a long time. {@link TradeJournal} keeps only the last
 * {@code MAX_LOADED} records in memory while the file holds every one, so writing the in-memory
 * list back deletes everything older than that window -- and the window is two thousand records,
 * which is months of trading before anyone notices the history has a floor.
 */
public class EditingARecordedTradeTest
{
	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	private static FlipRecord flip(int itemId, String name, long profit, long boughtAt, long soldAt)
	{
		return new FlipRecord(itemId, name, 100, 1000, 1050, 200, profit, boughtAt, soldAt,
			30, profit, "MODERATE");
	}

	private TradeJournal journalIn(Path root)
	{
		TradeJournal journal = new TradeJournal(TestStorage.rootedAt(root, "profile-a"), null,
			new AccountMonitor(null, null, null));
		journal.load();
		return journal;
	}

	private static int linesOnDisk(PluginStorage storage) throws Exception
	{
		return (int) Files.readAllLines(storage.accountDir().resolve("journal.jsonl")).stream()
			.filter(line -> !line.trim().isEmpty()).count();
	}

	// ------------------------------------------------------------------ deleting

	@Test
	public void deletingAFlipTakesItOffTheDiskAndOutOfTheTotals() throws Exception
	{
		Path root = folder.newFolder("data").toPath();
		PluginStorage storage = TestStorage.rootedAt(root, "profile-a");

		TradeJournal writing = new TradeJournal(storage, null, new AccountMonitor(null, null, null));
		writing.load();
		writing.record(flip(4151, "Abyssal whip", 5000, 1000, 2800));
		writing.record(flip(561, "Nature rune", 2500, 3000, 4200));

		assertTrue(writing.deleteFlip(flip(4151, "Abyssal whip", 5000, 1000, 2800)));

		assertEquals("the totals are recomputed, not patched", 2500L,
			writing.lifetimeStats().getProfit());
		assertEquals(1, writing.lifetimeStats().getFlips());
		assertEquals("and the file is what actually changed", 1, linesOnDisk(storage));

		// The proof that it is the file and not the memory: a fresh journal reading the same
		// directory sees the same thing.
		assertEquals(1, journalIn(root).getHistory().size());
	}

	@Test
	public void deletingAFlipOlderThanTheLoadedWindowKeepsEverythingElse() throws Exception
	{
		// The test this class exists for.
		//
		// history holds the last MAX_LOADED records; the file holds all of them. An implementation
		// that rewrote the file from the in-memory list would pass every other test here and quietly
		// truncate the journal to two thousand records the first time anyone deleted anything.
		Path root = folder.newFolder("data").toPath();
		PluginStorage storage = TestStorage.rootedAt(root, "profile-a");

		TradeJournal writing = new TradeJournal(storage, null, new AccountMonitor(null, null, null));
		writing.load();
		for (int i = 0; i < 2005; i++)
		{
			writing.record(flip(4151, "Abyssal whip", 10, 1000 + i, 2000 + i));
		}

		TradeJournal reloaded = journalIn(root);
		assertEquals("precondition: more records on disk than in memory", 2005,
			reloaded.lifetimeStats().getFlips());
		assertEquals(2000, reloaded.getHistory().size());

		// The very first flip, which is 2,005 records back and therefore not in the window at all.
		assertTrue(reloaded.deleteFlip(flip(4151, "Abyssal whip", 10, 1000, 2000)));

		assertEquals("one record gone, not five", 2004, reloaded.lifetimeStats().getFlips());
		assertEquals("and 2,004 still on disk", 2004, linesOnDisk(storage));
		assertEquals(20040L, reloaded.lifetimeStats().getProfit());
	}

	@Test
	public void deletingSomethingThatIsNotThereChangesNothing() throws Exception
	{
		Path root = folder.newFolder("data").toPath();
		PluginStorage storage = TestStorage.rootedAt(root, "profile-a");

		TradeJournal writing = new TradeJournal(storage, null, new AccountMonitor(null, null, null));
		writing.load();
		writing.record(flip(4151, "Abyssal whip", 5000, 1000, 2800));

		assertFalse("no match, so nothing is claimed",
			writing.deleteFlip(flip(561, "Nature rune", 1, 7, 8)));
		assertEquals(1, linesOnDisk(storage));
		assertEquals(5000L, writing.lifetimeStats().getProfit());
	}

	// ------------------------------------------------------------------ correcting

	@Test
	public void correctingAFlipReplacesItInPlace() throws Exception
	{
		Path root = folder.newFolder("data").toPath();
		PluginStorage storage = TestStorage.rootedAt(root, "profile-a");

		TradeJournal writing = new TradeJournal(storage, null, new AccountMonitor(null, null, null));
		writing.load();
		writing.record(flip(4151, "Abyssal whip", 5000, 1000, 2800));
		writing.record(flip(561, "Nature rune", 2500, 3000, 4200));

		FlipRecord corrected = new FlipRecord(4151, "Abyssal whip", 100, 1000, 1090, 200,
			9000, 1000, 2800, 30, 5000, "MODERATE");
		assertTrue(writing.updateFlip(flip(4151, "Abyssal whip", 5000, 1000, 2800), corrected));

		assertEquals("the corrected profit is what counts now", 11500L,
			writing.lifetimeStats().getProfit());
		assertEquals("and it replaced the row rather than adding one", 2, linesOnDisk(storage));

		TradeJournal reloaded = journalIn(root);
		assertEquals(11500L, reloaded.lifetimeStats().getProfit());
		assertEquals(2, reloaded.lifetimeStats().getFlips());
	}

	@Test
	public void correctingWithNothingIsRefused() throws Exception
	{
		Path root = folder.newFolder("data").toPath();
		PluginStorage storage = TestStorage.rootedAt(root, "profile-a");

		TradeJournal writing = new TradeJournal(storage, null, new AccountMonitor(null, null, null));
		writing.load();
		writing.record(flip(4151, "Abyssal whip", 5000, 1000, 2800));

		assertFalse("a null replacement must not be read as a delete",
			writing.updateFlip(flip(4151, "Abyssal whip", 5000, 1000, 2800), null));
		assertEquals(1, linesOnDisk(storage));
	}

	// ------------------------------------------------------------------ holdings

	@Test
	public void statingTheCostOfAHoldingMovesTheStopWithIt() throws Exception
	{
		Path root = folder.newFolder("data").toPath();
		PositionBook positions = new PositionBook(TestStorage.rootedAt(root, "profile-a"));
		positions.load();
		positions.recordBuy(4151, "Abyssal whip", 10, 10_000_000, Instant.now());

		Position held = positions.get(4151);
		held.setStopPrice(880_000);
		assertEquals(1_000_000, held.getAverageCost());

		// The player says it actually cost 800k each, not a million.
		assertTrue(positions.setCostAndQuantity(4151, 10, 800_000));

		assertEquals(800_000, held.getAverageCost());
		assertEquals(8_000_000L, held.getTotalCost());
		assertEquals("the stop keeps its distance below cost rather than its old number",
			704_000, held.getStopPrice());
	}

	@Test
	public void statingACostTeachesTheBookWhatAnAdoptedHoldingIsWorth() throws Exception
	{
		// A position adopted at login has no cost at all, which is the case the panel exists for:
		// until someone says what it cost, it cannot be priced and the flip cannot be scored.
		Path root = folder.newFolder("data").toPath();
		PositionBook positions = new PositionBook(TestStorage.rootedAt(root, "profile-a"));
		positions.load();
		positions.adoptExisting(4151, "Abyssal whip", 5, Instant.now());

		assertFalse("precondition: nothing is known about what it cost",
			positions.get(4151).isCostKnown());

		assertTrue(positions.setCostAndQuantity(4151, 5, 1_200_000));

		assertTrue(positions.get(4151).isCostKnown());
		assertEquals(1_200_000, positions.get(4151).getAverageCost());
	}

	@Test
	public void correctingTheCountLeavesTheAverageCostAlone() throws Exception
	{
		Path root = folder.newFolder("data").toPath();
		PositionBook positions = new PositionBook(TestStorage.rootedAt(root, "profile-a"));
		positions.load();
		positions.recordBuy(4151, "Abyssal whip", 10, 10_000_000, Instant.now());

		assertTrue(positions.setQuantity(4151, 4));

		assertEquals(4, positions.get(4151).getQuantity());
		assertEquals("shrinking the count must not re-price what is left", 1_000_000,
			positions.get(4151).getAverageCost());
		assertEquals(4_000_000L, positions.get(4151).getTotalCost());
	}

	@Test
	public void correctingTheCountToZeroRemovesTheHolding() throws Exception
	{
		Path root = folder.newFolder("data").toPath();
		PositionBook positions = new PositionBook(TestStorage.rootedAt(root, "profile-a"));
		positions.load();
		positions.recordBuy(4151, "Abyssal whip", 10, 10_000_000, Instant.now());

		assertTrue(positions.setQuantity(4151, 0));

		assertNull("the panel offers this as delete, and it has to actually delete",
			positions.get(4151));
		assertTrue(positions.isEmpty());
	}

	@Test
	public void editingAHoldingThatIsNotThereChangesNothing() throws Exception
	{
		Path root = folder.newFolder("data").toPath();
		PositionBook positions = new PositionBook(TestStorage.rootedAt(root, "profile-a"));
		positions.load();

		assertFalse(positions.setQuantity(4151, 5));
		assertFalse(positions.setCostAndQuantity(4151, 5, 1000));
		assertTrue(positions.isEmpty());
	}
}
