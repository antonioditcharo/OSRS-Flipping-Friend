package com.flippingfriend.core;

import java.util.*;

/** Optional prospective display metadata. It grants no financial, attribution or learning authority. */
public final class DisplayedPricingContext {
    private final String schemaVersion,accountId,adviceId,sourceEventId,action,configurationVersion,forecastFingerprint;
    private final int itemId,price,quantity;
    private final long acceptedAt;
    private final CaptureOrder acceptedOrder,observedOrder;
    private final Long expectedProfit;
    private final String reason;
    private final String alternativesScope;
    private final List<Alternative> accountAlternatives;
    private final PortfolioCandidate.MarketPriceEvidence market;
    private final Configuration configuration;
    public static final class Alternative {
        public final int itemId,buyPrice,sellPrice,quantity;
        public final long modeledNetProfit;
        public Alternative(PortfolioCandidate candidate) {
            itemId=candidate.getItemId();buyPrice=candidate.getTargetBuyPrice();sellPrice=candidate.getTargetSellPrice();
            quantity=candidate.getQuantity();modeledNetProfit=candidate.getNetProfit();
        }
        private boolean valid(){return itemId>0 && buyPrice>0 && sellPrice>0 && quantity>0;}
    }
    public static final class Configuration {
        public final String risk;
        public final Integer cadenceMinutes;
        public final int requestedHoldMinutes,requestedProfit;
        public final long spendingCap;
        public final boolean sellOnly;
        public Configuration(String risk,Integer cadence,int hold,int profit,long cap,boolean sellOnly) {
            this.risk=risk;cadenceMinutes=cadence;requestedHoldMinutes=hold;requestedProfit=profit;spendingCap=cap;this.sellOnly=sellOnly;
        }
        private String version(){return "DISPLAY_SETTINGS_V1:"+risk+":"+cadenceMinutes+":"+requestedHoldMinutes+":"+requestedProfit+":"+spendingCap+":"+sellOnly;}
        private boolean valid(){return (risk==null || Set.of("LOW","MODERATE","HIGH").contains(risk))
            && (cadenceMinutes==null || cadenceMinutes>0) && requestedHoldMinutes>=0 && requestedProfit>=0 && spendingCap>=0;}
    }
    public DisplayedPricingContext(String account,String adviceId,String action,int item,int price,int quantity,long at,CaptureOrder accepted,
            Long profit,String reason,ExecutionForecast forecast,Configuration configuration,
            List<Alternative> alternatives,PortfolioCandidate.MarketPriceEvidence market) {
        this(account,adviceId,null,action,item,price,quantity,at,accepted,null,profit,reason,
            forecast==null?null:forecast.getFingerprint(),configuration,alternatives,market);
    }
    private DisplayedPricingContext(String account,String advice,String source,String action,int item,int price,int quantity,long at,
            CaptureOrder accepted,CaptureOrder observed,Long profit,String reason,String forecast,Configuration configuration,
            List<Alternative> alternatives,PortfolioCandidate.MarketPriceEvidence market) {
        schemaVersion="1";accountId=account;adviceId=advice;sourceEventId=source;this.action=action;itemId=item;this.price=price;this.quantity=quantity;
        acceptedAt=at;acceptedOrder=accepted;observedOrder=observed;expectedProfit=profit;
        this.reason=reason==null?null:reason.substring(0,Math.min(2048,reason.length()));forecastFingerprint=forecast;
        this.configuration=configuration;configurationVersion=configuration==null?null:configuration.version();
        accountAlternatives=alternatives==null?List.of():List.copyOf(alternatives);this.market=market;
        alternativesScope="UP_TO_FOUR_PLANNED_ACCOUNT_ALLOCATIONS";
        if(!isValid())throw new IllegalArgumentException("Invalid optional display context");
    }
    public boolean isValid() {
        if(!"1".equals(schemaVersion) || accountId==null || !accountId.matches("rs-[0-9a-f]{64}")
            || adviceId==null || adviceId.isBlank() || !("BUY".equals(action) || "SELL".equals(action))
            || itemId<=0 || price<=0 || quantity<=0 || acceptedAt<=0 || acceptedOrder==null || acceptedOrder.getProcess()==null
            || acceptedOrder.getProcess().isBlank() || acceptedOrder.getOrder()<=0
            || accountAlternatives==null || accountAlternatives.size()>4 || accountAlternatives.stream().anyMatch(a->a==null || !a.valid())
            || forecastFingerprint!=null && !forecastFingerprint.matches("[0-9a-f]{64}")
            || !validMarket()
            || configuration!=null && !configuration.valid()
            || !"UP_TO_FOUR_PLANNED_ACCOUNT_ALLOCATIONS".equals(alternativesScope)
            || !Objects.equals(configurationVersion,configuration==null?null:configuration.version()))return false;
        return sourceEventId==null?observedOrder==null:!sourceEventId.isBlank() && observedOrder!=null && acceptedOrder.precedes(observedOrder);
    }
    private boolean validMarket() {
        if(market==null)return true;
        if(!com.flippingfriend.model.PublicPriceForecast.VERSION.equals(market.modelVersion)
            || market.historyFingerprint==null || !market.historyFingerprint.matches("[0-9a-f]{64}")
            || market.observedAt<=0 || market.observedAt>acceptedAt || market.horizonSeconds<=0 || market.horizonSeconds>28800
            || market.buyOrder<=0 || market.sellOrder<=0 || ("BUY".equals(action)?market.buyOrder:market.sellOrder)!=price)return false;
        for(var side:new com.flippingfriend.model.PublicPriceForecast.Estimate[]{market.low,market.high})
            if(side==null || !side.available() || !Double.isFinite(side.median) || side.lower>side.median || side.median>side.upper)return false;
        return true;
    }
    public DisplayedPricingContext bind(String event,CaptureOrder observed) {
        if(sourceEventId!=null)throw new IllegalStateException("Display context is already source-bound");
        return new DisplayedPricingContext(accountId,adviceId,event,action,itemId,price,quantity,acceptedAt,acceptedOrder,observed,
            expectedProfit,reason,forecastFingerprint,configuration,accountAlternatives,market);
    }
    public boolean matches(OfferEvent event) {
        return isValid() && sourceEventId!=null && event!=null && sourceEventId.equals(event.getEventId())
            && accountId.equals(event.getAccountId()) && event.isAuthenticatedObservation()
            && itemId==event.getItemId() && price==event.getPrice() && quantity==event.getTotalQuantity()
            && ("BUY".equals(action)==event.isBuying()) && event.getFilledQuantity()==0 && event.getFirstSeenAt()==event.getObservedAt()
            && (event.isBuying()?"BUYING":"SELLING").equals(event.getEventType())
            && acceptedAt<=event.getObservedAt() && event.getObservedAt()-acceptedAt<=AdviceObservationWindow.SECONDS;
    }
    public String getAdviceId(){return adviceId;}
    public String getSourceEventId(){return sourceEventId;}
    public Long getExpectedProfit(){return expectedProfit;}
    public String getConfigurationVersion(){return configurationVersion;}
    public String getForecastFingerprint(){return forecastFingerprint;}
    public List<Alternative> getAccountAlternatives(){return accountAlternatives==null?List.of():Collections.unmodifiableList(accountAlternatives);}
}
