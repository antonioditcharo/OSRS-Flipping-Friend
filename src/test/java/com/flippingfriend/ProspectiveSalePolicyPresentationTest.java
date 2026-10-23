package com.flippingfriend;

import com.flippingfriend.core.*;
import com.flippingfriend.model.*;
import java.time.Instant;
import java.util.*;
import net.runelite.api.Client;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** The real final presentation boundary must refuse an uncommitted prospective capture. */
public class ProspectiveSalePolicyPresentationTest {
    @Test public void failedProspectiveOutboxCommitWithdrawsAlreadySavedContinuationInsteadOfRecordingAdvice()throws Exception {check(false);}
    @Test public void successfulProspectiveOutboxCommitAllowsTheNormalPresentationPath()throws Exception {check(true);}
    private void check(boolean committed)throws Exception {
        var f=new AdviceDeliveryBoundaryTest.Fixture();var now=Instant.now().getEpochSecond();
        String account=AccountIdentity.fromProfileKey("fixture");
        var source=OfferEvent.builder("source",now,"SELLING").accountId(account).authenticatedObservation(true)
            .eventIdentity("source","session","standing").sequence(1).slot(0).item(561,"Rune").price(200)
            .quantities(10,0).firstSeenAt(now-3600).build();
        var receipt=com.flippingfriend.companion.SalePolicyAcceptanceDeliveryTest.receipt(account,"presentation",source,150,now);
        var accepted=AcceptedSalePolicyProof.accepted(receipt,now,CaptureOrder.next()).requiringProspectiveCapture();
        var advice=Suggestion.builder(SuggestionType.MODIFY_SELL).item(561,"Rune").slot(0).price(150).quantity(10).build();
        advice.bindSaleRepriceSource(source.getEventId());advice.bindSalePolicyReceipt(receipt);
        when(f.guide.isPaused()).thenReturn(false);when(f.guide.getSuggestion()).thenReturn(advice);
        var field=FlippingFriendPlugin.class.getDeclaredField("engine");field.setAccessible(true);var engine=(SuggestionEngine)field.get(f.plugin);
        when(engine.recordPresentedMaintenance(eq(advice),eq(account),anyLong())).thenReturn(true);
        when(engine.acceptedSalePolicies(account)).thenReturn(List.of(accepted));
        when(f.companion.retainPresentedSalePolicy(advice,List.of(accepted),account)).thenReturn(committed);
        clearInvocations(f.companion,f.guide);
        var method=FlippingFriendPlugin.class.getDeclaredMethod("recordVisibleAdvice");method.setAccessible(true);method.invoke(f.plugin);
        verify(f.companion).retainPresentedSalePolicy(advice,List.of(accepted),account);
        if(committed){verify(f.companion).recordPresentedAdvice(eq(advice),eq(account),anyInt());verify(f.guide,never()).withdrawAdvice(any());}
        else {verify(f.guide).withdrawAdvice(argThat(s->s!=null && s.getType()==SuggestionType.WAIT));
            verify(f.companion,never()).recordPresentedAdvice(any(),anyString(),anyInt());verify(f.companion).withdrawPresentedSuccessor();}
    }
}
