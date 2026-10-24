package com.flippingfriend.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

/** Immutable prospective policy output. Authority additionally requires its canonical issuance row. */
public final class SalePolicyReceipt {
    public static final String POLICY_VERSION="companion-sale-10";
    public enum Mode { ROUTINE, STOP, WIND_DOWN }
    private final int formatVersion;
    private final String receiptId,policyVersion,accountId,payloadHash,accountCorrelationId,riskAppetite;
    private final long accountRowId,accountObservedAt;
    private final Map<Integer,String> offerReadSet;
    private final int checkIntervalMinutes,targetHoldMinutes;
    private final boolean sellOnly,lossCut;
    private final String sourceEventId,sessionId,offerIdentity;
    private final int slot,itemId,standingPrice,totalQuantity,filledQuantity,proposedPrice,quantity;
    private final long spent,firstSeenAt,sourceSequence,sourceObservedAt,progressAt,decidedAt,patienceSeconds;
    private final Mode mode;

    private SalePolicyReceipt(String id,long row,String hash,AccountSnapshot account,OfferEvent source,
            long progress,int price,int quantity,boolean cut,long at,long patience) {
        formatVersion=1;policyVersion=POLICY_VERSION;receiptId=id;accountRowId=row;payloadHash=hash;
        accountId=account.getAccountId();accountCorrelationId=account.getCorrelationId();accountObservedAt=account.getObservedAt();
        offerReadSet=Collections.unmodifiableMap(new HashMap<>(account.getOfferReadSet()));
        riskAppetite=account.getRiskAppetite();checkIntervalMinutes=account.getCheckIntervalMinutes();
        targetHoldMinutes=account.getTargetHoldMinutes();sellOnly=account.isSellOnly();lossCut=cut;
        sourceEventId=source.getEventId();sessionId=source.getSessionId();offerIdentity=source.getOfferIdentity();
        slot=source.getSlot();itemId=source.getItemId();standingPrice=source.getPrice();
        totalQuantity=source.getTotalQuantity();filledQuantity=source.getFilledQuantity();spent=source.getSpent();
        firstSeenAt=source.getFirstSeenAt();sourceSequence=source.getSequence();sourceObservedAt=source.getObservedAt();
        progressAt=progress;proposedPrice=price;this.quantity=quantity;decidedAt=at;patienceSeconds=patience;
        mode=cut?Mode.STOP:sellOnly?Mode.WIND_DOWN:Mode.ROUTINE;
    }
    public static SalePolicyReceipt issue(String receiptId,long accountRowId,String payloadHash,
            AccountSnapshot settings,OfferEvent source,long progressAt,int proposedPrice,int quantity,
            boolean lossCut,long decidedAt,long patienceSeconds) {
        if(settings==null || source==null || !source.isAuthenticatedObservation() || source.isBuying()
            || !"SELLING".equals(source.getEventType()) || !Objects.equals(settings.getAccountId(),source.getAccountId()))
            throw new IllegalArgumentException("canonical sale settings and source required");
        var receipt=new SalePolicyReceipt(receiptId,accountRowId,payloadHash,settings,source,progressAt,
            proposedPrice,quantity,lossCut,decidedAt,patienceSeconds);
        if(!receipt.isWellFormed())throw new IllegalArgumentException("invalid sale policy receipt");
        return receipt;
    }
    public boolean isWellFormed() {
        if(formatVersion!=1 || !(POLICY_VERSION.equals(policyVersion) || "companion-sale-9".equals(policyVersion)) || blank(receiptId)
            || accountRowId<=0 || payloadHash==null || !payloadHash.matches("[0-9a-f]{64}")
            || accountId==null || !accountId.matches("rs-[0-9a-f]{64}") || blank(accountCorrelationId)
            || accountObservedAt<=0 || accountObservedAt>decidedAt || decidedAt-accountObservedAt>150
            || !("LOW".equals(riskAppetite) || "MODERATE".equals(riskAppetite) || "HIGH".equals(riskAppetite))
            || checkIntervalMinutes<=0 || targetHoldMinutes<0 || patienceSeconds!=Math.max(480L,checkIntervalMinutes*60L)
            || blank(sourceEventId) || blank(sessionId) || blank(offerIdentity) || slot<0 || slot>=8 || itemId<=0
            || standingPrice<=0 || proposedPrice<=0 || proposedPrice>=standingPrice || totalQuantity<=0
            || filledQuantity<0 || filledQuantity>=totalQuantity || quantity!=totalQuantity-filledQuantity || spent<0
            || (filledQuantity==0?spent!=0:spent<=0)
            || firstSeenAt<=0 || progressAt<firstSeenAt || progressAt>sourceObservedAt || sourceSequence<=0
            || sourceObservedAt<firstSeenAt || sourceObservedAt>decidedAt || decidedAt-sourceObservedAt>150
            || mode==null || mode!=(lossCut?Mode.STOP:sellOnly?Mode.WIND_DOWN:Mode.ROUTINE)
            || offerReadSet==null || offerReadSet.size()!=8 || new HashSet<>(offerReadSet.values()).size()!=8)return false;
        for(int i=0;i<8;i++)if(blank(offerReadSet.get(i)))return false;
        return mode!=Mode.ROUTINE || decidedAt-firstSeenAt>=patienceSeconds && decidedAt-progressAt>=patienceSeconds;
    }
    /** Includes late fills, but never another episode, changed order or regressed counter. */
    public boolean matchesEpisode(OfferEvent event) {
        return isWellFormed() && event!=null && event.isAuthenticatedObservation() && !event.isBuying()
            && accountId.equals(event.getAccountId()) && sessionId.equals(event.getSessionId())
            && offerIdentity.equals(event.getOfferIdentity()) && slot==event.getSlot() && itemId==event.getItemId()
            && standingPrice==event.getPrice() && totalQuantity==event.getTotalQuantity() && firstSeenAt==event.getFirstSeenAt()
            && event.getSequence()>=sourceSequence && event.getObservedAt()>=sourceObservedAt
            && event.getFilledQuantity()>=filledQuantity && event.getFilledQuantity()<=totalQuantity && event.getSpent()>=spent
            && (event.getFilledQuantity()==0?event.getSpent()==0:event.getSpent()>0)
            && ("SELLING".equals(event.getEventType()) || "CANCELLED_SELL".equals(event.getEventType())
                || "SOLD".equals(event.getEventType()) && event.getFilledQuantity()==totalQuantity);
    }
    public boolean matchesSource(OfferEvent event) {
        return matchesEpisode(event) && "SELLING".equals(event.getEventType()) && sourceEventId.equals(event.getEventId())
            && sourceSequence==event.getSequence() && sourceObservedAt==event.getObservedAt()
            && filledQuantity==event.getFilledQuantity() && spent==event.getSpent();
    }
    public boolean matchesOrder(int item,int price,int quantity){return isWellFormed() && itemId==item && proposedPrice==price && this.quantity==quantity;}
    public boolean matchesSettings(AccountSnapshot account) {
        return isWellFormed() && account!=null && Objects.equals(accountId,account.getAccountId())
            && Objects.equals(accountCorrelationId,account.getCorrelationId()) && accountObservedAt==account.getObservedAt()
            && Objects.equals(riskAppetite,account.getRiskAppetite()) && checkIntervalMinutes==account.getCheckIntervalMinutes()
            && targetHoldMinutes==account.getTargetHoldMinutes() && sellOnly==account.isSellOnly()
            && Objects.equals(offerReadSet,account.getOfferReadSet());
    }
    public boolean isValid(){return isWellFormed();}
    public int getFormatVersion(){return formatVersion;}
    public String getReceiptId(){return receiptId;}
    public String getPolicyVersion(){return policyVersion;}
    public String getAccountId(){return accountId;}
    public long getAccountRowId(){return accountRowId;}
    public String getPayloadHash(){return payloadHash;}
    public String getAccountCorrelationId(){return accountCorrelationId;}
    public long getAccountObservedAt(){return accountObservedAt;}
    public Map<Integer,String> getOfferReadSet(){return offerReadSet==null?Collections.emptyMap():Collections.unmodifiableMap(new HashMap<>(offerReadSet));}
    public String getRiskAppetite(){return riskAppetite;}
    public int getCheckIntervalMinutes(){return checkIntervalMinutes;}
    public int getTargetHoldMinutes(){return targetHoldMinutes;}
    public boolean isSellOnly(){return sellOnly;}
    public boolean isLossCut(){return lossCut;}
    public Mode getMode(){return mode;}
    public String getSourceEventId(){return sourceEventId;}
    public String getSessionId(){return sessionId;}
    public String getOfferIdentity(){return offerIdentity;}
    public int getSlot(){return slot;}
    public int getItemId(){return itemId;}
    public int getStandingPrice(){return standingPrice;}
    public int getTotalQuantity(){return totalQuantity;}
    public int getFilledQuantity(){return filledQuantity;}
    public long getSpent(){return spent;}
    public long getFirstSeenAt(){return firstSeenAt;}
    public long getSourceSequence(){return sourceSequence;}
    public long getSourceObservedAt(){return sourceObservedAt;}
    public long getProgressAt(){return progressAt;}
    public int getProposedPrice(){return proposedPrice;}
    public int getQuantity(){return quantity;}
    public long getDecidedAt(){return decidedAt;}
    public long getPatienceSeconds(){return patienceSeconds;}
    private static boolean blank(String text){return text==null || text.isBlank();}
    @Override public boolean equals(Object value) {
        if(!(value instanceof SalePolicyReceipt))return false;var r=(SalePolicyReceipt)value;
        return formatVersion==r.formatVersion && accountRowId==r.accountRowId && accountObservedAt==r.accountObservedAt
            && checkIntervalMinutes==r.checkIntervalMinutes && targetHoldMinutes==r.targetHoldMinutes && sellOnly==r.sellOnly
            && lossCut==r.lossCut && slot==r.slot && itemId==r.itemId && standingPrice==r.standingPrice
            && totalQuantity==r.totalQuantity && filledQuantity==r.filledQuantity && proposedPrice==r.proposedPrice
            && quantity==r.quantity && spent==r.spent && firstSeenAt==r.firstSeenAt && sourceSequence==r.sourceSequence
            && sourceObservedAt==r.sourceObservedAt && progressAt==r.progressAt && decidedAt==r.decidedAt && patienceSeconds==r.patienceSeconds
            && mode==r.mode && Objects.equals(receiptId,r.receiptId) && Objects.equals(policyVersion,r.policyVersion)
            && Objects.equals(accountId,r.accountId) && Objects.equals(payloadHash,r.payloadHash)
            && Objects.equals(accountCorrelationId,r.accountCorrelationId) && Objects.equals(riskAppetite,r.riskAppetite)
            && Objects.equals(offerReadSet,r.offerReadSet) && Objects.equals(sourceEventId,r.sourceEventId)
            && Objects.equals(sessionId,r.sessionId) && Objects.equals(offerIdentity,r.offerIdentity);
    }
    @Override public int hashCode(){return Objects.hash(receiptId,accountRowId,payloadHash,sourceEventId,decidedAt);}
}
