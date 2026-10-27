package com.flippingfriend.companion;

import java.util.*;

/** Bounded, in-memory diagnostic prefixes. Never ACK, account evidence or guidance authority. */
final class CompanionTransportTotals {
    static final long REPORT_INTERVAL_NANOS=60_000_000_000L;
    private static final long[] BOUNDS_MS={1,5,10,25,50,100,250,500,750,1000,2000,5000,10000,30000,60000};
    enum Group { HEALTH,MARKET,ACCOUNT,OFFERS,LEDGER,PLAN,POLICY,REPORTS,OTHER }
    private final String measurementId=UUID.randomUUID().toString();
    private long nextWindow=1;
    private final Window global=new Window(null,0,0,0);
    private Window active;

    synchronized Ticket begin(String account,long generation,String path,long started) {
        Window window;
        if(account==null)window=global;
        else if(active!=null && generation==active.generation && account.equals(active.account))window=active;
        else {
            window=new Window(account,generation,nextWindow++,started);
            // A late old request cannot replace the current account's counters. Its own ticket
            // retains the requested scope even when it finishes after a switch or restart.
            if(active==null || generation>active.generation)active=window;
        }
        window.noteStart(started);return new Ticket(window,group(path));
    }
    Report complete(Ticket ticket,long elapsed,int status,boolean bodyReadAndScoped,long now) {
        return ticket.window.complete(ticket.group,elapsed,status,bodyReadAndScoped,now);
    }
    synchronized Report snapshot(String account,long generation,long now) {
        if(account==null)return global.snapshot(now);
        return active!=null && generation==active.generation && account.equals(active.account)?active.snapshot(now):null;
    }
    static Group group(String path) {
        if("health".equals(path))return Group.HEALTH;
        if(path==null)return Group.OTHER;
        if(path.startsWith("market/"))return Group.MARKET;
        if(path.startsWith("events/ge-offer"))return Group.OFFERS;
        if(path.startsWith("events/account"))return Group.ACCOUNT;
        if(path.startsWith("ledger/"))return Group.LEDGER;
        if(path.startsWith("portfolio/"))return Group.PLAN;
        if(path.startsWith("policy/"))return Group.POLICY;
        if(path.startsWith("performance/") || path.startsWith("trade-review") || path.startsWith("trade-recovery/"))return Group.REPORTS;
        return Group.OTHER;
    }
    static final class Ticket {
        private final Window window;private final Group group;
        private Ticket(Window window,Group group){this.window=window;this.group=group;}
    }
    static final class Row {
        final String group;
        final long attempts,bodyReadAndScoped2xx,failed2xx,httpFailures,noResponse,invalidTiming;
        final long[] latencyHistogram;
        final Long p95UpperBoundMillis;
        final boolean p95AboveLargestBound;
        Row(Group group,long[] counts,long[] histogram) {
            this.group=group.name();attempts=counts[0];bodyReadAndScoped2xx=counts[1];failed2xx=counts[2];
            httpFailures=counts[3];noResponse=counts[4];invalidTiming=counts[5];latencyHistogram=histogram.clone();
            long valid=0;for(long count:histogram)valid+=count;
            long rank=valid/100*95+(valid%100*95+99)/100,seen=0;int bucket=-1;
            if(valid>0)for(int i=0;i<histogram.length;i++){seen+=histogram[i];if(seen>=rank){bucket=i;break;}}
            p95AboveLargestBound=bucket==BOUNDS_MS.length;
            p95UpperBoundMillis=bucket<0 || p95AboveLargestBound?null:BOUNDS_MS[bucket];
        }
    }
    static final class Report {
        final int schemaVersion=1;
        final String measurementId,account;
        final long window,generation,elapsedWindowMillis;
        final String semantics="Cumulative completed transport prefix; not durable ACK, typed-body validity or delivered guidance";
        final long[] histogramUpperBoundsMillis=BOUNDS_MS.clone();
        final boolean lastHistogramBucketAboveLargestBound=true;
        final List<Row> rows;
        Report(String id,String account,long window,long generation,long elapsed,List<Row> rows) {
            measurementId=id;this.account=account==null?"GLOBAL":account;this.window=window;
            this.generation=generation;elapsedWindowMillis=elapsed;this.rows=Collections.unmodifiableList(rows);
        }
    }
    private final class Window {
        final String account;final long generation,id;
        private long started,lastReport;private boolean initialized,reported;
        private final long[][] counts=new long[Group.values().length][6];
        private final long[][] histogram=new long[Group.values().length][BOUNDS_MS.length+1];
        Window(String account,long generation,long id,long started){this.account=account;this.generation=generation;this.id=id;this.started=started;}
        synchronized void noteStart(long at){if(!initialized || at-started<0)started=at;initialized=true;}
        synchronized Report complete(Group group,long elapsed,int status,boolean completed,long now) {
            long[] row=counts[group.ordinal()];row[0]++;
            if(status>=200 && status<300)row[completed?1:2]++;
            else row[status<0?4:3]++;
            if(elapsed<0)row[5]++;
            else {
                int bucket=0;while(bucket<BOUNDS_MS.length && elapsed>BOUNDS_MS[bucket]*1_000_000L)bucket++;
                histogram[group.ordinal()][bucket]++;
            }
            if(reported && now-lastReport<REPORT_INTERVAL_NANOS)return null;
            reported=true;lastReport=now;return snapshot(now);
        }
        synchronized Report snapshot(long now) {
            List<Row> result=new ArrayList<>();
            for(Group group:Group.values())if(counts[group.ordinal()][0]>0)
                result.add(new Row(group,counts[group.ordinal()],histogram[group.ordinal()]));
            return new Report(measurementId,account,id,generation,initialized?Math.max(0,now-started)/1_000_000:0,result);
        }
    }
}
