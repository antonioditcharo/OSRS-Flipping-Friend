package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.flippingfriend.model.*;
import java.nio.file.*;
import java.util.*;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Real callback/outbox boundaries; no HTTP or game input. */
public class ProspectiveSalePolicyCaptureTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    private long now(){return java.time.Instant.now().getEpochSecond();}
    private CommittedSalePolicyCaptureTest.Fixture fixture(Path path)throws Exception {
        var parent=new CommittedSalePolicyCaptureTest();parent.folder=folder;return parent.new Fixture(path,true);
    }
    @Test public void unreadableContinuationFileCannotEraseProspectivelyCommittedFirstCancellationProof()throws Exception {
        var path=folder.newFolder().toPath();var f=fixture(path);f.callback(GrandExchangeOfferState.SELLING,0);
        var accepted=f.accept(f.latest());assertTrue(accepted.isProspectiveCaptureRequired());f.ack();
        var saved=f.intentFile().resolveSibling("preserved-acceptance.json");Files.move(f.intentFile(),saved);Files.createDirectory(f.intentFile());
        var bytes=Files.readAllBytes(saved);f.callback(GrandExchangeOfferState.CANCELLED_SELL,2);
        var first=f.latest();assertNotNull(first.getAcceptedSalePolicyProof());assertTrue(first.getAcceptedSalePolicyProof().qualifiesFirst(first));
        assertEquals(2,first.getFilledQuantity());assertEquals(400,first.getSpent());assertNull(f.intents.acceptedPolicies(f.storage.accountId()));
        assertArrayEquals(bytes,Files.readAllBytes(saved));f.ack();
        var restart=new CompanionClient(f.storage,f.storage.gson(),new SuggestionLedger(),new OfferEventOutbox(f.storage));
        restart.setSalePolicyAcceptanceReader(a->{throw new IllegalStateException("still unreadable");});
        var late=f.tracker.getOffer(0).copyCurrentObservation();late.setQuantityFilled(3);late.setSpent(600);
        restart.captureObservedOffer(late,f.storage.accountId(),now());var pending=new OfferEventOutbox(f.storage).pending();
        assertEquals(first.getAcceptedSalePolicyProof(),pending.get(pending.size()-1).getAcceptedSalePolicyProof());
        assertEquals(600,pending.get(pending.size()-1).getSpent());
    }
    @Test public void failedOutboxAcceptanceCannotBeBorrowedFromTheContinuationFileOrRepairedAfterCancellation()throws Exception {
        var helper=new SalePolicyAcceptanceDeliveryTest();helper.folder=folder;var f=helper.new Fixture(folder.newFolder().toPath());
        var source=f.publish(f.working,helper.at);assertTrue(f.accept(source,"unstaged",false));
        var proof=f.intents.acceptedPolicies(f.storage.accountId()).get(0);assertTrue(proof.isProspectiveCaptureRequired());
        var target=f.storage.accountDir().resolve(OfferEventOutbox.FILE_NAME);var blocked=target.resolveSibling(target.getFileName()+".tmp");
        var before=Files.readAllBytes(target);Files.createDirectory(blocked);
        try{f.outbox.retainSalePolicy(proof);fail("Expected failed durable staging");}catch(Exception expected){}
        assertArrayEquals(before,Files.readAllBytes(target));Files.deleteIfExists(blocked);
        f.working.setState("CANCELLED_SELL");f.working.setQuantityFilled(2);f.working.setSpent(400);
        var cancelled=f.publish(f.working.copyCurrentObservation(),helper.at+1);assertNull(cancelled.getAcceptedSalePolicyProof());
        var snapshot=Files.readAllBytes(target);
        try{new OfferEventOutbox(f.storage).retainSalePolicy(proof);fail("No retrospective cancellation repair");}catch(IllegalStateException expected){}
        assertArrayEquals(snapshot,Files.readAllBytes(target));assertEquals(400,cancelled.getSpent());
    }
    @Test public void sameSecondCopiedCancellationCannotBorrowLaterProspectiveAcceptance()throws Exception {
        var helper=new SalePolicyAcceptanceDeliveryTest();helper.folder=folder;var f=helper.new Fixture(folder.newFolder().toPath());
        var source=f.publish(f.working,helper.at);var earlier=f.working.copyCurrentObservation();earlier.setState("CANCELLED_SELL");
        assertTrue(f.accept(source,"copied-earlier"));var first=f.publish(earlier,helper.at);
        assertNull(first.getAcceptedSalePolicyProof());f.working.setState("CANCELLED_SELL");
        assertNull(f.publish(f.working.copyCurrentObservation(),helper.at+1).getAcceptedSalePolicyProof());
    }
    @Test public void retainedAcceptanceIsIdempotentAndMalformedScopeRefusesWithoutRewriting()throws Exception {
        var f=fixture(folder.newFolder().toPath());f.callback(GrandExchangeOfferState.SELLING,0);var accepted=f.accept(f.latest());
        var target=f.storage.accountDir().resolve(OfferEventOutbox.FILE_NAME);var before=Files.readAllBytes(target);
        f.outbox.retainSalePolicy(accepted);assertArrayEquals(before,Files.readAllBytes(target));
        var wire=f.storage.gson().fromJson(Files.readString(target),com.google.gson.JsonObject.class);
        wire.getAsJsonObject("prospectiveSalePolicies").entrySet().iterator().next().getValue().getAsJsonObject()
            .getAsJsonObject("receipt").addProperty("accountId","foreign-account");
        Files.writeString(target,f.storage.gson().toJson(wire));var changed=Files.readAllBytes(target);
        try{new OfferEventOutbox(f.storage).pending();fail("Foreign prospective proof must refuse");}catch(IllegalStateException expected){}
        assertArrayEquals(changed,Files.readAllBytes(target));
    }
    @Test public void newProofCannotDowngradeByOmittingItsRequiredCaptureMarkerWhileLegacyProofStillLoads()throws Exception {
        var f=fixture(folder.newFolder().toPath());f.callback(GrandExchangeOfferState.SELLING,0);var proof=f.accept(f.latest());
        var wire=f.storage.gson().toJsonTree(proof).getAsJsonObject();assertEquals(2,wire.get("formatVersion").getAsInt());
        wire.remove("prospectiveCaptureRequired");assertFalse(f.storage.gson().fromJson(wire,AcceptedSalePolicyProof.class).isWellFormed());
        var legacy=AcceptedSalePolicyProof.accepted(proof.getReceipt(),proof.getAcceptedAt(),CaptureOrder.next());
        var old=f.storage.gson().toJsonTree(legacy).getAsJsonObject();assertFalse("Legacy payloads must not gain a default marker",old.has("prospectiveCaptureRequired"));
        assertTrue(f.storage.gson().fromJson(old,AcceptedSalePolicyProof.class).isWellFormed());
    }
    @Test public void boundFirstAcceptanceCannotBeReplacedByAnotherAcceptanceForTheSameReceipt()throws Exception {
        var f=fixture(folder.newFolder().toPath());f.callback(GrandExchangeOfferState.SELLING,0);var accepted=f.accept(f.latest());
        f.callback(GrandExchangeOfferState.SELLING,0);assertNotNull(f.latest().getAcceptedSalePolicyProof());f.ack();
        var target=f.storage.accountDir().resolve(OfferEventOutbox.FILE_NAME);var before=Files.readAllBytes(target);
        f.outbox.retainSalePolicy(accepted);assertArrayEquals(before,Files.readAllBytes(target));
        var other=AcceptedSalePolicyProof.accepted(accepted.getReceipt(),"another-acceptance",accepted.getAcceptedAt(),CaptureOrder.next()).requiringProspectiveCapture();
        try{f.outbox.retainSalePolicy(other);fail("The first acceptance must remain immutable");}catch(IllegalStateException expected){}
        assertArrayEquals(before,Files.readAllBytes(target));
    }
    @Test public void exactPresentedInstructionMustMatchVerifiedAcceptanceBeforeItCanBeStaged()throws Exception {
        var helper=new SalePolicyAcceptanceDeliveryTest();helper.folder=folder;var f=helper.new Fixture(folder.newFolder().toPath());
        var source=f.publish(f.working,helper.at);assertTrue(f.accept(source,"display-bound",false));var proofs=f.intents.acceptedPolicies(f.storage.accountId());
        var advice=Suggestion.builder(SuggestionType.MODIFY_SELL).item(561,"Rune").slot(0).price(150).quantity(10).build();
        advice.bindSaleRepriceSource(source.getEventId());advice.bindSalePolicyReceipt(proofs.get(0).getReceipt());
        assertFalse(f.client.retainPresentedSalePolicy(advice,List.of(),f.storage.accountId()));
        assertFalse(f.client.retainPresentedSalePolicy(advice,proofs,"foreign"));
        assertTrue(f.client.retainPresentedSalePolicy(advice,proofs,f.storage.accountId()));
        var renewed=Suggestion.builder(SuggestionType.MODIFY_SELL).item(561,"Rune").slot(0).price(149).quantity(10).build();
        renewed.bindSaleRepriceSource(source.getEventId());
        renewed.bindSalePolicyReceipt(SalePolicyAcceptanceDeliveryTest.receipt(f.storage.accountId(),"renewed-price",source,149,helper.at));
        assertTrue("A renewed price preserves the original workflow acceptance",f.client.retainPresentedSalePolicy(renewed,proofs,f.storage.accountId()));
        f.client.setSalePolicyAcceptanceReader(a->null);f.working.setState("CANCELLED_SELL");
        assertNotNull(f.publish(f.working.copyCurrentObservation(),helper.at+1).getAcceptedSalePolicyProof());
    }
}
