package com.flippingfriend.companion;
import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class SnapshotReconciliationTest
{
 @Test public void knownSnapshotAdoptsAndUnknownSnapshotFailsClosed() throws Exception{
  try(SqliteStore s=new SqliteStore(Files.createTempDirectory("snapshot-reconcile").resolve("db"))){
   AccountSnapshot a=account(100,new PositionSnapshot(1,"Known",3,300,true,10,0,0,0),new PositionSnapshot(2,"Unknown",4,0,false,10,0,0,0));
   SnapshotReconciliationResult r=s.recordAndReconcileAccount(a,PositionStateView.from(a),new Gson().toJson(a));
   assertEquals(1,r.adopted());assertEquals(1,r.reconciliationRequired());assertEquals(2,s.positionProjections().size());
  }
 }
 @Test public void lowerOrMissingSnapshotCannotDestroyDurablePosition() throws Exception{
  Path db=Files.createTempDirectory("snapshot-safe").resolve("db");
  try(SqliteStore s=new SqliteStore(db)){
   AccountSnapshot first=account(100,new PositionSnapshot(1,"Known",10,1000,true,10,0,0,0));s.recordAndReconcileAccount(first,PositionStateView.from(first),"first");
   AccountSnapshot lower=account(200,new PositionSnapshot(1,"Known",2,200,true,10,0,0,0));s.recordAndReconcileAccount(lower,PositionStateView.from(lower),"lower");
   assertEquals(10,s.positionProjections().get(0).getQuantity());assertEquals(1000,s.positionProjections().get(0).getTotalCost());
   AccountSnapshot empty=account(300);s.recordAndReconcileAccount(empty,PositionStateView.from(empty),"empty");assertEquals(1,s.positionProjections().size());
  }
 }
 static AccountSnapshot account(long at,PositionSnapshot...p){return new AccountSnapshot("snap-"+at,at,0,0,8,8,true,true,0,null,null,0,null,false,0,null,null,null,false,0,Arrays.asList(p));}
}
