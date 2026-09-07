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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A bought item must never disappear because the plugin cannot currently see it.
 * <p>
 * What this pins down cost real money. {@code reconcile} deleted a position on
 * {@code inHand + onOffer <= 0} and trimmed it proportionally on anything less than the recorded
 * quantity, gated only on the bank having been opened at some point in the session. That gate is
 * worth less than it looks: the game syncs the bank container only while the bank is <em>open</em>,
 * so the flag meant "you opened your bank once" while the contents behind it were a photograph from
 * that moment. Collect to bank afterwards and a real holding was indistinguishable from a sold one.
 * <p>
 * A 1,793-unit Earth orb position bought for 2,438,480 gp was found trimmed to 297 units and 403,920
 * gp — exactly the proportional reduction — while all 1,793 sat listed for sale. Two other holdings
 * were deleted outright, and because the cost basis lives only in the book, their sales later settled
 * with nothing behind them and were dropped from the journal as unpriceable: 90 Rune nails sold for
 * 70,200 gp and left no record anywhere that the trade had happened.
 * <p>
 * So the rule these tests hold: <b>visibility is evidence of presence, never of absence.</b>
 */
public class LostPositionTest
{
	private static final int MYSTIC_SMOKE_STAFF = 21_006;
	private static final int EARTH_ORB = 575;

	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	private PositionBook bookAt(Path root)
	{
		PositionBook book = new PositionBook(TestStorage.rootedAt(root, "p"));
		book.load();
		return book;
	}

	private static Map<Integer, Integer> holding(int itemId, int quantity)
	{
		Map<Integer, Integer> map = new HashMap<>();
		map.put(itemId, quantity);
		return map;
	}

	@Test
	public void anInvisibleHoldingIsKept() throws Exception
	{
		PositionBook book = bookAt(folder.newFolder("banked").toPath());
		book.recordBuy(MYSTIC_SMOKE_STAFF, "Mystic smoke staff", 1, 1_958_619, Instant.now());

		// Collected to the bank. Not in the inventory, not listed for sale, and the bank snapshot was
		// taken before it arrived -- which is the ordinary case, not an edge one.
		book.reconcile(Collections.emptyMap(), Collections.emptyMap(), true);

		assertNotNull("a holding the plugin cannot see is still a holding", book.get(MYSTIC_SMOKE_STAFF));
		assertEquals(1, book.get(MYSTIC_SMOKE_STAFF).getQuantity());
	}

	@Test
	public void aPartlyVisibleHoldingIsNotTrimmed() throws Exception
	{
		PositionBook book = bookAt(folder.newFolder("partial").toPath());
		book.recordBuy(EARTH_ORB, "Earth orb", 1_793, 2_438_480, Instant.now());

		// The exact shape found in the wild: 297 visible, the rest banked.
		book.reconcile(holding(EARTH_ORB, 297), Collections.emptyMap(), true);

		Position position = book.get(EARTH_ORB);
		assertEquals("seeing fewer must not shrink the position", 1_793, position.getQuantity());
		assertEquals("and must not re-price what is left", 2_438_480, position.getTotalCost());
	}

	@Test
	public void aHoldingAwayInASellOfferIsKept() throws Exception
	{
		PositionBook book = bookAt(folder.newFolder("listed").toPath());
		book.recordBuy(EARTH_ORB, "Earth orb", 1_793, 2_438_480, Instant.now());

		book.reconcile(Collections.emptyMap(), holding(EARTH_ORB, 1_793), true);

		assertEquals(1_793, book.get(EARTH_ORB).getQuantity());
	}

	@Test
	public void seeingMoreThanRecordedStillCorrectsAnAdoptedHolding() throws Exception
	{
		// The safe half of reconcile, which is kept: finding more of something whose cost was never
		// known cannot be a mistake about visibility.
		PositionBook book = bookAt(folder.newFolder("more").toPath());
		book.adoptExisting(EARTH_ORB, "Earth orb", 100, Instant.now());

		book.reconcile(holding(EARTH_ORB, 250), Collections.emptyMap(), true);

		assertEquals(250, book.get(EARTH_ORB).getQuantity());
	}

	@Test
	public void aKnownCostIsNeverOverwrittenByARecount() throws Exception
	{
		// A position built from watched fills is the better record. Finding more units of it means
		// some came from elsewhere, not that the recorded buy was wrong.
		PositionBook book = bookAt(folder.newFolder("known").toPath());
		book.recordBuy(EARTH_ORB, "Earth orb", 1_793, 2_438_480, Instant.now());

		book.reconcile(holding(EARTH_ORB, 5_000), Collections.emptyMap(), true);

		assertEquals(1_793, book.get(EARTH_ORB).getQuantity());
		assertEquals(2_438_480, book.get(EARTH_ORB).getTotalCost());
	}

	@Test
	public void aSaleStillClosesAPosition() throws Exception
	{
		// The rule is "only a sale or the player closes a position" -- so a sale had better still do it.
		PositionBook book = bookAt(folder.newFolder("sold").toPath());
		book.recordBuy(MYSTIC_SMOKE_STAFF, "Mystic smoke staff", 1, 1_958_619, Instant.now());

		Position.Removal removal = book.recordSell(MYSTIC_SMOKE_STAFF, 1);

		assertEquals(1, removal.getBackedQuantity());
		assertEquals(1_958_619, removal.getCostBasis());
		assertNull("a sold-out position leaves the book", book.get(MYSTIC_SMOKE_STAFF));
	}

	@Test
	public void thePlayerCanCloseAPositionByHand() throws Exception
	{
		// The counterpart to never deleting on absence: without this, anything disposed of outside the
		// plugin would sit in the list for ever.
		PositionBook book = bookAt(folder.newFolder("dismiss").toPath());
		book.recordBuy(MYSTIC_SMOKE_STAFF, "Mystic smoke staff", 1, 1_958_619, Instant.now());

		assertTrue(book.close(MYSTIC_SMOKE_STAFF));
		assertNull(book.get(MYSTIC_SMOKE_STAFF));
		assertFalse("closing something already gone is not an error", book.close(MYSTIC_SMOKE_STAFF));
	}

	/**
	 * The second copy of the cost, so a sale that arrives with no position behind it can still be
	 * priced instead of being dropped from the journal.
	 */
	@Test
	public void aBuyLeavesItsPriceBehindEvenIfThePositionDoesNot() throws Exception
	{
		PositionBook book = bookAt(folder.newFolder("ledger").toPath());
		book.recordBuy(4824, "Rune nails", 90, 70_200, Instant.now());

		// However the position goes -- closed by hand here, deleted by an older build in the wild --
		// the price paid must outlive it.
		book.close(4824);

		assertNull(book.get(4824));
		assertEquals("780 gp each, which is what was actually paid", 780, book.recentUnitCost(4824));
	}

	@Test
	public void theCostLedgerBlendsRepeatBuys() throws Exception
	{
		PositionBook book = bookAt(folder.newFolder("blend").toPath());
		book.recordBuy(4824, "Rune nails", 100, 100_000, Instant.now());
		book.recordBuy(4824, "Rune nails", 100, 200_000, Instant.now());

		assertEquals("the blend of 1,000 and 2,000", 1_500, book.recentUnitCost(4824));
	}

	@Test
	public void aPriceFromTooLongAgoIsNotUsed() throws Exception
	{
		PositionBook book = bookAt(folder.newFolder("stale").toPath());
		book.recordBuy(4824, "Rune nails", 90, 70_200,
			Instant.now().minusSeconds(72 * 3600));

		assertEquals("a three-day-old price must not value something bought since",
			0, book.recentUnitCost(4824));
	}

	@Test
	public void theCostLedgerSurvivesARestart() throws Exception
	{
		Path root = folder.newFolder("persist").toPath();
		PositionBook before = bookAt(root);
		before.recordBuy(4824, "Rune nails", 90, 70_200, Instant.now());
		before.save();

		PositionBook after = bookAt(root);

		assertEquals(780, after.recentUnitCost(4824));
	}

	@Test
	public void nothingIsKnownAboutAnItemNeverBought() throws Exception
	{
		assertEquals(0, bookAt(folder.newFolder("empty").toPath()).recentUnitCost(4824));
	}
}
