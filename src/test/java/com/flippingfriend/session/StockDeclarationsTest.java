package com.flippingfriend.session;

import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import java.nio.file.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class StockDeclarationsTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private PositionProjection row() { return PositionProjection.reconcile(100, "Example supplies", 3, 0, false, 10, 20, null, null, "unknown cost"); }
    @Test public void reviewsSurviveRestartRemainScopedAndDoNotChangeAccountingFiles() throws Exception {
        Path root = folder.newFolder().toPath();
        PluginStorage first = TestStorage.rootedAt(root, "one");
        Path records = first.accountDir();
        for (String name : new String[]{"positions.json", "journal.jsonl", "offers.json", "flipping-friend.db"})
            Files.writeString(records.resolve(name), "preserve-" + name);
        StockDeclarations review = new StockDeclarations(first);
        review.record(first.accountId(), 100, new StockDeclaration(row(), StockDeclaration.Use.PERSONAL));
        assertTrue(new StockDeclarations(first).read().get(100).matches(row()));
        assertTrue(new StockDeclarations(TestStorage.rootedAt(root, "two")).read().isEmpty());
        for (String name : new String[]{"positions.json", "journal.jsonl", "offers.json", "flipping-friend.db"})
            assertEquals("preserve-" + name, Files.readString(records.resolve(name)));
        review.record(first.accountId(), 100, null); assertTrue(review.read().isEmpty());
    }
    @Test public void wrongAccountAndMalformedSavedDeclarationsFailClosed() throws Exception {
        PluginStorage storage = TestStorage.rootedAt(folder.newFolder().toPath(), "one");
        StockDeclarations review = new StockDeclarations(storage);
        try { review.record(AccountIdentity.fromProfileKey("two"), 100, new StockDeclaration(row(), StockDeclaration.Use.PERSONAL)); fail(); }
        catch (IllegalStateException expected) { }
        assertFalse(Files.exists(storage.accountDir().resolve("stock-declarations.json")));
        Files.writeString(storage.accountDir().resolve("stock-declarations.json"), "malformed");
        assertTrue(review.read().isEmpty());
        try { review.record(storage.accountId(), 100, new StockDeclaration(row(), StockDeclaration.Use.PERSONAL)); fail(); }
        catch (Exception expected) { }
        assertEquals("malformed", Files.readString(storage.accountDir().resolve("stock-declarations.json")));
    }
    @Test public void legacyReviewsRemainPreservedWithoutPricingPersonalUnitsFromANewPurchase() throws Exception {
        PluginStorage storage = TestStorage.rootedAt(folder.newFolder().toPath(), "one");
        StockDeclarations review = new StockDeclarations(storage);
        review.record(storage.accountId(), 100, new StockDeclaration(row(), StockDeclaration.Use.PERSONAL));
        PositionBook positions = new PositionBook(storage);
        positions.adoptExisting(100, "Example supplies", 3, java.time.Instant.ofEpochSecond(10));
        assertTrue(review.excludesLocal(positions.get(100)));
        assertEquals(3, positions.get(100).getQuantity());
        positions.recordBuy(100, "Example supplies", 1, 100, java.time.Instant.ofEpochSecond(30));
        assertTrue(review.excludesLocal(positions.get(100)));
        assertFalse(positions.get(100).isCostKnown());
        assertEquals(3, positions.get(100).getQuantity());
    }
}
