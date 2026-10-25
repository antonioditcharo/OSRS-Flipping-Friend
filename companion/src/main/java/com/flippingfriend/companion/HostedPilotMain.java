package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Private hosted process. Its listener is accessible only through the encrypted SSH tunnel. */
public final class HostedPilotMain {
    public static final class Device {
        public String userId,installationId,tokenSha256;
        public Set<String> accounts=new HashSet<>();
    }
    public static final class Config {
        public int port=38888;
        public String dataDirectory;
        public List<Device> devices=new ArrayList<>();
    }
    private HostedPilotMain() { }
    public static void main(String[] args)throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Supply the private hosted configuration path");
        Gson gson=new Gson();Config config=gson.fromJson(Files.readString(Path.of(args[0])),Config.class);
        Path data=Path.of(config.dataDirectory);Files.createDirectories(data);
        var market=new MarketIngestionService(gson);
        var evidence=new PilotEvidenceStore(data.resolve("pilot-evidence.db"));
        var server=new Server(config,market,evidence,gson);server.start();
        var scheduler=Executors.newScheduledThreadPool(2,r->{var t=new Thread(r,"pilot-background");t.setDaemon(true);return t;});
        scheduler.scheduleWithFixedDelay(()->{try{market.refresh();}catch(Exception error){System.err.println("Market refresh failed: "+error.getClass().getSimpleName());}},0,60,TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(server::allocate,100,100,TimeUnit.MILLISECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(()->{scheduler.shutdownNow();server.close();try{evidence.close();}catch(Exception ignored){}}));
        System.out.println("Hosted pilot ready: "+PilotProtocol.VERSION+"; loopback only");Thread.currentThread().join();
    }
    static final class Server implements AutoCloseable {
        private final Config config;private final Gson gson;private final HttpServer http;
        private final Map<String,Device> devicesByHash=new HashMap<>();
        private final MarketIngestionService market;private final PilotEvidenceStore evidence;
        private final PilotCoordinator coordinator=new PilotCoordinator();
        private final ConcurrentMap<String,PilotProtocol.AllocationHint> hints=new ConcurrentHashMap<>();
        private final ThreadPoolExecutor foreground=new ThreadPoolExecutor(4,4,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2048));
        private final ThreadPoolExecutor replication=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8));
        Server(Config config,MarketIngestionService market,PilotEvidenceStore evidence,Gson gson)throws Exception {
            // Pinned JDK 17 HTTP server limits bound sockets and slow requests during the pilot.
            System.setProperty("jdk.httpserver.maxConnections","2048");
            System.setProperty("sun.net.httpserver.maxIdleConnections","1200");
            System.setProperty("sun.net.httpserver.maxReqTime","5");
            System.setProperty("sun.net.httpserver.maxRspTime","5");
            System.setProperty("sun.net.httpserver.maxReqHeaders","40");
            System.setProperty("sun.net.httpserver.maxReqHeaderSize","16384");
            System.setProperty("sun.net.httpserver.nodelay","true");
            this.config=config;this.market=market;this.evidence=evidence;this.gson=gson;
            if(config.devices==null || config.devices.isEmpty() || config.devices.size()>4096)throw new IllegalArgumentException("explicit device authorization required");
            var seen=new HashSet<String>();var accountOwners=new HashMap<String,String>();
            for(var d:config.devices) {
                if(d.userId==null || d.installationId==null || d.tokenSha256==null || !d.tokenSha256.matches("[0-9a-f]{64}")
                        || !seen.add(d.tokenSha256) || d.accounts==null || d.accounts.isEmpty())throw new IllegalArgumentException("invalid device authorization");
                devicesByHash.put(d.tokenSha256,d);
                for(String account:d.accounts){AccountIdentity.require(account);String owner=accountOwners.putIfAbsent(account,d.userId);if(owner!=null && !owner.equals(d.userId))throw new IllegalArgumentException("account owner conflict");}
            }
            evidence.restoreDemand(coordinator);
            http=HttpServer.create(new InetSocketAddress("127.0.0.1",config.port),128);
            http.createContext("/service/v1/",this::dispatch);http.setExecutor(foreground);
        }
        void start(){http.start();}int port(){return http.getAddress().getPort();}
        private void dispatch(HttpExchange x)throws java.io.IOException {
            try {
                String token=x.getRequestHeaders().getFirst("X-Flipping-Friend-Pilot-Token");
                String digest=token==null || token.length()>256?"":PilotHashes.sha(token);Device device=devicesByHash.get(digest);
                if(device==null){respond(x,401,Map.of("error","unauthorized"));return;}
                String route=x.getRequestURI().getPath();
                if("/service/v1/health".equals(route) && "GET".equals(x.getRequestMethod())) {
                    respond(x,200,Map.of("protocol",PilotProtocol.VERSION,"marketAt",market.state().observedAt));return;
                }
                if("/service/v1/market".equals(route) && "GET".equals(x.getRequestMethod())){respond(x,200,market.state());return;}
                if(!"POST".equals(x.getRequestMethod())){respond(x,405,Map.of("error","POST required"));return;}
                byte[] bytes=x.getRequestBody().readNBytes(2097153);if(bytes.length>2097152){respond(x,413,Map.of("error","bounded body required"));return;}
                String body=new String(bytes,StandardCharsets.UTF_8);
                if("/service/v1/allocations".equals(route)) {
                    var request=gson.fromJson(body,PilotProtocol.AllocationRequest.class);authorize(device,request.accountId,request.installationId);
                    long now=System.currentTimeMillis()/1000;coordinator.enqueue(device.userId,request,now);
                    var hint=hints.get(device.userId+"/"+request.accountId);
                    if(request.candidates.isEmpty())hint=coordinator.demandReceipt(device.userId,request,now);
                    if(hint==null || !hint.matches(request,now)){respond(x,202,Map.of("protocol",PilotProtocol.VERSION,"status","LOCAL_FALLBACK"));return;}
                    respond(x,200,hint);return;
                }
                if("/service/v1/evidence".equals(route)) {
                    var batch=gson.fromJson(body,PilotProtocol.EvidenceBatch.class);authorize(device,batch.accountId,batch.installationId);
                    Device selected=device;
                    try{replication.execute(()->{try{respond(x,200,evidence.accept(selected.userId,batch));}catch(Exception invalid){try{respond(x,409,Map.of("error","evidence prefix or scope refused"));}catch(Exception ignored){x.close();}}});}
                    catch(RejectedExecutionException busy){respond(x,503,Map.of("error","retry original evidence batch"));}
                    return;
                }
                respond(x,404,Map.of("error","unknown service endpoint"));
            }catch(IllegalArgumentException|NullPointerException invalid){respond(x,400,Map.of("error","invalid scope or request"));}
            catch(Exception failure){respond(x,503,Map.of("error","local guidance remains available"));}
        }
        private static void authorize(Device device,String account,String installation) {
            if(!device.installationId.equals(installation) || !device.accounts.contains(account))throw new IllegalArgumentException("unauthorized account");
        }
        void allocate() {
            try {
                coordinator.flush(evidence::saveDemands);
                var state=market.state();long now=System.currentTimeMillis()/1000;
                if(state.observedAt<=0 || now-state.observedAt>180)return;
                hints.putAll(coordinator.round((item,buy)->{
                    var row=state.hourly.getAsJsonObject(String.valueOf(item));
                    String side=buy?"lowPriceVolume":"highPriceVolume";
                    if(row==null || !row.has(side))return 0;
                    return Math.max(0,row.get(side).getAsLong());
                },now));
            }catch(Exception failure){System.err.println("Allocation round failed: "+failure.getClass().getSimpleName());}
        }
        private void respond(HttpExchange x,int status,Object body)throws java.io.IOException {
            byte[] bytes=gson.toJson(body).getBytes(StandardCharsets.UTF_8);
            x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
            x.getResponseHeaders().set("X-Flipping-Friend-Pilot-Protocol",PilotProtocol.VERSION);
            x.sendResponseHeaders(status,bytes.length);try(var out=x.getResponseBody()){out.write(bytes);}finally{x.close();}
        }
        @Override public void close(){http.stop(0);foreground.shutdownNow();replication.shutdownNow();}
    }
}
