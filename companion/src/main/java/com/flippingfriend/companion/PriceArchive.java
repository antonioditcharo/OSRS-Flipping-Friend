package com.flippingfriend.companion;

import com.flippingfriend.data.Candle;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Keeps the price history the wiki does not.
 *
 * <p>The companion already downloads every price every sixty seconds and then throws the previous
 * snapshot away — {@code state = new MarketState(...)} replaces it wholesale. The API retains a
 * bounded window, so six months from now a competitor starting fresh cannot reconstruct what this
 * account will have. Every seasonality, weekly-cycle and update-response feature depends on history
 * the public feed will not hand over retroactively, which makes this the one piece of work on the
 * plan where waiting has a permanent cost rather than a deferred one.
 *
 * <h2>The reader is defined first, on purpose</h2>
 *
 * <p>A previous incarnation of this project accumulated a 2.5 GB ingestion table that nothing read
 * and then deleted it. The lesson recorded at the time was to define the reader before the writer,
 * not to stop recording — so this class exposes {@link #asSeriesSource} from the outset. That is the
 * same {@link SeriesSource} the live engine and the replay harness already consume, so archived
 * history is usable by {@code PortfolioSimulator}, {@code ShadowTrader} and
 * {@code CandidateFactory} without any of them learning a new interface.
 *
 * <h2>Kept small deliberately</h2>
 *
 * <p>Roughly 4,600 items at twelve five-minute buckets an hour is about 1.3 million rows a day, which
 * is not a size to keep at full resolution forever. Recent history stays at five minutes, where the
 * fill model needs it; beyond {@value #FINE_RETENTION_DAYS} days it is rolled up to hourly, which is
 * the resolution long-horizon features actually use and about a twelfth of the rows.
 *
 * <p>Writes are idempotent: the primary key is {@code (item_id, timestep, ts)} and inserts ignore
 * conflicts, so polling every sixty seconds for a bucket that only changes every five minutes costs
 * nothing and a replayed poll cannot double-count.
 */
final class PriceArchive
{
	static final String FIVE_MINUTE = "5m";
	static final String HOURLY = "1h";

	/** How long five-minute resolution is kept before being rolled up. */
	static final int FINE_RETENTION_DAYS = 7;

	private final Connection connection;

	PriceArchive(Connection connection) throws Exception
	{
		this.connection = connection;
		try (Statement statement = connection.createStatement())
		{
			statement.execute("CREATE TABLE IF NOT EXISTS price_history ("
				+ "item_id INTEGER NOT NULL, timestep TEXT NOT NULL, ts INTEGER NOT NULL, "
				+ "avg_high INTEGER, avg_low INTEGER, high_volume INTEGER, low_volume INTEGER, "
				+ "PRIMARY KEY (item_id, timestep, ts))");
			// Rollup and range reads both scan by time, and without this they scan the table.
			statement.execute("CREATE INDEX IF NOT EXISTS price_history_ts ON price_history(timestep, ts)");
		}
	}

	/**
	 * Appends one poll's worth of bars.
	 *
	 * @param data the {@code data} object from {@code /5m} or {@code /1h}: item id to bar
	 * @param ts   the bucket these bars describe
	 * @return how many rows were new
	 */
	synchronized int record(JsonObject data, String timestep, long ts) throws Exception
	{
		if (data == null || ts <= 0)
		{
			return 0;
		}
		int written = 0;
		boolean autoCommit = connection.getAutoCommit();
		connection.setAutoCommit(false);
		try (PreparedStatement statement = connection.prepareStatement(
			"INSERT OR IGNORE INTO price_history"
				+ "(item_id, timestep, ts, avg_high, avg_low, high_volume, low_volume) "
				+ "VALUES(?,?,?,?,?,?,?)"))
		{
			for (Map.Entry<String, JsonElement> entry : data.entrySet())
			{
				if (!entry.getValue().isJsonObject())
				{
					continue;
				}
				int itemId;
				try
				{
					itemId = Integer.parseInt(entry.getKey());
				}
				catch (NumberFormatException notAnItem)
				{
					continue;
				}
				JsonObject bar = entry.getValue().getAsJsonObject();
				// A null price on one side means nothing traded there, which is itself a liquidity
				// signal. Stored as NULL rather than zero so the two stay distinguishable.
				statement.setInt(1, itemId);
				statement.setString(2, timestep);
				statement.setLong(3, ts);
				setNullableInt(statement, 4, bar, "avgHighPrice");
				setNullableInt(statement, 5, bar, "avgLowPrice");
				statement.setInt(6, intOr(bar, "highPriceVolume", 0));
				statement.setInt(7, intOr(bar, "lowPriceVolume", 0));
				statement.addBatch();
				written++;
			}
			int[] results = statement.executeBatch();
			connection.commit();
			int inserted = 0;
			for (int result : results)
			{
				if (result > 0)
				{
					inserted++;
				}
			}
			return inserted;
		}
		catch (Exception failed)
		{
			connection.rollback();
			throw failed;
		}
		finally
		{
			connection.setAutoCommit(autoCommit);
		}
	}

	/** Bars for one item at one resolution, oldest first. */
	synchronized List<Candle> series(int itemId, String timestep, long fromTs, long toTs)
		throws Exception
	{
		List<Candle> bars = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement(
			"SELECT ts, avg_high, avg_low, high_volume, low_volume FROM price_history "
				+ "WHERE item_id = ? AND timestep = ? AND ts >= ? AND ts <= ? ORDER BY ts"))
		{
			statement.setInt(1, itemId);
			statement.setString(2, timestep);
			statement.setLong(3, fromTs);
			statement.setLong(4, toTs);
			try (ResultSet result = statement.executeQuery())
			{
				while (result.next())
				{
					Integer high = result.getObject(2) == null ? null : result.getInt(2);
					Integer low = result.getObject(3) == null ? null : result.getInt(3);
					bars.add(new Candle(result.getLong(1), high, low, result.getInt(4), result.getInt(5)));
				}
			}
		}
		return bars;
	}

	/**
	 * The archive as the interface everything else already speaks, so history that outlives the API's
	 * own retention is usable by the replay harness and the shadow trader without either of them
	 * learning anything new.
	 */
	SeriesSource asSeriesSource(long fromTs, long toTs)
	{
		return (itemId, timestep) ->
		{
			try
			{
				return series(itemId, timestep, fromTs, toTs);
			}
			catch (Exception unreadable)
			{
				// A read failure is an empty history, which every caller already handles. Throwing
				// here would take down a planning pass over a storage problem.
				return new ArrayList<>();
			}
		};
	}

	/**
	 * Collapses five-minute bars older than {@code beforeTs} into hourly ones and deletes the
	 * originals.
	 *
	 * <p>Volume sums and prices are volume-weighted, which is what the wiki's own hourly bar is, so a
	 * rolled-up bar and a fetched one mean the same thing. A simple mean would quietly overweight the
	 * quiet buckets.
	 *
	 * @return how many fine-grained rows were retired
	 */
	synchronized int rollUp(long beforeTs) throws Exception
	{
		boolean autoCommit = connection.getAutoCommit();
		connection.setAutoCommit(false);
		try
		{
			try (PreparedStatement statement = connection.prepareStatement(
				"INSERT OR IGNORE INTO price_history"
					+ "(item_id, timestep, ts, avg_high, avg_low, high_volume, low_volume) "
					+ "SELECT item_id, ?, (ts / 3600) * 3600, "
					// Volume-weighted, and NULL when nothing traded on that side all hour.
					+ "CASE WHEN SUM(CASE WHEN avg_high IS NULL THEN 0 ELSE high_volume END) > 0 "
					+ "  THEN SUM(COALESCE(avg_high, 0) * high_volume) "
					+ "     / SUM(CASE WHEN avg_high IS NULL THEN 0 ELSE high_volume END) END, "
					+ "CASE WHEN SUM(CASE WHEN avg_low IS NULL THEN 0 ELSE low_volume END) > 0 "
					+ "  THEN SUM(COALESCE(avg_low, 0) * low_volume) "
					+ "     / SUM(CASE WHEN avg_low IS NULL THEN 0 ELSE low_volume END) END, "
					+ "SUM(high_volume), SUM(low_volume) "
					+ "FROM price_history WHERE timestep = ? AND ts < ? "
					+ "GROUP BY item_id, ts / 3600"))
			{
				statement.setString(1, HOURLY);
				statement.setString(2, FIVE_MINUTE);
				statement.setLong(3, beforeTs);
				statement.executeUpdate();
			}
			int retired;
			try (PreparedStatement statement = connection.prepareStatement(
				"DELETE FROM price_history WHERE timestep = ? AND ts < ?"))
			{
				statement.setString(1, FIVE_MINUTE);
				statement.setLong(2, beforeTs);
				retired = statement.executeUpdate();
			}
			connection.commit();
			return retired;
		}
		catch (Exception failed)
		{
			connection.rollback();
			throw failed;
		}
		finally
		{
			connection.setAutoCommit(autoCommit);
		}
	}

	synchronized long rowCount() throws Exception
	{
		try (Statement statement = connection.createStatement();
			ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM price_history"))
		{
			return result.next() ? result.getLong(1) : 0;
		}
	}

	/** Oldest bar on record, or 0 when empty. How much history has actually accumulated. */
	synchronized long earliest() throws Exception
	{
		try (Statement statement = connection.createStatement();
			ResultSet result = statement.executeQuery("SELECT MIN(ts) FROM price_history"))
		{
			return result.next() ? result.getLong(1) : 0;
		}
	}

	private static void setNullableInt(PreparedStatement statement, int index, JsonObject bar,
		String field) throws Exception
	{
		if (!bar.has(field) || bar.get(field).isJsonNull())
		{
			statement.setNull(index, java.sql.Types.INTEGER);
			return;
		}
		statement.setInt(index, bar.get(field).getAsInt());
	}

	private static int intOr(JsonObject bar, String field, int fallback)
	{
		return bar.has(field) && !bar.get(field).isJsonNull() ? bar.get(field).getAsInt() : fallback;
	}
}
