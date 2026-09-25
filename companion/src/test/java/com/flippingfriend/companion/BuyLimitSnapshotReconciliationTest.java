package com.flippingfriend.companion;
import com.flippingfriend.core.*;import java.nio.file.*;import java.util.*;import org.junit.Test;import static org.junit.Assert.*;
public class BuyLimitSnapshotReconciliationTest{
 @Test public void snapshotIsAnUpwardFloorOnly()throws Exception{try(SqliteStore s=new SqliteStore(Files.createTempDirectory("limit-snapshot").resolve("db"))){AccountSnapshot high=account(100,7);SnapshotReconciliationResult a=s.recordAndReconcileAccount(high,PositionStateView.from(high),"high");assertEquals(1,a.buyLimitsRaised());assertEquals(7,s.buyLimitProjections().get(0).getUsedQuantity());AccountSnapshot low=account(200,3);s.recordAndReconcileAccount(low,PositionStateView.from(low),"low");assertEquals(7,s.buyLimitProjections().get(0).getUsedQuantity());AccountSnapshot empty=account(300,0);s.recordAndReconcileAccount(empty,PositionStateView.from(empty),"empty");assertEquals(7,s.buyLimitProjections().get(0).getUsedQuantity());}}
 static AccountSnapshot account(long at,int used){Map<Integer,Integer> m=new HashMap<>();if(used>0)m.put(561,used);return new AccountSnapshot("s"+at,at,0,0,8,8,true,true,0,null,null,0,m);}
}
