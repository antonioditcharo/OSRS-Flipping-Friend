package com.flippingfriend;

import com.flippingfriend.companion.*;
import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.flippingfriend.session.TrackedOffer;
import com.google.gson.Gson;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.client.config.ConfigManager;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real callback-to-file boundary; the worker is deliberately unavailable and no HTTP is sent. */
public class CapturedOfferDispatchTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    class Fixture {
        final PluginStorage storage=TestStorage.rootedAt(folder.newFolder().toPath(),"dispatch");
        final OfferEventOutbox outbox=new OfferEventOutbox(storage);
        final CompanionClient companion=new CompanionClient(storage,new Gson(),new SuggestionLedger(),outbox);
        final FlippingFriendPlugin plugin=new FlippingFriendPlugin();
        final List<Runnable> queued=new ArrayList<>();
        final ExecutorService worker=mock(ExecutorService.class);
        Fixture()throws Exception {
            var profiles=mock(ConfigManager.class);when(profiles.getRSProfileKey()).thenReturn("dispatch");
            doAnswer(i->{queued.add(i.getArgument(0));return null;}).when(worker).execute(any());
            inject(plugin,"companion",companion);inject(plugin,"configManager",profiles);inject(plugin,"worker",worker);
        }
        void publish(TrackedOffer offer)throws Exception{invoke("publishOfferToCompanion",TrackedOffer.class,offer);}
        void clear(int slot)throws Exception{invoke("publishOfferClearedToCompanion",int.class,slot);}
        void invoke(String name,Class<?> type,Object argument)throws Exception {
            var m=FlippingFriendPlugin.class.getDeclaredMethod(name,type);m.setAccessible(true);m.invoke(plugin,argument);
        }
    }
    static void inject(Object o,String name,Object value)throws Exception {var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    TrackedOffer offer(String state,int filled) {
        var o=new TrackedOffer(0,561,true,100,3,Instant.now().getEpochSecond());
        o.setItemName("Nature rune");o.setState(state);o.setQuantityFilled(filled);o.setSpent(100L*filled);return o;
    }
    @Test public void capturedFillAndClearSurviveLogoutBeforeTheWorkerRuns()throws Exception {
        var f=new Fixture();f.publish(offer("BUYING",0));f.publish(offer("BOUGHT",3));f.clear(0);
        var field=FlippingFriendPlugin.class.getDeclaredField("offerObservationGeneration");field.setAccessible(true);
        ((AtomicLong)field.get(f.plugin)).incrementAndGet();f.companion.pauseOfferReplay();
        var pending=new OfferEventOutbox(f.storage).pending();
        assertEquals("An authenticated capture must precede dispatch",3,pending.size());
        assertEquals(List.of("BUYING","BOUGHT","EMPTY"),List.of(pending.get(0).getEventType(),pending.get(1).getEventType(),pending.get(2).getEventType()));
        assertEquals(300,pending.get(1).getSpent());assertEquals(3,pending.get(2).getSequence());
        for(var task:f.queued)task.run();assertEquals(pending.get(1).getEventId(),new OfferEventOutbox(f.storage).pending().get(1).getEventId());
    }
    @Test public void rejectedWorkerCannotDiscardAnAlreadyCapturedExecution()throws Exception {
        var f=new Fixture();doThrow(new RejectedExecutionException("stopping fixture")).when(f.worker).execute(any());
        f.publish(offer("BOUGHT",3));assertEquals(1,new OfferEventOutbox(f.storage).pending().size());
    }
    @Test public void aStoppedWorkerCannotDiscardTheLastFillOrClear()throws Exception {
        var f=new Fixture();when(f.worker.isShutdown()).thenReturn(true);
        f.publish(offer("BOUGHT",3));f.clear(0);assertEquals(2,new OfferEventOutbox(f.storage).pending().size());
    }
    @Test public void aMissingWorkerStillPreservesCapturedEvidenceForTheNextStart()throws Exception {
        var f=new Fixture();inject(f.plugin,"worker",null);f.publish(offer("BOUGHT",3));
        assertEquals(1,new OfferEventOutbox(f.storage).pending().size());
    }
}
