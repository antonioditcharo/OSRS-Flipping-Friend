package com.flippingfriend.core;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Process-local observation ordering. Restored objects must never acquire a fresh stamp. */
public final class CaptureOrder {
    private static final String PROCESS=UUID.randomUUID().toString();
    private static final AtomicLong NEXT=new AtomicLong();
    private final String process;
    private final long order;
    private CaptureOrder(String process,long order){this.process=process;this.order=order;}
    public static CaptureOrder next(){return new CaptureOrder(PROCESS,NEXT.incrementAndGet());}
    public boolean precedes(CaptureOrder other){return other!=null && process!=null && process.equals(other.process)
        && order>0 && order<other.order;}
    public String getProcess(){return process;}
    public long getOrder(){return order;}
}
