package com.flippingfriend.companion;
import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.core.OfferLifecycleProjection;
import com.flippingfriend.core.OfferLifecycleReducer;
import com.flippingfriend.core.OfferLifecycleState;
import com.flippingfriend.core.OfferLifecycleTransition;
import com.flippingfriend.core.PositionAccountingEffect;
import com.flippingfriend.core.PositionAccountingEffectType;
import com.flippingfriend.core.PositionConsistencyState;
import com.flippingfriend.core.PositionProjection;
import com.flippingfriend.core.AccountSnapshot;
import com.flippingfriend.core.PositionSnapshot;

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
	enum EventAcceptance { NEW, DUPLICATE }
	static final class ProjectedEventAcceptance
	{
		final EventAcceptance acceptance;
		final OfferLifecycleTransition transition;
		final PositionProjection positionProjection;
		final BuyLimitProjection buyLimitProjection;
		ProjectedEventAcceptance(EventAcceptance acceptance, OfferLifecycleTransition transition,
			PositionProjection positionProjection, BuyLimitProjection buyLimitProjection)
		{
			this.acceptance = acceptance;
			this.transition = transition;
			this.positionProjection = positionProjection;
			this.buyLimitProjection = buyLimitProjection;
		}
	}
	/**
	 * How much of a model payload to read back when only its name is wanted.
	 * <p>
	 * Comfortably longer than any model name, and short enough that reading it for every row costs
	 * kilobytes rather than the 62MB the payloads themselves come to.
	 */
	private static final int MODEL_NAME_LIMIT = 64;

	@FunctionalInterface
	interface TransactionCheckpoint
	{
		void reached(String checkpoint) throws Exception;
	}
	private static final TransactionCheckpoint NO_CHECKPOINT = checkpoint -> { };
	private final Connection connection;
	private final TransactionCheckpoint checkpoint;

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
	        this(database, false, NO_CHECKPOINT);
	}

	SqliteStore(Path database, TransactionCheckpoint checkpoint) throws Exception
	{
	        this(database, false, checkpoint);
	}

	private SqliteStore(Path database, boolean readOnly) throws Exception
	{
	        this(database, readOnly, NO_CHECKPOINT);
	}

	private SqliteStore(Path database, boolean readOnly, TransactionCheckpoint checkpoint) throws Exception
	{
	        if (checkpoint == null) throw new IllegalArgumentException("checkpoint is required");
	        this.checkpoint = checkpoint;
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
			statement.execute("CREATE TABLE IF NOT EXISTS event_log (id INTEGER PRIMARY KEY, observed_at INTEGER NOT NULL, correlation_id TEXT NOT NULL, event_id TEXT, event_type TEXT NOT NULL, payload TEXT NOT NULL)");
			// market_observation is not created any more, and is dropped below if an older database
			// still has it. Nothing ever read a row from it: every SELECT in all three modules is
			// nine queries and none name the table.
			statement.execute("CREATE TABLE IF NOT EXISTS portfolio_plan (id INTEGER PRIMARY KEY, created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL, correlation_id TEXT NOT NULL, payload TEXT NOT NULL)");
			statement.execute("CREATE TABLE IF NOT EXISTS model_snapshot (version INTEGER PRIMARY KEY, created_at INTEGER NOT NULL, payload TEXT NOT NULL)");
			statement.execute("CREATE TABLE IF NOT EXISTS migration_log (source TEXT PRIMARY KEY, migrated_at INTEGER NOT NULL, backup_path TEXT NOT NULL)");
			statement.execute("CREATE TABLE IF NOT EXISTS gate_result (id INTEGER PRIMARY KEY, evaluated_at INTEGER NOT NULL, resolution TEXT NOT NULL, gate TEXT NOT NULL, passed INTEGER NOT NULL, measured TEXT NOT NULL, payload TEXT NOT NULL)");
			statement.execute("CREATE TABLE IF NOT EXISTS execution_stat (item_id INTEGER PRIMARY KEY, completed INTEGER NOT NULL DEFAULT 0, observed INTEGER NOT NULL DEFAULT 0, fill_minutes REAL NOT NULL DEFAULT 0, predicted_minutes REAL NOT NULL DEFAULT 0)");
			statement.execute("CREATE TABLE IF NOT EXISTS counted_offer (identity TEXT PRIMARY KEY, counted_at INTEGER NOT NULL)");
			statement.execute("CREATE TABLE IF NOT EXISTS offer_projection (slot INTEGER PRIMARY KEY, lifecycle_state TEXT NOT NULL, offer_identity TEXT, session_id TEXT, last_sequence INTEGER NOT NULL, last_observed_at INTEGER NOT NULL, item_id INTEGER NOT NULL, item_name TEXT, buying INTEGER NOT NULL, price INTEGER NOT NULL, total_quantity INTEGER NOT NULL, filled_quantity INTEGER NOT NULL, spent INTEGER NOT NULL, recommendation_id TEXT, source_event_id TEXT, transition_reason TEXT NOT NULL, updated_at INTEGER NOT NULL)");
			statement.execute("CREATE TABLE IF NOT EXISTS position_projection (item_id INTEGER PRIMARY KEY, item_name TEXT, quantity INTEGER NOT NULL, total_cost INTEGER NOT NULL, cost_known INTEGER NOT NULL, opened_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, source_event_id TEXT, source_offer_identity TEXT, consistency_state TEXT NOT NULL, consistency_reason TEXT)");
			statement.execute("CREATE TABLE IF NOT EXISTS position_event (id INTEGER PRIMARY KEY AUTOINCREMENT, source_event_id TEXT, source_offer_identity TEXT, observed_at INTEGER NOT NULL, effect_type TEXT NOT NULL, item_id INTEGER NOT NULL, item_name TEXT, quantity INTEGER NOT NULL, acquisition_cost INTEGER NOT NULL, applied_cost_basis INTEGER NOT NULL DEFAULT 0, transition_reason TEXT, created_at INTEGER NOT NULL)");
			statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS position_event_source_unique ON position_event(source_event_id) WHERE source_event_id IS NOT NULL AND source_event_id <> ''");
			statement.execute("CREATE TABLE IF NOT EXISTS reconciliation_event (id INTEGER PRIMARY KEY AUTOINCREMENT, snapshot_correlation_id TEXT, snapshot_observed_at INTEGER NOT NULL, subject_type TEXT NOT NULL, subject_id TEXT NOT NULL, outcome TEXT NOT NULL, previous_state TEXT, resulting_state TEXT, reason TEXT NOT NULL, created_at INTEGER NOT NULL)");
			statement.execute("CREATE TABLE IF NOT EXISTS buy_limit_projection (item_id INTEGER PRIMARY KEY, started_at INTEGER NOT NULL, used_quantity INTEGER NOT NULL, updated_at INTEGER NOT NULL, source_event_id TEXT, source_snapshot_correlation_id TEXT)");
			statement.execute("CREATE TABLE IF NOT EXISTS buy_limit_event (id INTEGER PRIMARY KEY AUTOINCREMENT, item_id INTEGER NOT NULL, started_at INTEGER NOT NULL, quantity_delta INTEGER NOT NULL, resulting_used_quantity INTEGER NOT NULL, source_type TEXT NOT NULL, source_event_id TEXT, source_snapshot_correlation_id TEXT, observed_at INTEGER NOT NULL, created_at INTEGER NOT NULL)");
		}

		if (!hasColumn("event_log", "event_id"))
		{
			try (Statement statement = connection.createStatement())
			{
				statement.execute("ALTER TABLE event_log ADD COLUMN event_id TEXT");
			}
		}
		try (Statement statement = connection.createStatement())
		{
			statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS event_log_event_id_unique "
				+ "ON event_log(event_id) WHERE event_id IS NOT NULL AND event_id <> ''");
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
		// Every settled offer's time on the book, and the prediction it carried, whether or not it
		// finished.
		//
		// The columns above count only completions, and a completion is not a random sample of
		// offers -- it is the subset that happened to be quick. Calibrating a duration model on them
		// tells it the market is faster than it is, which is the same mistake the duration gate made
		// before it was rewritten. On the account this was written against the effect was not subtle:
		// 531 completions averaged 2.8 minutes against 27 predicted, while the 855 offers that did
		// not complete contributed nothing at all, so the naive ratio came out at 0.10 and would have
		// had the planner believe every fill takes a tenth of the time it does.
		//
		// An offer that was cancelled at forty minutes is not a missing observation. It is a real
		// one, censored: whatever the true fill time was, it was longer than forty minutes. Summing
		// the time every settled offer actually spent on the book, against what every one of them was
		// predicted to take, compares like with like and still understates rather than overstates.
		for (String column : new String[]{"paired_fill_minutes REAL NOT NULL DEFAULT 0",
			"paired_completed INTEGER NOT NULL DEFAULT 0",
			"open_minutes REAL NOT NULL DEFAULT 0",
			"open_predicted_minutes REAL NOT NULL DEFAULT 0",
			"open_observed INTEGER NOT NULL DEFAULT 0"})
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

	private boolean hasColumn(String table, String column) throws Exception
	{
		try (Statement statement = connection.createStatement();
			ResultSet rows = statement.executeQuery("PRAGMA table_info(" + table + ")"))
		{
			while (rows.next())
			{
				if (column.equals(rows.getString("name"))) return true;
			}
		}
		return false;
	}
	/** Atomically accepts one canonical offer event and advances its current slot projection. */
	synchronized ProjectedEventAcceptance recordAndProjectOffer(OfferEvent event, String payload) throws Exception
	{
		if (event == null) throw new IllegalArgumentException("event is required");
		boolean autoCommit = connection.getAutoCommit();
		connection.setAutoCommit(false);
		try
		{
			EventAcceptance acceptance = recordEvent(event.getObservedAt(), event.getCorrelationId(),
				event.getEventId(), event.getEventType(), payload);
			checkpoint.reached("OFFER_EVENT_RECORDED");
			if (acceptance == EventAcceptance.DUPLICATE)
			{
				connection.commit();
				return new ProjectedEventAcceptance(acceptance, null, null, null);
			}
			OfferLifecycleProjection previous = offerProjection(event.getSlot());
			OfferLifecycleTransition transition = OfferLifecycleReducer.apply(previous, event);
			writeProjection(transition.getProjection(), event.getEventId(), transition.getReason());
			checkpoint.reached("OFFER_PROJECTION_WRITTEN");
			PositionProjection position = applyPositionEffect(transition.getAccountingEffect());
			checkpoint.reached("POSITION_ACCOUNTING_APPLIED");
			BuyLimitProjection buyLimit = applyBuyLimitEffect(event, transition);
			checkpoint.reached("BUY_LIMIT_ACCOUNTING_APPLIED");
			connection.commit();
			return new ProjectedEventAcceptance(acceptance, transition, position, buyLimit);
		}
		catch (Exception failure)
		{
			connection.rollback();
			throw failure;
		}
		finally
		{
			connection.setAutoCommit(autoCommit);
		}
	}

	synchronized List<OfferLifecycleProjection> offerProjections() throws Exception
	{
		List<OfferLifecycleProjection> result = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement(
			"SELECT lifecycle_state, slot, offer_identity, session_id, last_sequence, last_observed_at, item_id, item_name, buying, price, total_quantity, filled_quantity, spent, recommendation_id FROM offer_projection ORDER BY slot");
			ResultSet rows = statement.executeQuery())
		{
			while (rows.next()) result.add(readProjection(rows));
		}
		return result;
	}

	private OfferLifecycleProjection offerProjection(int slot) throws Exception
	{
		try (PreparedStatement statement = connection.prepareStatement(
			"SELECT lifecycle_state, slot, offer_identity, session_id, last_sequence, last_observed_at, item_id, item_name, buying, price, total_quantity, filled_quantity, spent, recommendation_id FROM offer_projection WHERE slot=?"))
		{
			statement.setInt(1, slot);
			try (ResultSet row = statement.executeQuery()) { return row.next() ? readProjection(row) : null; }
		}
	}

	private static OfferLifecycleProjection readProjection(ResultSet row) throws Exception
	{
		return OfferLifecycleProjection.restore(OfferLifecycleState.valueOf(row.getString(1)),
			row.getInt(2), row.getString(3), row.getString(4), row.getLong(5), row.getLong(6),
			row.getInt(7), row.getString(8), row.getInt(9) != 0, row.getInt(10), row.getInt(11),
			row.getInt(12), row.getLong(13), row.getString(14));
	}

	private void writeProjection(OfferLifecycleProjection p, String eventId, String reason) throws Exception
	{
		try (PreparedStatement s = connection.prepareStatement(
			"INSERT INTO offer_projection(slot,lifecycle_state,offer_identity,session_id,last_sequence,last_observed_at,item_id,item_name,buying,price,total_quantity,filled_quantity,spent,recommendation_id,source_event_id,transition_reason,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(slot) DO UPDATE SET lifecycle_state=excluded.lifecycle_state,offer_identity=excluded.offer_identity,session_id=excluded.session_id,last_sequence=excluded.last_sequence,last_observed_at=excluded.last_observed_at,item_id=excluded.item_id,item_name=excluded.item_name,buying=excluded.buying,price=excluded.price,total_quantity=excluded.total_quantity,filled_quantity=excluded.filled_quantity,spent=excluded.spent,recommendation_id=excluded.recommendation_id,source_event_id=excluded.source_event_id,transition_reason=excluded.transition_reason,updated_at=excluded.updated_at"))
		{
			s.setInt(1,p.getSlot()); s.setString(2,p.getState().name()); s.setString(3,p.getOfferIdentity());
			s.setString(4,p.getSessionId()); s.setLong(5,p.getLastSequence()); s.setLong(6,p.getLastObservedAt());
			s.setInt(7,p.getItemId()); s.setString(8,p.getItemName()); s.setInt(9,p.isBuying()?1:0);
			s.setInt(10,p.getPrice()); s.setInt(11,p.getTotalQuantity()); s.setInt(12,p.getFilledQuantity());
			s.setLong(13,p.getSpent()); s.setString(14,p.getRecommendationId()); s.setString(15,eventId);
			s.setString(16,reason); s.setLong(17,Instant.now().getEpochSecond()); s.executeUpdate();
		}
	}

	synchronized List<PositionProjection> positionProjections() throws Exception
	{
		List<PositionProjection> result = new ArrayList<>();
		try (PreparedStatement s = connection.prepareStatement("SELECT item_id,item_name,quantity,total_cost,cost_known,opened_at,updated_at,source_event_id,source_offer_identity,consistency_state,consistency_reason FROM position_projection ORDER BY item_id"); ResultSet rows = s.executeQuery())
		{
			while (rows.next()) result.add(readPosition(rows));
		}
		return result;
	}

	synchronized int positionEventCount() throws Exception
	{
		try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM position_event"))
		{ return r.next() ? r.getInt(1) : 0; }
	}

	private PositionProjection applyPositionEffect(PositionAccountingEffect effect) throws Exception
	{
		if (effect == null || effect.getType() == PositionAccountingEffectType.NONE) return null;
		PositionProjection current = effect.getItemId() > 0 ? positionProjection(effect.getItemId()) : null;
		long appliedCost = 0;
		PositionProjection result = current;
		String reason = effect.getReason();
		switch (effect.getType())
		{
			case ACQUIRE:
				if (current == null)
				{
					result = PositionProjection.ready(effect.getItemId(), effect.getItemName(), effect.getQuantity(), effect.getAcquisitionCost(), effect.getObservedAt(), effect.getSourceEventId(), effect.getOfferIdentity());
				}
				else if (current.getConsistencyState() == PositionConsistencyState.READY && current.isCostKnown())
				{
					result = PositionProjection.ready(current.getItemId(), effect.getItemName() == null ? current.getItemName() : effect.getItemName(), current.getQuantity() + effect.getQuantity(), current.getTotalCost() + effect.getAcquisitionCost(), current.getOpenedAt(), effect.getObservedAt(), effect.getSourceEventId(), effect.getOfferIdentity());
				}
				else reason = "acquisition encountered a position requiring reconciliation";
				break;
			case DISPOSE:
				if (current == null) reason = "disposal has no durable position";
				else if (current.getConsistencyState() != PositionConsistencyState.READY) reason = current.getConsistencyReason();
				else if (effect.getQuantity() > current.getQuantity()) reason = "disposal exceeds durable position quantity";
				else
				{
					appliedCost = current.getTotalCost() * effect.getQuantity() / current.getQuantity();
					if (effect.getQuantity() == current.getQuantity()) result = null;
					else result = PositionProjection.ready(current.getItemId(), current.getItemName(), current.getQuantity() - effect.getQuantity(), current.getTotalCost() - appliedCost, current.getOpenedAt(), effect.getObservedAt(), effect.getSourceEventId(), effect.getOfferIdentity());
				}
				break;
			case RECONCILE:
				reason = effect.getReason();
				break;
			default: break;
		}
		appendPositionEvent(effect, appliedCost, reason);
		if (effect.getType() == PositionAccountingEffectType.RECONCILE || reason != null)
		{
			if (effect.getItemId() <= 0) return null;
			result = PositionProjection.reconcile(effect.getItemId(), effect.getItemName(), current == null ? 0 : current.getQuantity(), current == null ? 0 : current.getTotalCost(), current != null && current.isCostKnown(), current == null ? effect.getObservedAt() : current.getOpenedAt(), effect.getObservedAt(), effect.getSourceEventId(), effect.getOfferIdentity(), reason == null ? "accounting reconciliation required" : reason);
		}
		if (result == null)
		{
			if (current != null) deletePosition(current.getItemId());
		}
		else writePosition(result);
		return result;
	}

	private PositionProjection positionProjection(int itemId) throws Exception
	{
		try (PreparedStatement s = connection.prepareStatement("SELECT item_id,item_name,quantity,total_cost,cost_known,opened_at,updated_at,source_event_id,source_offer_identity,consistency_state,consistency_reason FROM position_projection WHERE item_id=?"))
		{
			s.setInt(1,itemId); try (ResultSet r=s.executeQuery()) { return r.next()?readPosition(r):null; }
		}
	}
	private static PositionProjection readPosition(ResultSet r) throws Exception
	{
		return PositionProjection.restore(r.getInt(1),r.getString(2),r.getInt(3),r.getLong(4),r.getInt(5)!=0,r.getLong(6),r.getLong(7),r.getString(8),r.getString(9),PositionConsistencyState.valueOf(r.getString(10)),r.getString(11));
	}
	private void writePosition(PositionProjection p) throws Exception
	{
		try (PreparedStatement s=connection.prepareStatement("INSERT INTO position_projection(item_id,item_name,quantity,total_cost,cost_known,opened_at,updated_at,source_event_id,source_offer_identity,consistency_state,consistency_reason) VALUES(?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(item_id) DO UPDATE SET item_name=excluded.item_name,quantity=excluded.quantity,total_cost=excluded.total_cost,cost_known=excluded.cost_known,opened_at=excluded.opened_at,updated_at=excluded.updated_at,source_event_id=excluded.source_event_id,source_offer_identity=excluded.source_offer_identity,consistency_state=excluded.consistency_state,consistency_reason=excluded.consistency_reason"))
		{
			s.setInt(1,p.getItemId());s.setString(2,p.getItemName());s.setInt(3,p.getQuantity());s.setLong(4,p.getTotalCost());s.setInt(5,p.isCostKnown()?1:0);s.setLong(6,p.getOpenedAt());s.setLong(7,p.getUpdatedAt());s.setString(8,p.getSourceEventId());s.setString(9,p.getSourceOfferIdentity());s.setString(10,p.getConsistencyState().name());s.setString(11,p.getConsistencyReason());s.executeUpdate();
		}
	}
	private void deletePosition(int itemId) throws Exception
	{
		try (PreparedStatement s=connection.prepareStatement("DELETE FROM position_projection WHERE item_id=?")){s.setInt(1,itemId);s.executeUpdate();}
	}
	private void appendPositionEvent(PositionAccountingEffect e,long appliedCost,String reason) throws Exception
	{
		try (PreparedStatement s=connection.prepareStatement("INSERT OR IGNORE INTO position_event(source_event_id,source_offer_identity,observed_at,effect_type,item_id,item_name,quantity,acquisition_cost,applied_cost_basis,transition_reason,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)"))
		{
			s.setString(1,e.getSourceEventId());s.setString(2,e.getOfferIdentity());s.setLong(3,e.getObservedAt());s.setString(4,e.getType().name());s.setInt(5,e.getItemId());s.setString(6,e.getItemName());s.setInt(7,e.getQuantity());s.setLong(8,e.getAcquisitionCost());s.setLong(9,appliedCost);s.setString(10,reason);s.setLong(11,Instant.now().getEpochSecond());s.executeUpdate();
		}
	}

	synchronized SnapshotReconciliationResult recordAndReconcileAccount(AccountSnapshot snapshot,
		PositionStateView view, String payload) throws Exception
	{
		if (snapshot == null || view == null) throw new IllegalArgumentException("snapshot and view are required");
		boolean autoCommit = connection.getAutoCommit();
		connection.setAutoCommit(false);
		try
		{
			recordEvent(snapshot.getObservedAt(), snapshot.getCorrelationId(), "ACCOUNT_STATE", payload);
			checkpoint.reached("ACCOUNT_EVENT_RECORDED");
			SnapshotReconciliationResult result = new SnapshotReconciliationResult();
			Map<Integer, PositionSnapshot> seen = view.byItem();
			for (PositionSnapshot incoming : seen.values()) reconcileSnapshotPosition(snapshot, incoming, result);
			for (PositionProjection existing : positionProjections())
			{
				if (!seen.containsKey(existing.getItemId()))
				{
					audit(snapshot, existing.getItemId(), "UNCHANGED", existing.getConsistencyState().name(), existing.getConsistencyState().name(), "snapshot absence is not authoritative disposal evidence");
					result.unchanged(existing);
				}
			}
			checkpoint.reached("SNAPSHOT_POSITIONS_RECONCILED");
			reconcileBuyLimits(snapshot, result);
			checkpoint.reached("SNAPSHOT_BUY_LIMITS_RECONCILED");
			connection.commit();
			return result;
		}
		catch (Exception failure)
		{
			connection.rollback();
			throw failure;
		}
		finally { connection.setAutoCommit(autoCommit); }
	}

	private void reconcileSnapshotPosition(AccountSnapshot snapshot, PositionSnapshot incoming,
		SnapshotReconciliationResult result) throws Exception
	{
		PositionProjection current = positionProjection(incoming.getItemId());
		PositionProjection next = current;
		String outcome;
		String reason;
		if (current == null)
		{
			if (incoming.isCostKnown())
			{
				next = PositionProjection.ready(incoming.getItemId(), incoming.getItemName(), incoming.getQuantity(), incoming.getTotalCost(), incoming.getOpenedAt(), snapshot.getObservedAt(), null, null);
				outcome = "ADOPTED"; reason = "snapshot supplied positive known-cost position evidence";
				result.adopted(next);
			}
			else
			{
				next = PositionProjection.reconcile(incoming.getItemId(), incoming.getItemName(), incoming.getQuantity(), 0, false, incoming.getOpenedAt(), snapshot.getObservedAt(), null, null, "snapshot position has unknown cost basis");
				outcome = "RECONCILE_REQUIRED"; reason = next.getConsistencyReason(); result.reconcile(next);
			}
		}
		else if (incoming.getQuantity() < current.getQuantity())
		{
			outcome = "UNCHANGED"; reason = "snapshot quantity is lower; absence is not authoritative disposal evidence"; result.unchanged(current);
		}
		else if (incoming.getQuantity() == current.getQuantity() && incoming.isCostKnown()
			&& incoming.getTotalCost() == current.getTotalCost())
		{
			if (current.getConsistencyState() == PositionConsistencyState.RECONCILE)
			{
				next = PositionProjection.ready(current.getItemId(), incoming.getItemName(), incoming.getQuantity(), incoming.getTotalCost(), current.getOpenedAt(), snapshot.getObservedAt(), current.getSourceEventId(), current.getSourceOfferIdentity());
				outcome = "REPAIRED"; reason = "snapshot confirmed durable quantity and cost basis"; result.repaired(next);
			}
			else { outcome = "CONFIRMED"; reason = "snapshot matches durable quantity and cost basis"; result.confirmed(current); }
		}
		else if (incoming.getQuantity() > current.getQuantity() && incoming.isCostKnown())
		{
			next = PositionProjection.ready(current.getItemId(), incoming.getItemName(), incoming.getQuantity(), incoming.getTotalCost(), current.getOpenedAt(), snapshot.getObservedAt(), current.getSourceEventId(), current.getSourceOfferIdentity());
			outcome = "REPAIRED"; reason = "snapshot supplied complete cost evidence for additional quantity"; result.repaired(next);
		}
		else
		{
			String conflict = incoming.getQuantity() > current.getQuantity()
				? "snapshot contains additional quantity without known cost basis"
				: "snapshot and durable cost basis disagree";
			next = PositionProjection.reconcile(current.getItemId(), current.getItemName(), current.getQuantity(), current.getTotalCost(), current.isCostKnown(), current.getOpenedAt(), snapshot.getObservedAt(), current.getSourceEventId(), current.getSourceOfferIdentity(), conflict);
			outcome = "RECONCILE_REQUIRED"; reason = conflict; result.reconcile(next);
		}
		if (next != current) writePosition(next);
		audit(snapshot, incoming.getItemId(), outcome, current == null ? null : current.getConsistencyState().name(), next.getConsistencyState().name(), reason);
	}

	private void audit(AccountSnapshot snapshot, int itemId, String outcome, String before,
		String after, String reason) throws Exception
	{
		try (PreparedStatement s = connection.prepareStatement("INSERT INTO reconciliation_event(snapshot_correlation_id,snapshot_observed_at,subject_type,subject_id,outcome,previous_state,resulting_state,reason,created_at) VALUES(?,?,?,?,?,?,?,?,?)"))
		{
			s.setString(1,snapshot.getCorrelationId()); s.setLong(2,snapshot.getObservedAt());
			s.setString(3,"POSITION"); s.setString(4,String.valueOf(itemId)); s.setString(5,outcome);
			s.setString(6,before); s.setString(7,after); s.setString(8,reason);
			s.setLong(9,Instant.now().getEpochSecond()); s.executeUpdate();
		}
	}

	synchronized int reconciliationEventCount() throws Exception
	{
		try (Statement s=connection.createStatement(); ResultSet r=s.executeQuery("SELECT COUNT(*) FROM reconciliation_event")) { return r.next()?r.getInt(1):0; }
	}

	private BuyLimitProjection applyBuyLimitEffect(OfferEvent event,
		OfferLifecycleTransition transition) throws Exception
	{
		if (event == null || transition == null || !transition.isAccepted()
			|| !event.isBuying() || event.getFilledQuantity() <= 0) return null;
		PositionAccountingEffect effect = transition.getAccountingEffect();
		int quantity = effect != null && effect.getType() == PositionAccountingEffectType.ACQUIRE
			? effect.getQuantity() : event.getFilledQuantity();
		if (quantity <= 0) return null;
		BuyLimitProjection current = buyLimitProjection(event.getItemId());
		long started = current == null || current.hasExpired(event.getObservedAt())
			? event.getObservedAt() : current.getStartedAt();
		int prior = current == null || current.hasExpired(event.getObservedAt())
			? 0 : current.getUsedQuantity();
		BuyLimitProjection next = new BuyLimitProjection(event.getItemId(), started,
			prior + quantity, event.getObservedAt(), event.getEventId(), null);
		writeBuyLimit(next);
		appendBuyLimitEvent(next, quantity, "CANONICAL_ACQUIRE", event.getEventId(),
			null, event.getObservedAt());
		return next;
	}

	private void reconcileBuyLimits(AccountSnapshot snapshot, SnapshotReconciliationResult result) throws Exception
	{
		for (Map.Entry<Integer,Integer> entry : snapshot.getBuyLimitUsed().entrySet())
		{
			int reported = entry.getValue() == null ? 0 : entry.getValue();
			if (entry.getKey() == null || entry.getKey() <= 0 || reported <= 0) continue;
			BuyLimitProjection current = buyLimitProjection(entry.getKey());
			long started = current == null || current.hasExpired(snapshot.getObservedAt()) ? snapshot.getObservedAt() : current.getStartedAt();
			int prior = current == null || current.hasExpired(snapshot.getObservedAt()) ? 0 : current.getUsedQuantity();
			if (reported > prior)
			{
				BuyLimitProjection next = new BuyLimitProjection(entry.getKey(), started, reported, snapshot.getObservedAt(), current == null ? null : current.getSourceEventId(), snapshot.getCorrelationId());
				writeBuyLimit(next); appendBuyLimitEvent(next, reported-prior, "SNAPSHOT_FLOOR", null, snapshot.getCorrelationId(), snapshot.getObservedAt()); result.buyLimitRaised();
			}
			else result.buyLimitUnchanged();
		}
	}

	synchronized List<BuyLimitProjection> buyLimitProjections() throws Exception
	{
		List<BuyLimitProjection> rows=new ArrayList<>();
		try(PreparedStatement s=connection.prepareStatement("SELECT item_id,started_at,used_quantity,updated_at,source_event_id,source_snapshot_correlation_id FROM buy_limit_projection ORDER BY item_id");ResultSet r=s.executeQuery())
		{while(r.next()) rows.add(new BuyLimitProjection(r.getInt(1),r.getLong(2),r.getInt(3),r.getLong(4),r.getString(5),r.getString(6)));}
		return rows;
	}
	private BuyLimitProjection buyLimitProjection(int itemId) throws Exception
	{
		try(PreparedStatement s=connection.prepareStatement("SELECT item_id,started_at,used_quantity,updated_at,source_event_id,source_snapshot_correlation_id FROM buy_limit_projection WHERE item_id=?")){s.setInt(1,itemId);try(ResultSet r=s.executeQuery()){return r.next()?new BuyLimitProjection(r.getInt(1),r.getLong(2),r.getInt(3),r.getLong(4),r.getString(5),r.getString(6)):null;}}
	}
	private void writeBuyLimit(BuyLimitProjection p) throws Exception
	{
		try(PreparedStatement s=connection.prepareStatement("INSERT INTO buy_limit_projection(item_id,started_at,used_quantity,updated_at,source_event_id,source_snapshot_correlation_id) VALUES(?,?,?,?,?,?) ON CONFLICT(item_id) DO UPDATE SET started_at=excluded.started_at,used_quantity=excluded.used_quantity,updated_at=excluded.updated_at,source_event_id=excluded.source_event_id,source_snapshot_correlation_id=excluded.source_snapshot_correlation_id")){s.setInt(1,p.getItemId());s.setLong(2,p.getStartedAt());s.setInt(3,p.getUsedQuantity());s.setLong(4,p.getUpdatedAt());s.setString(5,p.getSourceEventId());s.setString(6,p.getSourceSnapshotCorrelationId());s.executeUpdate();}
	}
	private void appendBuyLimitEvent(BuyLimitProjection p,int delta,String type,String eventId,String snapshotId,long observedAt) throws Exception
	{
		try(PreparedStatement s=connection.prepareStatement("INSERT INTO buy_limit_event(item_id,started_at,quantity_delta,resulting_used_quantity,source_type,source_event_id,source_snapshot_correlation_id,observed_at,created_at) VALUES(?,?,?,?,?,?,?,?,?)")){s.setInt(1,p.getItemId());s.setLong(2,p.getStartedAt());s.setInt(3,delta);s.setInt(4,p.getUsedQuantity());s.setString(5,type);s.setString(6,eventId);s.setString(7,snapshotId);s.setLong(8,observedAt);s.setLong(9,Instant.now().getEpochSecond());s.executeUpdate();}
	}
	synchronized int buyLimitEventCount() throws Exception{try(Statement s=connection.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM buy_limit_event")){return r.next()?r.getInt(1):0;}}
	synchronized int eventCount(String eventType) throws Exception
	{
		try (PreparedStatement s=connection.prepareStatement("SELECT COUNT(*) FROM event_log WHERE event_type=?"))
		{ s.setString(1,eventType); try(ResultSet r=s.executeQuery()){return r.next()?r.getInt(1):0;} }
	}
	synchronized long[] positionEventTotals() throws Exception
	{
		try (Statement s=connection.createStatement(); ResultSet r=s.executeQuery(
			"SELECT COALESCE(SUM(CASE WHEN effect_type='ACQUIRE' THEN quantity ELSE 0 END),0)," +
			"COALESCE(SUM(acquisition_cost),0),COALESCE(SUM(CASE WHEN effect_type='DISPOSE' THEN quantity ELSE 0 END),0),COALESCE(SUM(applied_cost_basis),0) FROM position_event"))
		{ return r.next()?new long[]{r.getLong(1),r.getLong(2),r.getLong(3),r.getLong(4)}:new long[4]; }
	}

	synchronized void recordEvent(long observedAt, String correlationId, String eventType, String payload) throws Exception
	{
		recordEvent(observedAt, correlationId, null, eventType, payload);
	}
	synchronized EventAcceptance recordEvent(long observedAt, String correlationId, String eventId,
		String eventType, String payload) throws Exception
	{
		String canonicalId = eventId == null || eventId.trim().isEmpty() ? null : eventId;
		String sql = canonicalId == null
			? "INSERT INTO event_log(observed_at, correlation_id, event_id, event_type, payload) VALUES(?,?,?,?,?)"
			: "INSERT INTO event_log(observed_at, correlation_id, event_id, event_type, payload) VALUES(?,?,?,?,?) ON CONFLICT(event_id) WHERE event_id IS NOT NULL AND event_id <> '' DO NOTHING";
		try (PreparedStatement statement = connection.prepareStatement(sql))
		{
			statement.setLong(1, observedAt); statement.setString(2, correlationId);
			statement.setString(3, canonicalId); statement.setString(4, eventType); statement.setString(5, payload);
			return statement.executeUpdate() > 0 ? EventAcceptance.NEW : EventAcceptance.DUPLICATE;
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
	/** The completions-only form, for callers that have no censored observation to add. */
	synchronized void recordExecution(int itemId, boolean completed, double fillMinutes,
		double predictedMinutes) throws Exception
	{
		recordExecution(itemId, completed, fillMinutes, predictedMinutes, 0, 0);
	}

	/**
	 * @param openMinutes how long the offer was on the book, completed or not, or 0 when unknown
	 */
	synchronized void recordExecution(int itemId, boolean completed, double fillMinutes,
		double predictedMinutes, double openMinutes, double openPredictedMinutes) throws Exception
	{
		int openObserved = openMinutes > 0 && openPredictedMinutes > 0 ? 1 : 0;
		try (PreparedStatement statement = connection.prepareStatement(
			"INSERT INTO execution_stat(item_id, completed, observed, fill_minutes, predicted_minutes, "
				+ "paired_fill_minutes, paired_completed, open_minutes, open_predicted_minutes, "
				+ "open_observed) "
				+ "VALUES(?,?,1,?,?,?,?,?,?,?) "
				+ "ON CONFLICT(item_id) DO UPDATE SET completed = completed + excluded.completed, "
				+ "observed = observed + 1, fill_minutes = fill_minutes + excluded.fill_minutes, "
				+ "predicted_minutes = predicted_minutes + excluded.predicted_minutes, "
				+ "paired_fill_minutes = paired_fill_minutes + excluded.paired_fill_minutes, "
				+ "paired_completed = paired_completed + excluded.paired_completed, "
				+ "open_minutes = open_minutes + excluded.open_minutes, "
				+ "open_predicted_minutes = open_predicted_minutes + excluded.open_predicted_minutes, "
				+ "open_observed = open_observed + excluded.open_observed"))
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
			statement.setDouble(7, openObserved == 1 ? openMinutes : 0);
			statement.setDouble(8, openObserved == 1 ? openPredictedMinutes : 0);
			statement.setInt(9, openObserved);
			statement.executeUpdate();
		}
	}

	/** Realised completion rate and mean fill time per item, for calibrating the fill model. */
	synchronized Map<Integer, ExecutionStat> executionStats() throws Exception
	{
		Map<Integer, ExecutionStat> stats = new HashMap<>();
		try (PreparedStatement statement = connection.prepareStatement(
			"SELECT item_id, completed, observed, fill_minutes, predicted_minutes, "
				+ "paired_fill_minutes, paired_completed, open_minutes, open_predicted_minutes, "
				+ "open_observed FROM execution_stat");
			ResultSet result = statement.executeQuery())
		{
			while (result.next())
			{
				stats.put(result.getInt(1), new ExecutionStat(result.getInt(2), result.getInt(3),
					result.getDouble(4), result.getDouble(5), result.getDouble(6), result.getInt(7),
					result.getDouble(8), result.getDouble(9), result.getInt(10)));
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

		/** Time on the book across every settled offer, completed or not. See the migration note. */
		final double openMinutes;
		final double openPredictedMinutes;
		final int openObserved;

		ExecutionStat(int completed, int observed, double fillMinutes, double predictedMinutes,
			double pairedFillMinutes, int pairedCompleted)
		{
			this(completed, observed, fillMinutes, predictedMinutes, pairedFillMinutes,
				pairedCompleted, 0, 0, 0);
		}

		ExecutionStat(int completed, int observed, double fillMinutes, double predictedMinutes,
			double pairedFillMinutes, int pairedCompleted, double openMinutes,
			double openPredictedMinutes, int openObserved)
		{
			this.openMinutes = openMinutes;
			this.openPredictedMinutes = openPredictedMinutes;
			this.openObserved = openObserved;
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
