package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;

/** Fair, bounded, two-sided opportunity allocation; never changes a working order. */
final class PilotCoordinator {
    static final double POOLED_SHARE=0.55;
    interface Capacity { long hourly(int item,boolean buy); }
    private final Map<String,Request> requests=new LinkedHashMap<>();
    private final Map<String,Lease> leases=new HashMap<>();
    private final Map<String,Long> userTurn=new HashMap<>(),accountTurn=new HashMap<>();
    private long revision;
    static final class Request {
        final String user;final PilotProtocol.AllocationRequest input;
        Request(String user,PilotProtocol.AllocationRequest input){this.user=user;this.input=input;}
    }
    static final class Lease {
        String account;int item,quantity;long expires;double hours;
    }
    synchronized void submit(String user,PilotProtocol.AllocationRequest input,long now) {
        submit(user,input,now,()->{});
    }
    synchronized void restore(String user,PilotProtocol.AllocationRequest input) {
        requests.put(user+"/"+input.accountId,new Request(user,input));
    }
    synchronized void submit(String user,PilotProtocol.AllocationRequest input,long now,Runnable persist) {
        input.validate(now);
        String key=user+"/"+input.accountId;
        if(!requests.containsKey(key) && requests.size()>=4096)throw new IllegalStateException("pilot account capacity");
        Request old=requests.get(key);
        if(old!=null && (!old.input.installationId.equals(input.installationId)
                || input.generation<old.input.generation || (input.generation==old.input.generation && input.frameOrder<old.input.frameOrder) || input.observedAt<old.input.observedAt))
            throw new IllegalArgumentException("stale or foreign capture writer");
        if(old!=null && input.generation==old.input.generation && input.frameOrder==old.input.frameOrder
                && (!Objects.equals(input.context,old.input.context) || input.observedAt!=old.input.observedAt))
            throw new IllegalArgumentException("conflicting capture frame");
        // Identical refreshes need no repeated disk write or additional allocation priority.
        if(old==null || input.generation!=old.input.generation || input.frameOrder!=old.input.frameOrder
                || !Objects.equals(input.context,old.input.context))persist.run();
        requests.put(key,new Request(user,input));
    }
    synchronized Map<String,PilotProtocol.AllocationHint> round(Capacity capacity,long now) {
        leases.values().removeIf(l->l.expires<=now);
        var active=new ArrayList<Map.Entry<String,Request>>();
        for(var e:requests.entrySet())if(!e.getValue().input.candidates.isEmpty() && now>=e.getValue().input.observedAt && now-e.getValue().input.observedAt<=150)active.add(e);
        active.sort(Comparator.<Map.Entry<String,Request>>comparingLong(e->userTurn.getOrDefault(e.getValue().user,0L))
            .thenComparingLong(e->accountTurn.getOrDefault(e.getKey(),0L)).thenComparing(Map.Entry::getKey));
        var totals=new HashMap<Integer,long[]>();
        for(var request:requests.values())for(var d:request.input.demand){var total=totals.computeIfAbsent(d.itemId,i->new long[2]);total[0]=saturatedAdd(total[0],d.buying);total[1]=saturatedAdd(total[1],d.selling);}
        var result=new HashMap<String,PilotProtocol.AllocationHint>();var served=new HashSet<String>();
        for(var entry:active) {
            Request r=entry.getValue();if(!served.add(r.user))continue;
            var in=r.input;var hint=new PilotProtocol.AllocationHint();
            hint.accountId=in.accountId;hint.installationId=in.installationId;hint.generation=in.generation;
            hint.context=in.context;hint.adviceId=UUID.randomUUID().toString();hint.revision=++revision;
            hint.expiresAt=now+15;hint.mode=in.coordinate?"COORDINATED":"SHADOW";
            var candidates=new ArrayList<>(in.candidates);
            candidates.sort(Comparator.<PortfolioCandidate>comparingInt(c->c.getNetProfit()>=in.preferredProfit?0:1)
                .thenComparing(Comparator.comparingDouble(PortfolioCandidate::expectedGpPerSlotHour).reversed()));
            for(var c:candidates) {
                if(c.getExpiresAt()<=now || c.getNetProfit()<=0 || c.expectedGpPerSlotHour()<=0)continue;
                long[] total=totals.getOrDefault(c.getItemId(),new long[2]);long buying=total[0],selling=total[1];
                for(var l:leases.values())if(l.item==c.getItemId() && !entry.getKey().equals(l.account)) {
                    // Rate pressure overlaps every horizon; long leases cannot each claim a full short window.
                    double overlap=Math.min(1,c.getHorizonHours()/l.hours);
                    long pressure=(long)Math.ceil(l.quantity*overlap);
                    buying=saturatedAdd(buying,pressure);selling=saturatedAdd(selling,pressure);
                }
                double hours=Math.max(1.0/60,Math.min(24,c.getHorizonHours()));
                long buyBudget=(long)(Math.max(0,capacity.hourly(c.getItemId(),true))*POOLED_SHARE*hours);
                long sellBudget=(long)(Math.max(0,capacity.hourly(c.getItemId(),false))*POOLED_SHARE*hours);
                if(buying>buyBudget-c.getQuantity() || selling>sellBudget-c.getQuantity()) {
                    hint.crowdedItems.add(c.getItemId());continue;
                }
                hint.itemId=c.getItemId();hint.buyPrice=c.getTargetBuyPrice();hint.sellPrice=c.getTargetSellPrice();hint.quantity=c.getQuantity();
                hint.expiresAt=Math.min(hint.expiresAt,c.getExpiresAt());
                Lease lease=new Lease();lease.account=entry.getKey();lease.item=c.getItemId();lease.quantity=c.getQuantity();
                lease.hours=hours;lease.expires=hint.expiresAt;if(in.coordinate)leases.put(entry.getKey(),lease);
                break;
            }
            result.put(entry.getKey(),hint);
            // A refresh cannot jump the fair queue; only a useful grant advances a user's turn.
            if(hint.itemId>0){userTurn.put(r.user,revision);accountTurn.put(entry.getKey(),revision);}
        }
        return result;
    }
    private static long saturatedAdd(long a,long b){return b>Long.MAX_VALUE-a?Long.MAX_VALUE:a+b;}
}
