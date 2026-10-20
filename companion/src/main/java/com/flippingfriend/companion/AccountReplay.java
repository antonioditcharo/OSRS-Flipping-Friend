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
    static final String VERSION="account-replay-9";
    private static final String ACCOUNT=AccountIdentity.fromProfileKey("synthetic-account-replay-only");
    interface Entries {
        PortfolioPlan plan(MarketIngestionService.MarketState market,AccountSnapshot account,
            Map<Integer,Integer> remaining,Collection<OfferEvent> offers,Instant at);
    }
    static final class Offer {
        final int slot,item,price,quantity; final boolean buy; final long placedAt; final String identity;
        int filled;long spent,collectionCoins,lastProgressAt,realizedProfit;boolean terminal,cancelled;
        OfferEvent origin,lastObservation;
        Offer(int slot,int item,int price,int quantity,boolean buy,long at,String id) {
            this.slot=slot;this.item=item;this.price=price;this.quantity=quantity;this.buy=buy;placedAt=at;lastProgressAt=at;identity=id;
        }
        int remaining(){return quantity-filled;}
    }
    /** Displayed instruction, frozen before the player's later action. Never backdate advice. */
    private static final class Guidance {
        final String kind,offerIdentity,acquisitionSource,plan;
        final int slot,item,price,quantity;
        final long advisedAt,cost,spent,validUntil;
        final boolean cut;
        final PortfolioCandidate entry;
        final AcquisitionExitContext context;
        Guidance(String kind,int slot,int item,int price,int quantity,String offerIdentity,String acquisitionSource,
            long cost,long spent,boolean cut,long at,PortfolioCandidate entry,String plan,AcquisitionExitContext context,long validUntil) {
            this.kind=kind;this.slot=slot;this.item=item;this.price=price;this.quantity=quantity;
            this.offerIdentity=offerIdentity;this.acquisitionSource=acquisitionSource;this.cost=cost;this.spent=spent;
            this.cut=cut;advisedAt=at;this.entry=entry;this.plan=plan;this.context=context;
            this.validUntil=validUntil;
        }
        boolean sameOrder(Guidance next) {
            return next!=null && kind.equals(next.kind) && slot==next.slot && item==next.item && price==next.price
                && quantity==next.quantity && cost==next.cost && spent==next.spent && cut==next.cut
                && Objects.equals(offerIdentity,next.offerIdentity) && Objects.equals(acquisitionSource,next.acquisitionSource)
                && Objects.equals(context,next.context);
        }
    }
    static final class Result {
        final String schemaVersion="1",evidence="SIMULATED_PUBLIC_VOLUME_SHARE",policyVersion=VERSION;
        final String completionModel;
        final String salePolicyVersion=CompanionSalePolicy.VERSION;
        final int cadenceMinutes,captureShareBps,actionSeconds,missedVisits;
        final long elapsedSeconds,initialCoins,bagCoins,lockedBuyCoins,collectionCoins,inventoryCost,terminalValueLower;
        final long equityLower,netEquityChangeLower,realizedProfit,tax,maximumDrawdown,interactions,completedSales,losingSales,saleUpdates,losingSaleUpdates;
        final boolean terminalMarksComplete;
        final long authorizedSales,withdrawnGuidance,replacementBuys,relistedSales;
        final double netEquityGpPerElapsedHourLower,realizedGpPerElapsedHour;
        final Map<String,Integer> abstentions;
        final List<String> actions;
        Result(AccountReplay r,long end) throws Exception {
            cadenceMinutes=r.cadence;captureShareBps=r.share;actionSeconds=r.input.actionSeconds;missedVisits=r.missed;
            completionModel=r.completionModel;
            authorizedSales=r.authorizedSales;withdrawnGuidance=r.withdrawnGuidance;
            replacementBuys=r.replacementBuys;relistedSales=r.relistedSales;
            elapsedSeconds=end-r.input.startAt;initialCoins=r.input.initialCoins;bagCoins=r.cash;
            lockedBuyCoins=r.locked();collectionCoins=r.collection();inventoryCost=r.cost();
            long marked=r.mark(end,true);terminalValueLower=marked;terminalMarksComplete=r.marksComplete;
            equityLower=Math.addExact(Math.addExact(bagCoins,lockedBuyCoins),Math.addExact(collectionCoins,marked));
            netEquityChangeLower=equityLower-initialCoins;realizedProfit=r.realized;tax=r.taxPaid;
            maximumDrawdown=r.drawdown;interactions=r.interactions;completedSales=r.completedSales;losingSales=r.losses;
            saleUpdates=r.saleUpdates;losingSaleUpdates=r.losingSaleUpdates;
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
    private final Path continuationFile;
    private static final class Continuations {
        List<OfferContinuation> buy=new ArrayList<>(),sell=new ArrayList<>();
    }
    private Continuations continuations=new Continuations();
    private final List<OfferContinuation> collectedBuys=new ArrayList<>(),collectedSells=new ArrayList<>();
    private final ReplacementEntryPlanner replacementPlanner;
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
    private final SeriesSource history;
    private final Map<String,Integer> abstentions=new TreeMap<>();
    private final List<String> actions=new ArrayList<>();
    private AccountReplayInput.Frame frame;
    private long now,sequence,cash,realized,taxPaid,interactions,saleUpdates,completedSales,losses,losingSaleUpdates,drawdown,peak;
    private long authorizedSales,withdrawnGuidance,replacementBuys,relistedSales;
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
        history=(item,step)->frame==null?List.of():
            ("5m".equals(step)?frame.shortHistory:frame.longHistory).getOrDefault(item,List.of());
        PortfolioPlanner planner=new PortfolioPlanner(history);
        baseline=challenger==null;
        if(challenger!=null) {
            if(input.startAt<=challenger.capturedAt)throw new IllegalArgumentException("Challenger replay must start after frozen training capture");
            planner.setCompletionScoring(challenger::probability);
        }
        completionModel=challenger==null?ExecutionForecast.MODEL_VERSION:ExecutionEvaluationMain.hash(AccountReplayMain.bytes(challenger));
        entries=fixture==null?planner::plan:fixture;
        replacementPlanner=new ReplacementEntryPlanner(entries::plan);
        List<ItemMetadata> metadata=new ArrayList<>();
        for(var item:input.mapping.values())metadata.add(new ItemMetadata(item.id,item.name,item.members,item.buyLimit,0));
        tax.resolveExemptions(metadata);syntheticDatabase=newSyntheticDatabase;
        continuationFile=newSyntheticDatabase.resolveSibling(newSyntheticDatabase.getFileName()+".continuations.json");
        if(java.nio.file.Files.exists(continuationFile))throw new IllegalArgumentException("New synthetic continuation file required");
        store=new SqliteStore(newSyntheticDatabase);
    }
    Result run() throws Exception {
        int index=0,visitIndex=0,restartIndex=0;
        while(index<input.frames.size() && input.frames.get(index).availableAt<=now)frame=input.frames.get(index++);
        capture();
        long visit=input.startAt,action=Long.MAX_VALUE,visitEnd=0;
        Guidance pending=null;
        while(true) {
            long market=index<input.frames.size()?input.frames.get(index).availableAt:Long.MAX_VALUE;
            long restart=restartIndex<input.restartAt.size()?input.restartAt.get(restartIndex):Long.MAX_VALUE;
            long next=Math.min(restart,Math.min(market,Math.min(visit,action)));if(next>input.endAt)break;now=next;
            boolean visitStarting=visit==next;
            // Publication precedes decisions at the same instant; no flow can fill a newly placed order in its old bucket.
            if(market==next) {frame=input.frames.get(index++);for(var flow:frame.flows)flow(flow);}
            if(restart==next) {
                store.close();store=new SqliteStore(syntheticDatabase);
                limits.restore(store.buyLimitProjections(),Instant.ofEpochSecond(now));restartIndex++;
                restoreOfferBindings();
                continuations=java.nio.file.Files.exists(continuationFile)
                    ?json.fromJson(java.nio.file.Files.readString(continuationFile),Continuations.class):new Continuations();
                if(continuations==null)throw new IllegalStateException("Missing synthetic continuation state");
                observeContinuations(null);
                advisedPrices.clear(); // The live display cache is session-local too.
                // The simulated game world persists. No old financial observation is replayed.
                var ledger=store.ledgerSnapshot(ACCOUNT,now);
                if(ledger.getTradingPositions().size()!=stocks.size())throw new IllegalStateException("Restart changed acquisition coverage");
                for(var p:ledger.getTradingPositions()) {
                    Position expected=stocks.get(p.getItemId());
                    if(expected==null || expected.getQuantity()!=p.getQuantity() || expected.getTotalCost()!=p.getTotalCost())
                        throw new IllegalStateException("Restart changed acquisition quantity/cost");
                }
                if(pending!=null) {
                    // A restarted foreground must accept guidance again; old displayed advice is withdrawn.
                    withdrawnGuidance++;pending=now<visitEnd && visit!=next?display(guide()):null;
                    action=pending!=null && now+input.actionSeconds<=visitEnd?now+input.actionSeconds:Long.MAX_VALUE;
                }
            }
            if(visitStarting) {
                pending=null;action=Long.MAX_VALUE;visitEnd=0;
                if(input.missedVisitIndices.contains(visitIndex++))missed++;
                else {
                    visitEnd=Math.addExact(now,input.visitSeconds);pending=display(guide());
                    if(pending!=null)action=Math.addExact(now,input.actionSeconds);
                }
                visit=Math.addExact(visit,cadence*60L);
            }
            if(market==next && now<visitEnd && !visitStarting) {
                // An attending player sees newly accepted guidance as evidence arrives. Identical
                // orders keep their original display clock/expiry; changed orders need a full delay.
                Guidance current=guide();
                if(pending==null || !pending.sameOrder(current) || pending.validUntil<=now) {
                    if(pending!=null){withdrawnGuidance++;abstain("DISPLAYED_GUIDANCE_CHANGED");}
                    pending=display(current);
                    action=pending!=null && now+input.actionSeconds<=visitEnd?now+input.actionSeconds:Long.MAX_VALUE;
                }
            }
            if(action==next) {
                Guidance current=guide(); // Fresh canonical sources and final authority at the actual action time.
                if(pending.sameOrder(current) && pending.validUntil>now) {
                    execute(pending);pending=display(guide());
                } else {
                    withdrawnGuidance++;abstain("DISPLAYED_GUIDANCE_CHANGED");pending=display(current);
                }
                action=pending!=null && now+input.actionSeconds<=visitEnd?now+input.actionSeconds:Long.MAX_VALUE;
            }
            long equity=Math.addExact(cash+locked()+collection(),mark(now,true));
            peak=Math.max(peak,equity);drawdown=Math.max(drawdown,peak-equity);
        }
        now=input.endAt;return new Result(this,now);
    }
    private Guidance guide() throws Exception {
        AccountSnapshot account=capture();
        CompanionAction collection=new CompanionActionSelector().select(events());
        if(collection.isActionable())return offerGuidance("COLLECT",offers[collection.getSlot()],0,null,false);
        MarketIngestionService.MarketState market=frame.market(input.mapping);
        if(now-market.observedAt>120){abstain("MARKET_DATA_STALE");return null;}
        observeContinuations(null);Guidance replacement=null;
        for(var continuation:collectedBuys) {
            var planning=currentEntryAccount(account,market);
            if(planning==null)break;
            var reply=replacementPlanner.plan(continuation.getItemId(),continuation.getCollectedRemaining(),market,
                planning,limits.remaining(input.mapping,Instant.ofEpochSecond(now)),events(),now);
            if(reply.getDecision().getAction()!=OfferLifecycleAction.PLACE_BUY){abstain(reply.getDecision().getReasonCode());continue;}
            replacement=entryGuidance(reply.getPlan(),reply.getPlan().getAllocations().get(0).getCandidate(),freeSlot(),
                "REPLACE_BUY",continuation.getOriginalIdentity());break;
        }
        var sale=store.saleProjections(ACCOUNT,now);
        Map<Integer,PositionProjection> canonical=new TreeMap<>();
        for(var p:sale.ledger.getTradingPositions())canonical.put(p.getItemId(),p);
        for(var continuation:collectedSells)if(freeSlot()>=0) {
            var holding=canonical.get(continuation.getItemId());
            var relist=sellGuidance(holding,continuation.getCollectedRemaining(),sale,account,market,
                "RELIST_SELL",continuation.getOriginalIdentity());
            if(relist==null)abstain("RELIST_EVIDENCE_OR_PRICE_UNAVAILABLE");
            else return relist;
        }
        // Live also keeps refused continuations while giving unrelated ready exits their turn.
        ExitProposal best=null;
        for(var holding:canonical.values()) {
            if(continuations.buy.stream().anyMatch(intent->intent.getItemId()==holding.getItemId()))continue;
            if(holding.getQuantity()<=listed(holding.getItemId()))continue;
            Offer buying=openBuy(holding.getItemId());
            if(buying==null && inventory.getOrDefault(holding.getItemId(),0)<=0)continue;
            var decision=exitDecision(holding);
            if(buying!=null) {
                if(decision.getAction()==SellDecision.Action.CUT)
                    return offerGuidance("CANCEL_BUY",buying,0,null,true);
                continue; // Never sell around a healthy purchase that is still accumulating.
            }
            var candidate=saleProposal(holding,Integer.MAX_VALUE,decision);
            if(candidate!=null && (best==null || candidate.moreUrgentThan(best)))best=candidate;
        }
        if(best!=null) {
            if(freeSlot()<0) {
                Offer give=null;
                for(var offer:offers)if(offer!=null && offer.buy && !offer.terminal
                    && (give==null || (long)offer.price*offer.remaining()>(long)give.price*give.remaining()))give=offer;
                if(give!=null)return offerGuidance("CANCEL_BUY",give,0,null,false);
                abstain("EXIT_WAITING_FOR_SLOT");return null;
            }
            return sellGuidance(best,sale,account,market,"SELL",null);
        }
        if(replacement!=null)return replacement;
        var ordered=new ArrayList<Offer>();
        for(var offer:offers)if(offer!=null && !offer.terminal)ordered.add(offer);
        ordered.sort(Comparator.comparingDouble(this::strandedBy).reversed().thenComparingInt(o->o.slot));
        for(Offer offer:ordered) {
            if(offer.buy) {
                if((now-offer.placedAt)/60<horizon.staleOfferMinutes())continue;
                if(limits.remainingAt(offer.item,input.mapping.get(offer.item).buyLimit,Instant.ofEpochSecond(now))<=0)
                    return offerGuidance("CANCEL_BUY",offer,0,null,false);
                var quote=latest(offer.item);
                if(quote==null || quote.getLow()==null || quote.getLow()>=Integer.MAX_VALUE)continue;
                int price=advisedPrices.price(offer.item,true,quote.getLow()+1,now);
                var proposal=BuyRepricePricing.quote(offer.item,offer.price,offer.remaining(),offer.placedAt,quote,
                    aggregate(offer.item,market),horizon,tax,now,price,input.minimumProfit);
                if(proposal!=null)return offerGuidance(proposal.replace?"REPRICE_BUY":"CANCEL_BUY",offer,price,null,false);
                continue;
            }
            var holding=canonical.get(offer.item);
            var proposal=maintenance.quote(offer.item,offer.price,offer.remaining(),position(holding),latest(offer.item),
                frame.shortHistory.getOrDefault(offer.item,List.of()),horizon,Instant.ofEpochSecond(now),false,exits,tax,
                offer.placedAt,sale.planning.saleProgressAt.getOrDefault(offer.slot,Long.MAX_VALUE));
            if(proposal!=null){
                int price=advisedPrices.price(offer.item,false,proposal.price,now);
                if(!proposal.lossCut && proposal.costKnown) {
                    if(proposal.floor<=0)continue;price=Math.max(price,proposal.floor);
                }
                if(offer.price-(long)price<horizon.outbidDeadband(offer.price))continue;
                if(!authorizeSale(sale,account,market,offer.item,price,offer.remaining(),offer.lastObservation.getEventId(),proposal.lossCut,offer.slot))continue;
                return offerGuidance("CANCEL_SELL",offer,price,holding,proposal.lossCut);
            }
        }
        int slot=freeSlot();
        if(slot<0){abstain("NO_FREE_SLOT");return null;}
        if(account.getFreeSlots()<=1)for(var holding:canonical.values())
            if(inventory.getOrDefault(holding.getItemId(),0)>0 && openBuy(holding.getItemId())==null
                && holding.getQuantity()>listed(holding.getItemId())) {
                abstain("LAST_SLOT_RESERVED_FOR_EXIT");return null;
            }
        account=currentEntryAccount(account,market);if(account==null)return null;
        Map<Integer,Integer> remaining=limits.remaining(input.mapping,Instant.ofEpochSecond(now));
        PortfolioPlan plan=entries.plan(market,account,remaining,events(),Instant.ofEpochSecond(now));
        PortfolioAllocation allocation=plan.getAllocations().isEmpty()?null:plan.getAllocations().get(0);
        PolicyDecision entry=new CompanionEntryPolicy().decide(new EntryPolicyContext(plan,allocation,now));
        if(entry.getAction()!=OfferLifecycleAction.PLACE_BUY){abstain(entry.getReasonCode());return null;}
        PortfolioCandidate c=allocation.getCandidate();
        var constraints=PortfolioPlanner.constraintsFor(account,RiskAppetite.forName(account.getRiskAppetite()),
            PortfolioPlanner.lossBudgetFor(account,now),PortfolioPlanner.committedByGroupFor(account,input.mapping),now);
        if(PortfolioPlanner.rejected(c,account) || remaining.getOrDefault(c.getItemId(),0)<c.getQuantity()
            || new PortfolioOptimizer().optimize(List.of(c),constraints,plan.getCorrelationId(),now).getAllocations().isEmpty()) {
            abstain("ENTRY_CONSTRAINTS_CHANGED");return null;
        }
        return entryGuidance(plan,c,slot,"BUY",null);
    }
    private AccountSnapshot currentEntryAccount(AccountSnapshot account,MarketIngestionService.MarketState market)throws Exception {
        var read=store.planningProjections(ACCOUNT);
        var exposure=DurablePlanningExposure.forAccount(read,account,now);
        if(exposure==null){abstain("CURRENT_EVIDENCE_UNAVAILABLE");return null;}
        var risk=DurableRisk.evaluate(read,account,exposure,market,now);
        if(!risk.isAvailable()){abstain("DURABLE_RISK_UNAVAILABLE: "+risk.getReason());return null;}
        return account.withPlanningRestrictions(exposure.committedByItem,exposure.unavailableEntryItems).withCanonicalRisk(risk);
    }
    private Guidance entryGuidance(PortfolioPlan plan,PortfolioCandidate c,int slot,String kind,String original) {
        int stop=(int)Math.round(c.getBuyPrice()*(1-horizon.getProfile().getLossCutPct()));
        AcquisitionExitContext context=c.getSellPrice()>stop && c.getSellHours()>0
            ?new AcquisitionExitContext(c.getSellPrice(),stop,c.getSellHours()*60):null;
        return new Guidance(kind,slot,c.getItemId(),c.getBuyPrice(),c.getQuantity(),original,null,0,0,false,now,c,plan.getCorrelationId(),context,
            Math.min(c.getExpiresAt(),plan.getExpiresAt()));
    }
    private Guidance sellGuidance(PositionProjection holding,int cap,SqliteStore.SaleProjections sale,AccountSnapshot account,
            MarketIngestionService.MarketState market,String kind,String original) {
        if(holding==null || freeSlot()<0)return null;
        if(openBuy(holding.getItemId())!=null)return null;
        var proposal=saleProposal(holding,cap,exitDecision(holding));
        return proposal==null?null:sellGuidance(proposal,sale,account,market,kind,original);
    }
    private SellDecision exitDecision(PositionProjection holding) {
        return exits.evaluateManagedExit(position(holding),latest(holding.getItemId()),null,
            frame.shortHistory.getOrDefault(holding.getItemId(),List.of()),horizon,Instant.ofEpochSecond(now),
            input.minimumProfit,inventory.getOrDefault(holding.getItemId(),0)>0,false,false);
    }
    private static final class ExitProposal {
        final PositionProjection holding;final SellDecision decision;final int quantity;
        ExitProposal(PositionProjection holding,SellDecision decision,int quantity){this.holding=holding;this.decision=decision;this.quantity=quantity;}
        boolean moreUrgentThan(ExitProposal other) {
            int cut=Boolean.compare(decision.getAction()==SellDecision.Action.CUT,other.decision.getAction()==SellDecision.Action.CUT);
            return cut!=0?cut>0:decision.getExpectedProfit()>other.decision.getExpectedProfit();
        }
    }
    private ExitProposal saleProposal(PositionProjection holding,int cap,SellDecision decision) {
        int item=holding.getItemId(),listed=listed(item);
        int quantity=Math.min(cap,Math.min(inventory.getOrDefault(item,0),holding.getQuantity()-listed));
        if(quantity<=0 || !decision.isSell())return null;
        long profit=tax.netProceeds(item,decision.getPrice(),quantity)-SaleAccounting.allocateCost(holding.getTotalCost(),holding.getQuantity(),quantity);
        if(listed>0 && profit<input.minimumProfit)return null;
        return new ExitProposal(holding,decision,quantity);
    }
    private Guidance sellGuidance(ExitProposal proposal,SqliteStore.SaleProjections sale,AccountSnapshot account,
            MarketIngestionService.MarketState market,String kind,String original) {
        var holding=proposal.holding;var decision=proposal.decision;int quantity=proposal.quantity;
        Position stock=position(holding);
        int price=advisedPrices.price(stock.getItemId(),false,decision.getPrice(),now);
        long cost=SaleAccounting.allocateCost(stock.getTotalCost(),stock.getQuantity(),quantity);
        if(decision.getAction()!=SellDecision.Action.CUT) {
            int floor=tax.breakEvenSellPriceForCost(stock.getItemId(),cost,quantity);
            if(floor<=0)return null;price=Math.max(price,SellTimingEngine.protectedPrice(stock,horizon,Instant.ofEpochSecond(now),floor));
        }
        boolean cut=decision.getAction()==SellDecision.Action.CUT;
        if(!authorizeSale(sale,account,market,stock.getItemId(),price,quantity,holding.getSourceEventId(),cut,-1))return null;
        return new Guidance(kind,freeSlot(),stock.getItemId(),price,quantity,original,holding.getSourceEventId(),
            holding.getTotalCost(),0,cut,now,null,null,holding.getExitContext(),Long.MAX_VALUE);
    }
    private static Position position(PositionProjection p) {
        if(p==null)return null;
        var result=new Position(p.getItemId(),p.getItemName(),p.getQuantity(),p.getTotalCost(),p.getOpenedAt(),true);
        result.applyExitContext(p.getExitContext());return result;
    }
    private Guidance offerGuidance(String kind,Offer offer,int price,PositionProjection holding,boolean cut) {
        return new Guidance(kind,offer.slot,offer.item,price,offer.remaining(),offer.identity,
            holding==null?null:holding.getSourceEventId(),holding==null?0:holding.getTotalCost(),offer.spent,cut,now,null,null,
            holding==null?null:holding.getExitContext(),Long.MAX_VALUE);
    }
    private boolean authorizeSale(SqliteStore.SaleProjections read,AccountSnapshot account,MarketIngestionService.MarketState market,
        int item,int price,int quantity,String source,boolean cut,int slot) {
        var decision=new CompanionSalePolicy().decide(read.planning,read.ledger,account,market,history,item,price,quantity,source,cut,slot,now);
        if(decision.getAction()==(slot<0?OfferLifecycleAction.PLACE_SELL:OfferLifecycleAction.CANCEL_SELL))return true;
        abstain(decision.getReasonCode());return false;
    }
    private void execute(Guidance advice) throws Exception {
        switch(advice.kind) {
            case "COLLECT": collect(offers[advice.slot]);break;
            case "CANCEL_BUY": cancel(offers[advice.slot]);break;
            case "REPRICE_BUY": cancel(offers[advice.slot]);break;
            case "CANCEL_SELL": authorizedSales++;cancel(offers[advice.slot]);break;
            case "SELL": authorizedSales++;place(advice);break;
            case "RELIST_SELL": authorizedSales++;relistedSales++;place(advice);break;
            case "REPLACE_BUY": replacementBuys++;place(advice);break;
            case "BUY": place(advice);break;
            default: throw new IllegalStateException("Unknown displayed replay action");
        }
    }
    private Guidance display(Guidance advice)throws Exception {
        if(advice!=null) {
            if("REPRICE_BUY".equals(advice.kind))rememberContinuation(offers[advice.slot],true);
            if("CANCEL_SELL".equals(advice.kind))rememberContinuation(offers[advice.slot],false);
            if("CANCEL_BUY".equals(advice.kind) && continuations.buy.removeIf(intent->intent.getItemId()==advice.item))saveContinuations();
        }
        return advice;
    }
    private void place(Guidance advice) throws Exception {
        int slot=advice.slot,item=advice.item,price=advice.price,quantity=advice.quantity;
        boolean buy="BUY".equals(advice.kind) || "REPLACE_BUY".equals(advice.kind);
        if(quantity<=0 || price<=0 || offers[slot]!=null)throw new IllegalStateException("Invalid simulated placement");
        if(buy)cash=Math.subtractExact(cash,Math.multiplyExact((long)price,quantity));
        else inventory.merge(item,-quantity,Integer::sum);
        if(cash<0 || inventory.getOrDefault(item,0)<0)throw new IllegalStateException("Shared capital or inventory overspent");
        Offer offer=new Offer(slot,item,price,quantity,buy,now,"sim-offer-"+(++sequence));offers[slot]=offer;
        if(buy) {
            // Prospective synthetic advice was actually displayed earlier. This is not real execution learning.
            offer.origin=event(offer,advice);
        }
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
        int slot=offer.slot;offers[slot]=null;publishEmpty(slot);
        observeContinuations(observation(offer,true));action("COLLECT "+offer.identity);
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
            offer.filled+=quantity;offer.lastProgressAt=now;offer.spent=Math.addExact(offer.spent,(long)offer.price*quantity);
            if(offer.buy) {
                buyBudget-=quantity;
                var p=stocks.get(offer.item);
                if(p==null) {
                    p=new Position(offer.item,name(offer.item),quantity,(long)offer.price*quantity,now,true);
                    stocks.put(offer.item,p);
                } else p.addFill(quantity,(long)offer.price*quantity);
            } else {
                sellBudget-=quantity;Position p=stocks.get(offer.item);
                long cost=SaleAccounting.allocateCost(p.getTotalCost(),p.getQuantity(),quantity);
                long net=tax.netProceeds(offer.item,offer.price,quantity),profit=net-cost;
                int left=p.getQuantity()-quantity;
                if(left==0)stocks.remove(offer.item);else {
                    var remaining=new Position(offer.item,p.getItemName(),left,p.getTotalCost()-cost,p.getOpenedAt(),true);
                    stocks.put(offer.item,remaining);
                }
                offer.collectionCoins=Math.addExact(offer.collectionCoins,net);realized=Math.addExact(realized,profit);
                offer.realizedProfit=Math.addExact(offer.realizedProfit,profit);
                taxPaid=Math.addExact(taxPaid,(long)offer.price*quantity-net);saleUpdates++;if(profit<0)losingSaleUpdates++;
            }
            offer.terminal=offer.remaining()==0;
            if(!offer.buy && offer.terminal){completedSales++;if(offer.realizedProfit<0)losses++;}
            publish(offer);
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
            positions.add(new PositionSnapshot(p.getItemId(),p.getItemName(),p.getQuantity(),p.getTotalCost(),true,p.getOpenedAt(),
                p.getTargetSellPrice(),p.getStopPrice(),p.getPredictedSellMinutes()));
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
    private OfferEvent publish(Offer o) throws Exception {
        var e=o.lastObservation==null && o.origin!=null?o.origin:event(o,null);
        accept(e);o.lastObservation=e;limits.restore(store.buyLimitProjections(),Instant.ofEpochSecond(now));
        observeContinuations(null);return e;
    }
    private OfferEvent event(Offer o,Guidance advice) {
        String type=o.terminal?(o.cancelled?(o.buy?"CANCELLED_BUY":"CANCELLED_SELL"):(o.buy?"BOUGHT":"SOLD")):(o.buy?"BUYING":"SELLING");
        var builder=OfferEvent.builder("synthetic-replay",now,type).accountId(ACCOUNT).slot(o.slot).item(o.item,name(o.item)).buying(o.buy)
            .price(o.price).quantities(o.quantity,o.filled).spent(o.spent).firstSeenAt(o.placedAt)
            .eventIdentity("sim-event-"+(++sequence),"synthetic-session",o.identity).sequence(sequence).authenticatedObservation(true);
        if(advice!=null)builder.recommendation(advice.plan,o.price,o.quantity,0,advice.entry.getBuyHours()*60,
            advice.entry.getDisplayBuyFillProbability()).predictionEvidence(null,advice.advisedAt,false)
            .executionForecast(advice.entry.getBuyForecast()).exitContext(advice.context);
        var raw=builder.build();
        return advice!=null?raw.withBoundPrediction(raw,true):o.origin==null?raw:raw.withBoundPrediction(o.origin,true);
    }
    private void accept(OfferEvent e) throws Exception {
        var result=store.recordAndProjectOffer(e,json.toJson(e),true);
        if(result.transition.getAction()==OfferLifecycleAction.RECONCILE)throw new IllegalStateException("Synthetic observation violated canonical acceptance");
    }
    /** Reload attribution from the exact retained synthetic source; in-memory plans cannot survive a restart. */
    private void restoreOfferBindings() throws Exception {
        var current=store.planningProjections(ACCOUNT).currentEvents;
        try(var connection=java.sql.DriverManager.getConnection("jdbc:sqlite:"+syntheticDatabase);
            var queryOnly=connection.createStatement()) {
            queryOnly.execute("PRAGMA query_only=ON");
            try(var sql=connection.prepareStatement("SELECT payload FROM event_log WHERE event_id=?")) {
                for(Offer offer:offers)if(offer!=null) {
                    OfferEvent restored=current.get(offer.slot);offer.origin=null;
                    if(restored==null || !offer.identity.equals(restored.getOfferIdentity()) || restored.getItemId()!=offer.item
                        || restored.getPrice()!=offer.price || restored.getTotalQuantity()!=offer.quantity
                        || restored.getFilledQuantity()!=offer.filled || restored.getSpent()!=offer.spent)
                        throw new IllegalStateException("Restart changed current synthetic offer evidence");
                    offer.lastObservation=restored;
                    if(restored.getPredictionSourceEventId()==null)continue;
                    sql.setString(1,restored.getPredictionSourceEventId());
                    try(var rows=sql.executeQuery()) {
                        if(!rows.next())throw new IllegalStateException("Restart lost original synthetic advice source");
                        var origin=json.fromJson(rows.getString(1),OfferEvent.class);
                        if(!ACCOUNT.equals(origin.getAccountId()) || !ExecutionObservationLedger.qualifiesOriginal(origin))
                            throw new IllegalStateException("Restart lost qualified synthetic advice");
                        offer.origin=origin;
                    }
                }
            }
        }
    }
    private Collection<OfferEvent> events(){List<OfferEvent> values=new ArrayList<>();for(Offer o:offers)if(o!=null)values.add(o.lastObservation);return values;}
    private static OfferContinuation.Observation observation(Offer o,boolean collected) {
        return new OfferContinuation.Observation(o.slot,o.item,o.buy,o.price,o.quantity,o.filled,o.placedAt,o.identity,
            o.terminal?(o.cancelled?(o.buy?"CANCELLED_BUY":"CANCELLED_SELL"):(o.buy?"BOUGHT":"SOLD")):(o.buy?"BUYING":"SELLING"),collected);
    }
    private List<OfferContinuation.Observation> observedBoard() {
        var board=new ArrayList<OfferContinuation.Observation>();
        for(var offer:offers)if(offer!=null)board.add(observation(offer,false));
        return board;
    }
    private void rememberContinuation(Offer offer,boolean buying)throws Exception {
        var current=buying?continuations.buy:continuations.sell;
        for(var intent:current)if(intent.valid(now) && intent.matches(observation(offer,false)))return;
        var created=OfferContinuation.advise(observation(offer,false),observedBoard(),!buying,now,0);
        if(created!=null){current.add(created);saveContinuations();}
    }
    private OfferContinuation.Status observeContinuations(OfferContinuation.Observation settled)throws Exception {
        var board=observedBoard();boolean changed=false;
        collectedBuys.clear();collectedSells.clear();
        for(boolean buy:new boolean[]{true,false}) {
            var iterator=(buy?continuations.buy:continuations.sell).iterator();
            while(iterator.hasNext()) {
                var intent=iterator.next();if(intent==null || intent.isBuying()!=buy)throw new IllegalStateException("Wrong continuation side");
                long revision=intent.getRevision();var status=intent.observe(board,settled,now);changed|=intent.getRevision()!=revision;
                if(status==OfferContinuation.Status.CLOSED){iterator.remove();changed=true;}
                else if(status==OfferContinuation.Status.COLLECTED)(buy?collectedBuys:collectedSells).add(intent);
            }
        }
        if(changed)saveContinuations();
        return !collectedBuys.isEmpty()?OfferContinuation.Status.COLLECTED:continuations.buy.isEmpty()?OfferContinuation.Status.CLOSED:OfferContinuation.Status.WAITING;
    }
    private void saveContinuations()throws Exception {
        var temp=continuationFile.resolveSibling(continuationFile.getFileName()+".tmp");
        java.nio.file.Files.writeString(temp,json.toJson(continuations));
        try {java.nio.file.Files.move(temp,continuationFile,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
        catch(java.nio.file.AtomicMoveNotSupportedException unsupported){java.nio.file.Files.move(temp,continuationFile,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
    }
    private com.flippingfriend.data.Candle aggregate(int item,MarketIngestionService.MarketState market) {
        var raw=market.fiveMinute.get(Integer.toString(item));
        return raw==null || !raw.isJsonObject()?null:json.fromJson(raw,com.flippingfriend.data.Candle.class);
    }
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
    private Offer openBuy(int item){for(Offer o:offers)if(o!=null && o.item==item && o.buy && !o.terminal)return o;return null;}
    private int listed(int item){int n=0;for(Offer o:offers)if(o!=null && o.item==item && !o.buy)n=Math.addExact(n,o.remaining());return n;}
    private double strandedBy(Offer offer) {
        var quote=latest(offer.item);if(quote==null || !quote.isComplete())return 0;
        int gap=offer.buy?quote.getLow()-offer.price:offer.price-quote.getHigh();
        return Math.max(0,gap)/(double)offer.price;
    }
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
