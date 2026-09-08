package com.flippingfriend.companion;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.core.CompanionHealth;
import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Health has to answer without reading the database, because the plugin asks constantly.
 *
 * <p>It used to count the learning record on every call: two queries, on the path of the one endpoint
 * whose whole job is to answer immediately. SQLite serialises writers against readers, so a run of
 * health polls competes with the writes that matter -- an account-state POST, which takes a write lock
 * to record the event, timed out behind exactly that. A status line that can block the thing it
 * reports the status of is worse than not having one.
 *
 * <p>The property is tested by taking the database away. Every query against a closed store fails, so
 * the old code reached its catch and reported the learning record as "unreadable"; the new code
 * reports what it already counted. Health telling the truth about the record while the database is
 * unreachable is the observable form of "it did not go and ask".
 */
public class HealthDoesNotQueryTheStoreTest
{
	@Test
	public void healthStillAnswersWhenTheStoreCannot() throws Exception
	{
		Path directory = Files.createTempDirectory("flipping-friend-health");
		SqliteStore store = new SqliteStore(directory.resolve("test.db"));
		CompanionService service = new CompanionService(new Gson(), store);

		// Every query against it now throws. If health needs one, this is where it fails.
		store.close();

		CompanionHealth health = service.health();

		assertNotNull("health has to survive a store it cannot read", health);
		assertTrue("it still reports the learning record: " + health.getReason(),
			health.getReason().contains("learning record"));
		assertFalse("and reports it from what it knows, not from a failed query: "
			+ health.getReason(), health.getReason().contains("unreadable"));

		service.close();
	}
}
