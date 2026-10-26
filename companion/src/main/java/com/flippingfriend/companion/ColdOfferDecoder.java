package com.flippingfriend.companion;

import com.flippingfriend.core.AcceptedBuyPlanProof;
import com.flippingfriend.core.ExecutionForecast;
import com.flippingfriend.core.OfferEvent;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.*;
import java.util.*;

/** One cold canonical read's bounded reuse of immutable values; observations are never reused. */
final class ColdOfferDecoder {
    static final int MAX_INPUT_CHARS=64*1024,MAX_ENTRY_CHARS=8*1024;
    static final int MAX_ENTRIES=128,MAX_RETAINED_CHARS=256*1024;
    private final Gson plain=new Gson();
    private final Gson shared=new GsonBuilder().registerTypeAdapterFactory(new Values()).create();
    private final LinkedHashMap<String,Object> values=new LinkedHashMap<>(16,.75f,true);
    private int retainedChars,attempts,hits;
    private boolean disabled;

    OfferEvent decode(String source) {
        return source!=null && source.length()<=MAX_INPUT_CHARS && !disabled
            ?shared.fromJson(source,OfferEvent.class):plain.fromJson(source,OfferEvent.class);
    }
    int retainedEntries(){return values.size();}
    int retainedChars(){return retainedChars;}

    private final class Values implements TypeAdapterFactory {
        @Override public <T> TypeAdapter<T> create(Gson gson,TypeToken<T> type) {
            Class<?> raw=type.getRawType();
            if(raw!=ExecutionForecast.class && raw!=AcceptedBuyPlanProof.class)return null;
            TypeAdapter<T> delegate=gson.getDelegateAdapter(this,type);
            return new TypeAdapter<T>() {
                @Override public T read(JsonReader input)throws IOException {
                    if(disabled)return delegate.read(input);
                    boolean lenient=input.isLenient();
                    String json=copyValue(input),key=raw.getName()+":"+json;
                    @SuppressWarnings("unchecked") T found=(T)values.get(key);
                    if(found!=null){trial(true);return found;}
                    // Copy every token, including duplicate members in their original order. A tree
                    // could erase an earlier malformed duplicate and silently change Gson semantics.
                    JsonReader replay=new JsonReader(new StringReader(json));replay.setLenient(lenient);
                    T decoded=delegate.read(replay);
                    if(!disabled && key.length()<=MAX_ENTRY_CHARS && reusable(decoded)) {
                        values.put(key,decoded);retainedChars+=key.length();
                        while(values.size()>MAX_ENTRIES || retainedChars>MAX_RETAINED_CHARS) {
                            var first=values.entrySet().iterator();var old=first.next();retainedChars-=old.getKey().length();first.remove();
                        }
                    }
                    // Histories with little repetition pay only a bounded trial, then use Gson's
                    // existing streaming reader directly for the rest of this rebuild.
                    trial(false);
                    return decoded;
                }
                @Override public void write(JsonWriter out,T value)throws IOException {delegate.write(out,value);}
            }.nullSafe();
        }
    }
    private void trial(boolean hit) {
        attempts++;if(hit)hits++;
        if(attempts<128)return;
        if(hits<8){disabled=true;values.clear();retainedChars=0;}
        attempts=0;hits=0;
    }
    private static boolean reusable(Object value) {
        try {
            return value instanceof ExecutionForecast?((ExecutionForecast)value).isValid()
                :value instanceof AcceptedBuyPlanProof && ((AcceptedBuyPlanProof)value).isWellFormed();
        }catch(RuntimeException malformed){return false;}
    }
    /** Iterative Gson token copying preserves duplicate fields, coercions and numeric lexemes. */
    private static String copyValue(JsonReader input)throws IOException {
        StringWriter text=new StringWriter();JsonWriter out=new JsonWriter(text);
        out.setLenient(true);out.setSerializeNulls(true);int depth=0;
        do {
            switch(input.peek()) {
                case BEGIN_OBJECT:input.beginObject();out.beginObject();depth++;break;
                case END_OBJECT:input.endObject();out.endObject();depth--;break;
                case BEGIN_ARRAY:input.beginArray();out.beginArray();depth++;break;
                case END_ARRAY:input.endArray();out.endArray();depth--;break;
                case NAME:out.name(input.nextName());break;
                case STRING:out.value(input.nextString());break;
                case NUMBER:out.jsonValue(input.nextString());break;
                case BOOLEAN:out.value(input.nextBoolean());break;
                case NULL:input.nextNull();out.nullValue();break;
                default:throw new JsonSyntaxException("Missing immutable evidence value");
            }
        }while(depth>0);
        out.flush();return text.toString();
    }
}
