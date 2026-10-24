package com.flippingfriend.core;

import java.util.List;
import java.util.HashSet;

/** One account's copied workflow coverage. Expiry withdraws conclusions, never the unfinished work. */
public final class TradeRecoverySnapshot {
    public final String accountId;
    public final long evaluatedAt, ledgerObservedAt;
    public final boolean evidenceCurrent;
    public final List<TradeRecoveryItem> items;
    public TradeRecoverySnapshot(String accountId,long at,long ledgerAt,boolean current,List<TradeRecoveryItem> items) {
        var ids=new HashSet<String>();
        for(var item:items)if(item==null || !ids.add(item.kind+":"+item.identity))throw new IllegalArgumentException("duplicate recovery obligation");
        this.accountId=accountId;this.evaluatedAt=at;this.ledgerObservedAt=ledgerAt;this.evidenceCurrent=current;
        this.items=List.copyOf(items);
    }
    public boolean isFresh(String account,long now){return java.util.Objects.equals(accountId,account) && now>=evaluatedAt && now-evaluatedAt<30;}
}
