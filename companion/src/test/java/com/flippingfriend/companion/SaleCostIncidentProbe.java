package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Opt-in incident reproduction on a NEW disposable copy under local-data. Never opens a live writer. */
public final class SaleCostIncidentProbe {
    public static void main(String[] args)throws Exception {
        if(args.length<4)throw new IllegalArgumentException("frozen database, NEW local-data destination, account, sale sources required");
        Path root=Path.of("local-data").toRealPath(),source=Path.of(args[0]).toRealPath(),destination=Path.of(args[1]).toAbsolutePath().normalize();
        if(!source.startsWith(root) || !destination.getParent().toRealPath().startsWith(root) || Files.exists(destination))
            throw new IllegalArgumentException("Only a new local-data copy is permitted");
        Files.copy(source,destination);String account=args[2];var gson=new GsonBuilder().setPrettyPrinting().create();
        AccountSnapshot snapshot;long evaluatedAt;
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+source.toUri()+"?mode=ro");var s=c.createStatement();
                var r=s.executeQuery("SELECT payload FROM event_log WHERE event_type='ACCOUNT_STATE' ORDER BY id DESC LIMIT 1")) {
            if(!r.next())throw new IllegalArgumentException("Frozen account capture missing");snapshot=gson.fromJson(r.getString(1),AccountSnapshot.class);
        }
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+source.toUri()+"?mode=ro");var s=c.createStatement();
                var r=s.executeQuery("SELECT MAX(observed_at) FROM event_log")) {r.next();evaluatedAt=Math.max(snapshot.getObservedAt(),r.getLong(1));}
        if(!account.equals(snapshot.getAccountId()))throw new IllegalArgumentException("Frozen account mismatch");
        var report=new LinkedHashMap<String,Object>();report.put("evidence","OFFLINE_FROZEN_COPY_ONLY");
        try(var store=new SqliteStore(destination)) {
            var read=store.planningProjections(account);report.put("unboundedScope",read.tradeScope.hasUnboundedReconciliation);
            var exposure=DurablePlanningExposure.forAccount(read,snapshot,snapshot.getObservedAt());report.put("exposureAvailable",exposure!=null);
            report.put("currentReadSetValid",CurrentOfferReadSet.valid(read,snapshot,evaluatedAt));
            report.put("historicalExposureAvailable",BoundedHistoricalExposure.calculate(read)!=null);
            var ids=Arrays.asList(Arrays.copyOfRange(args,3,args.length));store.amendSaleCosts(account,ids,evaluatedAt);
            store.amendSaleCosts(account,ids,evaluatedAt+1);
            var ledger=store.ledgerSnapshot(account,evaluatedAt);report.put("amendedSales",ledger.getRecentSales().stream()
                .filter(s->ids.contains(s.getSourceEventId())).collect(java.util.stream.Collectors.toList()));
            var after=store.planningProjections(account);report.put("riskRealizedLower",after.riskEvidence.profitLower);
            report.put("riskRealizedUpper",after.riskEvidence.profitUpper);report.put("riskUnresolvedManagedSales",after.riskEvidence.unresolvedManagedSales);
            report.put("riskWindow",after.riskEvidence.window);report.put("newGapBounds",ProspectiveGapExposure.calculate(after));
            // Missing-price scenario exercises the V3 verified-cash rules; it is NOT live authority.
            var market=new MarketIngestionService.MarketState(Map.of(),new JsonObject(),new JsonObject(),new JsonObject(),snapshot.getObservedAt());
            report.put("missingPriceScenarioRisk",DurableRisk.evaluate(after,snapshot,exposure,market,snapshot.getObservedAt()));
            if(BoundedHistoricalExposure.calculate(after)==null || after.tradeScope.hasUnboundedReconciliation || ledger.getRecentSales().stream()
                    .filter(s->ids.contains(s.getSourceEventId()) && s.getProfitLower()!=null).count()!=ids.size())
                throw new IllegalStateException("Incident reproduction failed");
        }
        System.out.println(gson.toJson(report));
    }
}
