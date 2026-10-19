package com.flippingfriend.companion;

import com.flippingfriend.data.Candle;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class MarketProjectionMainTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Gson json=new Gson();
    MarketProjectionMain.Input input() {
        var input=new MarketProjectionMain.Input();input.itemId=4151;input.bucketSeconds=3600;input.observedAt=1800000000;
        input.sourceUrl="https://prices.runescape.wiki/api/v1/osrs/timeseries?id=4151&timestep=1h";input.history=new ArrayList<>();
        for(int i=0;i<200;i++)input.history.add(new Candle(input.observedAt-(200L-i)*3600,1200+i*2,1000+i*2,10,10));return input;
    }
    @Test public void frozenProjectionAndChronologicalScoresReproduceWithoutADatabase()throws Exception {
        Path dir=folder.newFolder("local-data").toPath(),source=dir.resolve("input.json"),out=dir.resolve("bundle");
        Files.writeString(source,json.toJson(input()));MarketProjectionMain.run(source,out);MarketProjectionMain.verify(out);
        var report=json.fromJson(Files.readString(out.resolve("results.json")),JsonObject.class);
        assertEquals(4,report.getAsJsonArray("rollingOriginEvaluations").size());
        for(var e:report.getAsJsonArray("rollingOriginEvaluations"))assertEquals("EXPERIMENTAL_DISPLAY_ONLY",e.getAsJsonObject().get("promotionStatus").getAsString());
        assertTrue(Files.readString(out.resolve("results.json")).contains("unknown"));
        try{MarketProjectionMain.run(source,out);fail("A frozen bundle cannot be overwritten");}catch(FileAlreadyExistsException expected){}
    }
    @Test public void changedScoresOrFutureDataCannotSilentlyBecomeValidatedEvidence()throws Exception {
        var input=input();byte[] before=MarketProjectionMain.result(json.toJson(input).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        input.history.add(new Candle(input.observedAt+3600,1000000,900000,100,100));
        assertArrayEquals(before,MarketProjectionMain.result(json.toJson(input).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Path dir=folder.newFolder("local-data").toPath(),source=dir.resolve("input.json"),out=dir.resolve("bundle");
        Files.writeString(source,json.toJson(input));MarketProjectionMain.run(source,out);Files.writeString(out.resolve("results.json"),"{}");
        try{MarketProjectionMain.verify(out);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void unspecifiedSourceAndUnsupportedResolutionRefuseTheReport() {
        var input=input();input.sourceUrl=null;
        try{input.validate();fail();}catch(IllegalArgumentException expected){}
        input=input();input.bucketSeconds=300;
        try{input.validate();fail();}catch(IllegalArgumentException expected){}
        input=input();input.sourceUrl="https://prices.runescape.wiki/api/v1/osrs/timeseries?id=561&timestep=1h";
        try{input.validate();fail("Another item's public history is not this item's history");}catch(IllegalArgumentException expected){}
    }
}
