package com.flippingfriend.ui;

import com.flippingfriend.core.TradeRecoverySnapshot;

/** Recovery investigation only. The foreground guide retains all action authority. */
final class TradeRecoveryText {
    static String render(TradeRecoverySnapshot snapshot,long now) {
        if(snapshot==null)return "Trade recovery is unavailable while logged out or awaiting this account's first evaluation.";
        var text=new StringBuilder("Trade recovery\nEvery row is workflow evidence, not an instruction to place or cancel an offer.");
        text.append("\nEvaluated ").append(Math.max(0,now-snapshot.evaluatedAt)).append(" seconds ago.");
        if(!snapshot.evidenceCurrent || now<snapshot.evaluatedAt || now-snapshot.evaluatedAt>=30)
            text.append("\nCurrent evaluation is unavailable or expired. Retained obligations remain below; their previous eligibility is withdrawn.");
        text.append("\nOffers, acquired stock and continuations can describe the same units; do not add their quantities.");
        if(snapshot.items.isEmpty())text.append("\nNo unfinished trades in the evaluated evidence.");
        for(var item:snapshot.items) {
            text.append("\n\n").append(item.name==null?"Item "+item.itemId:item.name).append(" · ").append(item.kind);
            if(item.slot>=0)text.append(" · Slot ").append(item.slot+1);
            text.append("\n").append(snapshot.evidenceCurrent && now>=snapshot.evaluatedAt && now-snapshot.evaluatedAt<30?item.state:"AWAITING_CURRENT_EVALUATION")
                .append(" · Quantity: ").append(item.quantity);
            if(item.price>0)text.append(" · Observed price: ").append(item.price).append(" gp");
            text.append("\n").append(item.reason).append("\nNext: ").append(item.nextStep)
                .append("\nRecheck: ").append(item.reassessAt<=now?"due; waiting for worker/evidence":"within "+(item.reassessAt-now)+"s or when relevant evidence changes")
                .append("\nIdentity: ").append(item.identity);
        }
        return text.toString();
    }
    private TradeRecoveryText(){}
}
