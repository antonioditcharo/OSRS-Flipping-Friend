package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;

/** Aggregates every selected durable sale update, not a capped recent-history list. */
final class PerformanceRead {
    static final String SESSION_EVENT="PERFORMANCE_SESSION";
    static final class Session {
        String id;
        long startedAt,cursor;
        Session(String id,long at,long cursor){this.id=id;startedAt=at;this.cursor=cursor;}
    }
    static final class Group {
        String offer,name;int item;long quantity,at,updates,low,high;boolean unknown,completed;
        PerformanceSnapshot.Sale snapshot() { return new PerformanceSnapshot.Sale(offer,item,name,quantity,at,updates,
            unknown?null:low,unknown?null:high,completed); }
    }
    static PerformanceSnapshot aggregate(String account,String scope,Session session,long now,List<RecordedSale> rows,
        Set<String> completedOffers) {
        if(!Set.of("SESSION","DAY","WEEK","ALL").contains(scope))throw new IllegalArgumentException("Unknown performance scope");
        long since=scope.equals("DAY")?Math.max(0,now-86400):scope.equals("WEEK")?Math.max(0,now-604800):0;
        long updates=0,unresolved=0,low=0,high=0,cost=0;
        Map<String,Group> groups=new LinkedHashMap<>();
        for(var sale:rows) {
            if(sale.getObservedAt()>now)throw new IllegalStateException("Future sale evidence");
            if(scope.equals("SESSION") && (session==null || sale.getRowId()<=session.cursor))continue;
            if(sale.getObservedAt()<since)continue;
            updates++;
            if(sale.getProfitLower()==null)unresolved++;
            else {low=Math.addExact(low,sale.getProfitLower());high=Math.addExact(high,sale.getProfitUpper());cost=Math.addExact(cost,sale.getAppliedCost());}
            String key=sale.getOfferIdentity()==null?"unidentified-row:"+sale.getRowId():sale.getOfferIdentity();
            Group g=groups.computeIfAbsent(key,k->new Group());
            if(g.item!=0 && g.item!=sale.getItemId())throw new IllegalStateException("Inconsistent sale identity");
            g.offer=sale.getOfferIdentity();g.item=sale.getItemId();g.name=sale.getItemName();g.updates++;
            g.quantity=Math.addExact(g.quantity,sale.getQuantity());g.at=Math.max(g.at,sale.getObservedAt());
            if(sale.getProfitLower()==null)g.unknown=true;
            else {g.low=Math.addExact(g.low,sale.getProfitLower());g.high=Math.addExact(g.high,sale.getProfitUpper());}
            g.completed=completedOffers.contains(key);
        }
        List<PerformanceSnapshot.Sale> sales=new ArrayList<>();
        long completed=0;
        for(Group g:groups.values()){sales.add(g.snapshot());if(g.completed && !g.unknown)completed++;}
        sales.sort(Comparator.comparingLong((PerformanceSnapshot.Sale s)->s.observedAt).reversed().thenComparing(s->Objects.toString(s.offerIdentity,"")));
        return new PerformanceSnapshot(account,scope,session==null?null:session.id,now,
            scope.equals("SESSION")?(session==null?now:session.startedAt):since,session==null?0:session.cursor,
            updates,unresolved,completed,cost,low,high,sales);
    }
}
