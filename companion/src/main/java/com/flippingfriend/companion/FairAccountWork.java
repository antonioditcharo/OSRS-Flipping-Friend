package com.flippingfriend.companion;

import java.util.*;
import java.util.concurrent.*;

/** FIFO within each account; round-robin accounts, with one running task per account. */
final class FairAccountWork implements AutoCloseable {
    private final ExecutorService workers;
    private final Map<String,ArrayDeque<Runnable>> pending=new HashMap<>();
    private final Set<String> running=new HashSet<>();private final ArrayDeque<String> ready=new ArrayDeque<>();
    private int queued,active;private boolean closed;
    FairAccountWork(ExecutorService workers){this.workers=workers;}
    synchronized int queued(){return queued;}
    synchronized int pending(String account){return pending.getOrDefault(account,new ArrayDeque<>()).size();}
    synchronized void execute(String account,Runnable job) {
        if(closed || queued>=64)throw new RejectedExecutionException("account queue is full");
        var queue=pending.computeIfAbsent(account,key->new ArrayDeque<>());
        if(queue.size()>=16)throw new RejectedExecutionException("account queue is full");
        if(queue.isEmpty() && !running.contains(account))ready.add(account);
        queue.add(job);queued++;pump();
    }
    private void pump() {
        while(!closed && active<2 && !ready.isEmpty()) {
            String account=ready.remove();var queue=pending.get(account);Runnable job=queue.remove();queued--;
            running.add(account);active++;
            try{workers.execute(()->{try{job.run();}finally{complete(account);}});}
            catch(RejectedExecutionException error){running.remove(account);active--;queue.addFirst(job);queued++;ready.addFirst(account);throw error;}
        }
    }
    private synchronized void complete(String account) {
        active--;running.remove(account);if(closed)return;var queue=pending.get(account);
        if(queue.isEmpty())pending.remove(account);else ready.add(account);pump();
    }
    @Override public synchronized void close(){closed=true;pending.clear();ready.clear();queued=0;}
}
