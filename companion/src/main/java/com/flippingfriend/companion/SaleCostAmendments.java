package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.sql.*;
import java.util.*;

/** Explicit source-proven amendments. Original executions, unknown legacy sales and clocks stay intact. */
final class SaleCostAmendments {
    static final String VERSION="SOURCE_PROVEN_COST_AMENDMENT_V1";
    static void initialize(Connection connection)throws Exception {
        try(var s=connection.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS sale_cost_amendment (sale_source_event_id TEXT PRIMARY KEY, account_id TEXT NOT NULL, applied_cost_basis INTEGER NOT NULL, profit_lower INTEGER NOT NULL, profit_upper INTEGER NOT NULL, proof TEXT NOT NULL, created_at INTEGER NOT NULL)");
            s.execute("CREATE VIEW IF NOT EXISTS effective_position_event AS SELECT p.id,p.source_event_id,p.source_offer_identity,p.observed_at,p.effect_type,p.item_id,p.item_name,p.quantity,p.acquisition_cost,"
                +"COALESCE(a.applied_cost_basis,p.applied_cost_basis) applied_cost_basis,CASE WHEN a.sale_source_event_id IS NULL THEN p.transition_reason END transition_reason,p.created_at,p.gross_proceeds,p.tax_lower,p.tax_upper,"
                +"COALESCE(a.profit_lower,p.profit_lower) profit_lower,COALESCE(a.profit_upper,p.profit_upper) profit_upper,p.sale_tax_policy,"
                +"CASE WHEN a.sale_source_event_id IS NULL THEN p.sale_accounting_reason ELSE '"+VERSION+"' END sale_accounting_reason "
                +"FROM position_event p LEFT JOIN sale_cost_amendment a ON a.sale_source_event_id=p.source_event_id AND p.effect_type='DISPOSE'");
        }
    }
    static final class Request { String accountId; List<String> saleSourceEventIds; }
    static List<String> apply(Connection connection,String account,List<String> ids,long now)throws Exception {
        AccountIdentity.require(account);
        if(ids==null || ids.isEmpty() || ids.size()>20 || new HashSet<>(ids).size()!=ids.size())
            throw new IllegalArgumentException("One to twenty distinct sale sources required");
        var gson=new Gson();var history=new ArrayList<OfferEvent>();var sourceRows=new HashMap<String,Long>();
        var sources=new HashMap<String,OfferEvent>();var predecessors=new HashMap<String,OfferEvent>();var slots=new HashMap<Integer,OfferEvent>();
        try(var s=connection.createStatement();var r=s.executeQuery("SELECT id,event_id,payload FROM event_log WHERE event_id IS NOT NULL AND event_id<>'' ORDER BY id")) {
            while(r.next()) {
                OfferEvent e=gson.fromJson(r.getString(3),OfferEvent.class);
                if(!TradeGapEvidence.valid(e) || !account.equals(e.getAccountId()) || !r.getString(2).equals(e.getEventId())
                        || sourceRows.put(e.getEventId(),r.getLong(1))!=null)
                    throw new IllegalStateException("Complete matching account source history required");
                history.add(e);
                sources.put(e.getEventId(),e);predecessors.put(e.getEventId(),slots.put(e.getSlot(),e));
            }
        }
        var effects=new ArrayList<Map<String,Object>>();
        try(var s=connection.createStatement();var r=s.executeQuery("SELECT * FROM position_event ORDER BY id")) {
            while(r.next()) {
                Map<String,Object> row=new LinkedHashMap<>();
                for(int col=1;col<=r.getMetaData().getColumnCount();col++)row.put(r.getMetaData().getColumnName(col),r.getObject(col));
                effects.add(row);
            }
        }
        for(String id:ids) {
            var target=effects.stream().filter(e->id!=null && id.equals(e.get("source_event_id"))).findFirst()
                .orElseThrow(()->new IllegalArgumentException("Sale source missing"));
            if(!"DISPOSE".equals(target.get("effect_type")) || target.get("profit_lower")!=null || target.get("profit_upper")!=null
                    || number(target,"applied_cost_basis")!=0 || !"Sale lacks complete observed acquisition coverage".equals(target.get("transition_reason"))
                    || target.get("sale_accounting_reason")==null || !target.get("sale_accounting_reason").toString().startsWith("COST_BASIS_UNRESOLVED"))
                throw new IllegalStateException("Only an explicitly requested coverage-refused sale can be amended");
            Long sourceRow=sourceRows.get(id);
            if(sourceRow==null)throw new IllegalStateException("Sale source missing");
            var prefix=new ArrayList<OfferEvent>();OfferEvent sale=null,prior=null;
            for(var e:history)if(sourceRows.get(e.getEventId())<=sourceRow) {
                prefix.add(e);
                if(id.equals(e.getEventId()))sale=e;
            }
            for(var e:prefix)if(e!=sale && sale!=null && e.getSlot()==sale.getSlot())prior=e;
            int item=Math.toIntExact(number(target,"item_id")),quantity=Math.toIntExact(number(target,"quantity"));
            if(sale==null || prior==null || !sale.isAuthenticatedObservation() || !prior.isAuthenticatedObservation()
                    || sale.isBuying() || sale.getItemId()!=item || !Objects.equals(sale.getOfferIdentity(),target.get("source_offer_identity"))
                    || !Objects.equals(sale.getOfferIdentity(),prior.getOfferIdentity()) || !Objects.equals(sale.getSessionId(),prior.getSessionId())
                    || prior.isBuying() || prior.getItemId()!=item || sale.getPrice()!=prior.getPrice() || sale.getTotalQuantity()!=prior.getTotalQuantity()
                    || sale.getSequence()<=prior.getSequence() || sale.getObservedAt()<prior.getObservedAt()
                    || sale.getObservedAt()!=number(target,"observed_at") || sale.getFilledQuantity()-prior.getFilledQuantity()!=quantity
                    || target.get("gross_proceeds")==null || sale.getSpent()-prior.getSpent()!=number(target,"gross_proceeds"))
                throw new IllegalStateException("Sale delta lacks matching ordered authenticated evidence");
            var gaps=new HashMap<String,Integer>();
            for(var e:effects)if(number(e,"id")<number(target,"id") && "RECONCILE".equals(e.get("effect_type")))
                gaps.put((String)e.get("source_event_id"),Math.toIntExact(number(e,"item_id")));
            var scopes=TradeGapEvidence.scope(prefix,gaps,account);var scope=new AutomaticTradeScope();
            var financialPrefix=new ArrayList<Map<String,Object>>();
            for(var e:effects) {
                if(number(e,"id")>=number(target,"id"))break;
                financialPrefix.add(e);
                if(number(e,"item_id")==item && !"RECONCILE".equals(e.get("effect_type"))) {
                    String sourceId=(String)e.get("source_event_id");var original=sources.get(sourceId);var before=predecessors.get(sourceId);
                    boolean acquire="ACQUIRE".equals(e.get("effect_type"));
                    if(original==null || sourceRows.get(sourceId)>=sourceRow || original.getItemId()!=item || original.isBuying()!=acquire
                            || !Objects.equals(original.getOfferIdentity(),e.get("source_offer_identity")) || original.getObservedAt()!=number(e,"observed_at"))
                        throw new IllegalStateException("Acquisition pool lacks matching earlier canonical sources");
                    boolean same=before!=null && Objects.equals(before.getOfferIdentity(),original.getOfferIdentity())
                        && Objects.equals(before.getSessionId(),original.getSessionId()) && before.getItemId()==item && before.isBuying()==original.isBuying();
                    if(original.getFilledQuantity()-(same?before.getFilledQuantity():0)!=number(e,"quantity")
                            || acquire && original.getSpent()-(same?before.getSpent():0)!=number(e,"acquisition_cost"))
                        throw new IllegalStateException("Acquisition pool deltas disagree with their sources");
                }
                scope.apply((String)e.get("effect_type"),Math.toIntExact(number(e,"item_id")),(String)e.get("item_name"),
                    Math.toIntExact(number(e,"quantity")),number(e,"acquisition_cost"),number(e,"observed_at"),
                    (String)e.get("source_event_id"),(String)e.get("source_offer_identity"),scopes.get(e.get("source_event_id")));
            }
            var holding=scope.positions.get(item);
            if(scope.hasUnboundedReconciliation || scope.unsafeItems.contains(item) || holding==null || quantity<=0 || quantity>holding.getQuantity())
                throw new IllegalStateException("Sale has no complete unambiguous earlier acquisition pool");
            long cost=SaleAccounting.allocateCost(holding.getTotalCost(),holding.getQuantity(),quantity);
            var accounting=SaleAccounting.calculate(number(target,"gross_proceeds"),quantity,sale.getPrice(),sale.getItemName(),(String)target.get("sale_tax_policy"),cost);
            if(accounting.getProfitLower()==null || target.get("tax_lower")==null || target.get("tax_upper")==null
                    || accounting.getTaxLower()!=number(target,"tax_lower") || accounting.getTaxUpper()!=number(target,"tax_upper"))
                throw new IllegalStateException("Original tax evidence cannot be preserved");
            var proof=new LinkedHashMap<String,Object>();proof.put("version",VERSION);proof.put("accountId",account);
            proof.put("sale",target);proof.put("sourcePrefixSha256",digest(gson.toJson(prefix)));
            proof.put("effectPrefixSha256",digest(gson.toJson(financialPrefix)));proof.put("reconciliationScope",scopes);
            proof.put("poolQuantity",holding.getQuantity());proof.put("poolCost",holding.getTotalCost());
            String encoded=gson.toJson(proof);
            try(var s=connection.prepareStatement("SELECT account_id,applied_cost_basis,profit_lower,profit_upper,proof FROM sale_cost_amendment WHERE sale_source_event_id=?")) {
                s.setString(1,id);try(var r=s.executeQuery()) {if(r.next()) {
                    if(!account.equals(r.getString(1)) || cost!=r.getLong(2) || accounting.getProfitLower()!=r.getLong(3)
                            || accounting.getProfitUpper()!=r.getLong(4) || !encoded.equals(r.getString(5)))
                        throw new IllegalStateException("Amendment proof changed");
                    continue;
                }}
            }
            try(var s=connection.prepareStatement("INSERT INTO sale_cost_amendment VALUES(?,?,?,?,?,?,?)")) {
                s.setString(1,id);s.setString(2,account);s.setLong(3,cost);s.setLong(4,accounting.getProfitLower());
                s.setLong(5,accounting.getProfitUpper());s.setString(6,encoded);s.setLong(7,now);s.executeUpdate();
            }
        }
        return List.copyOf(ids);
    }
    private static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    private static String digest(String value)throws Exception {
        byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var result=new StringBuilder();for(byte b:bytes)result.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return result.toString();
    }
}
