package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.flippingfriend.model.FillEstimate;
import com.google.gson.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ColdOfferDecoderTest {
    final Gson gson=new Gson();
    final String account=AccountIdentity.fromProfileKey("cold-source");
    final long at=1800000000;
    ExecutionForecast forecast(int price) {
        return new ExecutionForecast(ExecutionForecast.MODEL_VERSION,.35,0,1,true,561,true,price,10,3,1,
            3600,12,12,120,new FillEstimate(.75,.2,100,.1),1,.2);
    }
    OfferEvent event(String id,int filled,long sequence,int price) {
        return OfferEvent.builder("fixture",at+sequence,"BUYING").accountId(account).authenticatedObservation(true)
            .eventIdentity(id,"session","episode").sequence(sequence).slot(0).item(561,"Nature rune")
            .buying(true).price(price).quantities(10,filled).spent(filled*(long)price).firstSeenAt(at)
            .recommendation("advice",price,10,0,12,.75).executionForecast(forecast(price)).build();
    }
    String withForecast(String forecast) {
        var root=gson.toJsonTree(event("source",0,1,100)).getAsJsonObject();root.remove("executionForecast");
        String json=root.toString();return json.substring(0,json.length()-1)+",\"executionForecast\":"+forecast+"}";
    }
    void equivalent(ColdOfferDecoder decoder,String raw) {
        assertEquals(gson.toJson(gson.fromJson(raw,OfferEvent.class)),gson.toJson(decoder.decode(raw)));
    }
    @Test public void repeatedEvidencePreservesEveryObservationAndOriginalAcquisitionContext() {
        var origin=event("source",0,1,100);
        var proof=AcceptedBuyPlanProof.accepted(account,"accepted",561,100,10,at,CaptureOrder.next(),
            new AcquisitionExitContext(140,90,480),forecast(100)).captured(CaptureOrder.next()).bindTo(origin);
        origin=origin.withAcceptedBuyPlanProof(proof);
        var partial=event("partial",2,2,100).withAcceptedBuyPlanProof(proof);
        var repeat=event("repeat",2,3,100).withAcceptedBuyPlanProof(proof);
        var decoder=new ColdOfferDecoder();List<OfferEvent> expected=new ArrayList<>(),actual=new ArrayList<>();
        for(var e:List.of(origin,partial,repeat)) {
            String raw=gson.toJson(e);equivalent(decoder,raw);
            expected.add(gson.fromJson(raw,OfferEvent.class));actual.add(decoder.decode(raw));
        }
        assertNotSame(actual.get(1),actual.get(2));assertEquals("partial",actual.get(1).getEventId());
        assertEquals("repeat",actual.get(2).getEventId());assertEquals(2,actual.get(2).getFilledQuantity());
        assertEquals(AcquisitionExitEvidence.read(expected),AcquisitionExitEvidence.read(actual));
        assertEquals(Set.of("source","partial","repeat"),AcquisitionExitEvidence.read(actual).keySet());
    }
    @Test public void duplicateFieldsNumericCoercionsUnknownValuesAndLenientStringsMatchGson() {
        String base=gson.toJson(forecast(100));var decoder=new ColdOfferDecoder();
        for(String value:List.of(base.replace("\"price\":100","\"price\":101,\"price\":100"),
                base.replace("\"price\":100","\"price\":\"100\"").replace("\"quantity\":10","\"quantity\":1e1"),
                base.substring(0,base.length()-1)+",\"unknown\":{\"x\":1,\"x\":2,\"a\":[null,true,\"\\u2028\"]}}",
                "/* retained lenient payload */"+base))equivalent(decoder,withForecast(value));
    }
    @Test public void anEarlierMalformedDuplicateCannotBeErasedByALaterValidMember() {
        String base=gson.toJson(forecast(100));
        for(String value:List.of(base.replace("\"price\":100","\"price\":{},\"price\":100"),
                base.replace("\"price\":100","\"price\":100,\"price\":{}"),"[]","\"wrong-type\"")) {
            String raw=withForecast(value);
            try{gson.fromJson(raw,OfferEvent.class);fail("Baseline must reject malformed evidence");}catch(JsonParseException expected){}
            try{new ColdOfferDecoder().decode(raw);fail("Cold read must retain rejection");}catch(JsonParseException expected){}
        }
    }
    @Test public void malformedOptionalClaimsStayUnqualifiedAndNonFiniteValuesAreNotCached() {
        var decoder=new ColdOfferDecoder();String base=gson.toJson(forecast(100));
        String unsupported=withForecast(base.replace(ExecutionForecast.MODEL_VERSION,"unsupported"));
        equivalent(decoder,unsupported);equivalent(decoder,unsupported);assertEquals(0,decoder.retainedEntries());
        String nan=withForecast(base.replace("\"completionProbability\":0.75","\"completionProbability\":NaN"));
        assertTrue(Double.isNaN(gson.fromJson(nan,OfferEvent.class).getExecutionForecast().getCompletionProbability()));
        assertTrue(Double.isNaN(decoder.decode(nan).getExecutionForecast().getCompletionProbability()));
        assertEquals(0,decoder.retainedEntries());
    }
    @Test public void largePayloadsAndLowRepetitionUseTheExistingDecoderWithoutUnboundedRetention() {
        var decoder=new ColdOfferDecoder();String large=gson.toJson(forecast(100));
        large=large.substring(0,large.length()-1)+",\"unknown\":\""+"x".repeat(ColdOfferDecoder.MAX_INPUT_CHARS)+"\"}";
        equivalent(decoder,withForecast(large));assertEquals(0,decoder.retainedEntries());
        for(int i=0;i<200;i++)equivalent(decoder,gson.toJson(event("source-"+i,0,i+1,100+i)));
        assertEquals(0,decoder.retainedEntries());assertEquals(0,decoder.retainedChars());
    }
    @Test public void repeatedAndNewClaimsRetainACombinedBoundAndDoNotBorrowAnotherOrder() {
        var decoder=new ColdOfferDecoder();String common=gson.toJson(event("common",0,1,100));
        for(int i=0;i<300;i++) {
            equivalent(decoder,common);equivalent(decoder,gson.toJson(event("new-"+i,0,i+2,101+i)));
            assertTrue(decoder.retainedEntries()<=ColdOfferDecoder.MAX_ENTRIES);
            assertTrue(decoder.retainedChars()<=ColdOfferDecoder.MAX_RETAINED_CHARS);
        }
        var a=decoder.decode(common);var b=decoder.decode(gson.toJson(event("other",0,2,102)));
        assertNotEquals(a.getExecutionForecast().getFingerprint(),b.getExecutionForecast().getFingerprint());
        assertFalse(a.getExecutionForecast().matches(b));
    }
}
