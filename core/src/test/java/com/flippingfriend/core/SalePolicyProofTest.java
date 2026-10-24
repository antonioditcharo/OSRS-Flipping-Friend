package com.flippingfriend.core;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.HashMap;
import org.junit.Test;
import static org.junit.Assert.*;

public class SalePolicyProofTest {
    @Test public void existingVersionNineAcceptanceRemainsReadableWhileNewReceiptsUseTen()throws Exception {
        var r=receipt();assertEquals("companion-sale-10",r.getPolicyVersion());
        var version=SalePolicyReceipt.class.getDeclaredField("policyVersion");version.setAccessible(true);version.set(r,"companion-sale-9");
        assertTrue(r.isWellFormed());assertTrue(r.matchesSource(original()));
        assertTrue(AcceptedSalePolicyProof.accepted(r,"legacy",at,CaptureOrder.next()).isWellFormed());
        version.set(r,"companion-sale-8");assertFalse(r.isWellFormed());
    }
    private final long placed=1_800_000_000L,at=placed+600;
    private final String account=AccountIdentity.fromProfileKey("sale-policy-proof");
    private AccountSnapshot settings(int cadence,boolean windDown) {
        var readSet=new HashMap<Integer,String>();for(int i=0;i<8;i++)readSet.put(i,"account-source-"+i);
        return new AccountSnapshot("account-capture",at,1_000_000,0,7,8,true,false,0,"MODERATE",
            Collections.emptyMap(),0,Collections.emptyMap(),true,cadence,Collections.emptySet(),
            Collections.emptySet(),Collections.emptySet(),windDown,120).withAccountId(account).withOfferReadSet(readSet);
    }
    private OfferEvent event(String id,String episode,long sequence,long observed,String state,int filled,long spent) {
        return OfferEvent.builder("source-capture",observed,state).accountId(account).authenticatedObservation(true)
            .eventIdentity(id,"session",episode).sequence(sequence).slot(0).item(561,"Nature rune").buying(false)
            .price(150).quantities(10,filled).spent(spent).firstSeenAt(placed).build();
    }
    private OfferEvent original(){return event("original","episode",10,at,"SELLING",2,300);}
    private SalePolicyReceipt receipt(){return receipt(settings(5,false),original(),placed,at,false);}
    private SalePolicyReceipt receipt(AccountSnapshot settings,OfferEvent source,long progress,long decided,boolean cut) {
        return SalePolicyReceipt.issue("receipt",17,"a".repeat(64),settings,source,progress,140,
            source.getTotalQuantity()-source.getFilledQuantity(),cut,decided,Math.max(480L,settings.getCheckIntervalMinutes()*60L));
    }
    private AcceptedSalePolicyProof accepted(){return AcceptedSalePolicyProof.accepted(receipt(),"accepted",at,CaptureOrder.next());}
    @Test public void originalCadenceIsFrozenAndCurrentSettingsCannotSubstituteForIt() {
        var receipt=receipt();assertTrue(receipt.matchesSettings(settings(5,false)));
        assertFalse(receipt.matchesSettings(settings(480,false)));assertEquals(480,receipt.getPatienceSeconds());
        assertEquals(5,receipt.getCheckIntervalMinutes());assertEquals(8,receipt.getOfferReadSet().size());
        assertTrue(receipt.matchesSource(original()));
        assertThrows(IllegalArgumentException.class,()->receipt(settings(480,false),original(),placed,at,false));
        var later=event("later","episode",11,placed+28_800,"SELLING",2,300);
        var readSet=new HashMap<Integer,String>();for(int i=0;i<8;i++)readSet.put(i,"later-"+i);
        var settings=new AccountSnapshot("later-capture",later.getObservedAt(),1_000_000,0,7,8,true,false,0,"MODERATE",
            Collections.emptyMap(),0,Collections.emptyMap(),true,480,Collections.emptySet(),Collections.emptySet(),
            Collections.emptySet(),false,120).withAccountId(account).withOfferReadSet(readSet);
        assertEquals(28_800,receipt(settings,later,placed,later.getObservedAt(),false).getPatienceSeconds());
    }
    @Test public void receiptKeepsExactSourcesAndOrderWithoutRequiringANewerCallbackToBeInTheOlderReadSet() {
        var receipt=receipt();assertNotEquals(receipt.getSourceEventId(),receipt.getOfferReadSet().get(0));
        assertTrue(receipt.matchesSource(original()));assertTrue(receipt.matchesOrder(561,140,8));
        assertFalse(receipt.matchesOrder(561,140,9));
        assertFalse(receipt.matchesSource(event("different","episode",10,at,"SELLING",2,300)));
        assertFalse(receipt.matchesEpisode(event("next","reused-slot",11,at+1,"SELLING",2,300)));
        assertFalse(receipt.matchesEpisode(original().withAccountId(AccountIdentity.fromProfileKey("other"))));
        assertThrows(UnsupportedOperationException.class,()->receipt.getOfferReadSet().put(0,"changed"));
    }
    @Test public void sameSecondAcceptanceRequiresALaterCopiedObservationAndAllowsLateFills() {
        var earlier=CaptureOrder.next();var acceptance=accepted();
        var cancelled=event("cancelled","episode",11,at,"CANCELLED_SELL",4,600);
        assertNull(acceptance.captured(earlier).bindTo(cancelled));
        assertNull(acceptance.captured(CaptureOrder.next()).bindTo(original()));
        var bound=acceptance.captured(CaptureOrder.next()).bindTo(cancelled);
        assertNotNull(bound);assertTrue(bound.qualifiesFirst(cancelled));assertTrue(bound.hasSameAcceptance(acceptance));
        assertEquals(4,bound.getFilledQuantity());assertEquals(600,bound.getSpent());
        assertFalse(bound.matchesEpisode(event("regressed","episode",12,at+1,"CANCELLED_SELL",3,450)));
        assertTrue(bound.matchesEpisode(event("late-fill","episode",12,at+1,"CANCELLED_SELL",5,750)));
    }
    @Test public void restartOrderingCannotInventSameSecondAcceptance() throws Exception {
        var acceptance=accepted();
        Constructor<CaptureOrder> constructor=CaptureOrder.class.getDeclaredConstructor(String.class,long.class);
        constructor.setAccessible(true);var restarted=constructor.newInstance("new-process",1L);
        var captured=acceptance.captured(restarted);
        assertNull(captured.bindTo(event("cancelled","episode",11,at,"CANCELLED_SELL",2,300)));
        assertNotNull(captured.bindTo(event("cancelled-later","episode",11,at+1,"CANCELLED_SELL",2,300)));
    }
    @Test public void decisionClockCanReachPatienceAfterFreshSourceWhileActualProgressKeepsItsOwnClock() {
        assertThrows(IllegalArgumentException.class,()->receipt(settings(5,false),original(),at-100,at,false));
        var tooYoung=event("new","episode",10,placed+400,"SELLING",2,300);
        var recentSettings=new AccountSnapshot("fresh",placed+480,0,0,7,8,true,false,0,"MODERATE",
            Collections.emptyMap(),0,Collections.emptyMap(),true,5,Collections.emptySet(),Collections.emptySet(),
            Collections.emptySet(),false,120).withAccountId(account).withOfferReadSet(settings(5,false).getOfferReadSet());
        assertEquals(480,receipt(recentSettings,tooYoung,placed,placed+480,false).getPatienceSeconds());
        assertThrows(IllegalArgumentException.class,()->receipt(recentSettings,tooYoung,placed+1,placed+480,false));
        assertThrows(IllegalArgumentException.class,()->AcceptedSalePolicyProof.accepted(receipt(),at-1,CaptureOrder.next()));
        assertThrows(IllegalArgumentException.class,()->AcceptedSalePolicyProof.accepted(receipt(),at+11,CaptureOrder.next()));
    }
    @Test public void stopAndWindDownRemainDistinctRecordedModesRatherThanRoutineAgeClaims() {
        assertEquals(SalePolicyReceipt.Mode.STOP,receipt(settings(480,false),original(),at,at,true).getMode());
        assertEquals(SalePolicyReceipt.Mode.WIND_DOWN,receipt(settings(480,true),original(),at,at,false).getMode());
        assertEquals(SalePolicyReceipt.Mode.ROUTINE,receipt().getMode());
    }
    @Test public void acceptedContextSurvivesEventCopiesWithoutChangingFinancialOrOriginalPredictionFields() {
        var raw=event("cancelled","episode",11,at+1,"CANCELLED_SELL",4,600);
        var proof=accepted().captured(CaptureOrder.next()).bindTo(raw);assertNotNull(proof);
        var event=raw.withAcceptedSalePolicyProof(proof).withAccountId(account);
        assertEquals(proof,event.getAcceptedSalePolicyProof());assertEquals(raw.getEventId(),event.getEventId());
        assertEquals(raw.getFilledQuantity(),event.getFilledQuantity());assertEquals(raw.getSpent(),event.getSpent());
        assertEquals("1",event.getSalePolicyEvidenceVersion());
        assertEquals("1",event.withAcceptedSalePolicyProof(null).getSalePolicyEvidenceVersion());
        assertNull(raw.getSalePolicyEvidenceVersion());
        assertNull(event.getRecommendationId());assertNull(event.getExitContext());assertFalse(event.isPredictionComparable());
    }
    @Test public void perObservationSaleVersionCannotBeErasedByAnOlderPlacementOrMissingProof() {
        var raw=original();var marked=raw.withSalePolicyEvidenceVersion("1");
        assertNull(raw.getSalePolicyEvidenceVersion());assertEquals("1",marked.withAccountId(account).getSalePolicyEvidenceVersion());
        assertEquals("1",marked.withAcceptedSalePolicyProof(null).getSalePolicyEvidenceVersion());
        assertEquals("2",marked.withSalePolicyEvidenceVersion("2").withAccountId(account).getSalePolicyEvidenceVersion());
    }
    private OfferContinuation hint() {
        return OfferContinuation.advise(new OfferContinuation.Observation(0,561,false,150,10,2,placed,"episode","SELLING",false),
            Collections.emptyList(),true,at,0);
    }
    @Test public void sourceBindingPersistsExactAcceptanceAndDoesNotRetrofitLegacyHints() {
        var accepted=accepted();var hint=hint();
        hint.bindSaleRepriceSource("original",placed,at,accepted);assertTrue(hint.isWellFormed());
        assertSame(accepted,hint.getAcceptedSalePolicyProof());
        var replacement=AcceptedSalePolicyProof.accepted(receipt(),"different",at,CaptureOrder.next());
        hint.bindSaleRepriceSource("original",placed,at,replacement);assertSame(accepted,hint.getAcceptedSalePolicyProof());
        var legacy=hint();legacy.bindSaleRepriceSource("original",placed,at);
        legacy.bindSaleRepriceSource("original",placed,at,accepted);assertNull(legacy.getAcceptedSalePolicyProof());
        assertTrue(legacy.isWellFormed());
        assertThrows(IllegalArgumentException.class,()->hint().bindSaleRepriceSource("original",placed+1,at,accepted));
    }
    @Test public void receiptOnlyAttachesToItsMatchingFinalSaleDecision() {
        var decision=PolicyDecision.action("1",SalePolicyReceipt.POLICY_VERSION,"decision",PolicyDecisionType.SELL_MAINTENANCE,
            OfferLifecycleAction.CANCEL_SELL,"SALE_AUTHORIZED",at,at,"candidate","original");
        assertEquals(receipt(),decision.withSalePolicyReceipt(receipt()).getSalePolicyReceipt());
        assertNull(decision.getSalePolicyReceipt());
        var other=PolicyDecision.action("1",SalePolicyReceipt.POLICY_VERSION,"decision",PolicyDecisionType.EXIT,
            OfferLifecycleAction.PLACE_SELL,"SALE_AUTHORIZED",at,at,"candidate","original");
        assertThrows(IllegalArgumentException.class,()->other.withSalePolicyReceipt(receipt()));
    }
    @Test public void freshlyRevalidatedRetryRetainsTheOriginalReceiptClockAndCannotExtendItsFiveSecondLifetime() {
        var original=receipt();
        for(long delay:new long[]{0,1,5}) {
            var current=PolicyDecision.action("1",SalePolicyReceipt.POLICY_VERSION,"retry",PolicyDecisionType.SELL_MAINTENANCE,
                OfferLifecycleAction.CANCEL_SELL,"SALE_AUTHORIZED",at+delay,at,"candidate","original");
            assertSame(original,current.withSalePolicyReceipt(original).getSalePolicyReceipt());
            assertEquals(at,original.getDecidedAt());
        }
        for(long delay:new long[]{-1,6}) {
            var current=PolicyDecision.action("1",SalePolicyReceipt.POLICY_VERSION,"retry",PolicyDecisionType.SELL_MAINTENANCE,
                OfferLifecycleAction.CANCEL_SELL,"SALE_AUTHORIZED",at+delay,at+delay,"candidate","original");
            assertThrows(IllegalArgumentException.class,()->current.withSalePolicyReceipt(original));
        }
    }
    @Test public void malformedVersionAndMissingReferenceNeverFallBackToLegacyAuthority() throws Exception {
        var receipt=receipt();var version=SalePolicyReceipt.class.getDeclaredField("formatVersion");version.setAccessible(true);
        version.setInt(receipt,2);assertFalse(receipt.isWellFormed());assertFalse(receipt.matchesSource(original()));
        assertThrows(IllegalArgumentException.class,()->AcceptedSalePolicyProof.accepted(receipt,at,CaptureOrder.next()));
        var proof=accepted();var proofVersion=AcceptedSalePolicyProof.class.getDeclaredField("formatVersion");proofVersion.setAccessible(true);
        proofVersion.setInt(proof,2);assertFalse(proof.isWellFormed());
        assertNull(proof.captured(CaptureOrder.next()).bindTo(event("next","episode",11,at+1,"SELLING",2,300)));
    }
}
