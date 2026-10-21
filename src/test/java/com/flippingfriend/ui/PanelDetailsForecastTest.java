package com.flippingfriend.ui;

import com.flippingfriend.core.ExecutionForecast;
import com.flippingfriend.model.FillEstimate;
import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionType;
import org.junit.Test;
import static org.junit.Assert.*;

public class PanelDetailsForecastTest {
    @Test public void detailsDistinguishLegDurationFromFullOrderCompletionHorizon() {
        ExecutionForecast forecast=new ExecutionForecast(ExecutionForecast.MODEL_VERSION,.35,0,1,true,
            561,false,100,10,3,1,3600,12,6,1000,new FillEstimate(.8,.2,100,.1),1,.2);
        Suggestion advice=Suggestion.builder(SuggestionType.SELL).expectedMinutes(12).executionForecast(forecast).build();
        String text=PanelDetails.forecastDescription(advice);
        assertTrue(text.contains("Sell full-order estimate: 12 minutes"));
        assertTrue(text.contains("within 180 minutes: 80%"));
        assertTrue(text.contains("not validated against account outcomes"));
        assertTrue(text.contains("alone does not authorize a price reduction"));
    }
    @Test public void unknownForecastDoesNotInventAnOrderHorizonOrProbability() {
        assertEquals("",PanelDetails.forecastDescription(Suggestion.idle()));
        String text=PanelDetails.forecastDescription(Suggestion.builder(SuggestionType.BUY).expectedMinutes(30).build());
        assertTrue(text.contains("not validated"));assertFalse(text.contains("%"));assertFalse(text.contains("within"));
    }
}
