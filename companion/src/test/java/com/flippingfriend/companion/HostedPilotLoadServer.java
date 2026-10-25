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
        var market=new MarketIngestionService(gson);var hourly=new JsonObject();
        for(int item=10000;item<10100;item++){var row=new JsonObject();row.addProperty("lowPriceVolume",1000000);row.addProperty("highPriceVolume",1000000);hourly.add(""+item,row);}
        var field=MarketIngestionService.class.getDeclaredField("state");field.setAccessible(true);
        var executor=Executors.newScheduledThreadPool(1);
        Runnable update=()->{try{field.set(market,new MarketIngestionService.MarketState(Map.of(),new JsonObject(),hourly,hourly,System.currentTimeMillis()/1000));}catch(Exception failure){throw new RuntimeException(failure);}};
        update.run();
        try(var evidence=new PilotEvidenceStore(root.resolve("synthetic.db"));var server=new HostedPilotMain.Server(config,market,evidence,gson)) {
            server.start();executor.scheduleWithFixedDelay(update,1,1,TimeUnit.SECONDS);
            executor.scheduleWithFixedDelay(server::allocate,100,100,TimeUnit.MILLISECONDS);
            System.out.println("SYNTHETIC_READY "+server.port());System.out.flush();
            Thread.sleep(180000);
        }finally{executor.shutdownNow();}
    }
}
