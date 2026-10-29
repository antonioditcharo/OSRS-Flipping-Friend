package com.flippingfriend.companion;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;

/** One bounded, reusable transport per persistent test JVM. Never used by production. */
final class LoopbackFixtureClient {
    static {
        // Also set as worker launch flags, before any JDK HttpClient initialization.
        System.setProperty("jdk.httpclient.redirects.retrylimit","1");
        System.setProperty("jdk.httpclient.disableRetryConnect","true");
        System.setProperty("jdk.httpclient.connectionPoolSize","8");
    }
    private static final ExecutorService EXECUTOR=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(64),r->{
        Thread t=new Thread(r,"fixture-http");t.setDaemon(true);return t;
    },new ThreadPoolExecutor.AbortPolicy());
    private static final HttpClient CLIENT=HttpClient.newBuilder().executor(EXECUTOR)
        .connectTimeout(Duration.ofSeconds(2)).version(HttpClient.Version.HTTP_1_1)
        .followRedirects(HttpClient.Redirect.NEVER).build();
    static final LongAdder requests=new LongAdder(),failures=new LongAdder(),nanos=new LongAdder();
    static HttpResponse<String> request(String url,Map<String,String> headers,String body)throws Exception {
        URI uri=URI.create(url);
        if(!"http".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost()))
            throw new IllegalArgumentException("Fixture transport is loopback only");
        long start=System.nanoTime();requests.increment();
        try {
            var b=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5));headers.forEach(b::header);
            if(body==null)b.GET();else b.POST(HttpRequest.BodyPublishers.ofString(body));
            // ofString consumes/closes the entire response, including non-2xx bodies and exceptions.
            return CLIENT.send(b.build(),HttpResponse.BodyHandlers.ofString());
        }catch(Exception failed){
            failures.increment();
            // Keep the original exception, deadline and failure count. Diagnostics explain a
            // slow runner/blocked handler without retrying a request or hiding its latency.
            System.err.println("Fixture request failure: "+failed.getClass().getSimpleName()+" "+uri.getPath()
                +"; elapsedMs="+(System.nanoTime()-start)/1_000_000+"; requests="+requests.sum()+"; failures="+failures.sum()
                +"; heapUsed="+(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())
                +"; activeFixtureThreads="+((ThreadPoolExecutor)EXECUTOR).getActiveCount()
                +"; queuedFixtureTasks="+((ThreadPoolExecutor)EXECUTOR).getQueue().size());
            Thread.getAllStackTraces().entrySet().stream()
                .filter(e->e.getKey().getName().matches(".*(companion|fixture-http|HTTP-Dispatcher|pool-).*"))
                .sorted(java.util.Comparator.comparing(e->e.getKey().getName())).limit(16).forEach(e->{
                    System.err.println("Fixture thread "+e.getKey().getName()+" "+e.getKey().getState());
                    for(int i=0;i<Math.min(12,e.getValue().length);i++)System.err.println("  at "+e.getValue()[i]);
                });
            throw failed;
        }
        finally{nanos.add(System.nanoTime()-start);}
    }
}
