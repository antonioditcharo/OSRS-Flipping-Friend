package com.flippingfriend.companion;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The companion is the sole writer of durable portfolio and telemetry state. */
final class SqliteStore implements AutoCloseable
{
	/**
	 * How much of a model payload to read back when only its name is wanted.
	 * <p>
	 * Comfortably longer than any model name, and short enough that reading it for every row costs
	 * kilobytes rather than the 62MB the payloads themselves come to.
	 */
	private static final int MODEL_NAME_LIMIT = 64;

	private final Connection connection;

	/**
	 * Opens an existing database for reading only, creating and altering nothing.
	 * <p>
	 * The replay needs the models the companion is actually shipping, and those live in the running
	 * service's database. Opening it the normal way would run the whole CREATE/ALTER sequence against
	 * a file another process is writing to, which is not something a read-only report should ever do.
	 */
	static SqliteStore openReadOnly(Path database) throws Exception
	{
		return new SqliteStore(database, true);
	}

	SqliteStore(Path database) throws Exception
	{
		this(database, false);
	}

	private SqliteStore(Path database, boolean readOnly) throws Exception
	{
		if (readOnly)
		{
			// The driver rejects the extra immutable/nolock parameters that look like they belong
			// here; mode=ro on a file: URL is the form it accepts.
			String path = database.toAbsolutePath().toString()
				.replace(java.io.File.separatorChar, '/');
			connection = DriverManager.getConnection("jdbc:sqlite:file:" + path + "?mode=ro");
			return;
		}
		Files.createDirectories(database.getParent());
		connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
		try (Statement statement = connection.createStatement())
		{
			statement.execute("PRAGMA journal_mode=WAL");
			statement.execute("PRAGMA foreign_keys=ON");
			statement.execute("CREATE TABLE IF NOT EXISTS event_log (id INTEGER PRIMARY KEY, observed_at INTEGER NOT NULL, correlation_id TEXT NOT NULL, event_type TEXT NOT NULL, payload TEXT NOT NULL)");
			// market_observation is not created any more, and is dropped below if an older database
			// still has it. Nothing ever read a row from it: every SELECT in all three modules is
			// nine queries and none name the table.
			statement.execute("CREATE TABLE IF NOT EXISTS portfolio_plan (id INTEGER PRIMARY KEY, created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL, correlation_id TEXT NOT NULL, payload TEXT NOT NULL)");
			statement.execute("CREATE TABLE IF NOT EXISTS model_snapshot (version INTEGER PRIMARY KEY, created_at INTEGER NOT NULL, payload TEXT NOT NULL)");
			statement.execute("CREATE TABLE IF NOT EXISTS migration_log (source TEXT PRIMARY KEY, migrated_at INTEGER NOT NULL, backup_path TEXT NOT NULL)");
			statement.execute("CREATE TABLE IF NOT EXISTS gate_result (id INTEGER PRIMARY KEY, evaluated_at INTEGER NOT NULL, resolution TEXT NOT NULL, gate TEXT NOT NULL, passed INTEGER NOT NULL, measured TEXT NOT NULL, payload TEXT NOT NULL)");
			statement.execute("CREATE TABLE IF NOT EXISTS execution_stat (item_id INTEGER PRIMARY KEY, completed INTEGER NOT NULL DEFAULT 0, observed INTEGER NOT NULL DEFAULT 0, fill_minutes REAL NOT NULL DEFAULT 0, predicted_minutes REAL NOT NULL DEFAULT 0)");
			statement.execute("CREATE TABLE IF NOT EXISTS counted_offer (identity TEXT PRIMARY KEY, counted_at INTEGER NOT NULL)");
			// What our own offers won out of the flow that passed them. Per item, because queue
			// competition is an item's property: a popular rune has a dozen buyers stacked at every
			// price and a slow-moving armour piece has none.
			statement.execute("CREATE TABLE IF NOT EXISTS capture_stat (item_id INTEGER PRIMARY KEY, filled REAL NOT NULL DEFAULT 0, flow REAL NOT NULL DEFAULT 0, observations INTEGER NOT NULL DEFAULT 0)");
		}

		// predicted_minutes arrived after the table had already shipped, so databases created by an
		// earlier build need it bolting on. SQLite has no "add column if missing", and the failure on
		// a database that already has it is both expected and harmless.
		try (Statement statement = connection.createStatement())
		{
			statement.execute(
				"ALTER TABLE execution_stat ADD COLUMN predicted_minutes REAL NOT NULL DEFAULT 0");
		}
		catch (Exception alreadyPresent)
		{
			// Nothing to do; the column is there.
		}
		// The paired totals: fills that arrived with a prediction to compare them against. Without
		// these, fill_minutes accumulated for every completed offer while predicted_minutes only did
		// for the ones carrying advice, so their ratio put unattributed durations over attributed
		// predictions and ran systematically high.
		for (String column : new String[]{"paired_fill_minutes REAL NOT NULL DEFAULT 0",
			"paired_completed INTEGER NOT NULL DEFAULT 0"})
		{
			try (Statement statement = connection.createStatement())
			{
				statement.execute("ALTER TABLE execution_stat ADD COLUMN " + column);
			}
			catch (Exception alreadyPresent)
			{
				// Nothing to do; the column is there.
			}
		}
	}

	synchronized void recordEvent(long observedAt, String correlationId, String eventType, String payload) throws Exception
	{
		try (PreparedStatement statement = connection.prepareStatement("INSERT INTO event_log(observed_at, correlation_id, event_type, payload) VALUES(?,?,?,?)"))
		{
			statement.setLong(1, observedAt);
			statement.setString(2, correlationId);
			statement.setString(3, eventType);
			statement.setString(4, payload);
			statement.executeUpdate();
		}
	}

	/**
	 * Notes that an endpoint was fetched. The response body is deliberately not kept.
	 * <p>
	 * It used to be. Nothing has ever read it back -- there is no SELECT against this table anywhere
	 * in the project -- and at roughly 140KB a row across every 'latest', '5m', '1h' and per-item
	 * timeseries response, the database was growing about 2.4GB a day on a daemon designed to run
	 * continuously. What the row is actually good for is answering "is ingestion running and how
	 * fresh is it", and that needs the timestamps, not the megabyte.
	 */
	// recordMarket lived here. Every ingestion tick wrote a row to market_observation, and nothing in
	// the project has ever read one: enumerating every SELECT in all three modules turns up nine, and
	// none of them name the table. Its payload column had already been gutted for the same reason,
	// which left four columns and a row being written for nobody -- plus a retention job to bound it.
	//
	// The writes are gone, the table is no longer created, and a database from an earlier build has it
	// dropped and the file compacted once at start-up -- see dropLegacyMarketObservations below.

	/**
	 * Claims an offer identity, returning true only the first time it is seen.
	 * <p>
	 * The de-duplication this replaces lived in a bounded in-memory map, which meant it lasted
	 * exactly as long as the process. The game re-announces every current Grand Exchange slot on
	 * login, so a companion restart followed by the client reconnecting counted each settled offer a
	 * second time -- inflating execution_stat, which is the sample the completion posterior and the
	 * learned durations are both built on. rehydrate() is careful not to replay execution stats for
	 * exactly this reason and says a sample inflated by restarts is worse than no sample at all;
	 * this closes the same hole from the other side.
	 */
	synchronized boolean claimOffer(String identity, long countedAt) throws Exception
	{
		try (PreparedStatement statement = connection.prepareStatement(
			"INSERT OR IGNORE INTO counted_offer(identity, counted_at) VALUES(?,?)"))
		{
			statement.setString(1, identity);
			statement.setLong(2, countedAt);
			return statement.executeUpdate() > 0;
		}
	}

	/**
	 * Bounds the event log.
	 * <p>
	 * It was the last table in the schema with no retention at all. Most of its rows are account
	 * snapshots, which the only query against it explicitly excludes -- they are kept because they are
	 * the forensic record of what the companion was told, which is how several faults in this project
	 * were eventually explained, but there is no reason to keep them for ever.
	 */
	synchronized int pruneEvents(int keepDays) throws Exception
	{
		long cutoff = Instant.now().getEpochSecond() - (long) keepDays * 86_400;
		try (PreparedStatement statement = connection.prepareStatement(
			"DELETE FROM event_log WHERE observed_at < ?"))
		{
			statement.setLong(1, cutoff);
			return statement.executeUpdate();
		}
	}

	/**
	 * Drops plans past their usefulness.
	 * <p>
	 * The only table in this schema that had no retention, and the one that grows fastest: measured at
	 * 62 plans an hour at roughly 4.8KB each, which is 1,477 a day and about 213MB a month. It is worth
	 * keeping — the monitor's entire history view is built on it — but not worth keeping forever.
	 */
	synchronized int prunePlans(int keepDays) throws Exception
	{
		long cutoff = Instant.now().getEpochSecond() - (long) keepDays * 86_400L;
		try (PreparedStatement statement = connection.prepareStatement(
			"DELETE FROM portfolio_plan WHERE created_at < ?"))
		{
			statement.setLong(1, cutoff);
			return statement.executeUpdate();
		}
	}

	/**
	 * Drops the weights of superseded models while keeping the rows that record they existed.
	 * <p>
	 * This table was the last one in the schema with no bound at all, and by some margin the largest:
	 * 94 rows at roughly 690KB each, 62MB of an 81MB database, two written per retrain and about four
	 * retrains an hour. That is around 34MB a day, forever, and it is how this file reached 2.65GB
	 * once already.
	 * <p>
	 * It hid because {@code saveModel} separates the model name from its JSON with a NUL, which is a
	 * sound choice — no name or JSON document can contain one — but SQLite's {@code length()} counts
	 * characters up to the first NUL. Every row therefore measured fourteen or fifteen bytes, so a
	 * table-by-table size audit reported this one as empty. Casting to a blob is the only way to see
	 * it, which is why the earlier compaction walked straight past 62MB.
	 * <p>
	 * Rows are blanked rather than deleted. The version numbers and timestamps are the retrain history
	 * the monitor charts as gold ticks, and deleting them would rewrite five days of that chart to say
	 * the retrains never happened. Only the weights go, and only once something newer under the same
	 * name has superseded them. A blanked row has no NUL, so {@link #loadModel} skips it exactly as it
	 * skips a row belonging to another model — no special case needed.
	 *
	 * @param keepPerName how many recent versions of each model keep their weights, for rollback
	 * @return how many rows were blanked
	 */
	synchronized int pruneModels(int keepPerName) throws Exception
	{
		List<Long> stale = new ArrayList<>();
		Map<String, Integer> seen = new HashMap<>();
		// Only the prefix is read back. Selecting the payload itself would pull 62MB through the
		// driver to decide what to discard, which is the opposite of the point.
		//
		// Cast to a blob first. substr() on TEXT stops at the first NUL exactly as length() does, so
		// the obvious spelling returns the model name with the separator already trimmed off and no
		// way to tell a real row from a blanked one. On a blob the indices are bytes and nothing is
		// hidden -- the same distinction that kept 62MB off every size audit of this table.
		try (PreparedStatement statement = connection.prepareStatement(
			"SELECT version, substr(CAST(payload AS BLOB), 1, " + MODEL_NAME_LIMIT + ") "
				+ "FROM model_snapshot ORDER BY version DESC");
			ResultSet rows = statement.executeQuery())
		{
			while (rows.next())
			{
				byte[] prefix = rows.getBytes(2);
				int separator = -1;
				for (int i = 0; prefix != null && i < prefix.length; i++)
				{
					if (prefix[i] == 0)
					{
						separator = i;
						break;
					}
				}
				if (separator <= 0)
				{
					// Already blanked, or a row this build does not understand. Either way there is
					// nothing left in it to reclaim.
					continue;
				}
				String name = new String(prefix, 0, separator, StandardCharsets.UTF_8);
				int kept = seen.merge(name, 1, Integer::sum);
				if (kept > keepPerName)
				{
					stale.add(rows.getLong(1));
				}
			}
		}
		if (stale.isEmpty())
		{
			return 0;
		}
		try (PreparedStatement statement = connection.prepareStatement(
			"UPDATE model_snapshot SET payload = '' WHERE version = ?"))
		{
			for (long version : stale)
			{
				statement.setLong(1, version);
				statement.addBatch();
			}
			statement.executeBatch();
		}
		return stale.size();
	}

	/** Forgets offer identities old enough that the game will never replay them. */
	synchronized int pruneCountedOffers(int keepDays) throws Exception
	{
		long cutoff = Instant.now().getEpochSecond() - (long) keepDays * 86_400L;
		try (PreparedStatement statement = connection.prepareStatement(
			"DELETE FROM counted_offer WHERE counted_at < ?"))
		{
			statement.setLong(1, cutoff);
			return statement.executeUpdate();
		}
	}

	/**
	 * Removes the ingestion table from a database created by an earlier build.
	 * <p>
	 * It was written on every tick and read by nothing, and on this machine it had reached 2.5 GB.
	 * Deleting rows alone would not give the space back -- SQLite reuses freed pages rather than
	 * shrinking the file -- so the caller follows this with a VACUUM while nothing else is connected.
	 *
	 * @return true when a table was actually there to drop
	 */
	synchronized boolean dropLegacyMarketObservations() throws Exception
	{
		try (ResultSet result = connection.createStatement().executeQuery(
			"SELECT name FROM sqlite_master WHERE type='table' AND name='market_observation'"))
		{
			if (!result.next())
			{
				return false;
			}
		}
		try (Statement statement = connection.createStatement())
		{
			statement.execute("DROP TABLE market_observation");
		}
		return true;
	}

	/**
	 * Flushes the write-ahead log back into the database and truncates it.
	 * <p>
	 * Only safe while nothing else has the file open, because a checkpoint cannot complete past an
	 * active reader -- which is exactly why it belongs at startup rather than on a timer.
	 */
	synchronized void checkpoint() throws Exception
	{
		try (Statement statement = connection.createStatement())
		{
			statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
		}
	}

	/** Reclaims the file space freed by dropped or pruned rows. Slow, so only when something changed. */
	synchronized void compact() throws Exception
	{
		try (Statement statement = connection.createStatement())
		{
			statement.execute("VACUUM");
		}
	}

	synchronized void savePlan(long createdAt, long expiresAt, String correlationId, String payload) throws Exception
	{
		try (PreparedStatement statement = connection.prepareStatement("INSERT INTO portfolio_plan(created_at, expires_at, correlation_id, payload) VALUES(?,?,?,?)"))
		{
			statement.setLong(1, createdAt);
			statement.setLong(2, expiresAt);
			statement.setString(3, correlationId);
			statement.setString(4, payload);
			statement.executeUpdate();
		}
	}

	/**
	 * Folds one settled offer into the per-item execution record.
	 * <p>
	 * {@code observed} counts offers placed and {@code completed} those that fully filled, so their
	 * ratio is the realised completion rate the fill model can be scored against. {@code fill_minutes}
	 * accumulates only over completed offers, so dividing by {@code completed} gives the mean time a
	 * fill actually took.
	 */
	synchronized void recordExecution(int itemId, boolean completed, double fillMinutes,
		double predictedMinutes) throws Exception
	{
		try (PreparedStatement statement = connection.prepareStatement(
			"INSERT INTO execution_stat(item_id, completed, observed, fill_minutes, predicted_minutes, "
				+ "paired_fill_minutes, paired_completed) "
				+ "VALUES(?,?,1,?,?,?,?) "
				+ "ON CONFLICT(item_id) DO UPDATE SET completed = completed + excluded.completed, "
				+ "observed = observed + 1, fill_minutes = fill_minutes + excluded.fill_minutes, "
				+ "predicted_minutes = predicted_minutes + excluded.predicted_minutes, "
				+ "paired_fill_minutes = paired_fill_minutes + excluded.paired_fill_minutes, "
				+ "paired_completed = paired_completed + excluded.paired_completed"))
		{
			// A duration only teaches us something when there is a prediction beside it to compare
			// against, and a fill time of zero means the plugin never saw the offer appear rather than
			// that it filled instantly -- pairing that with a real prediction drags the ratio to the
			// floor and makes the plan believe fills take half as long as they do.
			boolean comparable = completed && predictedMinutes > 0 && fillMinutes > 0;
			statement.setInt(1, itemId);
			statement.setInt(2, completed ? 1 : 0);
			statement.setDouble(3, fillMinutes);
			statement.setDouble(4, comparable ? predictedMinutes : 0);
			statement.setDouble(5, comparable ? fillMinutes : 0);
			statement.setInt(6, comparable ? 1 : 0);
			statement.executeUpdate();
		}
	}

	/**
	 * Folds one measured offer into the per-item capture record.
	 * <p>
	 * Accumulated as sums rather than as a running average, so an offer measured against a torrent
	 * counts for more than one measured against a trickle. Averaging the ratios would give a
	 * five-unit observation the same say as a five-thousand-unit one.
	 */
	synchronized void recordCapture(int itemId, double filled, double flow) throws Exception
	{
		if (itemId <= 0 || flow <= 0)
		{
			return;
		}
		try (PreparedStatement statement = connection.prepareStatement(
			"INSERT INTO capture_stat(item_id, filled, flow, observations) VALUES(?,?,?,1) "
				+ "ON CONFLICT(item_id) DO UPDATE SET filled = filled + excluded.filled, "
				+ "flow = flow + excluded.flow, observations = observations + 1"))
		{
			statement.setInt(1, itemId);
			statement.setDouble(2, Math.max(0, filled));
			statement.setDouble(3, flow);
			statement.executeUpdate();
		}
	}

	/** Everything learned about capture so far, for rebuilding the learner on startup. */
	synchronized Map<Integer, CaptureRates.Totals> captureStats() throws Exception
	{
		Map<Integer, CaptureRates.Totals> stats = new HashMap<>();
		try (PreparedStatement statement = connection.prepareStatement(
			"SELECT item_id, filled, flow, observations FROM capture_stat");
			ResultSet result = statement.executeQuery())
		{
			while (result.next())
			{
				stats.put(result.getInt(1), new CaptureRates.Totals(
					result.getDouble(2), result.getDouble(3), result.getInt(4)));
			}
		}
		return stats;
	}

	/** Realised completion rate and mean fill time per item, for calibrating the fill model. */
	synchronized Map<Integer, ExecutionStat> executionStats() throws Exception
	{
		Map<Integer, ExecutionStat> stats = new HashMap<>();
		try (PreparedStatement statement = connection.prepareStatement(
			"SELECT item_id, completed, observed, fill_minutes, predicted_minutes, "
				+ "paired_fill_minutes, paired_completed FROM execution_stat");
			ResultSet result = statement.executeQuery())
		{
			while (result.next())
			{
				stats.put(result.getInt(1), new ExecutionStat(result.getInt(2), result.getInt(3),
					result.getDouble(4), result.getDouble(5), result.getDouble(6), result.getInt(7)));
			}
		}
		return stats;
	}

	/** Observed execution behaviour for one item. */
	static final class ExecutionStat
	{
		final int completed;
		final int observed;
		final double fillMinutes;
		final double predictedMinutes;
		/** Fill time from offers that also carried a prediction, so the two are comparable. */
		final double pairedFillMinutes;
		final int pairedCompleted;

		ExecutionStat(int completed, int observed, double fillMinutes)
		{
			this(completed, observed, fillMinutes, 0);
		}

		ExecutionStat(int completed, int observed, double fillMinutes, double predictedMinutes)
		{
			// Same rule the write path applies: a duration is only comparable when a prediction sat
			// beside it and the fill time is real. Zero means the offer was never seen to appear.
			this(completed, observed, fillMinutes, predictedMinutes,
				predictedMinutes > 0 && fillMinutes > 0 ? fillMinutes : 0,
				predictedMinutes > 0 && fillMinutes > 0 ? completed : 0);
		}

		ExecutionStat(int completed, int observed, double fillMinutes, double predictedMinutes,
			double pairedFillMinutes, int pairedCompleted)
		{
			this.pairedFillMinutes = pairedFillMinutes;
			this.pairedCompleted = pairedCompleted;
			this.completed = completed;
			this.observed = observed;
			this.fillMinutes = fillMinutes;
			this.predictedMinutes = predictedMinutes;
		}

		/**
		 * How much longer fills really took than the model said, or zero when nothing comparable has
		 * been observed. Only offers that both completed and carried a prediction contribute.
		 */
		double durationRatio()
		{
			// Paired totals only. fillMinutes accumulates for every completed offer while
			// predictedMinutes accumulates only for the ones that carried advice, so dividing the two
			// raw totals put unattributed durations over attributed predictions -- inflating the
			// multiplier by however much of the trading went unrecommended.
			return predictedMinutes <= 0 ? 0 : pairedFillMinutes / predictedMinutes;
		}

		double completionRate() { return observed <= 0 ? 0 : (double) completed / observed; }
		double meanFillMinutes() { return completed <= 0 ? 0 : fillMinutes / completed; }
	}

	/**
	 * Offer events from the recent past, oldest first, for rebuilding in-memory state on startup.
	 * <p>
	 * Buy-limit windows live only in memory but describe a four-hour reality the game enforces
	 * regardless of whether this process was running. Without replay, restarting the companion makes
	 * it believe every allowance is untouched, and it will confidently recommend purchases the game
	 * refuses — which shows up as an offer that silently stops filling part-way, the exact failure
	 * the ledger exists to prevent.
	 */
	synchronized List<String> recentOfferEvents(long since) throws Exception
	{
		List<String> payloads = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement(
			"SELECT payload FROM event_log WHERE observed_at >= ? AND event_type <> 'ACCOUNT_STATE' "
				+ "AND event_type <> 'LEGACY_IMPORT' ORDER BY observed_at ASC, id ASC"))
		{
			statement.setLong(1, since);
			try (ResultSet result = statement.executeQuery())
			{
				while (result.next())
				{
					payloads.add(result.getString(1));
				}
			}
		}
		return payloads;
	}

	/** The newest trained model on record, or 0 when nothing has been trained yet. */
	/**
	 * The price archive, over this store's own connection.
	 *
	 * <p>One database file rather than a second: the same lock, the same backup, and no chance of the
	 * archive and the event log disagreeing about which of them is authoritative after a crash.
	 */
	synchronized PriceArchive priceArchive() throws Exception
	{
		if (archive == null)
		{
			archive = new PriceArchive(connection);
		}
		return archive;
	}

	private PriceArchive archive;

	synchronized long latestModelVersion() throws Exception
	{
		try (PreparedStatement statement =
			connection.prepareStatement("SELECT MAX(version) FROM model_snapshot");
			ResultSet result = statement.executeQuery())
		{
			return result.next() ? result.getLong(1) : 0;
		}
	}

	/**
	 * How many retrains are on record, weights or not.
	 * <p>
	 * Distinct from {@link #latestModelVersion()} once {@link #pruneModels} has run: the version
	 * number keeps climbing while the weights behind older rows are released, and this counts the
	 * rows that remain to say the retrain happened.
	 */
	synchronized int modelVersionCount() throws Exception
	{
		try (PreparedStatement statement =
			connection.prepareStatement("SELECT COUNT(*) FROM model_snapshot");
			ResultSet result = statement.executeQuery())
		{
			return result.next() ? result.getInt(1) : 0;
		}
	}

	/**
	 * Records one walk-forward evaluation, gate by gate.
	 * <p>
	 * Kept as history rather than a current-state row: the question that matters later is not only
	 * whether the gates pass today but whether a change made them better or worse, and that needs
	 * the previous answers still to be there.
	 */
	synchronized void recordGates(long evaluatedAt, String resolution, List<GateReport.Gate> gates,
		String payload) throws Exception
	{
		try (PreparedStatement statement = connection.prepareStatement(
			"INSERT INTO gate_result(evaluated_at, resolution, gate, passed, measured, payload) "
				+ "VALUES(?,?,?,?,?,?)"))
		{
			for (GateReport.Gate gate : gates)
			{
				statement.setLong(1, evaluatedAt);
				statement.setString(2, resolution);
				statement.setString(3, gate.getName());
				statement.setInt(4, gate.isPassed() ? 1 : 0);
				statement.setString(5, gate.getDetail());
				statement.setString(6, payload);
				statement.addBatch();
			}
			statement.executeBatch();
		}
	}

	/**
	 * Stores a trained model under a name, keeping every previous version.
	 * <p>
	 * History rather than replacement, because the question that matters after a change is not only
	 * whether the new model is good but whether it is better than the one it replaced — and answering
	 * that requires the old one to still exist. It is also the only way back if a promotion turns out
	 * to be a mistake.
	 */
	synchronized void saveModel(String name, String payload) throws Exception
	{
		try (PreparedStatement statement = connection.prepareStatement(
			"INSERT INTO model_snapshot(version, created_at, payload) VALUES("
				+ "(SELECT COALESCE(MAX(version), 0) + 1 FROM model_snapshot), ?, ?)"))
		{
			statement.setLong(1, Instant.now().getEpochSecond());
			statement.setString(2, name + "\0" + payload);
			statement.executeUpdate();
		}
	}

	/** The most recent model stored under this name, or null when none has been. */
	synchronized String loadModel(String name) throws Exception
	{
		try (PreparedStatement statement = connection.prepareStatement(
			"SELECT payload FROM model_snapshot ORDER BY version DESC"))
		{
			try (ResultSet result = statement.executeQuery())
			{
				while (result.next())
				{
					String payload = result.getString(1);
					int separator = payload.indexOf('\0');
					if (separator > 0 && payload.substring(0, separator).equals(name))
					{
						return payload.substring(separator + 1);
					}
				}
			}
		}
		return null;
	}

	synchronized void markMigration(String source, String backup) throws Exception
	{
		try (PreparedStatement statement = connection.prepareStatement("INSERT OR IGNORE INTO migration_log(source, migrated_at, backup_path) VALUES(?,?,?)"))
		{
			statement.setString(1, source);
			statement.setLong(2, Instant.now().getEpochSecond());
			statement.setString(3, backup);
			statement.executeUpdate();
		}
	}

	synchronized boolean wasMigrated(String source) throws Exception
	{
		try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM migration_log WHERE source=?"))
		{
			statement.setString(1, source);
			try (ResultSet result = statement.executeQuery()) { return result.next(); }
		}
	}

	@Override public synchronized void close() throws Exception { connection.close(); }
}
