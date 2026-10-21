package com.flippingfriend.session;

import com.flippingfriend.data.PluginStorage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Isolated workflow metadata: freezing a timeout must never rewrite offer/accounting snapshots. */
final class BuyMaintenanceDeadlines {
    static final String FILE_NAME="buy-maintenance-deadlines.json";
    private final PluginStorage storage;
    private final Map<String,State> accounts=new HashMap<>();
    private static final class Document {
        int formatVersion=1;String accountId;Map<String,Deadline> deadlines=new LinkedHashMap<>();
        Document(String account){accountId=account;}
    }
    private static final class Deadline {
        int slot,item,price,quantity;long firstSeen,frozenAt,at;
        Deadline(TrackedOffer offer,long frozen,long deadline) {
            slot=offer.getSlot();item=offer.getItemId();price=offer.getPrice();quantity=offer.getTotalQuantity();
            firstSeen=offer.getFirstSeen();frozenAt=frozen;at=deadline;
        }
        boolean valid(){return slot>=0 && slot<8 && item>0 && price>0 && quantity>0 && firstSeen>0 && frozenAt>=firstSeen && at>=firstSeen;}
        boolean matches(TrackedOffer offer){return valid() && slot==offer.getSlot() && item==offer.getItemId()
            && price==offer.getPrice() && quantity==offer.getTotalQuantity() && firstSeen==offer.getFirstSeen();}
    }
    private static final class State {
        final Path file;final Document document;byte[] committed;boolean dirty;
        State(Path path,Document data,byte[] image){file=path;document=data;committed=image;}
    }
    BuyMaintenanceDeadlines(PluginStorage storage){this.storage=storage;}
    synchronized long freeze(String account,TrackedOffer offer,double originalMinutes,double fallbackMinutes,long now) {
        if(account==null || !account.equals(storage.accountId()) || offer==null || !offer.isBuying()
            || !"BUYING".equals(offer.getState()) || offer.getFirstSeen()<=0 || now<offer.getFirstSeen())return 0;
        try {
            Path file=storage.accountDir(account).resolve(FILE_NAME);State state=accounts.get(account);
            byte[] current=Files.exists(file)?Files.readAllBytes(file):null;
            if(state==null) {
                Document data=current==null?new Document(account):storage.gson().fromJson(new String(current,StandardCharsets.UTF_8),Document.class);
                if(data==null || data.formatVersion!=1 || !account.equals(data.accountId) || data.deadlines==null
                    || data.deadlines.entrySet().stream().anyMatch(e->e.getKey()==null || e.getKey().isBlank() || e.getValue()==null || !e.getValue().valid()))return 0;
                state=new State(file,data,current);accounts.put(account,state);
            }
            if(!file.equals(state.file) || !Arrays.equals(current,state.committed))return 0;
            String identity=offer.getContinuationIdentity();Deadline deadline=state.document.deadlines.get(identity);
            if(deadline==null) {
                boolean original=Double.isFinite(originalMinutes) && originalMinutes>0;
                double minutes=original?originalMinutes:fallbackMinutes;long start=original?offer.getFirstSeen():now;
                if(!Double.isFinite(minutes) || minutes<=0 || minutes>(Long.MAX_VALUE-start)/60.0)return 0;
                deadline=new Deadline(offer,now,start+(long)Math.ceil(minutes*60));
                state.document.deadlines.put(identity,deadline);state.dirty=true;
            }
            if(!deadline.matches(offer) || deadline.frozenAt>now)return 0;
            if(state.dirty) {
                if(!account.equals(storage.accountId()))return 0;
                byte[] image=storage.gson().toJson(state.document).getBytes(StandardCharsets.UTF_8);
                if(!storage.writeDurably(state.file,image))return 0;
                state.committed=image;state.dirty=false;
            }
            return account.equals(storage.accountId())?deadline.at:0;
        }catch(Exception unavailable){return 0;}
    }
}
