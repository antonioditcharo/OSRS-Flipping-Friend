package com.flippingfriend.companion;

import com.flippingfriend.core.AccountSnapshot;
import com.flippingfriend.core.PositionSnapshot;
import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PositionStateConsumptionTest
{
        private static AccountSnapshot account(PositionSnapshot... positions)
        {
                return new AccountSnapshot("c", 1, 0, 0, 8, 8, true, true, 0,
                        "BALANCED", Collections.emptyMap(), 0, Collections.emptyMap(), false,
                        0, Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
                        false, 0, java.util.Arrays.asList(positions));
        }

        @Test
        public void serviceDerivesPositionsFromTheLatestAccountSnapshot() throws Exception
        {
                Path directory = Files.createTempDirectory("position-state-consumption");
                try (SqliteStore store = new SqliteStore(directory.resolve("companion.db"));
                     CompanionService service = new CompanionService(new Gson(), store))
                {
                        PositionSnapshot first = new PositionSnapshot(
                                4151, "Abyssal whip", 2, 3_000_000L,
                                true, 100, 1_600_000, 1_450_000, 42.5);
                        service.account(account(first));

                        assertEquals(1, service.positions().size());
                        assertEquals(first, service.positions().get(4151));

                        PositionSnapshot replacement = new PositionSnapshot(
                                561, "Nature rune", 1_000, 100_000L,
                                true, 200, 120, 90, 30);
                        service.account(account(replacement));

                        assertEquals(1, service.positions().size());
                        assertNull(service.positions().get(4151));
                        assertEquals(replacement, service.positions().get(561));
                }
        }

        @Test
        public void serviceStartsWithAnEmptyPositionView() throws Exception
        {
                Path directory = Files.createTempDirectory("empty-position-state");
                try (SqliteStore store = new SqliteStore(directory.resolve("companion.db"));
                     CompanionService service = new CompanionService(new Gson(), store))
                {
                        assertEquals(0, service.positions().size());
                        assertEquals(0L, service.positions().totalQuantity());
                }
        }
}
