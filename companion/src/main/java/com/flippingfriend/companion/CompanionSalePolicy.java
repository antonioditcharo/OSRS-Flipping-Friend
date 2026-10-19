package com.flippingfriend.companion;

import com.flippingfriend.*;
import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.flippingfriend.model.*;
import com.flippingfriend.session.*;
import java.time.Instant;
import java.util.*;

/** Final read-only authority for managed exits and observed sale maintenance. No cost comes from the request. */
final class CompanionSalePolicy {
    static final String VERSION="companion-sale-3";
    static String identity(int item,int price,int quantity,String source,boolean cut,int slot) {
        return item+":"+price+":"+quantity+":"+source+":"+cut+":"+slot;
    }
    PolicyDecision decide(SqliteStore.PlanningProjections read,AccountLedgerSnapshot ledger,AccountSnapshot account,
        MarketIngestionService.MarketState market,SeriesSource history,int item,int price,int quantity,String source,
        boolean cut,int slot,long now) {
        String identity=identity(item,price,quantity,source,cut,slot);
        PolicyDecisionType type=slot<0?PolicyDecisionType.EXIT:PolicyDecisionType.SELL_MAINTENANCE;
        try {
            if(account==null || account.getObservedAt()>now || now-account.getObservedAt()>150
                || ledger==null || !ledger.isValid() || !Objects.equals(account.getAccountId(),ledger.getAccountId())
                || item<=0 || price<=0 || quantity<=0 || source==null || source.isBlank() || slot< -1 || slot>7
                || market==null || market.observedAt<=0 || market.observedAt>now || now-market.observedAt>120)
                return refusal(type,identity,source,now,"SALE_EVIDENCE_UNAVAILABLE");
            boolean paused=ledger.hasUnboundedReconciliation();
            if(ledger.getReconciliationScope()!=null)for(var scope:ledger.getReconciliationScope().values())if(scope.contains(item))paused=true;
            if(slot<0 && paused)return refusal(type,identity,source,now,"SALE_ITEM_PAUSED");
            var raw=market.latest.get(Integer.toString(item));
            LatestPrice quote=raw==null?null:new com.google.gson.Gson().fromJson(raw,LatestPrice.class);
            Instant at=Instant.ofEpochSecond(now);
            if(quote==null || !quote.isComplete() || quote.getHighTime()==null || quote.getLowTime()==null
                || quote.getHighTime()<=0 || quote.getLowTime()<=0 || quote.secondsSinceHigh(at)<0 || quote.secondsSinceLow(at)<0
                || slot>=0 && quote.stalestSideSeconds(at)>300)
                return refusal(type,identity,source,now,"SALE_QUOTE_STALE");
            Position position=null;
            for(var p:ledger.getTradingPositions())if(!paused && p.getItemId()==item) {
                position=new Position(item,p.getItemName(),p.getQuantity(),p.getTotalCost(),p.getOpenedAt(),true);
                position.applyExitContext(p.getExitContext());
            }
            var tax=new TaxCalculator();List<ItemMetadata> metadata=new ArrayList<>();
            for(var m:market.mapping.values())metadata.add(new ItemMetadata(m.id,m.name,m.members,m.buyLimit,0));tax.resolveExemptions(metadata);
            var exits=new SellTimingEngine(new FillModel(),tax);
            TradingHorizon horizon=TradingHorizon.of(RiskProfile.valueOf(account.getRiskAppetite()),AccountReplay.interval(account.getCheckIntervalMinutes()),account.getTargetHoldMinutes());
            List<Candle> series=history.series(item,"5m");
            if(slot>=0) {
                OfferEvent offer=read.currentEvents.get(slot);
                OfferLifecycleProjection projection=null;for(var p:read.offers)if(p.getSlot()==slot)projection=p;
                if(offer==null || !offer.isAuthenticatedObservation() || !"SELLING".equals(offer.getEventType())
                    || offer.isBuying() || offer.getItemId()!=item || !source.equals(offer.getEventId()) || offer.getObservedAt()>now
                    || now-offer.getObservedAt()>150 || offer.getTotalQuantity()-offer.getFilledQuantity()!=quantity
                    || projection==null || projection.getState()!=OfferLifecycleClassifier.classify(offer)
                    || projection.getLastSequence()!=offer.getSequence() || projection.getLastObservedAt()!=offer.getObservedAt()
                    || !Objects.equals(projection.getOfferIdentity(),offer.getOfferIdentity())
                    || !Objects.equals(projection.getSessionId(),offer.getSessionId()) || projection.getItemId()!=item || projection.isBuying()
                    || projection.getPrice()!=offer.getPrice() || projection.getSpent()!=offer.getSpent()
                    || projection.getTotalQuantity()!=offer.getTotalQuantity() || projection.getFilledQuantity()!=offer.getFilledQuantity())
                    return refusal(type,identity,source,now,"SALE_OFFER_CHANGED");
                var proposed=new SaleMaintenancePricing().quote(item,offer.getPrice(),quantity,position,quote,series,horizon,at,account.isSellOnly(),exits,tax,
                    offer.getFirstSeenAt(),read.saleProgressAt.getOrDefault(slot,Long.MAX_VALUE));
                if(proposed==null || proposed.lossCut!=cut || Math.abs((long)proposed.price-price)>=horizon.outbidDeadband(proposed.price)
                    || offer.getPrice()-(long)price<horizon.outbidDeadband(offer.getPrice())
                    || !cut && proposed.costKnown && (proposed.floor<=0 || price<proposed.floor))
                    return refusal(type,identity,source,now,"SALE_PRICE_NOT_AUTHORIZED");
            } else {
                if(position==null || quantity>position.getQuantity())return refusal(type,identity,source,now,"SALE_ACQUISITION_UNAVAILABLE");
                if(account.getObservedHoldings()==null || account.getObservedHoldings().getOrDefault(item,0)<quantity)
                    return refusal(type,identity,source,now,"SALE_STOCK_NOT_OBSERVED");
                PositionProjection canonical=null;for(var p:ledger.getTradingPositions())if(p.getItemId()==item)canonical=p;
                if(canonical==null || !source.equals(canonical.getSourceEventId()))return refusal(type,identity,source,now,"SALE_ACQUISITION_CHANGED");
                int listed=0;
                for(var offer:read.currentEvents.values())if(offer.getItemId()==item && !"EMPTY".equals(offer.getEventType())) {
                    if(offer.isBuying())return refusal(type,identity,source,now,"PURCHASE_STILL_IN_PROGRESS");
                    listed=Math.addExact(listed,offer.getTotalQuantity()-offer.getFilledQuantity());
                }
                if(quantity>position.getQuantity()-listed)return refusal(type,identity,source,now,"SALE_QUANTITY_ALREADY_LISTED");
                var decision=exits.evaluateManagedExit(position,quote,null,series,horizon,at,account.getMinProfitPerFlip(),true,account.isSellOnly(),false);
                if(!decision.isSell() || (decision.getAction()==SellDecision.Action.CUT)!=cut
                    || Math.abs((long)decision.getPrice()-price)>=horizon.outbidDeadband(decision.getPrice()))
                    return refusal(type,identity,source,now,"SALE_EXIT_CHANGED");
                long cost=SaleAccounting.allocateCost(position.getTotalCost(),position.getQuantity(),quantity);
                if(!cut && (!account.isSellOnly() || quote.stalestSideSeconds(at)>300)) {
                    int floor=tax.breakEvenSellPriceForCost(item,cost,quantity);
                    if(floor<=0 || price<SellTimingEngine.protectedPrice(position,horizon,at,floor))
                        return refusal(type,identity,source,now,"SALE_EXACT_COST_FLOOR");
                }
            }
            return PolicyDecision.action("1",VERSION,identity+":"+now,type,slot<0?OfferLifecycleAction.PLACE_SELL:OfferLifecycleAction.CANCEL_SELL,
                "SALE_AUTHORIZED",now,Math.min(account.getObservedAt(),market.observedAt),identity,source);
        } catch(RuntimeException unsafe) {return refusal(type,identity,source,now,"SALE_EVIDENCE_INCONSISTENT");}
    }
    private static PolicyDecision refusal(PolicyDecisionType type,String identity,String source,long at,String reason) {
        return PolicyDecision.abstain("1",VERSION,identity+":"+at,type,PolicyAbstentionReason.ACCOUNT_STATE_INCONSISTENT,reason,at,at,identity,source);
    }
}
