package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class BuyLimitObservationAccountingTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private OfferEvent buy(int filled,long seq) {
        return OfferEvent.builder("fixture",1700000000L+seq,filled==10?"BOUGHT":"BUYING")
            .eventIdentity("event-"+seq,"session","buy").slot(0).item(561,"Nature rune")
            .buying(true).price(100).quantities(10,filled).spent(filled*100L).sequence(seq).build();
    }
    @Test public void repeatedCountersDoNotConsumeTheLimitAgainAcrossRestart() throws Exception {
        Path db=folder.newFolder().toPath().resolve("fixture.db");
        try (SqliteStore store=new SqliteStore(db)) {
            store.recordAndProjectOffer(buy(0,1),"fixture");
            store.recordAndProjectOffer(buy(4,2),"fixture");
            store.recordAndProjectOffer(buy(4,3),"fixture");
            assertEquals(4,store.buyLimitProjections().get(0).getUsedQuantity());
        }
        try (SqliteStore store=new SqliteStore(db)) {
            store.recordAndProjectOffer(buy(4,4),"fixture");
            store.recordAndProjectOffer(buy(10,5),"fixture");
            store.recordAndProjectOffer(buy(10,6),"fixture");
            assertEquals(10,store.buyLimitProjections().get(0).getUsedQuantity());
            assertEquals(2,store.buyLimitEventCount());
        }
    }
    @Test public void initiallyMissedFillsCountAsLimitEvidenceButAreNotReadded() throws Exception {
        try (SqliteStore store=new SqliteStore(folder.newFolder().toPath().resolve("fixture.db"))) {
            store.recordAndProjectOffer(buy(4,1),"fixture");
            store.recordAndProjectOffer(buy(4,2),"fixture");
            store.recordAndProjectOffer(buy(6,3),"fixture");
            assertEquals(6,store.buyLimitProjections().get(0).getUsedQuantity());
            assertEquals(2,store.buyLimitEventCount());
        }
    }
}
