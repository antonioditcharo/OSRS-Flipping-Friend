package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionType;
import java.time.Instant;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** The actual waiting card must explain durable risk without converting uncertainty into loss. */
public class RiskPausePresentationTest {
    final String account=AccountIdentity.fromProfileKey("pause-explanation");
    final long now=Instant.now().getEpochSecond();
    AccountRiskSnapshot risk(long loss,long reserve) {
        return AccountRiskSnapshot.builder(account,"persisted-window","session",now-1000,42,now,now)
            .capital(100000000,100000000).realized(-loss,-loss,loss==0?0:1)
            .uncertainty(reserve,reserve==0?0:1,0).build();
    }
    Suggestion present(AccountRiskSnapshot risk,String requestedAccount,PolicyAbstentionReason reason) {
        var plan=new PortfolioPlan("plan",now,now+90,"UNAVAILABLE","display text is not authority",0,List.of());
        if(risk!=null)plan=plan.withCanonicalRisk(risk);
        var decision=PolicyDecision.abstain("1","policy","decision",PolicyDecisionType.ENTRY,
            reason,reason.name(),now,now,null,"plan");
        var result=EntryDecisionPresenter.present(decision,plan,null,requestedAccount);
        assertNotNull(result);assertSame(decision,result.getDecision());
        assertEquals(SuggestionType.WAIT,result.getSuggestion().getType());
        assertEquals(0,result.getSuggestion().getPrice());assertEquals(0,result.getSuggestion().getQuantity());
        return result.getSuggestion();
    }
    Suggestion present(AccountRiskSnapshot risk) {
        return present(risk,account,PolicyAbstentionReason.DRAWDOWN_LIMIT_REACHED);
    }
    @Test public void reserveOnlyPauseExplainsMissingEvidenceInsteadOfSessionLoss() {
        var risk=risk(0,20000000);var suggestion=present(risk);
        assertTrue(suggestion.getHeadline(),suggestion.getHeadline().toLowerCase().contains("evidence"));
        assertTrue(suggestion.getDetail(),suggestion.getDetail().contains("uncertainty reserve"));
        assertTrue(suggestion.getDetail(),suggestion.getDetail().contains("not a confirmed loss"));
        assertFalse(suggestion.getDetail(),suggestion.getDetail().contains("session"));
        assertEquals(Long.valueOf(0),risk.getBoundedRealizedProfitLower());
        assertEquals(Long.valueOf(-5000000),risk.getRemainingLossBudget());
    }
    @Test public void mixedLossAndReserveDoesNotClaimThatAllRiskIsAnActualLoss() {
        var suggestion=present(risk(16000000,20000000));
        assertTrue(suggestion.getDetail(),suggestion.getDetail().contains("uncertainty reserve"));
        assertTrue(suggestion.getDetail(),suggestion.getDetail().contains("not a confirmed loss"));
    }
    @Test public void lossWithoutAReserveUsesAccountWordingAndPreservesTheRefusal() {
        var suggestion=present(risk(16000000,0));
        assertTrue(suggestion.getDetail(),suggestion.getDetail().contains("account"));
        assertTrue(suggestion.getDetail(),suggestion.getDetail().contains("loss bound"));
        assertFalse(suggestion.getDetail(),suggestion.getDetail().contains("session"));
        assertFalse(suggestion.getDetail(),suggestion.getDetail().contains("uncertainty reserve"));
    }
    @Test public void foreignStaleUnavailableAndNonBlockingRiskCannotSupplyTheExplanation() {
        var foreign=AccountRiskSnapshot.builder(AccountIdentity.fromProfileKey("other"),"other-window","s",now-1,0,now,now)
            .capital(100000000,100000000).uncertainty(20000000,1,0).build();
        var expired=AccountRiskSnapshot.builder(account,"window","s",now-1000,0,now-300,now-300)
            .capital(100000000,100000000).uncertainty(20000000,1,0).build();
        for(var risk:List.of(foreign,expired,risk(0,100),AccountRiskSnapshot.unavailable(account,now,"not enough evidence"))) {
            var suggestion=present(risk);
            assertFalse(suggestion.getDetail(),suggestion.getDetail().contains("uncertainty reserve"));
            assertFalse(suggestion.getDetail(),suggestion.getDetail().contains("session"));
        }
        assertFalse(present(null).getDetail().contains("session"));
        assertFalse(present(risk(0,20000000),null,PolicyAbstentionReason.DRAWDOWN_LIMIT_REACHED)
            .getDetail().contains("uncertainty reserve"));
    }
    @Test public void zeroCapitalIsExplainedWithoutInventingALoss() {
        var empty=AccountRiskSnapshot.builder(account,"window","session",now-1,0,now,now)
            .capital(100000000,0).build();
        var suggestion=present(empty);
        assertTrue(suggestion.getDetail(),suggestion.getDetail().contains("No observed trading capital"));
        assertFalse(suggestion.getDetail(),suggestion.getDetail().contains("loss bound"));
    }
    @Test public void anotherRefusalReasonIsNotOverriddenByARiskDiagnostic() {
        var suggestion=present(risk(0,20000000),account,PolicyAbstentionReason.NO_FREE_SLOT);
        assertEquals("Keep your current offers in place",suggestion.getHeadline());
        assertFalse(suggestion.getDetail().contains("uncertainty reserve"));
    }
    @Test public void currentStockValuationIsNotPresentedAsBrokenHistoricalRecords() {
        var risk=AccountRiskSnapshot.builder(account,"persisted-window","session",now-1000,42,now,now)
            .capital(100000000,100000000).uncertainty(20000000,0,0)
            .currentAccountPolicy(349000000,20000000).build();
        var suggestion=present(risk);
        assertEquals("Waiting for stock valuation",suggestion.getHeadline());
        assertTrue(suggestion.getDetail().contains("20,000,000 GP"));
        assertTrue(suggestion.getDetail().contains("automatically"));
        assertFalse(suggestion.getDetail().contains("349,000,000"));
        assertEquals(Long.valueOf(-5000000),risk.getRemainingLossBudget());
    }
    @Test public void currentProofWaitShowsItsReasonOnlyForTheRightFreshAccount() {
        var risk=AccountRiskSnapshot.unavailable(account,now,"The current offer for Nature rune has unresolved execution evidence.");
        var wait=present(risk,account,PolicyAbstentionReason.ACCOUNT_STATE_UNAVAILABLE);
        assertEquals("Verifying current account",wait.getHeadline());assertEquals(risk.getReason(),wait.getDetail());
        assertFalse(present(risk,AccountIdentity.fromProfileKey("other"),PolicyAbstentionReason.ACCOUNT_STATE_UNAVAILABLE)
            .getDetail().contains("Nature rune"));
        var expired=AccountRiskSnapshot.unavailable(account,now-151,"Old private explanation");
        assertFalse(present(expired,account,PolicyAbstentionReason.ACCOUNT_STATE_UNAVAILABLE)
            .getDetail().contains("Old private explanation"));
    }
    @Test public void confirmedLossThatAlreadyExhaustsAllowanceDoesNotPromiseAQuoteCanClearIt() {
        var risk=AccountRiskSnapshot.builder(account,"persisted-window","session",now-1000,42,now,now)
            .capital(100000000,84000000).realized(-16000000,-16000000,1)
            .uncertainty(20000000,0,0).currentAccountPolicy(349000000,20000000).build();
        assertEquals("New buys are paused",present(risk).getHeadline());
        assertTrue(risk.getRemainingLossBudget()<-risk.getUnpricedManagedCostReserve());
    }
}
