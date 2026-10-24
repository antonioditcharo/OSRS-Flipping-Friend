package com.flippingfriend.session;

import com.flippingfriend.core.*;
import com.flippingfriend.data.MarketSnapshot;
import com.flippingfriend.model.PositionStatus;
import com.flippingfriend.model.Suggestion;
import java.util.*;
import static com.flippingfriend.core.TradeRecoveryItem.Kind.*;
import static com.flippingfriend.core.TradeRecoveryItem.State.*;

/** Pure account-wide coverage. No price changes, persistence, disposal inference or financial reconstruction. */
public final class TradeRecoveryScanner {
    public static TradeRecoverySnapshot scan(String account,long now,AccountLedgerSnapshot ledger,AccountState physical,
            Collection<TrackedOffer> offers,MarketSnapshot market,Map<Integer,SaleMaintenanceDiagnostic> sales,
            Map<Integer,PositionStatus> stock,List<TradeRecoveryItem> workflows,Suggestion selected,
            com.flippingfriend.TradingHorizon horizon,com.flippingfriend.model.TaxCalculator tax,long minimumProfit) {
        boolean current=ledger!=null && ledger.isValid() && Objects.equals(account,ledger.getAccountId())
            && now>=ledger.getObservedAt() && now-ledger.getObservedAt()<=150;
        var rows=new ArrayList<TradeRecoveryItem>();var listed=new HashMap<Integer,Long>();var buying=new HashSet<Integer>();
        for(var offer:offers) {
            if(offer.isCollected() || "EMPTY".equals(offer.getState()))continue;
            boolean active=(offer.isBuying()?"BUYING":"SELLING").equals(offer.getState()) && offer.getRemaining()>0;
            if(active && offer.isBuying())buying.add(offer.getItemId());
            if(!offer.isBuying())listed.merge(offer.getItemId(),(long)Math.max(0,offer.getRemaining()),Long::sum);
            var status=active?WORKING:AWAITING_COLLECTION;
            String reason=active?"Offer is working; placement and observed fill progress are retained.":"Terminal or full-counter offer still occupies its collection box.";
            String next=active?"Wait for fill progress or a supported maintenance decision.":"Collect manually; wait for acknowledged collection before reusing its remainder.";
            long reassess=now+30;
            var quote=market.latest(offer.getItemId());
            if(active && (quote==null || !quote.isComplete())){status=AWAITING_EVIDENCE;reason="Current two-sided market prices are missing; the working offer is preserved.";}
            if(active && offer.isBuying() && offer.getBuyMaintenanceDeadlineAt()>0) {
                reassess=Math.min(reassess,Math.max(now,offer.getBuyMaintenanceDeadlineAt()));
                next="Recheck the original buy deadline at "+offer.getBuyMaintenanceDeadlineAt()+"; recent fills still protect progress.";
            }
            if(active && offer.isBuying()) {
                if(offer.getBuyMaintenanceDeadlineAt()<=0){status=AWAITING_EVIDENCE;reason="Original buy waiting budget is unavailable; no new budget is inferred.";}
                if(BuyRepricePricing.overdue(offer.getFirstSeen(),offer.getLastFillObservedAt(),offer.getBuyMaintenanceDeadlineAt(),quote,horizon,now)) {
                    status=ACTIONABLE;reason="Original buy waiting budget elapsed without recent observed fills.";
                    next="Authorize cancellation, collect the remainder and plan acquired partial stock; do not restart the same order.";
                }else if(quote!=null && quote.getLow()!=null && quote.getLow()<Integer.MAX_VALUE) {
                    var proposal=BuyRepricePricing.quote(offer.getItemId(),offer.getPrice(),offer.getRemaining(),offer.getFirstSeen(),offer.getLastFillObservedAt(),
                        quote,market.fiveMinute(offer.getItemId()),horizon,tax,now,quote.getLow()+1,minimumProfit);
                    if(proposal!=null){status=ACTIONABLE;reason=proposal.replace?"Material upward bid adjustment has current pricing support.":"The supported higher bid no longer clears the profit requirement.";
                        next=proposal.replace?"Reauthorize cancellation and a conditional bounded replacement; acquired partial stock remains tracked.":"Authorize cancellation and reassess returned capital and acquired units.";}
                }
            }
            var diagnostic=sales.get(offer.getSlot());
            if(active && !offer.isBuying() && diagnostic!=null && diagnostic.matches(offer,now)) {
                if(diagnostic.unavailable!=null){status=AWAITING_EVIDENCE;reason=diagnostic.unavailable;}
                else if(diagnostic.evaluation!=null) {
                    var result=diagnostic.evaluation;
                    reason="Sale check: "+result.status+". Exact cost and original sale plans remain authoritative.";
                    if(result.status==SaleMaintenancePricing.Status.ELIGIBLE){status=ACTIONABLE;next="Reauthorize the exact remaining sale before cancellation/relisting.";}
                    else if(result.status==SaleMaintenancePricing.Status.MISSING_PRICES || result.status==SaleMaintenancePricing.Status.STALE_PRICES
                        || result.status==SaleMaintenancePricing.Status.UNKNOWN_CLOCKS || result.status==SaleMaintenancePricing.Status.UNPROVEN_QUANTITY)status=AWAITING_EVIDENCE;
                    if(result.reassessAt>now)reassess=Math.min(reassess,result.reassessAt);
                }
            }
            if(selected!=null && offer.getContinuationIdentity().equals(selected.getMaintenanceOfferIdentity())) {
                status=ACTIONABLE;reason=selected.getHeadline();next=selected.getDetail();
            }
            if(quote!=null) {
                var at=java.time.Instant.ofEpochSecond(now);
                reason+=" Latest seller/buyer trade ages: "+quote.secondsSinceLow(at)+"s / "+quote.secondsSinceHigh(at)+"s (negative means unavailable).";
            }
            rows.add(new TradeRecoveryItem(offer.getContinuationIdentity(),OFFER,status,offer.getItemId(),offer.getItemName(),offer.getSlot(),
                Math.max(0,offer.getRemaining()),offer.getPrice(),offer.isBuying(),offer.getFirstSeen(),reassess,reason,next));
        }
        if(current && ledger.getTradingPositions()!=null)for(var p:ledger.getTradingPositions()) {
            long listedQuantity=listed.getOrDefault(p.getItemId(),0L);
            int unlisted=(int)Math.max(0,p.getQuantity()-listedQuantity);
            if(unlisted==0)continue;
            var status=AWAITING_EVIDENCE;
            String reason="Proven acquired stock is not fully listed. Exact acquisition pool cost is retained.";
            String next="Revalidate current inventory, original exit context and a supported sale price.";
            var evaluated=stock.get(p.getItemId());
            if(buying.contains(p.getItemId())){status=WORKING;reason="Partial acquisition is still arriving in a working buy.";next="Complete or cancel the buy, then collect and plan its acquired units.";}
            else if(evaluated!=null){reason=evaluated.summary();if(evaluated.getDecision()!=null && evaluated.getDecision().isSell())status=ACTIONABLE;}
            if(!buying.contains(p.getItemId()) && physical.getInventoryHoldings().getOrDefault(p.getItemId(),0)<=0) {
                status=AWAITING_ACCESS;
                reason=physical.getBankHoldings().getOrDefault(p.getItemId(),0)>0
                    ?"Bank evidence exists; the managed remainder is not currently proven in inventory."
                    :"The acquired remainder is not currently proven in inventory; unseen quantities remain reserved.";
                next="Inspect or retrieve the item manually; current physical observations must confirm the sellable quantity.";
            }else if(!buying.contains(p.getItemId()) && physical.getFreeSlots()<=0){status=PAUSED;next="Wait for collection or an independently authorized slot-recovery cancellation.";}
            rows.add(new TradeRecoveryItem(p.getSourceEventId(),STOCK,status,p.getItemId(),p.getItemName(),-1,unlisted,0,false,
                p.getOpenedAt(),now+30,reason,next));
        }
        if(current && ledger.getMissedFlips()!=null)for(var gap:ledger.getMissedFlips())rows.add(new TradeRecoveryItem(gap.getEventId(),EVIDENCE,
            PAUSED,gap.getItemId(),gap.getItemName(),-1,gap.getQuantity(),0,false,gap.getObservedAt(),now+30,gap.getReason(),
            "Investigate in Missed flips. Incomplete evidence is not current stock or permission to clear exposure."));
        rows.addAll(workflows);
        if(!current)rows.add(new TradeRecoveryItem("account-ledger",EVIDENCE,AWAITING_EVIDENCE,0,"Account evidence",-1,0,0,false,0,now+30,
            "Fresh acknowledged account ledger is unavailable; stock coverage cannot be established.","Wait for durable observations and ledger refresh; no history is repaired."));
        rows.sort(Comparator.comparingLong((TradeRecoveryItem r)->r.openedAt).thenComparing(r->r.identity));
        return new TradeRecoverySnapshot(account,now,current?ledger.getObservedAt():0,current,rows);
    }
    private TradeRecoveryScanner(){}
}
