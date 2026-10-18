package com.flippingfriend.companion;

import com.flippingfriend.core.SaleAccounting;
import com.flippingfriend.data.Candle;
import com.google.gson.*;
import java.util.*;

/** Frozen public inputs. Availability is distinct from the market bucket's own clock. */
final class AccountReplayInput {
    String schemaVersion="1", taxPolicy=SaleAccounting.CURRENT_POLICY, risk="MODERATE";
    String sourceDescription;
    long startAt,endAt,initialCoins=100_000_000;
    int slots=8,actionSeconds=15,visitSeconds=120;
    boolean members=true;
    long minimumProfit=10_000;
    int terminalHaircutBps=200;
    List<Integer> missedVisitIndices=new ArrayList<>();
    List<Long> restartAt=new ArrayList<>();
    Map<Integer,MarketIngestionService.Item> mapping=new TreeMap<>();
    List<Frame> frames=new ArrayList<>();
    static final class Frame {
        long availableAt,observedAt;
        JsonObject latest=new JsonObject(),fiveMinute=new JsonObject(),hourly=new JsonObject();
        Map<Integer,List<Candle>> shortHistory=new TreeMap<>(),longHistory=new TreeMap<>();
        List<Flow> flows=new ArrayList<>();
        MarketIngestionService.MarketState market(Map<Integer,MarketIngestionService.Item> mapping) {
            return new MarketIngestionService.MarketState(mapping,latest,fiveMinute,hourly,observedAt);
        }
    }
    static final class Flow {
        int itemId; long bucketStart,bucketEnd;
        int low,high,lowVolume,highVolume;
    }
    void validate() {
        if(!"1".equals(schemaVersion) || !SaleAccounting.CURRENT_POLICY.equals(taxPolicy)
            || sourceDescription==null || sourceDescription.isBlank() || startAt<=SaleAccounting.CURRENT_POLICY_SAFE_FROM
            || endAt<=startAt || endAt-startAt>31L*86400 || initialCoins!=100_000_000 || slots!=8 || !members
            || actionSeconds<1 || visitSeconds<actionSeconds || visitSeconds>=300 || minimumProfit<0
            || terminalHaircutBps<0 || terminalHaircutBps>10000 || missedVisitIndices==null
            || missedVisitIndices.stream().anyMatch(i->i==null || i<0)
            || restartAt==null || mapping==null || mapping.isEmpty() || frames==null || frames.isEmpty())
            throw new IllegalArgumentException("Invalid replay contract: 100M members/eight slots and explicit supported frozen inputs required");
        com.flippingfriend.RiskProfile.valueOf(risk);
        long lastRestart=startAt;
        for(Long at:restartAt) {if(at==null || at<=lastRestart || at>=endAt)throw new IllegalArgumentException("Invalid restart schedule");lastRestart=at;}
        for(var e:mapping.entrySet()) if(e.getKey()==null || e.getValue()==null || e.getValue().id!=e.getKey()
            || e.getKey()<=0 || e.getValue().buyLimit<=0 || e.getValue().name==null)
            throw new IllegalArgumentException("Invalid item mapping");
        long previous=0;Set<String> flows=new HashSet<>();
        for(Frame f:frames) {
            if(f==null || f.availableAt<=previous || f.availableAt>endAt || f.observedAt<=0 || f.observedAt>f.availableAt
                || f.latest==null || f.fiveMinute==null || f.hourly==null || f.flows==null)
                throw new IllegalArgumentException("Market availability must be strictly chronological");
            previous=f.availableAt;
            for(var e:f.latest.entrySet()) {
                if(!mapping.containsKey(Integer.parseInt(e.getKey())) || !e.getValue().isJsonObject())throw new IllegalArgumentException("Unknown quoted item");
                var q=e.getValue().getAsJsonObject();
                for(String field:List.of("lowTime","highTime"))if(q.has(field) && !q.get(field).isJsonNull()) {
                    long t=q.get(field).getAsBigDecimal().longValueExact();
                    if(t<=0 || t>f.observedAt)throw new IllegalArgumentException("Future quote is unavailable to this observation");
                }
            }
            history(f.shortHistory,300,f.availableAt);history(f.longHistory,3600,f.availableAt);
            for(Flow flow:f.flows) if(flow==null || !mapping.containsKey(flow.itemId) || flow.bucketStart<0
                || flow.bucketEnd-flow.bucketStart!=300 || flow.bucketEnd>f.availableAt || flow.low<=0 || flow.high<flow.low
                || flow.lowVolume<0 || flow.highVolume<0 || !flows.add(flow.itemId+":"+flow.bucketStart))
                throw new IllegalArgumentException("Invalid, future or duplicate execution-flow bucket");
        }
        if(frames.get(0).availableAt>startAt)throw new IllegalArgumentException("Start requires a frozen initial market observation");
    }
    private void history(Map<Integer,List<Candle>> histories,long seconds,long available) {
        if(histories==null)throw new IllegalArgumentException("Missing history map");
        for(var e:histories.entrySet()) {
            if(!mapping.containsKey(e.getKey()) || e.getValue()==null)throw new IllegalArgumentException("Unknown history item");
            long previous=-1;
            for(Candle bar:e.getValue()) {
                if(bar==null || bar.getTimestamp()<=previous || bar.getTimestamp()+seconds>available
                    || bar.getLowPriceVolume()<0 || bar.getHighPriceVolume()<0)throw new IllegalArgumentException("Incomplete, unordered or future history bucket");
                previous=bar.getTimestamp();
            }
        }
    }
}
