package com.flippingfriend.core;

import com.google.gson.*;
import java.time.Instant;
import java.util.*;

/** Secondary observations. Average prices and observed times are never exact fill assertions. */
public final class GeHistorySnapshot {
    public String accountId,previousSnapshot,historyJson;
    public long observedAt;
    public List<String> visibleHistory=new ArrayList<>();
    public static final class Row {
        public final int occurrence,itemId,quantity;
        public final boolean buying;
        public final long averagePrice,recordedAt;
        Row(int occurrence,int item,int quantity,boolean buy,long price,long at) {
            this.occurrence=occurrence;itemId=item;this.quantity=quantity;buying=buy;averagePrice=price;recordedAt=at;
        }
        public long grossLower(){return Math.multiplyExact(averagePrice,quantity);}
        public long grossUpper(){return Math.addExact(grossLower(),quantity-1L);}
    }
    public String evidenceId() {
        try {
            String raw=accountId+historyJson+new Gson().toJson(visibleHistory);var out=new StringBuilder();
            for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();
        }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public List<Row> rows() {
        AccountIdentity.require(accountId);
        if(observedAt<=0 || historyJson==null || historyJson.length()>1048576 || visibleHistory==null || visibleHistory.size()>2048)
            throw new IllegalArgumentException("bounded scoped history required");
        JsonElement parsed=JsonParser.parseString(historyJson);
        if(!parsed.isJsonArray() || parsed.getAsJsonArray().size()>1024)throw new IllegalArgumentException("invalid RuneLite history");
        var rows=new ArrayList<Row>();int occurrence=0;
        for(var element:parsed.getAsJsonArray()) {
            var row=element.getAsJsonObject();
            int item=row.get("i").getAsInt(),quantity=row.get("q").getAsInt();long price=row.get("p").getAsLong();
            JsonPrimitive time=row.getAsJsonPrimitive("t");
            long at=time.isNumber()?Instant.ofEpochMilli(time.getAsBigDecimal().longValueExact()).getEpochSecond()
                :Instant.parse(time.getAsString()).getEpochSecond();
            if(item<=0 || quantity<=0 || price<0 || at<=0 || at>observedAt)throw new IllegalArgumentException("invalid history row");
            Row value=new Row(occurrence++,item,quantity,row.get("b").getAsBoolean(),price,at);
            value.grossUpper();rows.add(value);
        }
        return List.copyOf(rows);
    }
}
