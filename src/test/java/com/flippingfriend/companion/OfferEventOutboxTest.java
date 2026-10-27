package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.data.PluginStorage;
import com.flippingfriend.data.TestStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class OfferEventOutboxTest
{
	@Rule public final TemporaryFolder folder = new TemporaryFolder();
    @Test public void unchangedBytesAvoidRepeatedDecodeWhileSameSizeSameTimeEditsAndOtherWritersInvalidate() throws Exception {
        PluginStorage storage=TestStorage.rootedAt(folder.newFolder("prepared-image").toPath(),"one");
        var outbox=new OfferEventOutbox(storage);var original=outbox.enqueue((e,s,q)->event(e,s,q,561));
        Path file=storage.accountDir().resolve(OfferEventOutbox.FILE_NAME);
        for(int i=0;i<20;i++){assertEquals(original.getEventId(),outbox.pending().get(0).getEventId());outbox.sessionId();}
        assertEquals(1,outbox.decodedImageCount());
        byte[] old=Files.readAllBytes(file);var time=Files.getLastModifiedTime(file);
        String corrupt=new String(old,java.nio.charset.StandardCharsets.UTF_8).replace("\"formatVersion\":1","\"formatVersion\":2");
        assertEquals(old.length,corrupt.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        Files.writeString(file,corrupt);Files.setLastModifiedTime(file,time);
        try{outbox.pending();org.junit.Assert.fail("Exact image changes must revalidate");}catch(IllegalStateException expected){}
        assertEquals(2,outbox.decodedImageCount());assertEquals(corrupt,Files.readString(file));
        Files.write(file,old);assertEquals(1,outbox.pending().size());
        var other=new OfferEventOutbox(storage);var second=other.enqueue((e,s,q)->event(e,s,q,4151));
        assertEquals(2,outbox.pending().size());assertEquals(3,outbox.decodedImageCount());
        assertTrue(outbox.acknowledge(original.getEventId(),original.getSessionId(),original.getSequence()));
        assertEquals(second.getEventId(),outbox.pending().get(0).getEventId());
        assertEquals(1,new OfferEventOutbox(storage).pending().size());
    }

	@Test public void refusesToUseTheDefaultAccountDirectory() throws Exception
	{
		Path root = folder.newFolder("no-account").toPath();
		OfferEventOutbox outbox = new OfferEventOutbox(TestStorage.rootedAt(root, null));
		try
		{
			outbox.pending();
			org.junit.Assert.fail("account-less outbox must fail closed");
		}
		catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("account")); }
		assertFalse(Files.exists(root.resolve("accounts/default").resolve(OfferEventOutbox.FILE_NAME)));
	}

	@Test public void legacyPendingEventsGainScopeWithoutChangingDeliveryIdentity() throws Exception
	{
		PluginStorage storage = TestStorage.rootedAt(folder.newFolder("legacy").toPath(), "one");
		OfferEventOutbox outbox = new OfferEventOutbox(storage);
		OfferEvent original = outbox.enqueue((e, s, q) -> event(e, s, q, 561));
		Path file = storage.accountDir().resolve(OfferEventOutbox.FILE_NAME);
		com.google.gson.JsonObject legacy = storage.gson().fromJson(Files.readString(file), com.google.gson.JsonObject.class);
		legacy.remove("accountId");
		legacy.getAsJsonArray("pending").get(0).getAsJsonObject().remove("accountId");
		Files.writeString(file, storage.gson().toJson(legacy));
		OfferEvent restored = new OfferEventOutbox(storage).pending().get(0);
		assertEquals(storage.accountId(), restored.getAccountId());
		assertEquals(original.getEventId(), restored.getEventId());
		assertEquals(original.getSessionId(), restored.getSessionId());
		assertEquals(original.getSequence(), restored.getSequence());
		assertEquals(storage.accountId(), new OfferEventOutbox(storage).pending().get(0).getAccountId());
	}

	@Test public void foreignScopeFailsClosedWithoutOverwritingTheQueue() throws Exception
	{
		PluginStorage storage = TestStorage.rootedAt(folder.newFolder("foreign").toPath(), "one");
		new OfferEventOutbox(storage).enqueue((e, s, q) -> event(e, s, q, 561));
		Path file = storage.accountDir().resolve(OfferEventOutbox.FILE_NAME);
		com.google.gson.JsonObject foreign = storage.gson().fromJson(Files.readString(file), com.google.gson.JsonObject.class);
		foreign.addProperty("accountId", com.flippingfriend.core.AccountIdentity.fromProfileKey("two"));
		Files.writeString(file, storage.gson().toJson(foreign));
		byte[] before = Files.readAllBytes(file);
		try { new OfferEventOutbox(storage).pending(); org.junit.Assert.fail("foreign queue accepted"); }
		catch (IllegalStateException expected) { }
		org.junit.Assert.assertArrayEquals(before, Files.readAllBytes(file));
	}

	@Test public void profileSwitchDuringEnqueueCannotRedirectTheWrite() throws Exception
	{
		Path root = folder.newFolder("switch").toPath();
		PluginStorage one = TestStorage.rootedAt(root, "one"), two = TestStorage.rootedAt(root, "two");
		new OfferEventOutbox(one).enqueue((e, s, q) -> event(e, s, q, 561));
		new OfferEventOutbox(two).enqueue((e, s, q) -> event(e, s, q, 4151));
		Path firstFile = one.accountDir().resolve(OfferEventOutbox.FILE_NAME);
		Path secondFile = two.accountDir().resolve(OfferEventOutbox.FILE_NAME);
		byte[] firstBefore = Files.readAllBytes(firstFile), secondBefore = Files.readAllBytes(secondFile);
		String firstId = one.accountId(), secondId = two.accountId();
		Path firstDirectory = one.accountDir();
		PluginStorage changing = org.mockito.Mockito.mock(PluginStorage.class);
		org.mockito.Mockito.when(changing.hasAccount()).thenReturn(true);
		org.mockito.Mockito.when(changing.gson()).thenReturn(one.gson());
		org.mockito.Mockito.when(changing.accountId()).thenReturn(firstId);
		org.mockito.Mockito.when(changing.accountDir(firstId)).thenReturn(firstDirectory);
		OfferEventOutbox outbox = new OfferEventOutbox(changing);
		try
		{
			outbox.enqueue((e, s, q) -> {
				org.mockito.Mockito.when(changing.accountId()).thenReturn(secondId);
				return event(e, s, q, 1603);
			});
			org.junit.Assert.fail("profile switch must reject enqueue");
		}
		catch (IllegalStateException expected) { }
		org.junit.Assert.assertArrayEquals(firstBefore, Files.readAllBytes(firstFile));
		org.junit.Assert.assertArrayEquals(secondBefore, Files.readAllBytes(secondFile));
		try { two.accountDir(one.accountId()); org.junit.Assert.fail("foreign directory accepted"); }
		catch (IllegalStateException expected) { }
	}

	@Test public void corruptPendingIdentityAndFutureSchemaPreserveTheQueue() throws Exception
	{
		PluginStorage storage = TestStorage.rootedAt(folder.newFolder("invalid-pending").toPath(), "one");
		new OfferEventOutbox(storage).enqueue((e, s, q) -> event(e, s, q, 561));
		Path file = storage.accountDir().resolve(OfferEventOutbox.FILE_NAME);
		String valid = Files.readString(file);
		for (String failure : new String[] {"schema", "sequence", "duplicate"})
		{
			com.google.gson.JsonObject invalid = storage.gson().fromJson(valid, com.google.gson.JsonObject.class);
			com.google.gson.JsonArray pending = invalid.getAsJsonArray("pending");
			if ("schema".equals(failure)) pending.get(0).getAsJsonObject().addProperty("schemaVersion", "2");
			else if ("sequence".equals(failure)) pending.get(0).getAsJsonObject().addProperty("sequence", 2);
			else pending.add(pending.get(0).deepCopy());
			Files.writeString(file, storage.gson().toJson(invalid));
			byte[] before = Files.readAllBytes(file);
			try { new OfferEventOutbox(storage).pending(); org.junit.Assert.fail("invalid queue accepted: " + failure); }
			catch (IllegalStateException expected) { }
			org.junit.Assert.assertArrayEquals(before, Files.readAllBytes(file));
		}
	}

	@Test public void persistsIdentitySequenceAndCompleteEventsAcrossRestart() throws Exception
	{
		Path root = folder.newFolder("restart").toPath();
		PluginStorage storage = TestStorage.rootedAt(root, "player");
		OfferEventOutbox writing = new OfferEventOutbox(storage);
		OfferEvent first = writing.enqueue((eventId, sessionId, sequence) -> event(eventId, sessionId, sequence, 561));
		OfferEvent second = writing.enqueue((eventId, sessionId, sequence) -> event(eventId, sessionId, sequence, 4151));
		assertEquals(1, first.getSequence()); assertEquals(2, second.getSequence());
		assertNotEquals(first.getEventId(), second.getEventId()); assertEquals(first.getSessionId(), second.getSessionId());

		OfferEventOutbox restarted = new OfferEventOutbox(TestStorage.rootedAt(root, "player"));
		List<OfferEvent> pending = restarted.pending();
		assertEquals(2, pending.size()); assertEquals(first.getEventId(), pending.get(0).getEventId());
		assertEquals(561, pending.get(0).getItemId()); assertEquals(4151, pending.get(1).getItemId());
		assertEquals(first.getSessionId(), restarted.sessionId());
	}

	@Test public void accountsHaveIndependentSessionsAndQueues() throws Exception
	{
		Path root = folder.newFolder("accounts").toPath();
		OfferEventOutbox one = new OfferEventOutbox(TestStorage.rootedAt(root, "one"));
		OfferEventOutbox two = new OfferEventOutbox(TestStorage.rootedAt(root, "two"));
		one.enqueue((e, s, q) -> event(e, s, q, 561));
		two.enqueue((e, s, q) -> event(e, s, q, 4151));
		assertNotEquals(one.sessionId(), two.sessionId());
		assertEquals(561, one.pending().get(0).getItemId()); assertEquals(4151, two.pending().get(0).getItemId());
	}

	@Test public void rejectsFactoryIdentityChangesWithoutPersisting() throws Exception
	{
		Path root = folder.newFolder("identity").toPath();
		OfferEventOutbox outbox = new OfferEventOutbox(TestStorage.rootedAt(root, "player"));
		try
		{
			outbox.enqueue((e, s, q) -> event("different", s, q, 561));
			org.junit.Assert.fail("identity change must be rejected");
		}
		catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("identity")); }
		assertTrue(outbox.pending().isEmpty());
	}

	@Test public void corruptStateFailsClosedInsteadOfStartingAnEmptyQueue() throws Exception
	{
		Path root = folder.newFolder("corrupt").toPath();
		PluginStorage storage = TestStorage.rootedAt(root, "player");
		Path file = storage.accountDir().resolve(OfferEventOutbox.FILE_NAME);
		Files.write(file, "not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		try
		{
			new OfferEventOutbox(storage).pending();
			org.junit.Assert.fail("corrupt durable state must not be discarded");
		}
		catch (Exception expected) { assertTrue(Files.exists(file)); }
	}


        @Test public void exactAcknowledgementRemovesOnlyTheMatchAcrossRestart() throws Exception
        {
                Path root = folder.newFolder("ack").toPath();
                OfferEventOutbox outbox = new OfferEventOutbox(TestStorage.rootedAt(root, "player"));
                OfferEvent first = outbox.enqueue((e, s, q) -> event(e, s, q, 561));
                OfferEvent second = outbox.enqueue((e, s, q) -> event(e, s, q, 4151));
                assertTrue(outbox.acknowledge(first.getEventId(), first.getSessionId(), first.getSequence()));
                assertEquals(second.getEventId(), outbox.pending().get(0).getEventId());
                OfferEventOutbox restarted = new OfferEventOutbox(TestStorage.rootedAt(root, "player"));
                assertEquals(1, restarted.pending().size());
                assertEquals(3, restarted.enqueue((e, s, q) -> event(e, s, q, 1603)).getSequence());
        }

        @Test public void mismatchesAndRepeatedAcknowledgementRemoveNothing() throws Exception
        {
                OfferEventOutbox outbox = new OfferEventOutbox(TestStorage.rootedAt(folder.newFolder("mismatch").toPath(), "player"));
                OfferEvent event = outbox.enqueue((e, s, q) -> event(e, s, q, 561));
                assertFalse(outbox.acknowledge("wrong", event.getSessionId(), event.getSequence()));
                assertFalse(outbox.acknowledge(event.getEventId(), "wrong", event.getSequence()));
                assertFalse(outbox.acknowledge(event.getEventId(), event.getSessionId(), event.getSequence() + 1));
                assertEquals(1, outbox.pending().size());
                assertTrue(outbox.acknowledge(event.getEventId(), event.getSessionId(), event.getSequence()));
                assertFalse(outbox.acknowledge(event.getEventId(), event.getSessionId(), event.getSequence()));
        }

	private static OfferEvent event(String eventId, String sessionId, long sequence, int itemId)
	{
		return OfferEvent.builder("trace", 100 + sequence, "OBSERVED")
			.eventIdentity(eventId, sessionId, "offer-" + itemId).sequence(sequence)
			.slot(0).item(itemId, "Item").buying(true).price(100).quantities(10, 2).spent(200).firstSeenAt(90).build();
	}
}
