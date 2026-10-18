package com.flippingfriend.core;

/** One durable incremental sale update. Null bounds preserve missing cost, proceeds or tax evidence. */
public final class RecordedSale {
    private final long rowId,observedAt,appliedCost;
    private final int itemId,quantity;
    private final String itemName,sourceEventId,offerIdentity,taxPolicy,reason;
    private final Long gross,taxLower,taxUpper,profitLower,profitUpper;
    public RecordedSale(long row,long at,int item,String name,int quantity,long appliedCost,String source,String offer,
        Long gross,Long taxLow,Long taxHigh,Long profitLow,Long profitHigh,String policy,String reason) {
        rowId=row;observedAt=at;itemId=item;itemName=name;this.quantity=quantity;this.appliedCost=appliedCost;
        sourceEventId=source;offerIdentity=offer;this.gross=gross;taxLower=taxLow;taxUpper=taxHigh;
        profitLower=profitLow;profitUpper=profitHigh;taxPolicy=policy;this.reason=reason;
        if(!isValid())throw new IllegalArgumentException("invalid recorded sale");
    }
    public boolean isValid() {
        if(rowId<=0 || observedAt<0 || itemId<=0 || quantity<=0 || appliedCost<0 || gross!=null && gross<0
            || (taxLower==null)!=(taxUpper==null) || (profitLower==null)!=(profitUpper==null))return false;
        if(taxLower!=null && (gross==null || taxLower<0 || taxUpper<taxLower || taxUpper>gross))return false;
        if(profitLower!=null) {
            if(taxLower==null || profitLower>profitUpper)return false;
            try{return profitLower==Math.subtractExact(Math.subtractExact(gross,taxUpper),appliedCost)
                && profitUpper==Math.subtractExact(Math.subtractExact(gross,taxLower),appliedCost);}
            catch(ArithmeticException invalid){return false;}
        }
        return true;
    }
    public long getRowId(){return rowId;}public long getObservedAt(){return observedAt;}
    public int getItemId(){return itemId;}public String getItemName(){return itemName==null?"Item "+itemId:itemName;}
    public int getQuantity(){return quantity;}public long getAppliedCost(){return appliedCost;}
    public String getSourceEventId(){return sourceEventId;}public String getOfferIdentity(){return offerIdentity;}
    public Long getGross(){return gross;}public Long getTaxLower(){return taxLower;}public Long getTaxUpper(){return taxUpper;}
    public Long getProfitLower(){return profitLower;}public Long getProfitUpper(){return profitUpper;}
    public String getTaxPolicy(){return taxPolicy;}public String getReason(){return reason;}
}
