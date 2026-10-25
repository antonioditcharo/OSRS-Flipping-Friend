package com.flippingfriend.session;

import com.flippingfriend.TradingHorizon;
import com.flippingfriend.core.TradeRecoveryItem;
import com.flippingfriend.data.*;
import com.flippingfriend.model.TaxCalculator;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Prospective cancel-and-reassess decisions, separate from player skips and financial history. */
@Singleton
public final class BuyRecoveryJournal {
    static final String FILE="buy-recovery.json";
    private static final class Decision {
        String identity,reason;int item,price,quantity;long acceptedAt,notBefore,releasedAt;
        boolean valid(){return identity!=null && !identity.isBlank() && reason!=null && item>0 && price>0 && quantity>0
            && acceptedAt>0 && notBefore>=acceptedAt && (releasedAt==0 || releasedAt>=notBefore);}
    }
    private static final class Document {
        int formatVersion=1;String accountId;Map<String,Decision> decisions=new LinkedHashMap<>();
    }
    private static final class Saved {
        final Document document;final byte[] image;Saved(Document document,byte[] image){this.document=document;this.image=image;}
    }
    private final PluginStorage storage;
    private final Map<String,Saved> accounts=new HashMap<>();
    @Inject public BuyRecoveryJournal(PluginStorage storage){this.storage=storage;}
    private Saved read(String account) {
        if(account==null || !account.equals(storage.accountId()))return null;
        try {
            var path=storage.accountDir(account).resolve(FILE);byte[] image=Files.exists(path)?Files.readAllBytes(path):null;
            var old=accounts.get(account);if(old!=null)return Arrays.equals(image,old.image)?old:null;
            Document doc=image==null?new Document():storage.gson().fromJson(new String(image,StandardCharsets.UTF_8),Document.class);
            if(image==null)doc.accountId=account;
            if(doc==null || doc.formatVersion!=1 || !account.equals(doc.accountId) || doc.decisions==null
                || doc.decisions.entrySet().stream().anyMatch(e->e.getValue()==null || !e.getValue().valid() || !e.getKey().equals(e.getValue().identity)))return null;
            if(!account.equals(storage.accountId()))return null;
            var saved=new Saved(doc,image);accounts.put(account,saved);return saved;
        }catch(Exception unavailable){return null;}
    }
    private boolean write(String account,Saved old,Document candidate) {
        try {
            if(!account.equals(storage.accountId()) || accounts.get(account)!=old)return false;
            var path=storage.accountDir(account).resolve(FILE);
            byte[] current=Files.exists(path)?Files.readAllBytes(path):null;
            if(!Arrays.equals(current,old.image))return false;
            byte[] image=storage.gson().toJson(candidate).getBytes(StandardCharsets.UTF_8);
            if(!storage.writeDurably(path,image))return false;
            accounts.put(account,new Saved(candidate,image));return account.equals(storage.accountId());
        }catch(Exception unavailable){return false;}
    }
    private Document copy(Saved old){return storage.gson().fromJson(storage.gson().toJson(old.document),Document.class);}
    public synchronized boolean record(String account,TrackedOffer offer,String reason,long now,long patience) {
        if(offer==null || !offer.isBuying() || !"BUYING".equals(offer.getState()) || offer.getRemaining()<=0
            || now<offer.getFirstSeen() || patience<0 || patience>Long.MAX_VALUE-now)return false;
        return record(account,offer.getContinuationIdentity(),offer.getItemId(),offer.getPrice(),offer.getRemaining(),reason,now,patience);
    }
    public synchronized boolean expire(String account,BuyRepriceIntent.Request request,long now,long patience) {
        return request!=null && record(account,request.originIdentity,request.itemId,request.originalPrice,request.quantity,
            "Original replacement waiting budget elapsed; acquired units still require an exit.",now,patience);
    }
    private boolean record(String account,String identity,int item,int price,int quantity,String reason,long now,long patience) {
        var saved=read(account);if(saved==null || now<=0 || patience<0 || patience>Long.MAX_VALUE-now)return false;
        if(saved.document.decisions.containsKey(identity))return true;
        var candidate=copy(saved);var decision=new Decision();decision.identity=identity;decision.reason=reason;
        decision.item=item;decision.price=price;decision.quantity=quantity;decision.acceptedAt=now;
        decision.notBefore=now+patience;if(!decision.valid())return false;
        candidate.decisions.put(decision.identity,decision);return write(account,saved,candidate);
    }
    public synchronized boolean isGuidedCancellation(String account,TrackedOffer offer,long now) {
        var saved=read(account);if(saved==null || offer==null || !offer.isBuying())return false;
        var decision=saved.document.decisions.get(offer.getContinuationIdentity());
        return decision!=null && now>=decision.acceptedAt && decision.item==offer.getItemId() && decision.price==offer.getPrice()
            && "CANCELLED_BUY".equals(offer.getState());
    }
    /** Null means the decision document is unavailable: withdraw all new entries, preserve exits. */
    public synchronized Set<Integer> pausedItems(String account,MarketSnapshot market,java.util.function.IntFunction<List<Candle>> series,
            TradingHorizon horizon,TaxCalculator tax,long minimumProfit,long now) {
        var saved=read(account);if(saved==null)return null;
        var paused=new HashSet<Integer>();Document candidate=null;
        for(var decision:saved.document.decisions.values())if(decision.releasedAt==0) {
            if(recovered(decision,market.latest(decision.item),series.apply(decision.item),horizon,tax,minimumProfit,now)) {
                if(candidate==null)candidate=copy(saved);candidate.decisions.get(decision.identity).releasedAt=now;
            }else paused.add(decision.item);
        }
        if(candidate!=null && !write(account,saved,candidate))for(var decision:saved.document.decisions.values())
            if(decision.releasedAt==0)paused.add(decision.item);
        return Collections.unmodifiableSet(paused);
    }
    private static boolean recovered(Decision d,LatestPrice quote,List<Candle> series,TradingHorizon horizon,TaxCalculator tax,long minimum,long now) {
        return recovered(d.item,d.price,d.quantity,d.acceptedAt,d.notBefore,quote,series,horizon,tax,minimum,now);
    }
    /** Pure live/replay qualification; it does not release a decision or authorize an entry. */
    public static boolean recovered(int item,int price,int quantity,long acceptedAt,long notBefore,LatestPrice quote,List<Candle> series,
            TradingHorizon horizon,TaxCalculator tax,long minimum,long now) {
        return BuyRecoveryEvidence.recovered(item,price,quantity,acceptedAt,notBefore,quote,series,horizon,tax,minimum,now);
    }
    public synchronized List<TradeRecoveryItem> recoveryItems(String account,long now) {
        var saved=read(account);if(saved==null)return List.of(new TradeRecoveryItem("buy-recovery-read",TradeRecoveryItem.Kind.EVIDENCE,
            TradeRecoveryItem.State.AWAITING_EVIDENCE,0,"Buy recovery decisions",-1,0,0,true,0,now+30,
            "Cancel-and-reassess metadata is unavailable; new entries wait.","Retry the original document; existing offers and exits remain managed."));
        var rows=new ArrayList<TradeRecoveryItem>();
        for(var d:saved.document.decisions.values())if(d.releasedAt==0)rows.add(new TradeRecoveryItem(d.identity,TradeRecoveryItem.Kind.ENTRY_PAUSE,
            TradeRecoveryItem.State.PAUSED,d.item,"Item "+d.item,-1,d.quantity,d.price,true,d.acceptedAt,Math.max(now+30,d.notBefore),
            "Cancelled buy is being reassessed: "+d.reason,"Wait for patience, a material price change and two new supported completed buckets. Acquired units retain their sale plan."));
        return List.copyOf(rows);
    }
}
