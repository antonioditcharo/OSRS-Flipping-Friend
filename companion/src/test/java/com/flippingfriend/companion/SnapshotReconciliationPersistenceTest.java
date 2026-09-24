package com.flippingfriend.companion;
import com.flippingfriend.core.*;
import java.nio.file.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class SnapshotReconciliationPersistenceTest
{
 @Test public void repairedStateAndAuditSurviveRestart() throws Exception{
  Path db=Files.createTempDirectory("snapshot-persist").resolve("db");
  AccountSnapshot a=SnapshotReconciliationTest.account(100,new PositionSnapshot(4151,"Whip",5,5500,true,10,0,0,0));
  try(SqliteStore s=new SqliteStore(db)){s.recordAndReconcileAccount(a,PositionStateView.from(a),"snapshot");assertEquals(1,s.reconciliationEventCount());}
  try(SqliteStore s=new SqliteStore(db)){assertEquals(1,s.positionProjections().size());assertEquals(5500,s.positionProjections().get(0).getTotalCost());assertEquals(1,s.reconciliationEventCount());}
 }
}
