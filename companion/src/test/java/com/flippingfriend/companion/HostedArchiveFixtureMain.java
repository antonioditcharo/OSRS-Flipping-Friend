package com.flippingfriend.companion;

import com.google.gson.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicLong;

/** Synthetic format interoperability only, never a promotion cohort or an account read. */
public final class HostedArchiveFixtureMain {
    public static void main(String[] args)throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("New local-data synthetic-public-archive directory required");
        Path root=Path.of(args[0]).toAbsolutePath().normalize();
        if(!root.toString().contains("local-data") || !root.getFileName().toString().contains("synthetic-public-archive") || Files.exists(root))throw new IllegalArgumentException("New private synthetic output required");
        var config=new HostedPublicArchive.Config();config.enabled=true;config.seedItems=1;config.durationSeconds=3600;
        var clock=new AtomicLong(1_800_000_000_000000000L);
        try(var archive=new HostedPublicArchive(root,config,()->HostedPublicArchiveTest.state(clock.get()/1000000000L),(item,step)-> {
            int seconds=step.equals("5m")?300:3600;long start=clock.get()/1000000000L/seconds*seconds;var bars=new JsonArray();
            for(int i=60;i>0;i--)bars.add(HostedPublicArchiveTest.bar(start-i*seconds));return bars;
        },clock::incrementAndGet,()->1024L*1024*1024)) {
            for(int i=0;i<35;i++){archive.tick();clock.addAndGet(60_000000000L);}
        }
        Files.writeString(root.resolve("synthetic-fixture-only.txt"),"SYNTHETIC FORMAT TEST; NOT REAL PUBLIC OBSERVATIONS OR QUALIFICATION DATA\n");
        System.out.println("Synthetic public archive fixture exported; not qualification evidence.");
    }
}
