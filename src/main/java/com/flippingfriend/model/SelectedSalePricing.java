package com.flippingfriend.model;

import com.flippingfriend.TradingHorizon;
import com.flippingfriend.core.AccountLedgerSnapshot;
import com.flippingfriend.core.SaleAccounting;
import com.flippingfriend.data.Candle;
import com.flippingfriend.data.MarketSnapshot;
import com.flippingfriend.session.Position;
import com.flippingfriend.session.SellDecision;
import com.flippingfriend.session.SellTimingEngine;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Selected sales use the same acquired-stock and exact-cost policy as normal exits. */
@Singleton
public final class SelectedSalePricing {
    private final SellTimingEngine exits;
    private final TaxCalculator tax;

    @Inject public SelectedSalePricing(SellTimingEngine exits, TaxCalculator tax) {
        this.exits=exits;this.tax=tax;
    }

    /** Produces a proposal only. Managed proposals still require companion final authority. */
    public Suggestion quote(int item,int selected,int inventory,String account,AccountLedgerSnapshot ledger,
            Map<Integer,Integer> listed,Set<Integer> buying,MarketSnapshot market,List<Candle> series,
            TradingHorizon horizon,Instant now,boolean sellOnly) {
        return quote(item,selected,inventory,account,ledger,listed,buying,market,series,horizon,now,sellOnly,null);
    }
    public Suggestion quote(int item,int selected,int inventory,String account,AccountLedgerSnapshot ledger,
            Map<Integer,Integer> listed,Set<Integer> buying,MarketSnapshot market,List<Candle> series,
            TradingHorizon horizon,Instant now,boolean sellOnly,com.flippingfriend.session.SellRelistIntent.CollectedRemainder reprice) {
        if(item<=0 || selected<=0 || inventory<selected)return waiting("Check the selected quantity",
            "Only units currently observed in inventory can be listed.");
        if(ledger==null || !ledger.isValid() || !java.util.Objects.equals(account,ledger.getAccountId())
            || ledger.getTradingPositions()==null || ledger.getObservedAt()>now.getEpochSecond()
            || now.getEpochSecond()-ledger.getObservedAt()>150)
            return waiting("Checking selected stock","Waiting for current account purchase records before pricing this sale.");
        boolean paused=ledger.hasUnboundedReconciliation();
        for(var gap:ledger.getMissedFlips())if("RECONCILE".equals(gap.getType())) {
            var scope=ledger.getReconciliationScope();
            if(gap.getItemId()==item || scope==null || scope.get(gap.getEventId())==null
                || scope.get(gap.getEventId()).contains(item))paused=true;
        }
        if(paused)return waiting("Checking purchase evidence",
            "The selected item's trade evidence is unresolved. Its recorded stock and cost are retained.");
        if(reprice!=null && (reprice.getItemId()!=item || reprice.getQuantity()<selected))
            return waiting("Checking the returned sale quantity","The selection exceeds this cancellation's proven collected remainder.");
        var acquired=ledger.getTradingPositions().stream().filter(p->p.getItemId()==item).findFirst().orElse(null);
        if(acquired==null) {
            var personal=ManualSalePrice.quote(item,selected,inventory,market,now);
            if(personal!=null && reprice!=null && reprice.getItemId()==item && selected<=reprice.getQuantity())
                personal.bindContinuationOrigin(reprice.getOriginIdentity());
            return personal;
        }
        int available=Math.max(0,acquired.getQuantity()-listed.getOrDefault(item,0));
        if(selected>available)return waiting("Select only the remaining managed stock",
            "There are "+available+" recorded, unlisted units. The larger selection may include personal items; it cannot borrow the trade's cost or plan.");
        if(buying.contains(item))return waiting("Purchase still in progress",
            "Finish or cancel and collect the remaining purchase before listing its acquired units.");
        Position position=new Position(item,acquired.getItemName(),acquired.getQuantity(),acquired.getTotalCost(),acquired.getOpenedAt(),true);
        position.applyExitContexts(acquired.getExitContexts());
        var decision=exits.evaluateManagedExit(position,market.latest(item),null,series,horizon,now,0,true,sellOnly,false);
        if(!decision.isSell())return waiting("Waiting for a supported sell price",decision.getReason());
        boolean cut=decision.getAction()==SellDecision.Action.CUT;
        long cost=SaleAccounting.allocateCost(position.getTotalCost(),position.getQuantity(),selected);
        int price=decision.getPrice();
        int floor=tax.breakEvenSellPriceForCost(item,cost,selected);
        var reduced=reprice==null || reprice.getSaleRepriceSource()==null?null
            :new com.flippingfriend.session.SaleMaintenancePricing().evaluateRelist(item,reprice.getOriginalPrice(),selected,position,
                market.latest(item),series,horizon,now,sellOnly,exits,tax,reprice.getFirstSeenAt(),reprice.getProgressAt(),
                reprice.getAcceptedSalePolicyProof()==null?null:reprice.getAcceptedSalePolicyProof().getReceipt(),market.getLatestFeedObservedAt()).quote;
        if(reprice!=null && reprice.getSaleRepriceSource()!=null && reduced==null)
            return waiting("Rechecking the returned sale",
                "Current prices and market evidence do not support this selected relist. Its saved remainder and accepted reduction are retained; the old target is not restored.");
        if(reduced!=null) {price=reduced.price;floor=reduced.floor;cut=reduced.lossCut;}
        else if(!cut && !sellOnly) {
            if(floor<=0)return waiting("No non-loss sell price is available","No legal price covers the exact recorded subset cost after tax.");
            price=Math.max(price,SellTimingEngine.protectedPrice(position,horizon,now,floor));
        }
        var forecast=exits.forecastForOrder(item,price,selected,series,horizon);
        double minutes=forecast==null?Double.NaN:forecast.getExpectedTotalSeconds()/60;
        return Suggestion.builder(SuggestionType.SELL).item(item,position.getItemName()).quantity(selected).price(price)
            .lossCut(cut).profitKnown(true).expectedProfit(tax.netProceeds(item,price,selected)-cost)
            .breakEvenPrice(floor).targetSellPrice(position.getTargetSellPrice())
            .expectedMinutes(minutes).fillMinutes(0,minutes).executionForecast(forecast)
            .legCompletionProbability(forecast==null?null:forecast.getCompletionProbability())
            .saleRepriceSource(reprice==null?null:reprice.getSaleRepriceSource())
            .continuationOrigin(reprice==null?null:reprice.getOriginIdentity())
            .adviceContext(account,null,0,now.getEpochSecond()+5)
            .headline((cut?"Cut your losses on ":"Sell your ")+position.getItemName())
            .detail((reduced==null?decision.getReason():reduced.reason)+" This quote covers only the selected recorded units; its timing estimate is unvalidated.").build();
    }
    private static Suggestion waiting(String title,String detail){return Suggestion.waiting(title,detail);}
}
