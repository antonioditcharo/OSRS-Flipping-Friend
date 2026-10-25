package com.flippingfriend.core;

import com.flippingfriend.model.FillEstimate;
import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

/** Pin the retained format: faster encoding cannot reidentify historical prediction evidence. */
public class ExecutionForecastIdentityTest {
    private ExecutionForecast forecast(int price) {
        return new ExecutionForecast(ExecutionForecast.MODEL_VERSION,.35,0,1,true,561,true,price,10,3,1,
            3600,12,12,120,new FillEstimate(.75,.2,100,.1),1,.2);
    }
    @Test public void retainedFingerprintAndWireRoundTripRemainIdentical() {
        var original=forecast(100);var gson=new Gson();
        assertEquals("8f43dc10b5fe21f1f54df63bb9629f8ddb4f3f1d4a601a70e93efb769684500a",original.getFingerprint());
        assertEquals(original.getFingerprint(),gson.fromJson(gson.toJson(original),ExecutionForecast.class).getFingerprint());
        assertNotEquals(original.getFingerprint(),forecast(101).getFingerprint());
        assertNull(forecast(0).getFingerprint());
        assertNull(gson.fromJson("{}",ExecutionForecast.class).getFingerprint());
    }
}
