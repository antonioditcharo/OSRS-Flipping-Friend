package com.flippingfriend.companion;

import com.flippingfriend.core.AccountIdentity;
import com.google.gson.Gson;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Actual account planner consumer; fixture databases, no market fetch or game interaction. */
public class ExecutionCalibrationAuthorityTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    private double activeMultiplier(boolean completed)throws Exception {
        var root=folder.newFolder().toPath();
        try(var marketStore=new SqliteStore(root.resolve("market.db"));
            var accountStore=new SqliteStore(root.resolve("account.db"));
            var market=new CompanionService(new Gson(),marketStore);
            var account=market.forAccountStore(accountStore,AccountIdentity.fromProfileKey("calibration-authority"))) {
            for(int i=0;i<40;i++)accountStore.recordExecution(561,completed,completed?10:0,
                completed?40:0,10,40);
            account.refreshAccountPlan();
            var field=CompanionService.class.getDeclaredField("planner");field.setAccessible(true);
            return ((PortfolioPlanner)field.get(account)).calibration().waitMultiplier(561);
        }
    }
    @Test public void canceledTotalsCannotPromoteAnUnvalidatedTimingCorrection()throws Exception {
        assertEquals("Ten minutes before cancellation does not prove a forty-minute fill forecast was slow",
            1,activeMultiplier(false),0);
    }
    @Test public void completedTotalsDoNotIdentifyAWaitingTimeMultiplier()throws Exception {
        assertEquals("Totals omit placement/completion intervals, quantity, wait/work decomposition and held-out gates",
            1,activeMultiplier(true),0);
    }
    @Test public void manyProspectiveForecastsStillRequireChronologicalPromotionGates()throws Exception {
        var root=folder.newFolder().toPath();var gson=new Gson();String id=AccountIdentity.fromProfileKey("prospective-calibration");
        long at=java.time.Instant.now().getEpochSecond()-10000;long sequence=1;
        try(var marketStore=new SqliteStore(root.resolve("market.db"));var store=new SqliteStore(root.resolve("account.db"));
            var market=new CompanionService(gson,marketStore);var account=market.forAccountStore(store,id)) {
            for(int i=0;i<40;i++) {
                long start=at+i*100;String birth="birth-"+i;
                var empty=com.flippingfriend.core.OfferEvent.builder("fixture",start-1,"EMPTY").accountId(id)
                    .eventIdentity("empty-"+i,"session",null).sequence(sequence++).authenticatedObservation(true).slot(0).build();
                store.recordAndProjectOffer(empty,gson.toJson(empty),true);
                for(boolean done:new boolean[]{false,true}) {
                    var event=com.flippingfriend.core.OfferEvent.builder("fixture",start+(done?10:0),done?"BOUGHT":"BUYING")
                        .accountId(id).eventIdentity(done?"done-"+i:birth,"session","offer-"+i).sequence(sequence++)
                        .authenticatedObservation(true).slot(0).item(561,"Nature rune").buying(true).price(100)
                        .quantities(10,done?10:0).spent(done?1000:0).firstSeenAt(start)
                        .recommendation("plan",100,10,0,40,.75).predictionEvidence(birth,start-2,true).build();
                    store.recordAndProjectOffer(event,gson.toJson(event),true);
                }
            }
            account.refreshAccountPlan();var report=account.learningEvidence();
            assertEquals(40,report.getCompletedIntervals());assertEquals(40,report.getComparableForecasts());
            assertEquals("HOLD_FOR_VALIDATION",report.getPromotionStatus());
            var field=CompanionService.class.getDeclaredField("planner");field.setAccessible(true);
            assertEquals(1,((PortfolioPlanner)field.get(account)).calibration().waitMultiplier(561),0);
            assertEquals(40,store.executionStats().get(561).observed);
        }
    }
}
