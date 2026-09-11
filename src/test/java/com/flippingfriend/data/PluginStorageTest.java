package com.flippingfriend.data;

import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the persistence result used by delivery queues.
 */
public class PluginStorageTest
{
	private static final Type STRING_LIST =
		new TypeToken<List<String>>() { }.getType();

	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void checkedWriteReportsSuccessAndCanBeReadBack() throws Exception
	{
		Path root = folder.newFolder("success").toPath();
		PluginStorage storage = TestStorage.rootedAt(root, "profile-a");
		Path file = storage.accountDir().resolve("event-outbox.json");

		assertTrue(storage.writeJsonChecked(
			file, Arrays.asList("first", "second"), STRING_LIST));

		assertEquals(
			Arrays.asList("first", "second"),
			storage.readJson(file, STRING_LIST, Collections.emptyList()));
	}

	@Test
	public void checkedWriteReportsFailureWhenParentCannotBeCreated() throws Exception
	{
		Path root = folder.newFolder("failure").toPath();
		PluginStorage storage = TestStorage.rootedAt(root, "profile-a");

		Path blockingFile = root.resolve("not-a-directory");
		Files.write(blockingFile, "blocked".getBytes(StandardCharsets.UTF_8));
		Path impossible = blockingFile.resolve("event-outbox.json");

		assertFalse(storage.writeJsonChecked(
			impossible, Collections.singletonList("event"), STRING_LIST));
		assertFalse(Files.exists(impossible));
	}
}
