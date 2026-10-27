package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.data.PluginStorage;
import com.flippingfriend.data.TestStorage;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Fixture account files only. Exercises durable writes, legacy delivery and isolation together. */
public class ExecutionAdviceBindingLifecycleTest {
    @Test public void optionalDisplayContextIsStoredOnceAndSurvivesFinancialAcknowledgementAndRestart()throws Exception {
        Fixture f=new Fixture(folder.newFolder().toPath(),"context");
        var context=new com.flippingfriend.core.DisplayedPricingContext(f.storage.accountId(),"display","BUY",561,100,10,AT-1,
            com.flippingfriend.core.CaptureOrder.next(),500L,"modeled",null,null,java.util.List.of(),null);
        var first=f.outbox.enqueueObservedOffer((id,session,sequence)->OfferEvent.builder("fixture",AT,"BUYING")
            .accountId(f.storage.accountId()).authenticatedObservation(true).eventIdentity(id,session,"same-offer").sequence(sequence)
            .slot(0).item(561,"Nature rune").buying(true).price(100).quantities(10,0).spent(0).firstSeenAt(AT)
            .displayedPricingContext(context.bind(id,com.flippingfriend.core.CaptureOrder.next())).build());
        assertTrue(first.getDisplayedPricingContext().matches(first));
        assertTrue(f.outbox.acknowledge(first.getEventId(),first.getSessionId(),first.getSequence()));
        var restarted=new Fixture(f.storage.accountDir().getParent().getParent(),"context");
        var later=restarted.publish(AT+1,100,10,1,null,0);
        assertNull(later.getDisplayedPricingContext());assertEquals(100,later.getSpent());
        assertEquals(first.getSequence()+1,later.getSequence());assertFalse(later.isPredictionComparable());
        assertTrue(Files.readString(f.file).contains("\"sourceEventId\":\""+first.getEventId()+"\""));
    }
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    static final long AT=1_700_000_000;
    static final class Fixture {
        final PluginStorage storage;final OfferEventOutbox outbox;final Path file;
        Fixture(Path root,String profile) {
            storage=TestStorage.rootedAt(root,profile);outbox=new OfferEventOutbox(storage);
            file=storage.accountDir().resolve(OfferEventOutbox.FILE_NAME);
        }
        OfferEvent publish(long observed,int price,int quantity,int filled,String plan,double minutes)throws Exception {
            return outbox.enqueueObservedOffer((id,session,seq)-> {
                var b=OfferEvent.builder("fixture",observed,filled==quantity?"BOUGHT":"BUYING")
                    .authenticatedObservation(true).eventIdentity(id,session,"same-offer").sequence(seq)
                    .slot(0).item(561,"Nature rune").buying(true).price(price).quantities(quantity,filled)
                    .spent((long)price*filled).firstSeenAt(AT);
                if(plan!=null)b.recommendation(plan,100,10,0,minutes,.75).predictionEvidence(null,AT-1,false);
                return b.build();
            });
        }
        void clear(boolean authenticated)throws Exception {
            outbox.enqueueObservedOffer((id,session,seq)->OfferEvent.builder("fixture",AT,"EMPTY")
                .authenticatedObservation(authenticated).eventIdentity(id,session,null).sequence(seq).slot(0).build());
        }
        JsonObject json()throws Exception{return storage.gson().fromJson(Files.readString(file),JsonObject.class);}
        void write(JsonObject value)throws Exception{Files.writeString(file,storage.gson().toJson(value));}
    }
    @Test public void unknownOriginalSurvivesAcknowledgementAndRestart()throws Exception {
        Path root=folder.newFolder().toPath();Fixture f=new Fixture(root,"one");
        OfferEvent first=f.publish(AT,100,10,0,null,0);
        assertTrue(f.outbox.acknowledge(first.getEventId(),first.getSessionId(),first.getSequence()));
        var after=new Fixture(root,"one").publish(AT,100,10,0,"late",120);
        assertNull(after.getRecommendationId());assertFalse(after.isPredictionComparable());
    }
    @Test public void failedSaveRetainsChangedInputsAndCannotRequalifyTheOriginalPrediction()throws Exception {
        Fixture f=new Fixture(folder.newFolder().toPath(),"one");
        var first=f.publish(AT,100,10,0,"original",12);byte[] before=Files.readAllBytes(f.file);
        Path temp=f.file.resolveSibling(f.file.getFileName()+".lock");
        Files.deleteIfExists(temp);Files.createDirectory(temp);Path obstruction=temp.resolve("fixture");Files.writeString(obstruction,"test");
        try {f.publish(AT+1,101,10,0,"changed",120);fail("fixture write must fail");}
        catch(java.io.IOException expected){ }
        assertArrayEquals(before,Files.readAllBytes(f.file));
        Files.delete(obstruction);Files.delete(temp);
        var retry=f.publish(AT+2,100,10,10,"later",120);
        assertEquals(first.getSequence()+2,retry.getSequence());assertFalse(retry.isPredictionComparable());
        assertEquals(0,retry.getPredictedMinutes(),0);assertEquals(1000,retry.getSpent());
        var pending=new OfferEventOutbox(f.storage).pending();assertEquals(3,pending.size());
        assertEquals("A price change cannot vanish merely because its first save failed",101,pending.get(1).getPrice());
        assertEquals(first.getEventId(),pending.get(1).getPredictionSourceEventId());
    }
    @Test public void changingBackToOriginalInputsDoesNotRestoreComparability()throws Exception {
        Fixture f=new Fixture(folder.newFolder().toPath(),"one");f.publish(AT,100,10,0,"original",12);
        var changed=f.publish(AT+1,101,10,1,"new",120);
        assertFalse(changed.isPredictionComparable());assertEquals(101,changed.getSpent());
        var terminal=f.publish(AT+2,100,10,10,"original",12);
        assertFalse(terminal.isPredictionComparable());assertEquals(0,terminal.getPredictedMinutes(),0);
    }
    @Test public void onlyAuthenticatedEmptyReleasesTheSlotBinding()throws Exception {
        Fixture f=new Fixture(folder.newFolder().toPath(),"one");f.publish(AT,100,10,0,"original",12);
        f.clear(false);assertEquals("original",f.publish(AT,100,10,0,"later",120).getRecommendationId());
        f.clear(true);var replacement=f.publish(AT,100,10,0,"replacement",120);
        assertEquals("replacement",replacement.getRecommendationId());assertEquals(120,replacement.getPredictedMinutes(),0);
        assertEquals(1,f.json().getAsJsonObject("predictions").size());
    }
    @Test public void legacyQueueKeepsRawClaimsButCannotAdoptANewOriginal()throws Exception {
        for(boolean scoped:new boolean[]{true,false}) {
            Path root=folder.newFolder().toPath();Fixture f=new Fixture(root,"one");var first=f.publish(AT,100,10,0,"legacy",12);
            JsonObject old=f.json();old.remove("predictions");
            JsonObject raw=old.getAsJsonArray("pending").get(0).getAsJsonObject();
            for(String field:new String[]{"predictionEvidenceVersion","predictionSourceEventId","adviceObservedAt","predictionComparable"})raw.remove(field);
            if(!scoped){old.remove("accountId");raw.remove("accountId");}f.write(old);
            Fixture restart=new Fixture(root,"one");var restored=restart.outbox.pending().get(0);
            assertEquals(first.getEventId(),restored.getEventId());assertEquals("legacy",restored.getRecommendationId());
            assertEquals(12,restored.getPredictedMinutes(),0);
            assertTrue(restart.outbox.acknowledge(restored.getEventId(),restored.getSessionId(),restored.getSequence()));
            assertFalse(restart.publish(AT,100,10,0,"late",120).isPredictionComparable());
        }
    }
    @Test public void profileBindingsCannotLeakBetweenAccounts()throws Exception {
        Path root=folder.newFolder().toPath();Fixture one=new Fixture(root,"one"),two=new Fixture(root,"two");
        var a=one.publish(AT,100,10,0,"one",12);var b=two.publish(AT,100,10,0,"two",120);
        assertNotEquals(a.getAccountId(),b.getAccountId());assertNotEquals(a.getSessionId(),b.getSessionId());
        assertEquals(12,new Fixture(root,"one").publish(AT+10,100,10,10,"other",120).getPredictedMinutes(),0);
        assertEquals(120,new Fixture(root,"two").publish(AT+10,100,10,10,"other",12).getPredictedMinutes(),0);
    }
    @Test public void corruptBindingIsRejectedWithoutOverwritingPendingAccounting()throws Exception {
        for(String corruption:new String[]{"accountId","schemaVersion","predictionEvidenceVersion"}) {
            Fixture f=new Fixture(folder.newFolder().toPath(),"one");f.publish(AT,100,10,0,"original",12);
            JsonObject invalid=f.json();var origin=invalid.getAsJsonObject("predictions").getAsJsonObject("0").getAsJsonObject("origin");
            origin.addProperty(corruption,"2");f.write(invalid);byte[] before=Files.readAllBytes(f.file);
            try {f.outbox.pending();fail("Corrupt binding accepted: "+corruption);}
            catch(IllegalStateException expected){ }
            assertArrayEquals(before,Files.readAllBytes(f.file));
        }
    }
    @Test public void sameSecondAdviceMayRemainDiagnosticButCannotProveTemporalComparability()throws Exception {
        Fixture f=new Fixture(folder.newFolder().toPath(),"one");
        var event=f.outbox.enqueueObservedOffer((id,session,seq)->OfferEvent.builder("fixture",AT,"BUYING")
            .eventIdentity(id,session,"offer").authenticatedObservation(true).sequence(seq).slot(0)
            .item(561,"Nature rune").buying(true).price(100).quantities(10,0).firstSeenAt(AT)
            .recommendation("same-second",100,10,0,12,.75).predictionEvidence(null,AT,false).build());
        assertEquals("same-second",event.getRecommendationId());
        assertFalse("Seconds cannot establish advice came first",event.isPredictionComparable());
        assertEquals(0,event.getPredictedMinutes(),0);
    }
}
