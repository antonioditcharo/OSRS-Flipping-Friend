package com.flippingfriend.companion;

import com.flippingfriend.data.PluginStorage;
import com.flippingfriend.data.TestStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Persistence and ordering contract for companion event delivery.
 */
public class CompanionEventOutboxTest
{
	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void queuedEventsSurviveAReconstructedOutbox() throws Exception
	{
		Path root = folder.newFolder("restart").toPath();
		PluginStorage storage = TestStorage.rootedAt(root, "profile-a");

		CompanionEventOutbox before = new CompanionEventOutbox(storage);
		assertTrue(before.enqueue("events/account-state", "{\"number\":1}"));
		assertTrue(before.enqueue("events/ge-offer", "{\"number\":2}"));

		CompanionEventOutbox after = new CompanionEventOutbox(storage);
		List<String> delivered = new ArrayList<>();

		assertEquals(2, after.drain((path, body) ->
		{
			delivered.add(path + "=" + body);
			return true;
		}));

		assertEquals(Arrays.asList(
			"events/account-state={\"number\":1}",
			"events/ge-offer={\"number\":2}"), delivered);
		assertEquals(0, after.size());
	}

	@Test
	public void failedDeliveryRetainsTheEntryAndStopsFifoDrain() throws Exception
	{
		Path root = folder.newFolder("failure").toPath();
		CompanionEventOutbox outbox =
			new CompanionEventOutbox(TestStorage.rootedAt(root, "profile-a"));

		assertTrue(outbox.enqueue("events/ge-offer", "{\"number\":1}"));
		assertTrue(outbox.enqueue("events/ge-offer", "{\"number\":2}"));

		List<String> attempted = new ArrayList<>();
		assertEquals(0, outbox.drain((path, body) ->
		{
			attempted.add(body);
			return false;
		}));

		assertEquals(Arrays.asList("{\"number\":1}"), attempted);
		assertEquals(2, outbox.size());
	}

	@Test
	public void acknowledgedEntriesAreDurablyRemoved() throws Exception
	{
		Path root = folder.newFolder("removal").toPath();
		PluginStorage storage = TestStorage.rootedAt(root, "profile-a");
		CompanionEventOutbox outbox = new CompanionEventOutbox(storage);

		assertTrue(outbox.enqueue("events/ge-offer", "{\"number\":1}"));
		assertEquals(1, outbox.drain((path, body) -> true));

		CompanionEventOutbox reconstructed =
			new CompanionEventOutbox(storage);
		assertEquals(0, reconstructed.size());
	}

	@Test
	public void queueRejectsNewEntriesAtItsBound() throws Exception
	{
		Path root = folder.newFolder("bounded").toPath();
		CompanionEventOutbox outbox =
			new CompanionEventOutbox(TestStorage.rootedAt(root, "profile-a"));

		for (int i = 0; i < CompanionEventOutbox.MAX_ENTRIES; i++)
		{
			assertTrue("entry " + i, outbox.enqueue(
				"events/ge-offer", "{\"number\":" + i + "}"));
		}

		assertFalse(outbox.enqueue(
			"events/ge-offer", "{\"number\":1001}"));
		assertEquals(CompanionEventOutbox.MAX_ENTRIES, outbox.size());
	}

	@Test
	public void nothingIsWrittenBeforeAnAccountProfileExists() throws Exception
	{
		Path root = folder.newFolder("no-profile").toPath();
		PluginStorage storage =
			TestStorage.rootedAt(root, null);
		CompanionEventOutbox outbox = new CompanionEventOutbox(storage);

		assertFalse(outbox.enqueue(
			"events/ge-offer", "{\"number\":1}"));
		assertEquals(0, outbox.size());
		assertFalse(Files.exists(root.resolve("accounts")));
	}

	@Test
	public void accountQueuesRemainSeparate() throws Exception
	{
		Path root = folder.newFolder("profiles").toPath();

		CompanionEventOutbox first = new CompanionEventOutbox(
			TestStorage.rootedAt(root, "profile-a"));
		CompanionEventOutbox second = new CompanionEventOutbox(
			TestStorage.rootedAt(root, "profile-b"));

		assertTrue(first.enqueue("events/ge-offer", "{\"account\":\"a\"}"));
		assertTrue(second.enqueue("events/ge-offer", "{\"account\":\"b\"}"));

		List<String> firstBodies = new ArrayList<>();
		List<String> secondBodies = new ArrayList<>();

		assertEquals(1, first.drain((path, body) ->
		{
			firstBodies.add(body);
			return true;
		}));

		assertEquals(1, second.drain((path, body) ->
		{
			secondBodies.add(body);
			return true;
		}));

		assertEquals(Arrays.asList("{\"account\":\"a\"}"), firstBodies);
		assertEquals(Arrays.asList("{\"account\":\"b\"}"), secondBodies);
	}

}
