import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import org.junit.runner.*;

/** Persistent isolated JUnit worker; exits on the first failure, without retrying. */
public final class WorkflowSoakWorker {
    public static void main(String[] args)throws Exception {
        long duration=Long.parseLong(args[1]),start=System.nanoTime();
        Class<?>[] classes=new Class<?>[args.length-2];
        for(int i=2;i<args.length;i++)classes[i-2]=Class.forName(args[i]);
        Path log=Path.of(args[0]);Files.createFile(log);int cycle=0;
        do {
            long before=System.nanoTime();Result result=new JUnitCore().run(classes);
            long elapsed=(System.nanoTime()-start)/1_000_000,phase=(System.nanoTime()-before)/1_000_000;
            long[] transport=transport();
            var memory=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            String row="{\"at\":\""+Instant.now()+"\",\"cycle\":"+(++cycle)+",\"elapsedMs\":"+elapsed
                +",\"phaseMs\":"+phase+",\"tests\":"+result.getRunCount()+",\"failures\":"+result.getFailureCount()
                +",\"fixtureRequests\":"+transport[0]+",\"fixtureTransportFailures\":"+transport[1]+",\"fixtureRequestNanos\":"+transport[2]
                +",\"heapUsed\":"+memory.getUsed()+",\"threads\":"+ManagementFactory.getThreadMXBean().getThreadCount()+"}";
            Files.writeString(log,row+"\n",StandardCharsets.UTF_8,StandardOpenOption.APPEND);
            for(var failure:result.getFailures()) {System.err.println(failure);System.err.println(failure.getTrace());}
            if(!result.wasSuccessful() || transport[1]>0)System.exit(1);
            long remaining=duration*1000-(System.nanoTime()-start)/1_000_000;
            if(remaining<=0)break;
            Thread.sleep(Math.min(remaining,Math.max(0,60000-phase)));
        }while((System.nanoTime()-start)/1_000_000<duration*1000);
        System.out.println("COMPLETED_UNINTERRUPTED "+cycle);
    }
    private static long[] transport()throws Exception {
        Class<?> type;
        try{type=Class.forName("com.flippingfriend.companion.LoopbackFixtureClient");}
        catch(ClassNotFoundException unavailable){return new long[]{0,0,0};}
        long[] result=new long[3];String[] names={"requests","failures","nanos"};
        for(int i=0;i<names.length;i++) {
            var field=type.getDeclaredField(names[i]);field.setAccessible(true);
            result[i]=((java.util.concurrent.atomic.LongAdder)field.get(null)).sum();
        }
        return result;
    }
}
