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
        }catch(Exception failed){failures.increment();throw failed;}
        finally{nanos.add(System.nanoTime()-start);}
    }
}
