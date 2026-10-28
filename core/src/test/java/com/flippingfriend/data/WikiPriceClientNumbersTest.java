package com.flippingfriend.data;

import com.google.gson.Gson;
import okhttp3.*;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class WikiPriceClientNumbersTest {
    private WikiPriceClient client(String body) {
        var http=new OkHttpClient.Builder().addInterceptor(chain->new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(200).message("OK").body(ResponseBody.create(body,MediaType.get("application/json"))).build()).build();
        return new WikiPriceClient(http,new Gson());
    }
    @Test public void actualBulkEndpointDecoderKeepsValidRowsBesideMultiBillionQuotes()throws Exception {
        var values=client("{\"data\":{\"1\":{\"low\":8770000000,\"high\":8865000000},\"2\":{\"low\":100,\"high\":120}}}").fetchLatest();
        assertEquals(1,values.size());assertEquals(Integer.valueOf(100),values.get(2).getLow());
    }
    @Test public void malformedAggregateRowCannotAbortTheOtherItemsOrNarrowItsClock()throws Exception {
        var values=client("{\"timestamp\":4294967297,\"data\":{\"1\":{\"avgLowPrice\":4294967396,\"lowPriceVolume\":1,\"highPriceVolume\":1},\"2\":{\"avgLowPrice\":100,\"avgHighPrice\":120,\"lowPriceVolume\":1,\"highPriceVolume\":2}}}").fetchAverages("5m");
        assertEquals(1,values.size());assertEquals(4294967297L,values.get(2).getTimestamp());
    }
    @Test public void unsupportedSeriesHasAnItemLocalFailureDistinctFromTransportFailure()throws Exception {
        try{client("{\"data\":[{\"timestamp\":1800,\"avgLowPrice\":4294967396,\"lowPriceVolume\":1,\"highPriceVolume\":1}]}").fetchTimeseries(1,"5m");fail();}
        catch(WikiPriceClient.UnsupportedHistoryException expected){}
        try{client("{\"timestamp\":1800.5,\"data\":{}}").fetchAverages("5m");fail();}catch(IOException expected){}
    }
}
