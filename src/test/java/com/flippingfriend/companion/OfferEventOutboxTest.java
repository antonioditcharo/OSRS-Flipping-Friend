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

	private static OfferEvent event(String eventId, String sessionId, long sequence, int itemId)
	{
		return OfferEvent.builder("trace", 100 + sequence, "OBSERVED")
			.eventIdentity(eventId, sessionId, "offer-" + itemId).sequence(sequence)
			.slot(0).item(itemId, "Item").buying(true).price(100).quantities(10, 2).spent(200).firstSeenAt(90).build();
	}
}
