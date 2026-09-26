package com.flippingfriend;
import com.flippingfriend.companion.OfferEventOutbox;
import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.data.PluginStorage;
import com.flippingfriend.data.TestStorage;
import java.nio.file.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class Phase2LostAcknowledgementRecoveryTest
{
 @Test public void lostAcknowledgementLeavesExactEventForDuplicateSafeRedelivery() throws Exception
 {
  Path root=Files.createTempDirectory("lost-ack"); PluginStorage storage=TestStorage.rootedAt(root,"player"); OfferEventOutbox first=new OfferEventOutbox(storage);
  OfferEvent committed=first.enqueue((eventId,sessionId,sequence)->OfferEvent.builder("trace",100,"BUYING").eventIdentity(eventId,sessionId,"offer").slot(1).item(561,"Nature rune").buying(true).price(100).quantities(10,0).spent(0).sequence(sequence).build());
  assertEquals(1,first.pending().size());
  OfferEventOutbox restarted=new OfferEventOutbox(TestStorage.rootedAt(root,"player")); OfferEvent redelivered=restarted.pending().get(0);
  assertEquals(committed.getEventId(),redelivered.getEventId());assertEquals(committed.getSessionId(),redelivered.getSessionId());assertEquals(committed.getSequence(),redelivered.getSequence());
  assertTrue(restarted.acknowledge(redelivered.getEventId(),redelivered.getSessionId(),redelivered.getSequence()));assertTrue(restarted.pending().isEmpty());assertFalse(restarted.acknowledge(redelivered.getEventId(),redelivered.getSessionId(),redelivered.getSequence()));
 }
}
