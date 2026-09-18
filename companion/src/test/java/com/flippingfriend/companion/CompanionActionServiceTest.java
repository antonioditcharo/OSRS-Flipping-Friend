package com.flippingfriend.companion;

import com.flippingfriend.core.CompanionAction;
import com.flippingfriend.core.CompanionActionType;
import com.flippingfriend.core.OfferEvent;
import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CompanionActionServiceTest
{
        private static OfferEvent offer(int slot, String state, boolean buying, long observedAt)
        {
                return OfferEvent.builder("c", observedAt, state)
                        .slot(slot)
                        .item(4151, "Abyssal whip")
                        .buying(buying)
                        .price(1_500_000)
                        .quantities(2, 2)
                        .sequence(1)
                        .build();
        }

        @Test
        public void serviceReturnsWaitBeforeAnOfferIsCollectable() throws Exception
        {
                Path directory = Files.createTempDirectory("companion-action-wait");
                try (SqliteStore store = new SqliteStore(directory.resolve("companion.db"));
                     CompanionService service = new CompanionService(new Gson(), store))
                {
                        CompanionAction initial = service.action();
                        assertEquals(CompanionActionType.WAIT, initial.getType());
                        assertFalse(initial.isActionable());

                        service.offer(offer(3, "BUYING", true, 100));

                        CompanionAction running = service.action();
                        assertEquals(CompanionActionType.WAIT, running.getType());
                        assertFalse(running.isActionable());
                }
        }

        @Test
        public void serviceReturnsCollectUntilTheSlotIsCleared() throws Exception
        {
                Path directory = Files.createTempDirectory("companion-action-collect");
                try (SqliteStore store = new SqliteStore(directory.resolve("companion.db"));
                     CompanionService service = new CompanionService(new Gson(), store))
                {
                        service.offer(offer(3, "BOUGHT", true, 100));

                        CompanionAction collect = service.action();
                        assertEquals(CompanionActionType.COLLECT, collect.getType());
                        assertEquals(3, collect.getSlot());
                        assertEquals(4151, collect.getItemId());
                        assertEquals(2, collect.getQuantity());
                        assertTrue(collect.isActionable());

                        service.offer(OfferEvent.builder("clear", 200, "EMPTY")
                                .slot(3)
                                .sequence(2)
                                .build());

                        CompanionAction cleared = service.action();
                        assertEquals(CompanionActionType.WAIT, cleared.getType());
                        assertFalse(cleared.isActionable());
                }
        }
}
