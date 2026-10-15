package com.flippingfriend.companion;

import java.nio.file.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ExecutionEvaluationReadOnlyTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    @Test public void readOnlyPathsKeepLiteralHashCharactersAndSpaces()throws Exception {
        Path database=folder.newFolder().toPath().resolve("account #1.db");
        try(var store=new SqliteStore(database)){store.recordEvent(100,"fixture","FIXTURE","{}");}
        byte[] before=Files.readAllBytes(database);
        try(var store=SqliteStore.openReadOnly(database)){assertEquals(1,store.eventCount("FIXTURE"));}
        assertArrayEquals(before,Files.readAllBytes(database));
    }
    @Test public void missingDatabaseCannotBeCreatedAndWritesAreRejected()throws Exception {
        Path missing=folder.newFolder().toPath().resolve("missing # database.db");
        try(var ignored=SqliteStore.openReadOnly(missing)){fail("missing path opened");}
        catch(IllegalArgumentException expected){ }
        assertFalse(Files.exists(missing));
        Path database=folder.newFolder().toPath().resolve("existing % # database.db");
        try(var ignored=new SqliteStore(database)){ }
        try(var store=SqliteStore.openReadOnly(database)) {
            try{store.recordEvent(100,"fixture","WRITE","{}");fail("read-only connection accepted a write");}
            catch(java.sql.SQLException expected){assertTrue(expected.getMessage().contains("readonly"));}
            assertEquals(0,store.eventCount("WRITE"));
        }
    }
}
