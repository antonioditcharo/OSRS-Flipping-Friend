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
}
