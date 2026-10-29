package com.flippingfriend.ui;

import java.util.concurrent.*;

/** Attributes existing serial work without changing financial event ordering or retry behavior. */
public final class GuidanceWorker extends ThreadPoolExecutor {
    public enum Task { MAIN, SELECTED_SALE, OFFER_REPLAY, OTHER }
    private volatile Active active;
    private static final class Active {
        final Task task;final long started=System.nanoTime();
        Active(Task task){this.task=task;}
    }
    private static final class Work implements Runnable {
        final Task task;final Runnable body;
        Work(Task task,Runnable body){this.task=task;this.body=body;}
        @Override public void run(){body.run();}
    }
    public GuidanceWorker(){super(1,1,0,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<>(),body->{
        Thread thread=new Thread(body,"flipping-friend-engine");thread.setDaemon(true);return thread;
    });}
    public static Executor named(ExecutorService worker,Task task){return body->worker.execute(new Work(task,body));}
    public static void describe(ExecutorService worker,GuidanceTiming timing){
        if(!(worker instanceof GuidanceWorker)){timing.workerState("UNINSTRUMENTED",0,0);return;}
        var owned=(GuidanceWorker)worker;var running=owned.active;
        timing.workerState(running==null?"IDLE":running.task.name(),running==null?0:Math.max(0,(System.nanoTime()-running.started)/1000),owned.getQueue().size());
    }
    @Override protected void beforeExecute(Thread thread,Runnable work){
        super.beforeExecute(thread,work);active=new Active(work instanceof Work?((Work)work).task:Task.OTHER);
    }
    @Override protected void afterExecute(Runnable work,Throwable failure){
        active=null;super.afterExecute(work,failure);
    }
}
