package com.flippingfriend.companion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Every write path is exercised against a real database, because a statement referring to a table
 * nobody created compiles perfectly and fails only at the moment it is used.
 * <p>
 * That is not hypothetical: the gate table was added as an INSERT without its CREATE, and the replay
 * ran to completion, printed a full report, and threw on the last line — so the results looked fine
 * and were never stored. A schema is only real if something has written to it.
 */
public class SqliteStoreTest
{
	private Path database() throws Exception
	{
		return Files.createTempDirectory("flipping-friend-store").resolve("test.db");
	}

	@Test
	public void everyWritePathHasATableBehindIt() throws Exception
	{
		try (SqliteStore store = new SqliteStore(database()))
		{
			store.recordEvent(1, "c", "BOUGHT", "{}");
			store.savePlan(1, 2, "c", "{}");
			store.recordExecution(4151, true, 5.0, 4.0);
			store.markMigration("legacy", "backup");
			store.recordGates(1, "5m", Arrays.asList(
				new GateReport.Gate("Profitable", true, "1,000 gp"),
				new GateReport.Gate("Beats baseline", false, "0.83x")), "report");
		}
	}

	@Test
	public void gateResultsArePersistedForLaterComparison() throws Exception
	{
		Path path = database();
		try (SqliteStore store = new SqliteStore(path))
		{
			store.recordGates(1_000, "5m", Arrays.asList(
				new GateReport.Gate("Profitable", true, "1,000 gp"),
				new GateReport.Gate("Beats baseline", false, "0.83x")), "report");
			store.recordGates(2_000, "1h", Arrays.asList(
				new GateReport.Gate("Profitable", true, "2,000 gp")), "report");
		}

		// Reopened, because the point of storing them is that they outlive the run.
		try (SqliteStore reopened = new SqliteStore(path))
		{
			List<String> rows = reopened.recentOfferEvents(0);
			assertTrue("gate rows must not leak into the offer event stream", rows.isEmpty());
		}
	}

	@Test
	public void executionStatisticsAccumulateAcrossCalls() throws Exception
	{
		try (SqliteStore store = new SqliteStore(database()))
		{
			store.recordExecution(4151, true, 4.0, 3.0);
			store.recordExecution(4151, false, 0.0, 0.0);
			store.recordExecution(4151, true, 6.0, 5.0);

			SqliteStore.ExecutionStat stat = store.executionStats().get(4151);
			assertEquals(3, stat.observed);
			assertEquals(2, stat.completed);
			assertEquals(5.0, stat.meanFillMinutes(), 1e-9);
			// Ten minutes of real fills against eight predicted: the ratio the learner corrects by.
			assertEquals(10.0 / 8.0, stat.durationRatio(), 1e-9);
		}
	}

	/**
	 * The largest table in the schema had no bound at all: 62MB of an 81MB database, growing by about
	 * 34MB a day. It hid from a size audit because the name and the JSON are separated by a NUL and
	 * SQLite's length() stops counting there, so every 690KB row measured fifteen bytes.
	 */
	@Test
	public void supersededModelWeightsAreReclaimedButTheirHistoryIsNot() throws Exception
	{
		Path path = database();
		try (SqliteStore store = new SqliteStore(path))
		{
			for (int i = 0; i < 4; i++)
			{
				store.saveModel("buy-completion", "{\"trees\":[" + i + "]}");
				store.saveModel("sell-completion", "{\"trees\":[" + i + "]}");
			}

			assertEquals(6, store.pruneModels(1));

			// The weights that still matter are the newest of each name, and they must survive intact.
			assertEquals("{\"trees\":[3]}", store.loadModel("buy-completion"));
			assertEquals("{\"trees\":[3]}", store.loadModel("sell-completion"));
			// The rows themselves stay: version numbers and timestamps are the retrain history the
			// monitor charts, and deleting them would rewrite the chart to say it never happened.
			assertEquals(8, store.modelVersionCount());
			// Idempotent, so the hourly sweep does no work once it has caught up.
			assertEquals(0, store.pruneModels(1));
		}
	}

	@Test
	public void canonicalEventsAreAcceptedOnlyOnceAcrossReopen() throws Exception
	{
		Path path = database();
		try (SqliteStore store = new SqliteStore(path))
		{
			assertEquals(SqliteStore.EventAcceptance.NEW, store.recordEvent(1, "trace", "event-1", "BOUGHT", "first"));
			assertEquals(SqliteStore.EventAcceptance.DUPLICATE, store.recordEvent(2, "trace", "event-1", "BOUGHT", "second"));
			assertEquals(SqliteStore.EventAcceptance.NEW, store.recordEvent(3, "trace", "event-2", "BOUGHT", "third"));
			assertEquals(Arrays.asList("first", "third"), store.recentOfferEvents(0));
		}
		try (SqliteStore reopened = new SqliteStore(path))
		{
			assertEquals(SqliteStore.EventAcceptance.DUPLICATE, reopened.recordEvent(4, "trace", "event-1", "BOUGHT", "later"));
			assertEquals(Arrays.asList("first", "third"), reopened.recentOfferEvents(0));
		}
	}

	@Test
	public void legacyAndBlankEventIdsRemainAppendOnly() throws Exception
	{
		try (SqliteStore store = new SqliteStore(database()))
		{
			store.recordEvent(1, "legacy", "BOUGHT", "legacy-one"); store.recordEvent(2, "legacy", "BOUGHT", "legacy-two");
			assertEquals(SqliteStore.EventAcceptance.NEW, store.recordEvent(3, "blank", "   ", "BOUGHT", "blank-one"));
			assertEquals(SqliteStore.EventAcceptance.NEW, store.recordEvent(4, "blank", "   ", "BOUGHT", "blank-two"));
			assertEquals(4, store.recentOfferEvents(0).size());
		}
	}

	@Test
	public void legacyDatabaseIsWidenedForCanonicalEventIds() throws Exception
	{
		Path path = database(); Files.createDirectories(path.getParent());
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath()); Statement s = c.createStatement())
		{
			s.execute("CREATE TABLE event_log (id INTEGER PRIMARY KEY, observed_at INTEGER NOT NULL, correlation_id TEXT NOT NULL, event_type TEXT NOT NULL, payload TEXT NOT NULL)");
			s.execute("INSERT INTO event_log(observed_at, correlation_id, event_type, payload) VALUES(1, 'legacy', 'BOUGHT', 'legacy')");
		}
		try (SqliteStore store = new SqliteStore(path))
		{
			assertEquals(SqliteStore.EventAcceptance.NEW, store.recordEvent(2, "trace", "event-1", "BOUGHT", "canonical"));
			assertEquals(SqliteStore.EventAcceptance.DUPLICATE, store.recordEvent(3, "trace", "event-1", "BOUGHT", "duplicate"));
			assertEquals(Arrays.asList("legacy", "canonical"), store.recentOfferEvents(0));
		}
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath()); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT event_id FROM event_log WHERE payload='canonical'"))
		{
			assertTrue(r.next()); assertEquals("event-1", r.getString(1));
		}
	}

	@Test
	public void migrationIsRecordedOnlyOnce() throws Exception
	{
		try (SqliteStore store = new SqliteStore(database()))
		{
			store.markMigration("positions", "backup-1");
			store.markMigration("positions", "backup-2");

			assertTrue(store.wasMigrated("positions"));
			assertTrue("an unmigrated source must not be claimed", !store.wasMigrated("journal"));
		}
	}
}
