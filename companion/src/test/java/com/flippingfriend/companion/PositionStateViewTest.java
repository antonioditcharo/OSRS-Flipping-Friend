package com.flippingfriend.companion;

import com.flippingfriend.core.AccountSnapshot;
import com.flippingfriend.core.PositionSnapshot;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PositionStateViewTest
{
        private static AccountSnapshot account(PositionSnapshot... positions)
        {
                return new AccountSnapshot("c", 1, 0, 0, 8, 8, true, true, 0,
                        "BALANCED", Collections.emptyMap(), 0, Collections.emptyMap(), false,
                        0, Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
                        false, 0, Arrays.asList(positions));
        }

        @Test
        public void summarizesValidatedPositionState()
        {
                PositionSnapshot known = new PositionSnapshot(
                        4151, "Abyssal whip", 10, 15_000_000L,
                        true, 100, 1_600_000, 1_450_000, 42.5);
                PositionSnapshot unknown = new PositionSnapshot(
                        561, "Nature rune", 2_000, 0,
                        false, 200, 120, 90, 30);

                PositionStateView view = PositionStateView.from(account(known, unknown));

                assertEquals(2, view.size());
                assertEquals(2_010L, view.totalQuantity());
                assertEquals(15_000_000L, view.knownCostCapital());
                assertEquals(1, view.unknownCostPositions());
                assertEquals(known, view.get(4151));
                assertEquals(unknown, view.get(561));
        }

        @Test
        public void ignoresInvalidEntriesAndLetsTheLatestDuplicateWin()
        {
                PositionSnapshot first = new PositionSnapshot(
                        4151, "Old", 1, 1_000_000L,
                        true, 100, 0, 0, 0);
                PositionSnapshot latest = new PositionSnapshot(
                        4151, "Latest", 3, 6_000_000L,
                        true, 200, 0, 0, 0);
                PositionSnapshot invalidItem = new PositionSnapshot(
                        0, "Invalid", 5, 500, true, 0, 0, 0, 0);
                PositionSnapshot empty = new PositionSnapshot(
                        561, "Empty", 0, 0, false, 0, 0, 0, 0);

                PositionStateView view = PositionStateView.from(
                        account(first, null, invalidItem, empty, latest));

                assertEquals(1, view.size());
                assertEquals(3L, view.totalQuantity());
                assertEquals(6_000_000L, view.knownCostCapital());
                assertEquals(0, view.unknownCostPositions());
                assertEquals("Latest", view.get(4151).getItemName());
                assertNull(view.get(561));
        }

        @Test(expected = UnsupportedOperationException.class)
        public void exposedCollectionIsReadOnly()
        {
                PositionSnapshot position = new PositionSnapshot(
                        4151, "Abyssal whip", 1, 1_500_000L,
                        true, 100, 0, 0, 0);

                PositionStateView.from(account(position)).all().clear();
        }

        @Test
        public void nullAccountProducesAnEmptyView()
        {
                PositionStateView view = PositionStateView.from(null);

                assertEquals(0, view.size());
                assertEquals(0L, view.totalQuantity());
                assertEquals(0L, view.knownCostCapital());
                assertEquals(0, view.unknownCostPositions());
                assertNull(view.get(4151));
        }
}
