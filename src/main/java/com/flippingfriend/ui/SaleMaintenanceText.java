package com.flippingfriend.ui;

import com.flippingfriend.session.SaleMaintenanceDiagnostic;
import com.flippingfriend.session.SaleMaintenancePricing;
import java.util.List;

/** Plain investigation text. No action controls or trading authority. */
final class SaleMaintenanceText {
    static String render(List<SaleMaintenanceDiagnostic> rows,long now,boolean loggedIn) {
        if(!loggedIn)return "Active managed sales: unavailable while logged out.";
        if(rows.isEmpty())return "No active managed sales in the latest confirmed evaluation.";
        StringBuilder text=new StringBuilder("Active sale maintenance\nEligibility is a pricing check; final companion authority and manual action are still required.");
        for(var row:rows) {
            var offer=row.offer();
            text.append("\n\nSlot ").append(offer.getSlot()+1).append(" · ").append(offer.getItemName())
                .append("\nStanding ask: ").append(offer.getPrice()).append(" gp · Remaining: ").append(offer.getRemaining())
                .append("\nSince placement observation: ").append(age(now,offer.getFirstSeen()))
                .append(" · Since observed progress: ").append(age(now,offer.getLastFillObservedAt()));
            if(!row.matches(offer,now)) {text.append("\nCurrent evaluation expired; waiting for refreshed evidence.");continue;}
            if(row.unavailable!=null) {text.append("\n").append(row.unavailable);continue;}
            text.append("\nLatest buyer-paid trade: ").append(trade(row.highTradePrice,row.highAge,now-row.evaluatedAt))
                .append("\nLatest seller-received trade: ").append(trade(row.lowTradePrice,row.lowAge,now-row.evaluatedAt));
            text.append("\n").append(description(row.evaluation.status));
            if(row.evaluation.status==SaleMaintenancePricing.Status.IMMATERIAL_CHANGE) {
                int deadband=com.flippingfriend.TradingHorizon.outbidDeadbandForPrice(offer.getPrice());
                text.append(" Minimum material reduction: ").append(deadband).append(" gp per item.");
                if(row.highTradePrice!=null && row.highTradePrice>0)
                    text.append(" Standing ask minus latest buyer-paid trade: ")
                        .append((long)offer.getPrice()-row.highTradePrice).append(" gp per item.");
                text.append(" A crossed original stop still requires a material supported change.");
            }
            if(row.evaluation.reassessAt>now)text.append(" Clock-only reassessment in ")
                .append(duration(row.evaluation.reassessAt-now)).append("; current market evidence will still be required.");
            if(Double.isFinite(row.originalExpectedMinutes) && row.originalExpectedMinutes>0) {
                text.append("\nOriginal actual-order estimate (unvalidated): ").append(Math.round(row.originalExpectedMinutes)).append("m.");
                if(now-offer.getFirstSeen()>row.originalExpectedMinutes*60)
                    text.append(" Past that estimate; this alone does not authorize repricing.");
            } else text.append("\nOriginal actual-order estimate: unavailable; acquisition estimates are separate.");
            if(row.evaluation.quote!=null && row.evaluation.quote.costKnown)
                text.append("\nExact allocated remaining cost: ").append(row.evaluation.quote.allocatedCost).append(" gp.");
        }
        return text.toString();
    }
    private static String trade(Integer price,long age,long elapsed) {
        if(price==null || price<=0 || age<0)return "unavailable";
        long currentAge=age+elapsed;
        return price+" gp, "+duration(currentAge)+" old"+(currentAge>300?" (stale for maintenance)":"");
    }
    private static String age(long now,long at){return at<=0 || at>now?"unavailable":duration(now-at);}
    private static String duration(long seconds){return seconds<60?seconds+"s":seconds/60+"m "+seconds%60+"s";}
    private static String description(SaleMaintenancePricing.Status status) {
        switch(status) {
            case ELIGIBLE:return "Supported price change is eligible for further checks. Final authority, delivery and foreground priority can still withhold guidance.";
            case INVALID_QUANTITY:return "No remaining quantity is proven for this evaluation.";
            case MISSING_PRICES:return "Waiting for complete two-sided market prices.";
            case STALE_PRICES:return "Waiting for fresh two-sided market prices (at most 5 minutes old).";
            case IMMATERIAL_CHANGE:return "No material supported price reduction is available.";
            case UNPROVEN_QUANTITY:return "The listing exceeds the proven managed remainder; personal extras cannot borrow managed pricing protection.";
            case UNKNOWN_CLOCKS:return "Placement or progress ordering is unproven; no patience deadline can be claimed.";
            case PLACEMENT_PATIENCE:return "Waiting for placement patience.";
            case RECENT_PROGRESS:return "Recent observed fills: waiting for the no-progress interval.";
            case MARKET_SUPPORT:return "Waiting for two consecutive completed 5-minute buckets with buyer volume supporting a materially lower ask.";
            case COST_FLOOR:return "The exact after-tax remaining-cost floor prevents a material supported reduction.";
            default:throw new IllegalArgumentException("Unknown sale status: "+status);
        }
    }
}
