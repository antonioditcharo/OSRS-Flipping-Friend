package com.flippingfriend.session;

import com.flippingfriend.data.TestStorage;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Reconciliation has to satisfy two things that pull in opposite directions: an item listed for sale
 * sits in no container and must not be forgotten, while an item that has genuinely gone must not be
 * remembered forever. Getting only the first right is what left a sold position showing in the panel
 * indefinitely.
 */
public class PositionBookTest
{
	private static final int MAGIC_LOGS = 1513;

	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	private PositionBook bookAt(Path root)
	{
		PositionBook book = new PositionBook(TestStorage.rootedAt(root, "p"));
		book.load();
		return book;
	}

	/**
	 * This used to assert the opposite, and the opposite was wrong.
	 * <p>
	 * "Nothing held, nothing listed" was read as "the sale completed unobserved" and the position was
	 * dropped. It is far more often "the item is in the bank": the game syncs the bank container only
	 * while the bank is open, so anything collected or banked afterwards is invisible and looks
	 * exactly the same. A 1,793-unit Earth orb holding worth 2.4m was trimmed to 297 on that reasoning
	 * and two others were deleted outright, taking their cost basis with them — which then made their
	 * eventual sales unpriceable and dropped those flips from the journal entirely.
	 * <p>
	 * The stale record this was guarding against costs the player a line in a list they can dismiss.
	 * The deletion costs them the trade. They are not comparable.
	 */
	@Test
	public void keepsAPositionItSimplyCannotSee() throws Exception
	{
		PositionBook book = bookAt(folder.newFolder("a").toPath());
		book.recordBuy(MAGIC_LOGS, "Magic logs", 10, 10_000, Instant.now());
		assertNotNull(book.get(MAGIC_LOGS));

		// In the bank, with the bank closed. Indistinguishable from sold, and not the same thing.
		book.reconcile(Collections.emptyMap(), Collections.emptyMap());

		assertNotNull("absence of evidence is not evidence of absence", book.get(MAGIC_LOGS));
		assertEquals(10, book.get(MAGIC_LOGS).getQuantity());
	}

	@Test
	public void keepsAPositionThatIsAwayInASellOffer() throws Exception
	{
		PositionBook book = bookAt(folder.newFolder("b").toPath());
		book.recordBuy(MAGIC_LOGS, "Magic logs", 10, 10_000, Instant.now());

		Map<Integer, Integer> listed = new HashMap<>();
		listed.put(MAGIC_LOGS, 10);

		// Nothing in any container, but all ten are sitting in a sell offer.
		book.reconcile(Collections.emptyMap(), listed);

		assertNotNull("items listed for sale are still held", book.get(MAGIC_LOGS));
		assertEquals(10, book.get(MAGIC_LOGS).getQuantity());
	}

	@Test
	public void countsHeldAndListedTogether() throws Exception
	{
		PositionBook book = bookAt(folder.newFolder("c").toPath());
		book.recordBuy(MAGIC_LOGS, "Magic logs", 10, 10_000, Instant.now());

		Map<Integer, Integer> holdings = new HashMap<>();
		holdings.put(MAGIC_LOGS, 4);
		Map<Integer, Integer> listed = new HashMap<>();
		listed.put(MAGIC_LOGS, 6);

		book.reconcile(holdings, listed);

		assertEquals("four in hand plus six on offer is still ten", 10,
			book.get(MAGIC_LOGS).getQuantity());
	}

	/**
	 * Also reversed, for the same reason: seeing three of ten does not mean seven were sold. It far
	 * more often means seven are banked. Trimming took the cost down proportionally with the count,
	 * so a holding could be silently repriced as well as shrunk.
	 */
	@Test
	public void doesNotShrinkToWhatHappensToBeVisible() throws Exception
	{
		PositionBook book = bookAt(folder.newFolder("d").toPath());
		book.recordBuy(MAGIC_LOGS, "Magic logs", 10, 10_000, Instant.now());

		Map<Integer, Integer> holdings = new HashMap<>();
		holdings.put(MAGIC_LOGS, 3);

		book.reconcile(holdings, Collections.emptyMap());

		assertEquals("seven in the bank are still seven owned", 10,
			book.get(MAGIC_LOGS).getQuantity());
		assertEquals("and they still cost what they cost", 10_000,
			book.get(MAGIC_LOGS).getTotalCost());
	}

	@Test
	public void willNotDeleteAPositionBeforeTheBankHasBeenSeen() throws Exception
	{
		// Logging in and never opening the bank makes a banked item look identical to a sold one.
		// Acting on that would silently destroy real positions, which is far worse than briefly
		// showing a stale one.
		PositionBook book = bookAt(folder.newFolder("f").toPath());
		book.recordBuy(MAGIC_LOGS, "Magic logs", 10, 10_000, Instant.now());

		book.reconcile(Collections.emptyMap(), Collections.emptyMap(), false);

		assertNotNull("nothing may be removed on incomplete information", book.get(MAGIC_LOGS));
		assertEquals(10, book.get(MAGIC_LOGS).getQuantity());

		// And having opened the bank once does not make it conclusive either, which is what this
		// half used to assert. The flag latches for the session while the contents behind it stop
		// updating the moment the interface closes, so "the bank has been seen" was never the same
		// claim as "the bank is being seen".
		book.reconcile(Collections.emptyMap(), Collections.emptyMap(), true);
		assertNotNull("a photograph of the bank is not a live view of it", book.get(MAGIC_LOGS));
	}

	@Test
	public void doesNotInventQuantityThatIsNotThere() throws Exception
	{
		PositionBook book = bookAt(folder.newFolder("e").toPath());
		book.recordBuy(MAGIC_LOGS, "Magic logs", 10, 10_000, Instant.now());

		Map<Integer, Integer> holdings = new HashMap<>();
		holdings.put(MAGIC_LOGS, 50);

		book.reconcile(holdings, Collections.emptyMap());

		assertEquals("a position must not grow just because more were found", 10,
			book.get(MAGIC_LOGS).getQuantity());
	}
}
