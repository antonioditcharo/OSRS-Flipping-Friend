package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Optional asynchronous hints. Calling the planner never waits for hosting. */
final class PilotGateway implements AutoCloseable {
    static final class Config {
        boolean enabled,shadow=true,recommendations,coordination,automaticRecovery=true;
        String endpoint="http://127.0.0.1:38888",token,installationId;
        long generation=1;
    }
    private static final class State {
        volatile PilotProtocol.AllocationHint hint;
        volatile String context;
        volatile boolean inFlight,attempted;
        volatile long frameOrder;
        volatile long nextAttempt;
        volatile String status="LOCAL_FALLBACK";
        volatile long submitted,received,failed,matched,agreed,different,lastResponseMillis;
    }
    private final Config config;private final Gson gson=new Gson();
    private final ConcurrentMap<String,State> states=new ConcurrentHashMap<>();
    private final ScheduledThreadPoolExecutor worker=new ScheduledThreadPoolExecutor(2,r->{var t=new Thread(r,"pilot-hints");t.setDaemon(true);return t;});
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();
    PilotGateway(Config config) {
        this.config=config;
        URI uri=URI.create(config.endpoint);
        // HTTP is permitted only on the loopback endpoint carried by the SSH tunnel.
        if(!"http".equals(uri.getScheme()) || !Set.of("127.0.0.1","localhost","[::1]").contains(uri.getHost())
                || uri.getUserInfo()!=null || uri.getQuery()!=null || !uri.getPath().isEmpty())throw new IllegalArgumentException("private tunnel endpoint required");
        if(config.enabled && (config.token==null || config.token.length()<32 || config.installationId==null || config.generation<1))
            throw new IllegalArgumentException("configured pilot identity required");
        worker.setRemoveOnCancelPolicy(true);
    }
    static PilotGateway load(Path file) {
        try {
            if(!Files.isRegularFile(file))return null;
            var gson=new Gson();Config config=gson.fromJson(Files.readString(file),Config.class);
            config.generation=Math.addExact(config.generation,1);
            Path temp=Files.createTempFile(file.getParent(),"pilot-generation-",".tmp");
            try {
                byte[] bytes=gson.toJson(config).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                try(var channel=java.nio.channels.FileChannel.open(temp,StandardOpenOption.WRITE)) {
                    var buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
                }
                Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            }
            finally{Files.deleteIfExists(temp);}
            return new PilotGateway(config);
        }catch(Exception failure){System.err.println("Hosted configuration refused; retaining local guidance: "+failure.getClass().getSimpleName());return null;}
    }
    boolean automaticRecovery(){return config.automaticRecovery;}
    List<PortfolioCandidate> choose(AccountSnapshot account,List<PortfolioCandidate> candidates,
            Collection<OfferEvent> offers,long marketAt,long now,Runnable refresh,
            PortfolioConstraints limits,PortfolioPlan baseline) {
        if(!config.enabled || account.getAccountId()==null || account.getOfferReadSet().size()!=8 || candidates.isEmpty())return candidates;
        State state=states.get(account.getAccountId());
        if(state==null){if(states.size()>=4096)return candidates;state=states.computeIfAbsent(account.getAccountId(),key->new State());}
        var input=new PilotProtocol.AllocationRequest();input.accountId=account.getAccountId();input.installationId=config.installationId;
        input.generation=config.generation;input.observedAt=account.getObservedAt();input.marketAt=marketAt;
        input.preferredProfit=account.getMinProfitPerFlip();input.coordinate=config.coordination;
        input.context=PilotHashes.sha(gson.toJson(Arrays.asList(input.accountId,account.getOfferReadSet(),account.getObservedAt(),
            account.getSpendableCoins(),account.getFreeSlots(),account.getRiskAppetite(),account.getMinProfitPerFlip(),
            account.getCheckIntervalMinutes(),account.getTargetHoldMinutes(),account.getBlockedItems(),account.getSkippedItems(),
            account.isMembers(),account.isSellOnly(),account.getBuyLimitUsed(),limits,
            ExecutionForecast.MODEL_VERSION,marketAt)));
        // Never reserve or return a candidate that cannot pass this account's local authority.
        var optimizer=new PortfolioOptimizer();input.candidates=new ArrayList<>();
        for(var candidate:candidates)if(!optimizer.optimizePreferred(List.of(candidate),limits,"pilot-validation",now).getAllocations().isEmpty())
            input.candidates.add(candidate);
        input.candidates.sort(Comparator.comparingDouble(PortfolioCandidate::expectedGpPerSlotHour).reversed());
        if(input.candidates.size()>512)input.candidates=new ArrayList<>(input.candidates.subList(0,512));
        var demand=new TreeMap<Integer,PilotProtocol.Demand>();
        for(var position:account.getPositions())if(position.isCostKnown() && position.getQuantity()>0) {
            var d=demand.computeIfAbsent(position.getItemId(),i->new PilotProtocol.Demand(i,0,0,false));
            d.selling=Math.addExact(d.selling,position.getQuantity());
        }
        var listed=new HashMap<Integer,Long>();var futureBuys=new HashMap<Integer,Long>();
        for(var offer:offers) {
            long remaining=Math.max(0,offer.getTotalQuantity()-offer.getFilledQuantity());
            var d=demand.computeIfAbsent(offer.getItemId(),i->new PilotProtocol.Demand(i,0,0,false));
            if("BUYING".equals(offer.getEventType())){d.buying=Math.addExact(d.buying,remaining);futureBuys.merge(offer.getItemId(),remaining,Math::addExact);}
            if("SELLING".equals(offer.getEventType()))listed.merge(offer.getItemId(),remaining,Math::addExact);
        }
        for(var d:demand.values())d.selling=Math.addExact(Math.max(d.selling,listed.getOrDefault(d.itemId,0L)),futureBuys.getOrDefault(d.itemId,0L));
        input.demand=new ArrayList<>(demand.values());
        long clock=System.nanoTime();
        synchronized(state) {
            if(!Objects.equals(state.context,input.context)){state.context=input.context;state.frameOrder=Math.addExact(state.frameOrder,1);}
            input.frameOrder=state.frameOrder;
            if(!state.inFlight && (!state.attempted || clock-state.nextAttempt>=0)) {
                state.attempted=true;state.inFlight=true;state.submitted++;State selected=state;
                worker.execute(()->fetch(input,selected,refresh,0));
            }
        }
        var hint=state.hint;
        if(hint==null || !hint.matches(input,now))return candidates;
        PortfolioCandidate selected=null;
        for(var candidate:input.candidates)if(candidate.getItemId()==hint.itemId && candidate.getTargetBuyPrice()==hint.buyPrice
                && candidate.getTargetSellPrice()==hint.sellPrice && candidate.getQuantity()==hint.quantity){selected=candidate;break;}
        synchronized(state) {
            state.matched++;
            var first=baseline.getAllocations().isEmpty()?null:baseline.getAllocations().get(0).getCandidate();
            if(first!=null && selected!=null && first.getItemId()==selected.getItemId() && first.getQuantity()==selected.getQuantity())state.agreed++;
            else state.different++;
        }
        if(config.shadow || (!config.recommendations && !config.coordination))return candidates;
        // Intersect with freshly generated local economics and immutable local forecasts.
        if(selected!=null)return List.of(selected);
        if(config.coordination && hint.itemId==0 && "COORDINATED".equals(hint.mode)) {
            var uncrowded=new ArrayList<PortfolioCandidate>();
            for(var c:candidates)if(!hint.crowdedItems.contains(c.getItemId()))uncrowded.add(c);
            return uncrowded;
        }
        return candidates;
    }
    long expiresAt(String account,long localExpiry,long now) {
        if(account==null || !config.enabled || config.shadow || (!config.recommendations && !config.coordination))return localExpiry;
        State state=states.get(account);var hint=state==null?null:state.hint;
        return !config.shadow && hint!=null && hint.expiresAt>now?Math.min(localExpiry,hint.expiresAt):localExpiry;
    }
    Map<String,Object> status(String account) {
        State state=states.get(account);if(state==null)return Map.of("mode",config.shadow?"SHADOW":"LOCAL_FALLBACK","submitted",0);
        synchronized(state){return Map.of("mode",config.shadow?"SHADOW":state.status,"submitted",state.submitted,
            "received",state.received,"failed",state.failed,"matched",state.matched,"agreed",state.agreed,
            "different",state.different,"lastResponseMillis",state.lastResponseMillis);}
    }
    private void fetch(PilotProtocol.AllocationRequest input,State state,Runnable refresh,int attempt) {
        long started=System.nanoTime();
        try {
            var response=post("allocations",gson.toJson(input));
            if(response.statusCode()==202 && attempt<3){worker.schedule(()->fetch(input,state,refresh,attempt+1),150,TimeUnit.MILLISECONDS);return;}
            if(response.statusCode()!=200)throw new IllegalStateException("pilot unavailable");
            var hint=gson.fromJson(response.body(),PilotProtocol.AllocationHint.class);
            if(hint==null || !hint.matches(input,System.currentTimeMillis()/1000))throw new IllegalStateException("pilot hint identity refused");
            synchronized(state) {
                if(Objects.equals(state.context,input.context)){state.hint=hint;state.status="HOSTED_COMPATIBLE";state.received++;}
                state.lastResponseMillis=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
                state.nextAttempt=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);state.inFlight=false;
            }
            refresh.run();
        }catch(Exception unavailable) {
            synchronized(state){state.hint=null;state.status="LOCAL_FALLBACK";state.failed++;
                state.nextAttempt=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);state.inFlight=false;}
            refresh.run();
        }
    }
    HttpResponse<String> post(String route,String body)throws Exception {
        var response=http.send(HttpRequest.newBuilder(URI.create(config.endpoint+"/service/v1/"+route))
            .timeout(Duration.ofMillis(750)).header("Content-Type","application/json")
            .header("X-Flipping-Friend-Pilot-Token",config.token).POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        if(!response.headers().firstValue("X-Flipping-Friend-Pilot-Protocol").orElse("").equals(PilotProtocol.VERSION)
                || response.body().length()>2097152)throw new IllegalStateException("pilot protocol refused");
        return response;
    }
    Config config(){return config;}
    @Override public void close(){worker.shutdownNow();}
}
