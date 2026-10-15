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
}
