package com.flippingfriend.companion;

import com.flippingfriend.data.Candle;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class FrozenReplayInputsTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    private final Gson json=new Gson();
    private String blob(Path root,JsonElement value)throws Exception {
        byte[] raw=json.toJson(value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String key=ExecutionEvaluationMain.hash(raw);Path path=root.resolve(key+".json.gz");
        if(!Files.exists(path))try(var out=new GZIPOutputStream(Files.newOutputStream(path))){out.write(raw);}
        return key;
    }
    private Path compact(Path directory,AccountReplayInput input)throws Exception {
        Files.createDirectories(directory.resolve("blobs"));Path blobs=directory.resolve("blobs");
        JsonObject metadata=json.toJsonTree(input).getAsJsonObject();metadata.remove("frames");JsonArray refs=new JsonArray();
        for(var frame:input.frames) {
            JsonObject value=json.toJsonTree(frame).getAsJsonObject();
            for(String field:List.of("shortHistory","longHistory")) {
                JsonObject source=value.getAsJsonObject(field),histories=new JsonObject();
                for(var entry:source.entrySet()) {
                    JsonArray chunks=new JsonArray(),bars=entry.getValue().getAsJsonArray();
                    for(int i=0;i<bars.size();i+=64) {
                        JsonArray chunk=new JsonArray();for(int j=i;j<Math.min(i+64,bars.size());j++)chunk.add(bars.get(j));
                        chunks.add(blob(blobs,chunk));
                    }
                    histories.add(entry.getKey(),chunks);
                }
                value.add(field,histories);
            }
            JsonObject ref=new JsonObject();ref.addProperty("availableAt",frame.availableAt);ref.addProperty("sha256",blob(blobs,value));refs.add(ref);
        }
        JsonObject storage=new JsonObject();storage.addProperty("version",FrozenReplayInputs.VERSION);storage.add("frames",refs);metadata.add("storage",storage);
        Path result=directory.resolve("input.json");Files.writeString(result,json.toJson(metadata));return result;
    }
    private AccountReplayInput input() {
        var input=new AccountReplayTest().input(1,30);
        for(var f:input.frames)f.shortHistory.put(1,List.of(new Candle(input.startAt-300,120,100,100,100)));
        return input;
    }
    @Test public void referencedFramesMatchFullFramesAndPreserveReplayAccountingAndActions()throws Exception {
        var full=input();Path source=compact(folder.newFolder().toPath(),full);
        var compact=FrozenReplayInputs.load(source);compact.validate();
        assertTrue(compact.frames instanceof FrozenReplayInputs.Frames);
        for(int i=0;i<full.frames.size();i++)assertEquals(json.toJson(full.frames.get(i)),json.toJson(compact.frames.get(i)));
        AccountReplay.Result a,b;
        try(var replay=new AccountReplay(full,5,100,folder.newFolder().toPath().resolve("full.db"),new AccountReplayTest().fixtureEntries(10))){a=replay.run();}
        try(var replay=new AccountReplay(compact,5,100,folder.newFolder().toPath().resolve("compact.db"),new AccountReplayTest().fixtureEntries(10))){b=replay.run();}
        assertEquals(json.toJson(a),json.toJson(b));assertTrue(a.completedSales>0);
    }
    @Test public void windowRetainsOnlyPastWarmupWithoutMaterializingHistoryAndDoesNotMutateSource()throws Exception {
        var full=input();var compact=FrozenReplayInputs.load(compact(folder.newFolder().toPath(),full));
        var window=PricingQualificationMain.slice(compact,full.startAt+900,full.startAt+1200);
        assertEquals(21,window.frames.size());assertEquals(31,compact.frames.size());
        assertTrue(window.frames.stream().allMatch(f->f.availableAt<=full.startAt+1200));
        assertEquals(json.toJson(full.window(full.startAt+900,full.startAt+1200)),json.toJson(window));
    }
    @Test public void frozenCopyIsIndependentAndChangedReferencedEvidenceCannotBeVerified()throws Exception {
        Path source=compact(folder.newFolder().toPath(),input());Path destination=folder.newFolder().toPath().resolve("input.json");
        FrozenReplayInputs.copy(source,destination);var copy=FrozenReplayInputs.load(destination);copy.validate();
        String filename;
        try(var files=Files.list(source.getParent().resolve("blobs"))){filename=files.findFirst().get().getFileName().toString();}
        Files.write(source.getParent().resolve("blobs").resolve(filename),new byte[]{1,2,3});
        FrozenReplayInputs.load(destination).validate();
        try{FrozenReplayInputs.load(source).validate();fail();}catch(UncheckedIOException|IllegalArgumentException expected){}
    }
    @Test public void sharedChunkCacheIsBoundedAndMutableCandlesCannotRewriteLaterFrames()throws Exception {
        var full=new AccountReplayTest().input(1,1100);
        for(int i=0;i<full.frames.size();i++)full.frames.get(i).shortHistory.put(1,List.of(new Candle(full.startAt-300,120+i,100,100,100)));
        var compact=FrozenReplayInputs.load(compact(folder.newFolder().toPath(),full));compact.validate();
        var frames=(FrozenReplayInputs.Frames)compact.frames;assertTrue(frames.cachedChunkCount()<=1024);assertTrue(frames.cachedHistoryCount()<=256);
        try{compact.frames.get(0).shortHistory.get(1).get(0).setTimestamp(1);fail();}catch(UnsupportedOperationException expected){}
        compact.frames.get(1);assertEquals(full.startAt-300,compact.frames.get(0).shortHistory.get(1).get(0).getTimestamp());
    }
    @Test public void mismatchedIndexConflictingFormatsAndUnsafeBlobNamesAreRejected()throws Exception {
        for(String mutation:List.of("time","path","both")) {
            Path source=compact(folder.newFolder().toPath(),input());JsonObject object=JsonParser.parseString(Files.readString(source)).getAsJsonObject();
            var ref=object.getAsJsonObject("storage").getAsJsonArray("frames").get(0).getAsJsonObject();
            if("time".equals(mutation))ref.addProperty("availableAt",1);
            else if("path".equals(mutation))ref.addProperty("sha256","../credentials.properties");
            else object.add("frames",new JsonArray());
            Files.writeString(source,json.toJson(object));
            try{FrozenReplayInputs.load(source).validate();fail(mutation);}catch(IllegalArgumentException expected){}
        }
    }
}
