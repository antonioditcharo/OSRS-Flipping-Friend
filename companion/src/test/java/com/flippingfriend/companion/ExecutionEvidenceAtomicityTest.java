package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Crash boundaries in disposable stores only. No live database or network is used. */
public class ExecutionEvidenceAtomicityTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final String account=AccountIdentity.fromProfileKey("execution-atomicity");
    final long at=Instant.now().getEpochSecond()-60;
    OfferEvent event(String id,String type,int filled,long sequence) {
        return OfferEvent.builder("fixture",at+sequence*20,type).accountId(account)
            .eventIdentity(id,"session","offer").slot(0).item(561,"Nature rune").buying(true).price(100)
            .quantities(10,filled).spent(filled*100L).sequence(sequence).firstSeenAt(at)
            .recommendation("advice",100,10,0,1,.9).build();
    }
    static final class FailingStore extends SqliteStore {
        boolean failNext;
        FailingStore(Path path)throws Exception {super(path);}
        @Override synchronized void recordExecution(int item,boolean complete,double fill,double prediction,
                double open,double openPrediction)throws Exception {
            if(failNext){failNext=false;throw new IllegalStateException("fixture execution write failure");}
            super.recordExecution(item,complete,fill,prediction,open,openPrediction);
        }
    }
    @Test public void failedStatisticsWriteCannotLeaveAPermanentDeduplicationClaim()throws Exception {
        try(var store=new FailingStore(folder.newFolder().toPath().resolve("claim.db"))) {
            var terminal=event("done","BOUGHT",10,2);store.failNext=true;
            try {new ExecutionRecorder(store).record(terminal);fail("fixture failure missing");}
            catch(IllegalStateException expected){assertEquals("fixture execution write failure",expected.getMessage());}
            assertTrue(store.executionStats().isEmpty());
            assertTrue("A retry must recover the lost learning observation",new ExecutionRecorder(store).record(terminal));
            assertEquals(1,store.executionStats().get(561).observed);
        }
    }
    @Test public void canonicalTerminalRetryRecoversAccountingAndLearningTogether()throws Exception {
        try(var store=new FailingStore(folder.newFolder().toPath().resolve("offer.db"));var service=new CompanionService(new Gson(),store)) {
            service.offer(event("open","BUYING",0,1));store.failNext=true;
            var terminal=event("done","BOUGHT",10,2);
            try {service.offer(terminal);fail("fixture failure missing");}
            catch(IllegalStateException expected){assertEquals("fixture execution write failure",expected.getMessage());}
            assertEquals("The failed offer must remain retryable instead of being accepted without its learning record",
                SqliteStore.EventAcceptance.NEW,service.offer(terminal));
            assertEquals(1,store.executionStats().get(561).observed);
            assertEquals(10,store.positionProjections().get(0).getQuantity());
            assertEquals(1,store.positionEventCount());
            assertEquals(10,store.buyLimitProjections().get(0).getUsedQuantity());
            assertEquals(SqliteStore.EventAcceptance.DUPLICATE,service.offer(terminal));
            assertEquals(1,store.executionStats().get(561).observed);
        }
    }
}
