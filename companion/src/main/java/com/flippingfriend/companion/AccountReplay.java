package com.flippingfriend.companion;

import com.flippingfriend.*;
import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.flippingfriend.model.*;
import com.flippingfriend.session.*;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/** One simulated account, one bankroll and eight physical slots; never opens a live database. */
final class AccountReplay implements AutoCloseable {
    static final String VERSION="account-replay-2";
    private static final String ACCOUNT=AccountIdentity.fromProfileKey("synthetic-account-replay-only");
    interface Entries {
        PortfolioPlan plan(MarketIngestionService.MarketState market,AccountSnapshot account,
            Map<Integer,Integer> remaining,Collection<OfferEvent> offers,Instant at);
    }
    static final class Offer {
        final int slot,item,price,quantity; final boolean buy; final long placedAt; final String identity;
        int filled;long spent,collectionCoins;boolean terminal,cancelled;
        Offer(int slot,int item,int price,int quantity,boolean buy,long at,String id) {
            this.slot=slot;this.item=item;this.price=price;this.quantity=quantity;this.buy=buy;placedAt=at;identity=id;
        }
        int remaining(){return quantity-filled;}
    }
    static final class Result {
        final String schemaVersion="1",evidence="SIMULATED_PUBLIC_VOLUME_SHARE",policyVersion=VERSION;
        final String completionModel;
        final int cadenceMinutes,captureShareBps,actionSeconds,missedVisits;
        final long elapsedSeconds,initialCoins,bagCoins,lockedBuyCoins,collectionCoins,inventoryCost,terminalValueLower;
        final long equityLower,netEquityChangeLower,realizedProfit,tax,maximumDrawdown,interactions,completedSales,losingSales;
        final boolean terminalMarksComplete;
        final double netEquityGpPerElapsedHourLower,realizedGpPerElapsedHour;
        final Map<String,Integer> abstentions;
        final List<String> actions;
        Result(AccountReplay r,long end) throws Exception {
            cadenceMinutes=r.cadence;captureShareBps=r.share;actionSeconds=r.input.actionSeconds;missedVisits=r.missed;
            completionModel=r.completionModel;
            elapsedSeconds=end-r.input.startAt;initialCoins=r.input.initialCoins;bagCoins=r.cash;
            lockedBuyCoins=r.locked();collectionCoins=r.collection();inventoryCost=r.cost();
            long marked=r.mark(end,true);terminalValueLower=marked;terminalMarksComplete=r.marksComplete;
            equityLower=Math.addExact(Math.addExact(bagCoins,lockedBuyCoins),Math.addExact(collectionCoins,marked));
            netEquityChangeLower=equityLower-initialCoins;realizedProfit=r.realized;tax=r.taxPaid;
            maximumDrawdown=r.drawdown;interactions=r.interactions;completedSales=r.saleUpdates;losingSales=r.losses;
            netEquityGpPerElapsedHourLower=netEquityChangeLower*3600.0/elapsedSeconds;
            realizedGpPerElapsedHour=realizedProfit*3600.0/elapsedSeconds;
            abstentions=Collections.unmodifiableMap(new TreeMap<>(r.abstentions));actions=List.copyOf(r.actions);
            var ledger=r.store.ledgerSnapshot(ACCOUNT,end);
            if(ledger.getUnresolvedSales()!=0 || ledger.getProfitLower()!=realizedProfit || ledger.getProfitUpper()!=realizedProfit
                || ledger.getTaxLower()!=tax || ledger.getTaxUpper()!=tax)
                throw new IllegalStateException("Replay and canonical sale accounting disagree");
        }
    }
    private final AccountReplayInput input;
    private final int cadence,share;
    private SqliteStore store;
    private final Path syntheticDatabase;
    private final Gson json=new Gson();
    private final Offer[] offers=new Offer[8];
    private final Map<Integer,Position> stocks=new TreeMap<>();
    private final Map<Integer,Integer> inventory=new TreeMap<>();
    private final BuyLimitLedger limits=new BuyLimitLedger();
    private final TaxCalculator tax=new TaxCalculator();
    private final SellTimingEngine exits=new SellTimingEngine(new FillModel(),tax);
    private final SaleMaintenancePricing maintenance=new SaleMaintenancePricing();
    private final AdvisedPriceTracker advisedPrices=new AdvisedPriceTracker();
    private final TradingHorizon horizon;
    private final Entries entries;
    private final Map<String,Integer> abstentions=new TreeMap<>();
    private final List<String> actions=new ArrayList<>();
    private AccountReplayInput.Frame frame;
    private long now,sequence,cash,realized,taxPaid,interactions,saleUpdates,losses,drawdown,peak;
    private int missed; private boolean marksComplete=true;
    private final String completionModel;
    private final boolean baseline;

    AccountReplay(AccountReplayInput input,int cadence,int share,Path newSyntheticDatabase) throws Exception {
        this(input,cadence,share,newSyntheticDatabase,null);
    }
    AccountReplay(AccountReplayInput input,int cadence,int share,Path newSyntheticDatabase,Entries fixture) throws Exception {
        this(input,cadence,share,newSyntheticDatabase,fixture,null);
    }
    AccountReplay(AccountReplayInput input,int cadence,int share,Path newSyntheticDatabase,Entries fixture,ExecutionChallenger challenger) throws Exception {
        input.validate();
        if(!List.of(5,30,120,480).contains(cadence) || share<1 || share>100 || java.nio.file.Files.exists(newSyntheticDatabase))
            throw new IllegalArgumentException("Four benchmark cadences, 0.01–1% volume share and a new synthetic database required");
        this.input=input;this.cadence=cadence;this.share=share;now=input.startAt;cash=input.initialCoins;peak=cash;
        horizon=TradingHorizon.of(RiskProfile.valueOf(input.risk),interval(cadence));
        SeriesSource source=(item,step)->frame==null?List.of():
            ("5m".equals(step)?frame.shortHistory:frame.longHistory).getOrDefault(item,List.of());
        PortfolioPlanner planner=new PortfolioPlanner(source);
        baseline=challenger==null;
        if(challenger!=null) {
            if(input.startAt<=challenger.capturedAt)throw new IllegalArgumentException("Challenger replay must start after frozen training capture");
            planner.setCompletionScoring(challenger::probability);
        }
        completionModel=challenger==null?ExecutionForecast.MODEL_VERSION:ExecutionEvaluationMain.hash(AccountReplayMain.bytes(challenger));
        entries=fixture==null?planner::plan:fixture;
        List<ItemMetadata> metadata=new ArrayList<>();
        for(var item:input.mapping.values())metadata.add(new ItemMetadata(item.id,item.name,item.members,item.buyLimit,0));
        tax.resolveExemptions(metadata);syntheticDatabase=newSyntheticDatabase;store=new SqliteStore(newSyntheticDatabase);
    }
    Result run() throws Exception {
        int index=0,visitIndex=0,restartIndex=0;
        while(index<input.frames.size() && input.frames.get(index).availableAt<=now)frame=input.frames.get(index++);
        capture();
        long visit=input.startAt,action=Long.MAX_VALUE,visitEnd=0;
        while(true) {
            long market=index<input.frames.size()?input.frames.get(index).availableAt:Long.MAX_VALUE;
            long restart=restartIndex<input.restartAt.size()?input.restartAt.get(restartIndex):Long.MAX_VALUE;
            long next=Math.min(restart,Math.min(market,Math.min(visit,action)));if(next>input.endAt)break;now=next;
            // Publication precedes decisions at the same instant; no flow can fill a newly placed order in its old bucket.
            if(market==next) {frame=input.frames.get(index++);for(var flow:frame.flows)flow(flow);}
            if(restart==next) {
                store.close();store=new SqliteStore(syntheticDatabase);
                limits.restore(store.buyLimitProjections(),Instant.ofEpochSecond(now));restartIndex++;
                advisedPrices.clear(); // The live display cache is session-local too.
                // The simulated game world persists. No old financial observation is replayed.
                var ledger=store.ledgerSnapshot(ACCOUNT,now);
                if(ledger.getTradingPositions().size()!=stocks.size())throw new IllegalStateException("Restart changed acquisition coverage");
                for(var p:ledger.getTradingPositions()) {
                    Position expected=stocks.get(p.getItemId());
                    if(expected==null || expected.getQuantity()!=p.getQuantity() || expected.getTotalCost()!=p.getTotalCost())
                        throw new IllegalStateException("Restart changed acquisition quantity/cost");
                }
            }
            if(visit==next) {
                if(input.missedVisitIndices.contains(visitIndex++))missed++;
                else {action=Math.addExact(now,input.actionSeconds);visitEnd=Math.addExact(now,input.visitSeconds);}
                visit=Math.addExact(visit,cadence*60L);
            }
            if(action==next) {
                boolean acted=act();
                action=acted && now+input.actionSeconds<=visitEnd?now+input.actionSeconds:Long.MAX_VALUE;
            }
            long equity=Math.addExact(cash+locked()+collection(),mark(now,true));
            peak=Math.max(peak,equity);drawdown=Math.max(drawdown,peak-equity);
        }
        now=input.endAt;return new Result(this,now);
    }
    private boolean act() throws Exception {
        AccountSnapshot account=capture();
        CompanionAction collection=new CompanionActionSelector().select(events());
        if(collection.isActionable()) {collect(offers[collection.getSlot()]);return true;}
        MarketIngestionService.MarketState market=frame.market(input.mapping);
        if(now-market.observedAt>120){abstain("MARKET_DATA_STALE");return false;}
        var buyDecision=new BuyMaintenanceServiceAdapter().decide(events(),market,limits,now);
        if(buyDecision!=null && buyDecision.getAction()==OfferLifecycleAction.CANCEL_BUY) {
            for(Offer offer:offers)if(offer!=null && offer.buy && offer.identity.equals(buyDecision.getCandidateId())) {cancel(offer);return true;}
        }
        for(Offer offer:offers)if(offer!=null && !offer.buy && !offer.terminal
            && now-offer.placedAt>=horizon.patienceMinutes(stocks.get(offer.item)==null?0:stocks.get(offer.item).getPredictedSellMinutes())*60) {
            var proposal=maintenance.quote(offer.item,offer.price,offer.remaining(),stocks.get(offer.item),latest(offer.item),
                frame.shortHistory.getOrDefault(offer.item,List.of()),horizon,Instant.ofEpochSecond(now),false,exits,tax);
            if(proposal!=null){
                int price=advisedPrices.price(offer.item,false,proposal.price,now);
                if(!proposal.lossCut && proposal.costKnown) {
                    if(proposal.floor<=0)continue;price=Math.max(price,proposal.floor);
                }
                if(price>=offer.price)continue;
                cancel(offer);return true;
            }
        }
        int slot=freeSlot();
        if(slot<0){abstain("NO_FREE_SLOT");return false;}
        for(var stock:stocks.values()) {
            int quantity=inventory.getOrDefault(stock.getItemId(),0);if(quantity<=0 || offered(stock.getItemId()))continue;
            LatestPrice quote=latest(stock.getItemId());
            if(quote==null || !quote.isComplete() || quote.secondsSinceHigh(Instant.ofEpochSecond(now))<0
                || quote.secondsSinceLow(Instant.ofEpochSecond(now))<0 || quote.stalestSideSeconds(Instant.ofEpochSecond(now))>300)continue;
            var decision=exits.evaluate(stock,quote,null,frame.shortHistory.getOrDefault(stock.getItemId(),List.of()),
                horizon,Instant.ofEpochSecond(now),0,true,false,false);
            if(!decision.isSell())continue;
            int price=advisedPrices.price(stock.getItemId(),false,decision.getPrice(),now);
            long cost=SaleAccounting.allocateCost(stock.getTotalCost(),stock.getQuantity(),quantity);
            if(decision.getAction()!=SellDecision.Action.CUT) {
                int floor=tax.breakEvenSellPriceForCost(stock.getItemId(),cost,quantity);
                if(floor<=0)continue;price=Math.max(price,floor);
            }
            place(slot,stock.getItemId(),price,quantity,false);return true;
        }
        var read=store.planningProjections(ACCOUNT);
        var exposure=DurablePlanningExposure.forAccount(read,account,now);
        if(exposure==null){abstain("CURRENT_EVIDENCE_UNAVAILABLE");return false;}
        var risk=DurableRisk.evaluate(read,account,exposure,market,now);
        if(!risk.isAvailable()){abstain("DURABLE_RISK_UNAVAILABLE: "+risk.getReason());return false;}
        account=account.withPlanningRestrictions(exposure.committedByItem,exposure.unavailableEntryItems).withCanonicalRisk(risk);
        Map<Integer,Integer> remaining=limits.remaining(input.mapping,Instant.ofEpochSecond(now));
        PortfolioPlan plan=entries.plan(market,account,remaining,events(),Instant.ofEpochSecond(now));
        PortfolioAllocation allocation=plan.getAllocations().isEmpty()?null:plan.getAllocations().get(0);
        PolicyDecision entry=new CompanionEntryPolicy().decide(new EntryPolicyContext(plan,allocation,now));
        if(entry.getAction()!=OfferLifecycleAction.PLACE_BUY){abstain(entry.getReasonCode());return false;}
        PortfolioCandidate c=allocation.getCandidate();
        var constraints=PortfolioPlanner.constraintsFor(account,RiskAppetite.forName(account.getRiskAppetite()),
            PortfolioPlanner.lossBudgetFor(account,now),PortfolioPlanner.committedByGroupFor(account,input.mapping),now);
        if(PortfolioPlanner.rejected(c,account) || remaining.getOrDefault(c.getItemId(),0)<c.getQuantity()
            || new PortfolioOptimizer().optimize(List.of(c),constraints,plan.getCorrelationId(),now).getAllocations().isEmpty()) {
            abstain("ENTRY_CONSTRAINTS_CHANGED");return false;
        }
        place(slot,c.getItemId(),c.getBuyPrice(),c.getQuantity(),true);return true;
    }
    private void place(int slot,int item,int price,int quantity,boolean buy) throws Exception {
        if(quantity<=0 || price<=0 || offers[slot]!=null)throw new IllegalStateException("Invalid simulated placement");
        if(buy)cash=Math.subtractExact(cash,Math.multiplyExact((long)price,quantity));
        else inventory.merge(item,-quantity,Integer::sum);
        if(cash<0 || inventory.getOrDefault(item,0)<0)throw new IllegalStateException("Shared capital or inventory overspent");
        Offer offer=new Offer(slot,item,price,quantity,buy,now,"sim-offer-"+(++sequence));offers[slot]=offer;
        publish(offer);action((buy?"BUY":"SELL")+" "+item+" "+quantity+" @ "+price);
    }
    private void cancel(Offer offer) throws Exception {
        offer.terminal=true;offer.cancelled=true;publish(offer);action("CANCEL "+offer.identity+" remaining "+offer.remaining());
    }
    private void collect(Offer offer) throws Exception {
        if(offer.buy) {
            inventory.merge(offer.item,offer.filled,Integer::sum);
            cash=Math.addExact(cash,(long)offer.remaining()*offer.price);
        } else {inventory.merge(offer.item,offer.remaining(),Integer::sum);cash=Math.addExact(cash,offer.collectionCoins);}
        int slot=offer.slot;offers[slot]=null;publishEmpty(slot);action("COLLECT "+offer.identity);
    }
    private void flow(AccountReplayInput.Flow flow) throws Exception {
        if(flow.bucketEnd>now)throw new IllegalStateException("Future execution bucket");
        int buyBudget=(int)((long)flow.lowVolume*share/10000),sellBudget=(int)((long)flow.highVolume*share/10000);
        for(Offer offer:offers)if(offer!=null && offer.item==flow.itemId && !offer.terminal
            && offer.placedAt+input.actionSeconds<=flow.bucketStart) {
            int budget=offer.buy?buyBudget:sellBudget;
            if(offer.buy?offer.price<flow.low:offer.price>flow.high)continue;
            int quantity=Math.min(offer.remaining(),budget);
            if(offer.buy)quantity=Math.min(quantity,limits.remainingAt(offer.item,input.mapping.get(offer.item).buyLimit,Instant.ofEpochSecond(now)));
            if(quantity<=0)continue;
            offer.filled+=quantity;offer.spent=Math.addExact(offer.spent,(long)offer.price*quantity);
            if(offer.buy) {
                buyBudget-=quantity;
                var p=stocks.get(offer.item);
                if(p==null)stocks.put(offer.item,new Position(offer.item,name(offer.item),quantity,(long)offer.price*quantity,now,true));
                else p.addFill(quantity,(long)offer.price*quantity);
            } else {
                sellBudget-=quantity;Position p=stocks.get(offer.item);
                long cost=SaleAccounting.allocateCost(p.getTotalCost(),p.getQuantity(),quantity);
                long net=tax.netProceeds(offer.item,offer.price,quantity),profit=net-cost;
                int left=p.getQuantity()-quantity;
                if(left==0)stocks.remove(offer.item);else stocks.put(offer.item,new Position(offer.item,p.getItemName(),left,p.getTotalCost()-cost,p.getOpenedAt(),true));
                offer.collectionCoins=Math.addExact(offer.collectionCoins,net);realized=Math.addExact(realized,profit);
                taxPaid=Math.addExact(taxPaid,(long)offer.price*quantity-net);saleUpdates++;if(profit<0)losses++;
            }
            offer.terminal=offer.remaining()==0;publish(offer);
        }
    }
    private AccountSnapshot capture() throws Exception {
        Map<Integer,String> readSet=new TreeMap<>();Set<Integer> onOffer=new HashSet<>();
        for(int slot=0;slot<8;slot++) {
            OfferEvent event=offers[slot]==null?publishEmpty(slot):publish(offers[slot]);readSet.put(slot,event.getEventId());
            if(offers[slot]!=null)onOffer.add(offers[slot].item);
        }
        List<PositionSnapshot> positions=new ArrayList<>();Map<Integer,Long> exposure=new TreeMap<>();
        for(var p:stocks.values()) {
            positions.add(new PositionSnapshot(p.getItemId(),p.getItemName(),p.getQuantity(),p.getTotalCost(),true,p.getOpenedAt(),0,0,0));
            exposure.put(p.getItemId(),p.getTotalCost());
        }
        for(Offer o:offers)if(o!=null && o.buy)exposure.merge(o.item,(long)o.remaining()*o.price,Math::addExact);
        AccountSnapshot a=new AccountSnapshot("replay:"+cadence+":"+now,now,cash,locked(),8-(int)Arrays.stream(offers).filter(Objects::nonNull).count(),8,
            true,false,0,input.risk,exposure,input.minimumProfit,Map.of(),baseline,cadence,Set.of(),Set.of(),onOffer,false,0,positions)
            .withAccountId(ACCOUNT).withAutomaticTradeScope().withOfferReadSet(readSet).withObservedHoldings(inventory);
        store.recordAndReconcileAccount(a,PositionStateView.from(a),json.toJson(a),now);return a;
    }
    private OfferEvent publishEmpty(int slot) throws Exception {
        var e=OfferEvent.builder("synthetic-replay",now,"EMPTY").accountId(ACCOUNT).slot(slot)
            .eventIdentity("sim-event-"+(++sequence),"synthetic-session",null).sequence(sequence).authenticatedObservation(true).build();accept(e);return e;
    }
    private OfferEvent publish(Offer o) throws Exception {var e=event(o);accept(e);limits.restore(store.buyLimitProjections(),Instant.ofEpochSecond(now));return e;}
    private OfferEvent event(Offer o) {
        String type=o.terminal?(o.cancelled?(o.buy?"CANCELLED_BUY":"CANCELLED_SELL"):(o.buy?"BOUGHT":"SOLD")):(o.buy?"BUYING":"SELLING");
        return OfferEvent.builder("synthetic-replay",now,type).accountId(ACCOUNT).slot(o.slot).item(o.item,name(o.item)).buying(o.buy)
            .price(o.price).quantities(o.quantity,o.filled).spent(o.spent).firstSeenAt(o.placedAt)
            .eventIdentity("sim-event-"+(++sequence),"synthetic-session",o.identity).sequence(sequence).authenticatedObservation(true)
            .recommendation("sim-plan-"+o.identity,o.price,o.quantity,0,0,0).build();
    }
    private void accept(OfferEvent e) throws Exception {
        var result=store.recordAndProjectOffer(e,json.toJson(e),true);
        if(result.transition.getAction()==OfferLifecycleAction.RECONCILE)throw new IllegalStateException("Synthetic observation violated canonical acceptance");
    }
    private Collection<OfferEvent> events(){List<OfferEvent> values=new ArrayList<>();for(Offer o:offers)if(o!=null)values.add(event(o));return values;}
    private long mark(long at,boolean haircut) {
        marksComplete=true;long value=0;
        for(Position p:stocks.values()) {
            LatestPrice q=latest(p.getItemId());Instant instant=Instant.ofEpochSecond(at);
            if(q==null || q.getLow()==null || q.getLow()<=0 || q.secondsSinceLow(instant)<0 || q.secondsSinceLow(instant)>120) {marksComplete=false;continue;}
            long net=tax.netProceeds(p.getItemId(),q.getLow(),p.getQuantity());
            if(haircut)net=net*(10000-input.terminalHaircutBps)/10000;
            value=Math.addExact(value,net);
        }
        return value;
    }
    private LatestPrice latest(int item){var q=frame.latest.get(Integer.toString(item));return q==null?null:json.fromJson(q,LatestPrice.class);}
    private int freeSlot(){for(int i=0;i<8;i++)if(offers[i]==null)return i;return -1;}
    private boolean offered(int item){for(Offer o:offers)if(o!=null && o.item==item)return true;return false;}
    private long locked(){long n=0;for(Offer o:offers)if(o!=null && o.buy)n=Math.addExact(n,(long)o.remaining()*o.price);return n;}
    private long collection(){long n=0;for(Offer o:offers)if(o!=null)n=Math.addExact(n,o.collectionCoins);return n;}
    private long cost(){long n=0;for(Position p:stocks.values())n=Math.addExact(n,p.getTotalCost());return n;}
    private String name(int item){return input.mapping.get(item).name;}
    private void action(String value){interactions++;actions.add(now+" "+value);}
    private void abstain(String reason){abstentions.merge(reason,1,Integer::sum);}
    static CheckInterval interval(int cadence) {
        for(CheckInterval c:CheckInterval.values())if(c.getMinutes()==cadence)return c;
        throw new IllegalArgumentException("Unsupported cadence");
    }
    @Override public void close() throws Exception {store.close();}
}
