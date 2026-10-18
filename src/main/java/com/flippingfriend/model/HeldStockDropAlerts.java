package com.flippingfriend.model;

import com.flippingfriend.session.Position;
import java.util.*;

/** Account-local duplicate suppression; only fresh, authoritative managed quantities trigger an alert. */
public final class HeldStockDropAlerts {
    private String account;
    private final Set<Integer> alerted=new HashSet<>();
    public synchronized List<String> update(String currentAccount, Collection<Position> positions,
        List<MarketDropDetector.Drop> drops) {
        if (!Objects.equals(account,currentAccount)) { alerted.clear(); account=currentAccount; }
        if (currentAccount==null) { alerted.clear(); return Collections.emptyList(); }
        Map<Integer,Position> managed=new HashMap<>();
        for (Position p:positions) if(p.isCostKnown()) managed.put(p.getItemId(),p);
        alerted.retainAll(managed.keySet());
        List<String> messages=new ArrayList<>();
        for(var drop:drops) if(managed.containsKey(drop.itemId) && alerted.add(drop.itemId))
            messages.add(String.format(Locale.ROOT,"Held stock price drop: %s is %.1f%% below the previous five-minute low-price average. Current low: %,d gp. This is not a sell instruction.",
                drop.name,drop.fraction*100,drop.currentLow));
        // A new account/holding resets this episode. Stale/missing prices cannot re-arm an alert.
        return messages;
    }
}
