package com.flippingfriend.companion;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Synthetic capacity process only: separate test class, credentials, accounts and database. */
public final class HostedPilotLoadServer {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath();
        if(!root.getFileName().toString().startsWith("ff-capacity-") || Files.exists(root))
            throw new IllegalArgumentException("new disposable ff-capacity- directory required");
        Files.createDirectories(root);Gson gson=new Gson();
        var config=gson.fromJson(Files.readString(Path.of(args[1])),HostedPilotMain.Config.class);
        var market=new MarketIngestionService(gson);var hourly=new JsonObject();var mapping=new HashMap<Integer,MarketIngestionService.Item>();
        for(int item=10000;item<16000;item++){
            var row=new JsonObject();row.addProperty("lowPriceVolume",1000000);row.addProperty("highPriceVolume",1000000);
            row.addProperty("avgLowPrice",100);row.addProperty("avgHighPrice",120);hourly.add(""+item,row);
            mapping.put(item,new MarketIngestionService.Item(item,"Synthetic public item "+item,10000,true));
        }
        var field=MarketIngestionService.class.getDeclaredField("state");field.setAccessible(true);
        var executor=Executors.newScheduledThreadPool(1);
        Runnable update=()->{try{
            long now=System.currentTimeMillis()/1000;var latest=new JsonObject();
            for(int item:mapping.keySet()){
                var row=new JsonObject();row.addProperty("low",100);row.addProperty("high",120);row.addProperty("lowTime",now-1);row.addProperty("highTime",now-2);latest.add(""+item,row);
            }
            field.set(market,new MarketIngestionService.MarketState(mapping,latest,hourly,hourly,now,now/300*300-300,now/3600*3600-3600));
        }catch(Exception failure){throw new RuntimeException(failure);}};
        update.run();
        try(var evidence=new PilotEvidenceStore(root.resolve("synthetic.db"));var server=new HostedPilotMain.Server(config,market,evidence,gson);
            var archive=new HostedPublicArchive(root.resolve("public-archive"),config.publicArchive,market::state,(item,step)-> {
                var bars=new JsonArray();int seconds=step.equals("5m")?300:3600;long now=System.currentTimeMillis()/1000/seconds*seconds;
                for(int i=720;i>0;i--){var bar=hourly.getAsJsonObject("10000").deepCopy();bar.addProperty("timestamp",now-i*seconds);bars.add(bar);}return bars;
            },HostedPublicArchive::now,()->1024L*1024*1024)) {
            server.start();executor.scheduleWithFixedDelay(update,1,1,TimeUnit.SECONDS);
            executor.scheduleWithFixedDelay(server::allocate,100,100,TimeUnit.MILLISECONDS);
            executor.scheduleWithFixedDelay(archive::request,0,60,TimeUnit.SECONDS);
            System.out.println("SYNTHETIC_READY "+server.port());System.out.flush();
            Thread.sleep(args.length>2?Long.parseLong(args[2])*1000:180000);
        }finally{executor.shutdownNow();}
    }
}
