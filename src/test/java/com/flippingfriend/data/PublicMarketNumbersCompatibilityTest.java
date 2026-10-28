package com.flippingfriend.data;

import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** This module runs with RuneLite's pinned Gson 2.8.5, rather than the companion's newer Gson. */
public class PublicMarketNumbersCompatibilityTest {
    @Test public void providedGsonDecodesExactValuesAndRejectsNarrowingPerItem() {
        var gson=new Gson();var rows=gson.fromJson("{\"1\":{\"low\":8770000000,\"high\":8865000000},\"2\":{\"low\":100,\"high\":120}}",JsonObject.class);
        var decoded=PublicMarketNumbers.quotes(rows);
        assertEquals(1,decoded.size());assertEquals(Integer.valueOf(100),decoded.get(2).getLow());
        assertEquals(8770000000L,rows.getAsJsonObject("1").get("low").getAsLong());
    }
}
