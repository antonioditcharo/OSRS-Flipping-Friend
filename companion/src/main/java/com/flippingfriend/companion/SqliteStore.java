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
import com.flippingfriend.core.AccountLedgerSnapshot;
import com.flippingfriend.core.SaleAccounting;
import com.flippingfriend.core.PositionSnapshot;
import com.flippingfriend.core.BuyReplacementIntent;
import com.flippingfriend.core.BuyReplacementIntentState;

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
        boolean planningStateChanged;
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
	/** Immutable metadata is cached only after its transaction commits. No cached absence. */
	private final Map<String,RiskWindow> riskWindows = new HashMap<>();
    private static final String ACCOUNTING_MODE="AUTOMATIC_ACCOUNTING_V1";
    // Presence means automatic; true additionally proves the non-expiring marker exists.
    private final Map<String,Boolean> automaticModes=new HashMap<>();
    private com.flippingfriend.core.TradeReviewSnapshot cachedTradeReview;
    private java.util.Set<Integer> cachedReviewLiveItems=java.util.Set.of();
    private List<com.flippingfriend.core.ContinuationDisposition> cachedContinuationSales;
    private long continuationSalesCursor=-1;
    private int continuationSalesHistorySize=-1;
	// Accepted canonical sources are append-only. Keep their validated prefix in RAM, reading only
	// the indexed tail. External commits and every rollback discard it. Live eight-slot evidence
	// is always reread. In particular a failed transaction cannot publish a prefix.
	private List<OfferEvent> canonicalHistory;
	private String canonicalHistoryAccount;
	private long canonicalHistoryCursor, canonicalHistoryDataVersion = -1;
	private AutomaticTradeScope scopedEffects;
	private long scopedEffectsCursor=-1;
	private void discardCanonicalHistory() {
		canonicalHistory=null; canonicalHistoryAccount=null; canonicalHistoryCursor=0;
		scopedEffects=null;scopedEffectsCursor=-1;
        automaticModes.clear();
        cachedTradeReview=null;cachedReviewLiveItems=java.util.Set.of();
        cachedContinuationSales=null;continuationSalesCursor=-1;
        continuationSalesHistorySize=-1;
	}

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
            if (!Files.isRegularFile(database)) throw new IllegalArgumentException("existing database is required");
            connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath().toUri().toASCIIString() + "?mode=ro");
			return;
		}
		Files.createDirectories(database.getParent());
		connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
		try (Statement statement = connection.createStatement())
		{
			statement.execute("PRAGMA journal_mode=WAL");
			statement.execute("PRAGMA foreign_keys=ON");
			statement.execute("CREATE TABLE IF NOT EXISTS event_log (id INTEGER PRIMARY KEY, observed_at INTEGER NOT NULL, correlation_id TEXT NOT NULL, event_id TEXT, event_type TEXT NOT NULL, payload TEXT NOT NULL)");
            SalePolicyReceiptStore.initialize(statement);
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
			statement.execute("CREATE TABLE IF NOT EXISTS buy_replacement_intent_projection (intent_id TEXT PRIMARY KEY, schema_version TEXT NOT NULL, original_offer_identity TEXT NOT NULL, recommendation_id TEXT NOT NULL, slot INTEGER NOT NULL, item_id INTEGER NOT NULL, item_name TEXT NOT NULL, original_price INTEGER NOT NULL, original_quantity INTEGER NOT NULL, filled_quantity INTEGER NOT NULL, remainder_quantity INTEGER NOT NULL, created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL, state TEXT NOT NULL, reason_code TEXT NOT NULL, updated_at INTEGER NOT NULL)");
		}

		for (String definition : new String[] {"gross_proceeds INTEGER", "tax_lower INTEGER", "tax_upper INTEGER",
			"profit_lower INTEGER", "profit_upper INTEGER", "sale_tax_policy TEXT", "sale_accounting_reason TEXT"})
		{
			String column = definition.substring(0, definition.indexOf(' '));
			if (!hasColumn("position_event", column))
				try (Statement statement = connection.createStatement())
				{ statement.execute("ALTER TABLE position_event ADD COLUMN " + definition); }
		}
		if (!hasColumn("offer_projection", "sale_tax_policy"))
			try (Statement statement = connection.createStatement())
			{ statement.execute("ALTER TABLE offer_projection ADD COLUMN sale_tax_policy TEXT"); }

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
        return recordAndProjectOffer(event, payload, false);
    }
    synchronized ProjectedEventAcceptance recordAndProjectOffer(OfferEvent event, String payload,
            boolean automaticScope) throws Exception
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
            boolean recordedMode=event.getAccountId()!=null && recordedAutomaticTradeScope(event.getAccountId());
            automaticScope=automaticScope || recordedMode;
            if(automaticScope)recordAutomaticMode(event.getAccountId(),event.getObservedAt());
			OfferLifecycleProjection previous = offerProjection(event.getSlot());
			OfferLifecycleTransition transition = OfferLifecycleReducer.apply(previous, event);
			if (transition.isAccepted()) {
				String episodeGap = episodeReconciliationReason(previous, event, transition);
				if (episodeGap != null)
					transition = OfferLifecycleReducer.rejectForReconciliation(previous, event, episodeGap);
			}
			String taxPolicy = saleTaxPolicy(previous, event, transition);
			writeProjection(transition.getProjection(), event.getEventId(), transition.getReason());
			try (PreparedStatement statement = connection.prepareStatement("UPDATE offer_projection SET sale_tax_policy=? WHERE slot=?"))
			{ statement.setString(1, taxPolicy); statement.setInt(2, event.getSlot()); statement.executeUpdate(); }
			checkpoint.reached("OFFER_PROJECTION_WRITTEN");
			PositionProjection position = automaticScope ? applyAutomaticPositionEffect(transition.getAccountingEffect(), event, previous, taxPolicy)
                : applyPositionEffect(transition.getAccountingEffect(), event, previous, taxPolicy);
			checkpoint.reached("POSITION_ACCOUNTING_APPLIED");
			BuyLimitProjection buyLimit = applyBuyLimitEffect(event, previous, transition);
			checkpoint.reached("BUY_LIMIT_ACCOUNTING_APPLIED");
			// Accept the real execution sample in the same transaction as its source event. A
			// terminal replay cannot recover a sample lost after event acceptance has committed.
			if (transition.isAccepted() && !hasOfferAccountingGap(event.getOfferIdentity()))
				new ExecutionRecorder(this).record(event, executionPrediction(event));
			connection.commit();
            if(automaticScope)automaticModes.put(event.getAccountId(),true);
            ProjectedEventAcceptance result=new ProjectedEventAcceptance(acceptance, transition, position, buyLimit);
            result.planningStateChanged=!samePlanningState(previous,transition.getProjection());
            return result;
		}
		catch (Exception failure)
		{
			connection.rollback(); discardCanonicalHistory();
			throw failure;
		}
		finally
		{
			connection.setAutoCommit(autoCommit);
		}
	}

    private static final String REUSED_EPISODE = "offer identity reappeared outside its current slot episode";
    private static final String UNKNOWN_EPISODE_HISTORY = "offer identity history is incomplete";

    /** Only a prospective guard. Reads retained canonical sources; never replays/revises old money.
     * New-episode checks use SQLite JSON filtering rather than parsing the entire history in Java.
     * Ordinary unchanged heartbeats need neither a history scan nor a cross-request cache. */
    private String episodeReconciliationReason(OfferLifecycleProjection previous, OfferEvent event,
            OfferLifecycleTransition transition) throws Exception {
        String identity = event.getOfferIdentity();
        if ("EMPTY".equals(event.getEventType()) || identity == null || identity.isBlank()) return null;
        boolean continuation = previous != null && previous.getState() != OfferLifecycleState.EMPTY
            && identity.equals(previous.getOfferIdentity());
        if ((previous != null && previous.getState() == OfferLifecycleState.ERROR_RECONCILIATION)
                || transition.getAccountingEffect().getType() != PositionAccountingEffectType.NONE) {
            try (var sql = connection.prepareStatement("SELECT transition_reason FROM position_event "
                    + "WHERE source_offer_identity=? AND effect_type='RECONCILE' AND transition_reason IN (?,?) LIMIT 1")) {
                sql.setString(1, identity); sql.setString(2, REUSED_EPISODE); sql.setString(3, UNKNOWN_EPISODE_HISTORY);
                try (var rows = sql.executeQuery()) { if (rows.next()) return rows.getString(1); }
            }
        }
        if (continuation) return null;
        // CASE protects json_extract from malformed legacy payloads; missing nonempty identity is
        // uncertainty, not proof that a key is new. Account snapshots have no canonical event ID.
        try (var sql = connection.prepareStatement("SELECT payload FROM event_log WHERE event_id IS NOT NULL "
                + "AND event_id<>'' AND event_id<>? AND CASE WHEN json_valid(payload)=0 THEN 1 "
                + "WHEN json_type(payload)<>'object' THEN 1 "
                + "WHEN json_type(payload,'$.offerIdentity')='text' THEN json_extract(payload,'$.offerIdentity')=? "
                + "WHEN event_type='EMPTY' THEN 0 ELSE 1 END ORDER BY id DESC LIMIT 1")) {
            sql.setString(1, event.getEventId()); sql.setString(2, identity);
            try (var rows = sql.executeQuery()) {
                if (!rows.next()) return null;
                try {
                    OfferEvent old = new com.google.gson.Gson().fromJson(rows.getString(1), OfferEvent.class);
                    if (old != null && identity.equals(old.getOfferIdentity())) return REUSED_EPISODE;
                } catch (RuntimeException unreadable) { /* preserve unknown identity evidence */ }
                return UNKNOWN_EPISODE_HISTORY;
            }
        }
    }

    /** Optional learning evidence never changes raw events or financial acceptance. Legacy totals
     * and keys remain intact; this qualifies only new, explicitly versioned predictions. */
    private double executionPrediction(OfferEvent event) throws Exception {
        if (event.getPredictionEvidenceVersion()==null) return 0;
        if (!("BOUGHT".equals(event.getEventType()) || "SOLD".equals(event.getEventType())
                || "CANCELLED_BUY".equals(event.getEventType()) || "CANCELLED_SELL".equals(event.getEventType()))
                || !"1".equals(event.getPredictionEvidenceVersion()) || !event.isPredictionComparable()
                || !event.isAuthenticatedObservation()
                || event.getPredictionSourceEventId()==null || event.getPredictionSourceEventId().isBlank()) return 0;
        try (var sql=connection.prepareStatement("SELECT 1 FROM counted_offer WHERE identity=?")) {
            sql.setString(1,ExecutionRecorder.identity(event));
            try (var rows=sql.executeQuery()) { if (rows.next()) return 0; }
        }
        OfferEvent origin=null;
        // Accounting/ledger reads already validate this immutable account prefix. Reuse it and
        // validate the indexed tail inside this same transaction instead of decoding every old
        // source again on a completed fill. External changes and rollback discard the prefix.
        var history=offerHistory(event.getAccountId());
        if(history==null)return 0;
        for (OfferEvent recorded:history) {
            boolean same=java.util.Objects.equals(event.getAccountId(),recorded.getAccountId())
                && java.util.Objects.equals(event.getSessionId(),recorded.getSessionId())
                && java.util.Objects.equals(event.getOfferIdentity(),recorded.getOfferIdentity())
                && event.getSlot()==recorded.getSlot();
            if (same) {
                if (!TradeGapEvidence.valid(recorded) || recorded.getItemId()!=event.getItemId()
                        || recorded.isBuying()!=event.isBuying() || recorded.getFirstSeenAt()!=event.getFirstSeenAt()
                        || recorded.getPrice()!=event.getPrice() || recorded.getTotalQuantity()!=event.getTotalQuantity()
                        || !"1".equals(recorded.getPredictionEvidenceVersion()) || !recorded.isPredictionComparable()
                        || !event.getPredictionSourceEventId().equals(recorded.getPredictionSourceEventId())) return 0;
                if (origin==null) {
                    origin=recorded;
                    if (!origin.getEventId().equals(event.getPredictionSourceEventId())
                            || !ExecutionObservationLedger.qualifiesOriginal(origin)) return 0;
                }
                if (recorded.getSequence()<origin.getSequence() || recorded.getObservedAt()<origin.getObservedAt()) return 0;
            }
            if (event.getEventId().equals(recorded.getEventId()))
                return origin==null?0:origin.getPredictedMinutes();
        }
        return 0;
    }

    private static boolean samePlanningState(OfferLifecycleProjection a,OfferLifecycleProjection b) {
        return a!=null && b!=null && a.getState()==b.getState() && a.getSlot()==b.getSlot()
            && java.util.Objects.equals(a.getOfferIdentity(),b.getOfferIdentity()) && a.getItemId()==b.getItemId()
            && a.isBuying()==b.isBuying() && a.getPrice()==b.getPrice() && a.getTotalQuantity()==b.getTotalQuantity()
            && a.getFilledQuantity()==b.getFilledQuantity() && a.getSpent()==b.getSpent();
    }

    /** One SELECT-only snapshot. Existing aggregates, source rows and counted-offer keys are untouched. */
    synchronized ExecutionSourceSnapshot freezeExecutionSources(String account) throws Exception {
        com.flippingfriend.core.AccountIdentity.require(account);
        if(!connection.getAutoCommit())throw new IllegalStateException("A separate snapshot transaction is required");
        connection.setAutoCommit(false);
        try {
            List<ExecutionSourceSnapshot.Row> offers=new ArrayList<>();
            List<ExecutionSourceSnapshot.Gap> gaps=new ArrayList<>();
            try(var s=connection.createStatement();var r=s.executeQuery(
                    "SELECT id,observed_at,event_id,event_type,payload FROM event_log WHERE event_id IS NOT NULL AND event_id<>'' ORDER BY id")) {
                while(r.next())offers.add(new ExecutionSourceSnapshot.Row(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),r.getString(5)));
            }
            checkpoint.reached("EXECUTION_SOURCE_OFFERS_READ");
            try(var s=connection.createStatement();var r=s.executeQuery(
                    "SELECT id,observed_at,source_event_id,source_offer_identity,item_id FROM position_event WHERE effect_type='RECONCILE' ORDER BY id")) {
                while(r.next())gaps.add(new ExecutionSourceSnapshot.Gap(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),r.getInt(5)));
            }
            var snapshot=new ExecutionSourceSnapshot(account,Instant.now().getEpochSecond(),offers,gaps);
            snapshot.observations(); // Validate before releasing the same coherent source snapshot.
            connection.commit();return snapshot;
        }catch(Exception invalid){connection.rollback(); discardCanonicalHistory();throw invalid;}
        finally{connection.setAutoCommit(true);}
    }

    synchronized com.flippingfriend.core.ExecutionLearningReport executionLearningReport(String account,long now) throws Exception {
        com.flippingfriend.core.AccountIdentity.require(account);
        try {
            var snapshot=freezeExecutionSources(account);
            return new com.flippingfriend.core.ExecutionLearningReport(account,now,snapshot.sourceCursor,"OBSERVING",
                "Public-price baseline active. Timing intervals are diagnostic; no challenger has passed promotion gates.",snapshot.observations(now));
        }catch(IllegalArgumentException invalid) {
            return com.flippingfriend.core.ExecutionLearningReport.unavailable(account,now,invalid.getMessage());
        }
    }

	/** Positions and offers captured in the same database read transaction. */
	static final class PlanningProjections
	{
		final List<PositionProjection> positions;
		final List<OfferLifecycleProjection> offers;
        AutomaticTradeScope tradeScope;
        Map<Integer,OfferEvent> currentEvents=java.util.Collections.emptyMap();
        Map<Integer,Long> saleProgressAt=java.util.Collections.emptyMap();
        List<OfferEvent> history;
        RiskLedgerEvidence riskEvidence;
        SalePolicyReceiptStore.Evidence salePolicyEvidence=new SalePolicyReceiptStore.Evidence();
		PlanningProjections(List<PositionProjection> positions,
			List<OfferLifecycleProjection> offers)
		{
			this.positions = java.util.Collections.unmodifiableList(new ArrayList<>(positions));
			this.offers = java.util.Collections.unmodifiableList(new ArrayList<>(offers));
		}
	}
	static final class SaleProjections
	{
		final PlanningProjections planning;
		final AccountLedgerSnapshot ledger;
		SaleProjections(PlanningProjections planning, AccountLedgerSnapshot ledger)
		{ this.planning=planning; this.ledger=ledger; }
	}
	/** One fresh source prefix for final sale authority; never cached between requests. */
	synchronized SaleProjections saleProjections(String accountId,long evaluatedAt) throws Exception
	{
		com.flippingfriend.core.AccountIdentity.require(accountId);
		if (!connection.getAutoCommit()) throw new IllegalStateException("sale read requires its own transaction");
		connection.setAutoCommit(false);
		try
		{
			PlanningProjections planning=readPlanningProjections(accountId);
            planning.saleProgressAt=saleProgressEvidence(accountId,planning.currentEvents);
            // Exact cancel/collect relists are checked against this same durable source prefix.
            planning.history=offerHistory(accountId);
            planning.salePolicyEvidence=SalePolicyReceiptStore.read(connection,accountId,planning.history);
			checkpoint.reached("SALE_PLANNING_READ");
			AccountLedgerSnapshot ledger=readLedgerSnapshot(accountId,evaluatedAt,planning.tradeScope);
			connection.commit();
			return new SaleProjections(planning,ledger);
		}
		catch(Exception failure) { connection.rollback(); discardCanonicalHistory(); throw failure; }
		finally { connection.setAutoCommit(true); }
	}
    /** POST-only issuance after final policy authorization; metadata cannot book a financial effect. */
    synchronized com.flippingfriend.core.SalePolicyReceipt issueSalePolicyReceipt(String request,AccountSnapshot settings,
            OfferEvent source,long progress,int price,int quantity,boolean cut,long now,long patience)throws Exception {
        return SalePolicyReceiptStore.issue(connection,request,settings,source,progress,price,quantity,cut,now,patience,checkpoint);
    }
    /** Last observed disposal of this exact live episode. Read-only; never reconstruct an execution. */
    private Map<Integer,Long> saleProgressEvidence(String account,Map<Integer,OfferEvent> current) throws Exception {
        var result=new HashMap<Integer,Long>();var parser=new com.google.gson.Gson();
        try(var sql=connection.prepareStatement("SELECT e.payload,p.observed_at,p.source_event_id FROM position_event p "
            +"LEFT JOIN event_log e ON e.event_id=p.source_event_id AND e.event_id IS NOT NULL AND e.event_id<>'' "
            +"WHERE p.effect_type='DISPOSE' AND p.source_offer_identity=? ORDER BY p.id DESC LIMIT 1")) {
            for(var e:current.values())if(!e.isBuying() && "SELLING".equals(e.getEventType())) {
                long progress=e.getFirstSeenAt();
                if(e.getFilledQuantity()>0) {
                    progress=Long.MAX_VALUE; // Missing/inconsistent progress proof cannot mean "stalled".
                    sql.setString(1,e.getOfferIdentity());
                    try(var rows=sql.executeQuery()) {if(rows.next()) {
                        try {
                            var fill=parser.fromJson(rows.getString(1),OfferEvent.class);
                            if(fill!=null && TradeGapEvidence.valid(fill) && fill.isAuthenticatedObservation()
                                && account.equals(fill.getAccountId()) && !fill.isBuying() && fill.getItemId()==e.getItemId()
                                && java.util.Objects.equals(fill.getOfferIdentity(),e.getOfferIdentity()) && fill.getSlot()==e.getSlot()
                                && java.util.Objects.equals(fill.getSessionId(),e.getSessionId())
                                && fill.getPrice()==e.getPrice() && fill.getTotalQuantity()==e.getTotalQuantity()
                                && fill.getFilledQuantity()>0 && fill.getFilledQuantity()<=e.getFilledQuantity()
                                && fill.getSpent()<=e.getSpent() && fill.getObservedAt()>=e.getFirstSeenAt()
                                && fill.getObservedAt()<=e.getObservedAt() && fill.getObservedAt()==rows.getLong(2)
                                && java.util.Objects.equals(fill.getEventId(),rows.getString(3)) && fill.getSequence()<=e.getSequence())
                                progress=fill.getObservedAt();
                        } catch(RuntimeException malformed) { /* retain unavailable progress */ }
                    }}
                }
                result.put(e.getSlot(),progress);
            }
        }
        return java.util.Collections.unmodifiableMap(result);
    }
	/** Does not change durable rows or participate in an existing write transaction. */
	synchronized PlanningProjections planningProjections() throws Exception
	{
		return planningProjections(null);
	}
	synchronized PlanningProjections planningProjections(String expectedAccountId) throws Exception
	{
		if (!connection.getAutoCommit())
			throw new IllegalStateException("planning read requires its own transaction");
		connection.setAutoCommit(false);
		try
		{
			PlanningProjections result = readPlanningProjections(expectedAccountId);
			connection.commit();
			cacheRiskWindow(result.riskEvidence.window);
			return result;
		}
		catch (Exception failure)
		{
			connection.rollback(); discardCanonicalHistory();
			throw failure;
		}
		finally
		{
			connection.setAutoCommit(true);
		}
	}

	/** Caller owns the read/write transaction; no nested commit or live-state rewrite. */
	private PlanningProjections readPlanningProjections(String expectedAccountId) throws Exception
	{
		PlanningProjections result = new PlanningProjections(positionProjections(), offerProjections());
        OfferHistoryRead history = new OfferHistoryRead(expectedAccountId);
		result.tradeScope = automaticTradeScope(expectedAccountId, history);
		result.currentEvents = currentOfferEvidence(expectedAccountId);
		if (!result.tradeScope.unsafeItems.isEmpty()) result.history = history.get();
		String session = result.currentEvents.isEmpty() ? null : result.currentEvents.values().iterator().next().getSessionId();
		result.riskEvidence = riskLedgerEvidence(result.tradeScope, riskWindow(expectedAccountId, session));
		return result;
	}

	private RiskWindow riskWindow(String expectedAccountId, String session) throws Exception
	{
		if (expectedAccountId == null || session == null || session.isBlank()) return null;
		RiskWindow cached = riskWindows.get(expectedAccountId + ":" + session);
		if (cached != null) return cached;
		RiskWindow result = null;
		try (PreparedStatement sql = connection.prepareStatement(
			"SELECT observed_at,payload,event_id FROM event_log WHERE event_type=? AND correlation_id=? ORDER BY id"))
		{
			sql.setString(1, RiskWindow.EVENT_TYPE); sql.setString(2, RiskWindow.correlation(session));
			try (ResultSet rows = sql.executeQuery())
			{
				while (rows.next())
				{
					RiskWindow window = new com.google.gson.Gson().fromJson(rows.getString(2), RiskWindow.class);
					if (result != null || window == null || !window.isValid()
						|| !expectedAccountId.equals(window.accountId) || !session.equals(window.sessionId)
						|| rows.getLong(1) != window.startedAt || rows.getString(3) != null)
						throw new IllegalStateException("Risk window provenance is inconsistent.");
					result = window;
				}
			}
		}
		if (result != null) validateRiskWindowSources(result);
		return result;
	}
	private void validateRiskWindowSources(RiskWindow window) throws Exception
	{
        var parser=new com.google.gson.Gson();
        try(var sql=connection.prepareStatement("SELECT observed_at,payload FROM event_log WHERE event_id=? AND event_id IS NOT NULL AND event_id<>''")) {
            for(int slot=0;slot<8;slot++) {
                sql.setString(1,window.sourceForSlot(slot));
                try(var rows=sql.executeQuery()) {
                    if(!rows.next())throw new IllegalStateException("Risk window source receipt is missing.");
                    var source=parser.fromJson(rows.getString(2),OfferEvent.class);
                    if(source==null || !TradeGapEvidence.valid(source) || !source.isAuthenticatedObservation()
                        || !window.accountId.equals(source.getAccountId()) || !window.sessionId.equals(source.getSessionId())
                        || !window.sourceForSlot(slot).equals(source.getEventId()) || source.getSlot()!=slot
                        || source.getObservedAt()!=window.startedAt || rows.getLong(1)!=window.startedAt || rows.next())
                        throw new IllegalStateException("Risk window source receipt is inconsistent.");
                }
            }
        }
	}
	private void cacheRiskWindow(RiskWindow window)
	{
		if (window != null) riskWindows.put(window.accountId + ":" + window.sessionId, window);
	}

	private RiskLedgerEvidence riskLedgerEvidence(AutomaticTradeScope current, RiskWindow window) throws Exception
	{
		RiskLedgerEvidence result = new RiskLedgerEvidence(window);
		AutomaticTradeScope replay = new AutomaticTradeScope();
		try (Statement sql = connection.createStatement(); ResultSet rows = sql.executeQuery(
			"SELECT id,effect_type,item_id,item_name,quantity,acquisition_cost,observed_at,source_event_id," +
			"source_offer_identity,profit_lower,profit_upper,applied_cost_basis FROM position_event ORDER BY id"))
		{
			while (rows.next())
			{
				String type = rows.getString(2); int item = rows.getInt(3);
				if(window!=null && rows.getLong(1)>window.positionCursor)result.open(replay);
				var affected = current.reconciliationScope.get(rows.getString(8));
				result.apply(rows.getLong(1), type, item, rows.getInt(5), rows.getLong(6),rows.getLong(12),nullableLong(rows,10),nullableLong(rows,11),
					replay.positions.get(item),replay.unsafeItems.contains(item)||replay.hasUnboundedReconciliation,affected);
				replay.apply(type,item,rows.getString(4),rows.getInt(5),rows.getLong(6),rows.getLong(7),rows.getString(8),rows.getString(9),affected);
			}
		}
		result.open(replay);
		if (window != null && window.positionCursor > result.maxPositionCursor)
			throw new IllegalStateException("Risk cursor exceeds the recorded ledger.");
		return result;
	}

	private RiskWindow startRiskWindowIfProven(AccountSnapshot snapshot,long now) throws Exception
	{
		if (!snapshot.isAutomaticTradeScope()) return null;
		var sources = currentOfferEvidence(snapshot.getAccountId());
		if (!sources.isEmpty())
		{
			String session = sources.values().iterator().next().getSessionId();
			RiskWindow committed = riskWindows.get(snapshot.getAccountId() + ":" + session);
			if (committed != null) return committed;
		}
		var read = readPlanningProjections(snapshot.getAccountId());
		if (!CurrentOfferReadSet.valid(read, snapshot, now) || read.riskEvidence.window != null) return read.riskEvidence.window;
		long capital = DurableRisk.knownCapital(read, snapshot, now);
		if (capital <= 0) return null;
		String session = read.currentEvents.values().iterator().next().getSessionId();
		RiskWindow window = new RiskWindow(snapshot, session, read.riskEvidence.maxPositionCursor, capital);
		recordEvent(window.startedAt, RiskWindow.correlation(session), RiskWindow.EVENT_TYPE, new com.google.gson.Gson().toJson(window));
		checkpoint.reached("RISK_WINDOW_RECORDED");
		return window;
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

	/** Restore current high-water state, independent of event-log retention or buy-limit age. */
	synchronized List<OfferEvent> activeOfferEvents() throws Exception
	{
		List<OfferEvent> result = new ArrayList<>();
		try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
			"SELECT lifecycle_state,slot,offer_identity,session_id,last_sequence,last_observed_at,item_id,item_name,buying,price,total_quantity,filled_quantity,spent,recommendation_id,source_event_id FROM offer_projection ORDER BY slot"))
		{
			while (rows.next())
			{
				OfferLifecycleProjection projection = readProjection(rows);
				String eventType;
				switch (projection.getState())
				{
					case BUY_OPEN: case BUY_PARTIAL: eventType = "BUYING"; break;
					case BUY_FILLED_UNCOLLECTED: eventType = "BOUGHT"; break;
					case BUY_CANCELLED_UNCOLLECTED: eventType = "CANCELLED_BUY"; break;
					case SELL_OPEN: case SELL_PARTIAL: eventType = "SELLING"; break;
					case SELL_FILLED_UNCOLLECTED: eventType = "SOLD"; break;
					case SELL_CANCELLED_UNCOLLECTED: eventType = "CANCELLED_SELL"; break;
					default: continue;
				}
				result.add(OfferEvent.builder("restored-projection", projection.getLastObservedAt(), eventType)
					.eventIdentity(rows.getString("source_event_id"), projection.getSessionId(), projection.getOfferIdentity())
					.slot(projection.getSlot()).item(projection.getItemId(), projection.getItemName())
					.buying(projection.isBuying()).price(projection.getPrice())
					.quantities(projection.getTotalQuantity(), projection.getFilledQuantity())
					.spent(projection.getSpent()).sequence(projection.getLastSequence())
					.recommendation(projection.getRecommendationId(), 0, 0, 0, 0).build());
			}
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

	synchronized void saveBuyReplacementIntent(BuyReplacementIntent i) throws Exception
	{ if(i==null)throw new IllegalArgumentException("intent is required"); BuyReplacementIntent old=buyReplacementIntent(i.getIntentId()); if(old!=null&&!sameReplacementIdentity(old,i))throw new IllegalArgumentException("replacement intent identity mismatch"); boolean ac=connection.getAutoCommit();connection.setAutoCommit(false);try{try(PreparedStatement s=connection.prepareStatement("INSERT INTO buy_replacement_intent_projection(intent_id,schema_version,original_offer_identity,recommendation_id,slot,item_id,item_name,original_price,original_quantity,filled_quantity,remainder_quantity,created_at,expires_at,state,reason_code,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(intent_id) DO UPDATE SET state=excluded.state,reason_code=excluded.reason_code,updated_at=excluded.updated_at")){s.setString(1,i.getIntentId());s.setString(2,i.getSchemaVersion());s.setString(3,i.getOriginalOfferIdentity());s.setString(4,i.getRecommendationId());s.setInt(5,i.getSlot());s.setInt(6,i.getItemId());s.setString(7,i.getItemName());s.setInt(8,i.getOriginalPrice());s.setInt(9,i.getOriginalQuantity());s.setInt(10,i.getFilledQuantity());s.setInt(11,i.getRemainderQuantity());s.setLong(12,i.getCreatedAt());s.setLong(13,i.getExpiresAt());s.setString(14,i.getState().name());s.setString(15,i.getReasonCode());s.setLong(16,Instant.now().getEpochSecond());s.executeUpdate();}checkpoint.reached("BUY_REPLACEMENT_INTENT_WRITTEN");connection.commit();}catch(Exception e){connection.rollback(); discardCanonicalHistory();throw e;}finally{connection.setAutoCommit(ac);} }
	private static boolean sameReplacementIdentity(BuyReplacementIntent a,BuyReplacementIntent b){return a.getIntentId().equals(b.getIntentId())&&a.getOriginalOfferIdentity().equals(b.getOriginalOfferIdentity())&&a.getRecommendationId().equals(b.getRecommendationId())&&a.getSlot()==b.getSlot()&&a.getItemId()==b.getItemId()&&a.getItemName().equals(b.getItemName())&&a.getOriginalPrice()==b.getOriginalPrice()&&a.getOriginalQuantity()==b.getOriginalQuantity()&&a.getFilledQuantity()==b.getFilledQuantity()&&a.getCreatedAt()==b.getCreatedAt()&&a.getExpiresAt()==b.getExpiresAt();}
	synchronized List<BuyReplacementIntent> buyReplacementIntents() throws Exception{List<BuyReplacementIntent> x=new ArrayList<>();try(PreparedStatement s=connection.prepareStatement("SELECT schema_version,intent_id,original_offer_identity,recommendation_id,slot,item_id,item_name,original_price,original_quantity,filled_quantity,remainder_quantity,created_at,expires_at,state,reason_code FROM buy_replacement_intent_projection ORDER BY intent_id");ResultSet r=s.executeQuery()){while(r.next())x.add(readBuyReplacementIntent(r));}return x;}
	private BuyReplacementIntent buyReplacementIntent(String id)throws Exception{try(PreparedStatement s=connection.prepareStatement("SELECT schema_version,intent_id,original_offer_identity,recommendation_id,slot,item_id,item_name,original_price,original_quantity,filled_quantity,remainder_quantity,created_at,expires_at,state,reason_code FROM buy_replacement_intent_projection WHERE intent_id=?")){s.setString(1,id);try(ResultSet r=s.executeQuery()){return r.next()?readBuyReplacementIntent(r):null;}}}
	private static BuyReplacementIntent readBuyReplacementIntent(ResultSet r)throws Exception{return BuyReplacementIntent.restore(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getInt(5),r.getInt(6),r.getString(7),r.getInt(8),r.getInt(9),r.getInt(10),r.getInt(11),r.getLong(12),r.getLong(13),BuyReplacementIntentState.valueOf(r.getString(14)),r.getString(15));}

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

	private String saleTaxPolicy(OfferLifecycleProjection previous, OfferEvent event,
		OfferLifecycleTransition transition) throws Exception
	{
		if (event.isBuying() || transition.getNewState() == OfferLifecycleState.EMPTY) return null;
		// A restored current slot is not proof of when a replacement sell was placed.
		if (transition.isAccepted() && previous != null
			&& previous.getState() == OfferLifecycleState.ERROR_RECONCILIATION)
			return event.getObservedAt() >= SaleAccounting.CURRENT_POLICY_SAFE_FROM
				? SaleAccounting.UNPROVEN_PLACEMENT : "HISTORICAL_RULE_UNPROVEN";
		if (previous != null && previous.getState() != OfferLifecycleState.EMPTY)
			try (PreparedStatement statement = connection.prepareStatement("SELECT sale_tax_policy FROM offer_projection WHERE slot=?"))
			{
				statement.setInt(1, event.getSlot());
				try (ResultSet row = statement.executeQuery())
				{ if (row.next() && row.getString(1) != null) return row.getString(1); }
			}
		if (transition.isAccepted() && previous != null && previous.getState() == OfferLifecycleState.EMPTY
			&& previous.getLastObservedAt() >= SaleAccounting.CURRENT_POLICY_SAFE_FROM
			&& event.getObservedAt() >= previous.getLastObservedAt() && event.getFilledQuantity() == 0)
			return SaleAccounting.CURRENT_POLICY;
		return event.getObservedAt() >= SaleAccounting.CURRENT_POLICY_SAFE_FROM
			? SaleAccounting.UNPROVEN_PLACEMENT : "HISTORICAL_RULE_UNPROVEN";
	}

    /** Shares the complete validated prefix, including this transaction's indexed tail. */
    private final class OfferHistoryRead {
        private final String account;
        private boolean loaded;
        private List<OfferEvent> events;
        OfferHistoryRead(String account) { this.account=account; }
        List<OfferEvent> get() throws Exception {
            if (!loaded) { events=offerHistory(account); loaded=true; }
            return events;
        }
    }
    private AutomaticTradeScope automaticTradeScope(String expectedAccountId) throws Exception {
        return automaticTradeScope(expectedAccountId, new OfferHistoryRead(expectedAccountId));
    }
    private AutomaticTradeScope automaticTradeScope(String expectedAccountId, OfferHistoryRead history) throws Exception {
        // Validate new canonical sources before reusing an immutable economic prefix. A new
        // observation cannot alter old scope; a new effect or any external edit forces a rebuild.
        // Current offers, cash, prices, buy limits and risk are evaluated independently each time.
        if(expectedAccountId!=null)checkCanonicalDataVersion(expectedAccountId);
        boolean hasGaps;
        try(var s=connection.createStatement();var r=s.executeQuery("SELECT 1 FROM position_event WHERE effect_type='RECONCILE' LIMIT 1")){hasGaps=r.next();}
        List<OfferEvent> sources=expectedAccountId==null?null:hasGaps?history.get():java.util.List.of();
        long cursor;
        try(var s=connection.createStatement();var r=s.executeQuery("SELECT COALESCE(MAX(id),0) FROM position_event")) {r.next();cursor=r.getLong(1);}
        if(sources!=null && scopedEffects!=null && scopedEffectsCursor==cursor)return scopedEffects;
        AutomaticTradeScope scope = new AutomaticTradeScope();
        scope.reconciliationScope.putAll(reconciliationScope(expectedAccountId, history));
        Map<String,com.flippingfriend.core.AcquisitionExitContext> exitContexts=null;
        var parser=new com.google.gson.Gson();
        try (PreparedStatement s = connection.prepareStatement(
            "SELECT p.effect_type,p.item_id,p.item_name,p.quantity,p.acquisition_cost,p.observed_at,p.source_event_id,p.source_offer_identity,e.payload "
                +"FROM position_event p LEFT JOIN event_log e ON e.event_id=p.source_event_id AND e.event_id IS NOT NULL AND e.event_id<>'' ORDER BY p.id");
             ResultSet r = s.executeQuery()) {
            while (r.next()) {
                com.flippingfriend.core.AcquisitionExitContext context=null;
                if(expectedAccountId!=null && "ACQUIRE".equals(r.getString(1)) && r.getString(9)!=null) {
                    OfferEvent source=null;
                    try {source=parser.fromJson(r.getString(9),OfferEvent.class);}catch(RuntimeException malformed){ /* context stays unknown */ }
                    if(source!=null && source.getExitContext()!=null && expectedAccountId.equals(source.getAccountId())
                        && java.util.Objects.equals(r.getString(7),source.getEventId()) && r.getInt(2)==source.getItemId()
                        && java.util.Objects.equals(r.getString(8),source.getOfferIdentity()) && r.getLong(6)==source.getObservedAt()) {
                        if(exitContexts==null)exitContexts=AcquisitionExitEvidence.read(history.get());
                        context=exitContexts.get(source.getEventId());
                    }
                }
                scope.apply(r.getString(1), r.getInt(2), r.getString(3), r.getInt(4),
                r.getLong(5), r.getLong(6), r.getString(7), r.getString(8),
                scope.reconciliationScope.get(r.getString(7)),context);
            }
        }
        if(sources!=null){scopedEffects=scope;scopedEffectsCursor=cursor;}
        return scope;
    }

    private Map<String,java.util.Set<Integer>> reconciliationScope(String expectedAccountId, OfferHistoryRead read) throws Exception {
        Map<String,Integer> gaps=new HashMap<>();
        try (Statement s=connection.createStatement(); ResultSet r=s.executeQuery(
                "SELECT source_event_id,item_id FROM position_event WHERE effect_type='RECONCILE'")) {
            while (r.next()) gaps.put(r.getString(1),r.getInt(2));
        }
        if (gaps.isEmpty()) return java.util.Collections.emptyMap();
        List<OfferEvent> history=read.get();
        return history==null?TradeGapEvidence.unknown(gaps):TradeGapEvidence.scope(history,gaps,expectedAccountId);
    }
    private List<OfferEvent> offerHistory(String expectedAccountId) throws Exception {
        if (expectedAccountId==null) return null;
        checkCanonicalDataVersion(expectedAccountId);
        List<OfferEvent> history=new ArrayList<>(canonicalHistory==null?java.util.List.of():canonicalHistory);
        long cursor=canonicalHistory==null?0:canonicalHistoryCursor;
        com.google.gson.Gson parser=new com.google.gson.Gson();
        try (PreparedStatement s=connection.prepareStatement(
                "SELECT event_id,payload,id FROM event_log WHERE id>? AND event_id IS NOT NULL AND event_id<>'' ORDER BY id")) {
            s.setLong(1,cursor);
            try(ResultSet r=s.executeQuery()) {
            while (r.next()) {
                OfferEvent event;
                try { event=parser.fromJson(r.getString(2),OfferEvent.class); }
                catch (RuntimeException incomplete) { discardCanonicalHistory(); return null; }
                if (event==null || !r.getString(1).equals(event.getEventId())
                        || !expectedAccountId.equals(event.getAccountId()) || !TradeGapEvidence.valid(event)) {
                    discardCanonicalHistory(); return null;
                }
                history.add(event);
                cursor=r.getLong(3);
            }
            }
        }
        canonicalHistory=java.util.Collections.unmodifiableList(history);
        canonicalHistoryCursor=cursor;
        return canonicalHistory;
    }
    private void checkCanonicalDataVersion(String expectedAccountId) throws Exception {
        long version;
        try (Statement s=connection.createStatement(); ResultSet r=s.executeQuery("PRAGMA data_version")) {
            r.next(); version=r.getLong(1);
        }
        if(version!=canonicalHistoryDataVersion || !expectedAccountId.equals(canonicalHistoryAccount))
            discardCanonicalHistory();
        canonicalHistoryAccount=expectedAccountId; canonicalHistoryDataVersion=version;
    }
    private Map<Integer,OfferEvent> currentOfferEvidence(String expectedAccountId) throws Exception {
        Map<Integer,OfferEvent> result=new HashMap<>();
        com.google.gson.Gson parser=new com.google.gson.Gson();
        // Match the existing partial identity index. Without its nonblank predicate SQLite can
        // rebuild an automatic covering index over the entire event history on each heartbeat.
        // Keep the predicate in ON so a missing source still withdraws the whole eight-slot proof.
        try (Statement s=connection.createStatement(); ResultSet r=s.executeQuery(
                "SELECT p.slot,p.source_event_id,e.payload FROM offer_projection p LEFT JOIN event_log e ON e.event_id=p.source_event_id AND e.event_id IS NOT NULL AND e.event_id<>''")) {
            while(r.next()) {
                OfferEvent event;
                try { event=parser.fromJson(r.getString(3),OfferEvent.class); }
                catch(RuntimeException invalid){return java.util.Collections.emptyMap();}
                if(event==null || expectedAccountId==null || !expectedAccountId.equals(event.getAccountId())
                        || !java.util.Objects.equals(r.getString(2),event.getEventId()) || r.getInt(1)!=event.getSlot())
                    return java.util.Collections.emptyMap();
                result.put(event.getSlot(),event);
            }
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    /** A current slot baseline cannot establish a complete historical timing/outcome sample. */
    synchronized boolean hasOfferAccountingGap(String offerIdentity) throws Exception {
        if (offerIdentity == null || offerIdentity.isBlank()) return false;
        try (PreparedStatement s = connection.prepareStatement(
                "SELECT 1 FROM position_event WHERE source_offer_identity=? AND effect_type='RECONCILE' LIMIT 1")) {
            s.setString(1,offerIdentity);
            try (ResultSet r = s.executeQuery()) { return r.next(); }
        }
    }

    private java.util.List<com.flippingfriend.core.MissedFlipEvidence> missedFlipEvidence() throws Exception {
        java.util.List<com.flippingfriend.core.MissedFlipEvidence> result = new ArrayList<>();
        try (PreparedStatement s = connection.prepareStatement(
            "SELECT source_event_id,source_offer_identity,item_id,item_name,effect_type,quantity,observed_at," +
            "acquisition_cost,gross_proceeds,tax_lower,tax_upper,profit_lower,profit_upper," +
            "COALESCE(sale_accounting_reason,transition_reason,'Incomplete trade evidence') FROM position_event " +
            "WHERE effect_type='RECONCILE' OR transition_reason IS NOT NULL OR " +
            "(effect_type='DISPOSE' AND (profit_lower IS NULL OR profit_lower<>profit_upper)) ORDER BY id DESC");
             ResultSet r = s.executeQuery()) {
            while (r.next()) result.add(new com.flippingfriend.core.MissedFlipEvidence(
                r.getString(1),r.getString(2),r.getInt(3),r.getString(4),r.getString(5),r.getInt(6),r.getLong(7),
                "ACQUIRE".equals(r.getString(5)) ? nullableLong(r,8) : null, nullableLong(r,9),
                nullableLong(r,10),nullableLong(r,11),nullableLong(r,12),nullableLong(r,13),r.getString(14)));
        }
        return result;
    }
    private static Long nullableLong(ResultSet row, int column) throws Exception {
        long value = row.getLong(column); return row.wasNull() ? null : value;
    }

    /** Future observed executions use the same event-ledger quantity view as exits and planning.
     * No startup rebuilding, rewriting old sales, or replacing legacy projections occurs here. */
    private PositionProjection applyAutomaticPositionEffect(PositionAccountingEffect effect, OfferEvent event,
            OfferLifecycleProjection previous, String taxPolicy) throws Exception {
        if (effect == null || effect.getType() == PositionAccountingEffectType.NONE) return null;
        AutomaticTradeScope scope = automaticTradeScope(event.getAccountId());
        PositionProjection current = scope.positions.get(effect.getItemId());
        Long cost = null;
        String reason = effect.getReason();
        if (effect.getType() == PositionAccountingEffectType.DISPOSE) {
            if (current != null && !scope.hasUnboundedReconciliation && !scope.unsafeItems.contains(effect.getItemId())
                    && effect.getQuantity() <= current.getQuantity())
                cost = SaleAccounting.allocateCost(current.getTotalCost(), current.getQuantity(), effect.getQuantity());
            else reason = "Sale lacks complete observed acquisition coverage";
        }
        SaleAccounting sale = effect.getType() == PositionAccountingEffectType.DISPOSE
            ? SaleAccounting.calculate(event.getSpent() - (previous == null ? 0 : previous.getSpent()),
                effect.getQuantity(), event.getPrice(), event.getItemName(), taxPolicy, cost) : null;
        appendPositionEvent(effect, cost == null ? 0 : cost, reason, sale, taxPolicy);
        return null;
    }

	private PositionProjection applyPositionEffect(PositionAccountingEffect effect, OfferEvent event,
		OfferLifecycleProjection previous, String taxPolicy) throws Exception
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
					appliedCost = SaleAccounting.allocateCost(current.getTotalCost(), current.getQuantity(), effect.getQuantity());
					if (effect.getQuantity() == current.getQuantity()) result = null;
					else result = PositionProjection.ready(current.getItemId(), current.getItemName(), current.getQuantity() - effect.getQuantity(), current.getTotalCost() - appliedCost, current.getOpenedAt(), effect.getObservedAt(), effect.getSourceEventId(), effect.getOfferIdentity());
				}
				break;
			case RECONCILE:
				reason = effect.getReason();
				break;
			default: break;
		}
		SaleAccounting sale = effect.getType() == PositionAccountingEffectType.DISPOSE
			? SaleAccounting.calculate(event.getSpent() - (previous == null ? 0 : previous.getSpent()),
				effect.getQuantity(), event.getPrice(), event.getItemName(), taxPolicy,
				reason == null && current != null && current.isCostKnown() ? appliedCost : null) : null;
		appendPositionEvent(effect, appliedCost, reason, sale, taxPolicy);
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
	private void appendPositionEvent(PositionAccountingEffect e,long appliedCost,String reason,
		SaleAccounting sale, String taxPolicy) throws Exception
	{
		try (PreparedStatement s=connection.prepareStatement("INSERT OR IGNORE INTO position_event(source_event_id,source_offer_identity,observed_at,effect_type,item_id,item_name,quantity,acquisition_cost,applied_cost_basis,transition_reason,created_at,gross_proceeds,tax_lower,tax_upper,profit_lower,profit_upper,sale_tax_policy,sale_accounting_reason) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"))
		{
			s.setString(1,e.getSourceEventId());s.setString(2,e.getOfferIdentity());s.setLong(3,e.getObservedAt());s.setString(4,e.getType().name());s.setInt(5,e.getItemId());s.setString(6,e.getItemName());s.setInt(7,e.getQuantity());s.setLong(8,e.getAcquisitionCost());s.setLong(9,appliedCost);s.setString(10,reason);s.setLong(11,Instant.now().getEpochSecond());
			s.setObject(12, sale == null ? null : sale.getGross());
			s.setObject(13, sale == null ? null : sale.getTaxLower());
			s.setObject(14, sale == null ? null : sale.getTaxUpper());
			s.setObject(15, sale == null ? null : sale.getProfitLower());
			s.setObject(16, sale == null ? null : sale.getProfitUpper());
			s.setString(17, sale == null ? null : taxPolicy);
			s.setString(18, sale == null ? null : sale.getReason());
			s.executeUpdate();
		}
	}

	synchronized AccountLedgerSnapshot ledgerSnapshot(String accountId) throws Exception
	{
		return ledgerSnapshot(accountId,Instant.now().getEpochSecond());
	}
	synchronized AccountLedgerSnapshot ledgerSnapshot(String accountId,long evaluatedAt) throws Exception
	{
		return readLedgerSnapshot(accountId,evaluatedAt,automaticTradeScope(accountId));
	}
	private AccountLedgerSnapshot readLedgerSnapshot(String accountId,long evaluatedAt,AutomaticTradeScope scope) throws Exception
	{
		try (Statement statement = connection.createStatement(); ResultSet row = statement.executeQuery(
			"SELECT COUNT(*),COUNT(profit_lower),COALESCE(SUM(CASE WHEN profit_lower<>profit_upper THEN 1 ELSE 0 END),0),COUNT(tax_lower)," +
			"COALESCE(SUM(profit_lower),0),COALESCE(SUM(profit_upper),0)," +
			"COALESCE(SUM(tax_lower),0),COALESCE(SUM(tax_upper),0) FROM position_event WHERE effect_type='DISPOSE'"))
		{
			row.next();
			return new AccountLedgerSnapshot(accountId, evaluatedAt, row.getLong(1),
				row.getLong(2), row.getLong(3), row.getLong(4), row.getLong(5), row.getLong(6),
				row.getLong(7), row.getLong(8), positionProjections())
                .withTradeEvidence(scope.readyPositions(), missedFlipEvidence(), scope.reconciliationScope)
                .withRecentSales(recentSales())
                .withContinuationDispositions(continuationSaleDispositions(accountId,evaluatedAt));
		}
	}
	private List<com.flippingfriend.core.RecordedSale> recentSales() throws Exception {
		return recordedSales(" LIMIT 100");
	}
	private List<com.flippingfriend.core.RecordedSale> recordedSales(String limit) throws Exception {
		List<com.flippingfriend.core.RecordedSale> result=new ArrayList<>();
		try(Statement statement=connection.createStatement();ResultSet r=statement.executeQuery(
			"SELECT id,observed_at,item_id,item_name,quantity,applied_cost_basis,source_event_id,source_offer_identity,gross_proceeds,tax_lower,tax_upper,profit_lower,profit_upper,sale_tax_policy,"+
                "CASE WHEN transition_reason IS NULL THEN sale_accounting_reason ELSE COALESCE(sale_accounting_reason,'Incomplete accounting') || '; ' || transition_reason END FROM position_event WHERE effect_type='DISPOSE' ORDER BY id DESC"+limit)) {
			while(r.next())result.add(new com.flippingfriend.core.RecordedSale(r.getLong(1),r.getLong(2),r.getInt(3),r.getString(4),r.getInt(5),r.getLong(6),
				r.getString(7),r.getString(8),nullableLong(r,9),nullableLong(r,10),nullableLong(r,11),nullableLong(r,12),nullableLong(r,13),r.getString(14),r.getString(15)));
		}
		return result;
	}
	private PerformanceRead.Session performanceSession() throws Exception {
		try(var s=connection.prepareStatement("SELECT payload FROM event_log WHERE event_type=? ORDER BY id DESC LIMIT 1")) {
			s.setString(1,PerformanceRead.SESSION_EVENT);
			try(var r=s.executeQuery()) {
				if(!r.next())return null;
				var session=new com.google.gson.Gson().fromJson(r.getString(1),PerformanceRead.Session.class);
				if(session==null || session.id==null || session.startedAt<0 || session.cursor<0)
					throw new IllegalStateException("Invalid performance session");
				return session;
			}
		}
	}
	/** Only a fresh authenticated account capture or explicit reset calls this. Risk anchors are untouched. */
	synchronized void performanceSession(String account,long now,boolean reset,String requestId) throws Exception {
		com.flippingfriend.core.AccountIdentity.require(account);
		if(!reset && performanceSession()!=null)return;
		if(requestId==null || !requestId.matches("[a-zA-Z0-9-]{1,80}"))throw new IllegalArgumentException("Reset identity required");
		try(var s=connection.prepareStatement("SELECT 1 FROM event_log WHERE event_type=? AND correlation_id=?")) {
			s.setString(1,PerformanceRead.SESSION_EVENT);s.setString(2,requestId);try(var r=s.executeQuery()){if(r.next())return;}
		}
		long cursor;
		try(var s=connection.createStatement();var r=s.executeQuery("SELECT COALESCE(MAX(id),0) FROM position_event")){r.next();cursor=r.getLong(1);}
		var session=new PerformanceRead.Session(requestId,now,cursor);
		recordEvent(now,requestId,PerformanceRead.SESSION_EVENT,new com.google.gson.Gson().toJson(session));
	}
    /** Recover only the accounting mode of this explicitly scoped account, never its spending proof. */
    synchronized boolean recordedAutomaticTradeScope(String account) throws Exception {
        com.flippingfriend.core.AccountIdentity.require(account);
        checkCanonicalDataVersion(account);
        if(automaticModes.containsKey(account))return true;
        try(var s=connection.prepareStatement("SELECT payload FROM event_log WHERE event_type=? ORDER BY id LIMIT 1")) {
            s.setString(1,ACCOUNTING_MODE);
            try(var r=s.executeQuery()) {
                if(r.next()) {
                    var mode=new com.google.gson.Gson().fromJson(r.getString(1),com.google.gson.JsonObject.class);
                    if(mode==null || !mode.has("accountId") || !account.equals(mode.get("accountId").getAsString())
                        || !mode.has("version") || !"1".equals(mode.get("version").getAsString()))
                        throw new IllegalStateException("Recorded accounting mode does not match this account");
                    if(connection.getAutoCommit())automaticModes.put(account,true);
                    return true;
                }
            }
        }
        try(var s=connection.prepareStatement("SELECT payload FROM event_log WHERE event_type='ACCOUNT_STATE' ORDER BY id DESC LIMIT 1");
            var r=s.executeQuery()) {
            if(r.next() && snapshotAutomaticMode(account,r.getString(1))) {
                if(connection.getAutoCommit())automaticModes.put(account,false);
                return true;
            }
        }
        // A legacy client may already have appended a false flag before this prevention existed.
        // Positive recorded activation remains authoritative; this does not restore an old account capture.
        try(var s=connection.prepareStatement("SELECT payload FROM event_log WHERE event_type='ACCOUNT_STATE' " +
            "AND CASE WHEN json_valid(payload) THEN json_extract(payload,'$.automaticTradeScope') END=1 ORDER BY id DESC");
            var r=s.executeQuery()) {
            while(r.next())if(snapshotAutomaticMode(account,r.getString(1))) {
                if(connection.getAutoCommit())automaticModes.put(account,false);
                return true;
            }
        }
        return false;
    }
    private List<com.flippingfriend.core.ContinuationDisposition> continuationSaleDispositions(String account,long now)throws Exception {
        var history=offerHistory(account); // Validates account/source identities, external changes and appended tails.
        if(history==null)return java.util.List.of();
        long cursor;
        try(var s=connection.createStatement();var r=s.executeQuery("SELECT COALESCE(MAX(id),0) FROM position_event")) {
            r.next();cursor=r.getLong(1);
        }
        if(cachedContinuationSales==null || cursor!=continuationSalesCursor || history.size()!=continuationSalesHistorySize) {
            java.util.Set<String> disposals=new java.util.HashSet<>(),rejected=new java.util.HashSet<>();
            Map<String,OfferEvent> sources=new HashMap<>();for(var source:history)sources.put(source.getEventId(),source);
            try(var s=connection.createStatement();var r=s.executeQuery(
                "SELECT source_event_id,effect_type,item_id,source_offer_identity,observed_at,quantity FROM position_event WHERE effect_type IN ('DISPOSE','RECONCILE')")) {
                while(r.next()) {
                    String id=r.getString(1);
                    if("RECONCILE".equals(r.getString(2))){rejected.add(id);continue;}
                    var source=sources.get(id);
                    if(source!=null && source.getItemId()==r.getInt(3) && java.util.Objects.equals(source.getOfferIdentity(),r.getString(4))
                        && source.getObservedAt()==r.getLong(5) && r.getInt(6)>0 && r.getInt(6)<=source.getFilledQuantity())disposals.add(id);
                }
            }
            cachedContinuationSales=ContinuationSaleRead.read(history,disposals,rejected);continuationSalesCursor=cursor;
            continuationSalesHistorySize=history.size();
        }
        return cachedContinuationSales.stream().filter(sale->sale.getObservedAt()<=now).collect(java.util.stream.Collectors.toList());
    }
    private boolean snapshotAutomaticMode(String account,String payload) {
        final com.google.gson.JsonElement parsed;
        try{parsed=com.google.gson.JsonParser.parseString(payload);}catch(com.google.gson.JsonParseException invalid){return false;}
        if(parsed==null || !parsed.isJsonObject())return false;
        var snapshot=parsed.getAsJsonObject();var owner=snapshot.get("accountId");
        if(owner==null || owner.isJsonNull())return false; // Unassigned legacy rows prove no ownership or mode.
        if(!owner.isJsonPrimitive() || !account.equals(owner.getAsString()))
            throw new IllegalStateException("Recorded accounting scope does not match this account");
        var mode=snapshot.get("automaticTradeScope");
        return mode!=null && mode.isJsonPrimitive() && mode.getAsJsonPrimitive().isBoolean() && mode.getAsBoolean();
    }
    private void recordAutomaticMode(String account,long at)throws Exception {
        com.flippingfriend.core.AccountIdentity.require(account);
        recordedAutomaticTradeScope(account); // Validate an existing marker even for explicit automatic callers.
        if(Boolean.TRUE.equals(automaticModes.get(account)))return;
        try(var s=connection.prepareStatement("SELECT payload FROM event_log WHERE event_type=? LIMIT 1")) {
            s.setString(1,ACCOUNTING_MODE);try(var r=s.executeQuery()){if(r.next())return;}
        }
        recordEvent(at,account,ACCOUNTING_MODE,new com.google.gson.Gson().toJson(java.util.Map.of("version",1,"accountId",account)));
    }
    private List<TradeReviewRead.Row> tradeReviewRows()throws Exception {
        List<TradeReviewRead.Row> rows=new ArrayList<>();
        try(var s=connection.createStatement();var r=s.executeQuery(
            "SELECT p.id,p.source_event_id,p.source_offer_identity,p.item_id,EXISTS(SELECT 1 FROM position_event b WHERE b.effect_type='ACQUIRE' AND b.item_id=p.item_id AND b.id<p.id) " +
            "FROM position_event p WHERE p.effect_type='DISPOSE' AND p.profit_lower IS NULL ORDER BY p.id")) {
            while(r.next())if(r.getString(2)!=null && !r.getString(2).isBlank())
                rows.add(new TradeReviewRead.Row(r.getLong(1),r.getString(2),r.getString(3),r.getInt(4),r.getBoolean(5)));
        }
        return rows;
    }
    private java.util.Set<String> archivedTradeSources(String account)throws Exception {
        java.util.Set<String> archived=new java.util.HashSet<>();
        try(var s=connection.prepareStatement("SELECT payload FROM event_log WHERE event_type=? ORDER BY id")) {
            s.setString(1,TradeReviewRead.EVENT_TYPE);try(var r=s.executeQuery()) {
                while(r.next()) {
                    var request=new com.google.gson.Gson().fromJson(r.getString(1),com.flippingfriend.core.TradeReviewSnapshot.Request.class);
                    if(request==null || !request.isValid() || !account.equals(request.accountId))throw new IllegalStateException("Invalid account trade-review history");
                    if(request.archive)archived.addAll(request.sourceEventIds);else archived.removeAll(request.sourceEventIds);
                }
            }
        }
        return archived;
    }
    synchronized com.flippingfriend.core.TradeReviewSnapshot tradeReview(String account,long now)throws Exception {
        com.flippingfriend.core.AccountIdentity.require(account);
        long cursor;try(var s=connection.createStatement();var r=s.executeQuery("SELECT COALESCE(MAX(id),0) FROM position_event")){r.next();cursor=r.getLong(1);}
        java.util.Set<String> rejected=new java.util.HashSet<>();
        try(var s=connection.createStatement();var r=s.executeQuery("SELECT source_event_id FROM position_event WHERE effect_type='RECONCILE'")){while(r.next())rejected.add(r.getString(1));}
        java.util.Set<Integer> live=new java.util.HashSet<>(automaticTradeScope(account).positions.keySet());
        for(var p:positionProjections())if(p.getQuantity()>0 || p.getTotalCost()>0)live.add(p.getItemId());
        for(var p:offerProjections())if(p.getState()!=com.flippingfriend.core.OfferLifecycleState.EMPTY && p.getItemId()>0)live.add(p.getItemId());
        var history=offerHistory(account); // Validate the appended canonical tail even on a cache hit.
        if(history!=null && cachedTradeReview!=null && account.equals(cachedTradeReview.accountId)
            && cachedTradeReview.observedAt<=now && cursor==cachedTradeReview.positionCursor && live.equals(cachedReviewLiveItems))
            return new com.flippingfriend.core.TradeReviewSnapshot(account,now,cursor,cachedTradeReview.states,cachedTradeReview.archivedSources);
        var result=TradeReviewRead.classify(account,now,cursor,tradeReviewRows(),history,rejected,live,archivedTradeSources(account));
        if(history!=null){cachedTradeReview=result;cachedReviewLiveItems=java.util.Set.copyOf(live);}
        return result;
    }
    synchronized com.flippingfriend.core.TradeReviewSnapshot reviewTrades(String account,
        com.flippingfriend.core.TradeReviewSnapshot.Request request,long now)throws Exception {
        if(request==null || !request.isValid() || !account.equals(request.accountId))throw new IllegalArgumentException("Invalid account trade-review request");
        if(!connection.getAutoCommit())throw new IllegalStateException("Review requires its own transaction");
        connection.setAutoCommit(false);
        try {
            String payload=new com.google.gson.Gson().toJson(request);
            try(var s=connection.prepareStatement("SELECT payload FROM event_log WHERE event_type=? AND correlation_id=?")) {
                s.setString(1,TradeReviewRead.EVENT_TYPE);s.setString(2,request.requestId);try(var r=s.executeQuery()) {
                    if(r.next()) {
                        if(!payload.equals(r.getString(1)))throw new IllegalArgumentException("Review identity was already used for another request");
                        var result=tradeReview(account,now);connection.commit();return result;
                    }
                }
            }
            var current=tradeReview(account,now);
            if(request.positionCursor>current.positionCursor)throw new IllegalArgumentException("Future review cursor");
            java.util.Map<String,Long> cursors=new java.util.HashMap<>();for(var row:tradeReviewRows())cursors.put(row.source,row.id);
            for(String source:request.sourceEventIds) {
                if(!cursors.containsKey(source) || cursors.get(source)>request.positionCursor
                    || request.archive && !com.flippingfriend.core.TradeReviewSnapshot.ELIGIBLE.equals(current.states.get(source))
                    || !request.archive && !current.archivedSources.contains(source))
                    throw new IllegalArgumentException("Selected trade is not eligible for this review action");
            }
            recordEvent(now,request.requestId,TradeReviewRead.EVENT_TYPE,payload);
            cachedTradeReview=null;
            checkpoint.reached("TRADE_REVIEW_RECORDED");
            var result=tradeReview(account,now);connection.commit();return result;
        }catch(Exception failure){connection.rollback();discardCanonicalHistory();throw failure;}
        finally{connection.setAutoCommit(true);}
    }
	synchronized com.flippingfriend.core.PerformanceSnapshot performance(String account,String scope,long now) throws Exception {
		com.flippingfriend.core.AccountIdentity.require(account);
		if(!connection.getAutoCommit())throw new IllegalStateException("Performance requires its own read");
		connection.setAutoCommit(false);
		try {
			java.util.Set<String> completed=new java.util.HashSet<>();
			java.util.Set<String> disposalSources=new java.util.HashSet<>();
			java.util.Set<String> gappedOffers=new java.util.HashSet<>();
			try(var s=connection.createStatement();var r=s.executeQuery("SELECT source_event_id FROM position_event WHERE effect_type='DISPOSE'")) {
				while(r.next())disposalSources.add(r.getString(1));
			}
			try(var s=connection.createStatement();var r=s.executeQuery("SELECT source_offer_identity FROM position_event WHERE effect_type='RECONCILE'")) {
				while(r.next())gappedOffers.add(r.getString(1));
			}
			var history=offerHistory(account);
			// A sell order counts once, after observed full completion and with bounded realized
			// profit. Partial updates still contribute their actual money but never inflate flips.
            java.util.Set<String> disposedOffers=new java.util.HashSet<>();
            if(history!=null)for(var e:history) {
                if(e.getObservedAt()>now)continue;
                String key=e.getOfferIdentity()+":"+e.getItemId();
                if(disposalSources.contains(e.getEventId()) && !e.isBuying())disposedOffers.add(key);
                // Full SELLING counters can book the final delta before the terminal SOLD frame.
                // That terminal must be accepted, in the same recorded episode, and follow a sale.
                if(disposedOffers.contains(key) && !gappedOffers.contains(e.getOfferIdentity()) && !e.isBuying()
                    && "SOLD".equals(e.getEventType()) && e.getFilledQuantity()==e.getTotalQuantity()
                    && e.getTotalQuantity()>0)completed.add(e.getOfferIdentity());
            }
            java.util.Set<String> archived;
            try{archived=tradeReview(account,now).effectiveArchived();}catch(Exception unavailable){archived=java.util.Set.of();}
			var result=PerformanceRead.aggregate(account,scope,performanceSession(),now,recordedSales(""),completed,archived);
			connection.commit();return result;
		}catch(Exception e){connection.rollback();discardCanonicalHistory();throw e;}
		finally{connection.setAutoCommit(true);}
	}

	synchronized SnapshotReconciliationResult recordAndReconcileAccount(AccountSnapshot snapshot,
		PositionStateView view, String payload) throws Exception
	{
		return recordAndReconcileAccount(snapshot,view,payload,Instant.now().getEpochSecond());
	}

	/** Explicit clock for isolated replay; HTTP/live callers use the wall-clock overload above. */
	synchronized SnapshotReconciliationResult recordAndReconcileAccount(AccountSnapshot snapshot,
		PositionStateView view,String payload,long evaluatedAt) throws Exception
	{
		if (snapshot == null || view == null) throw new IllegalArgumentException("snapshot and view are required");
		boolean autoCommit = connection.getAutoCommit();
		connection.setAutoCommit(false);
		try
		{
			if(!snapshot.isAutomaticTradeScope() && snapshot.getAccountId()!=null && recordedAutomaticTradeScope(snapshot.getAccountId()))
                throw new IllegalArgumentException("Automatic account accounting cannot be downgraded by a legacy snapshot");
			recordEvent(snapshot.getObservedAt(), snapshot.getCorrelationId(), "ACCOUNT_STATE", payload);
            if(snapshot.isAutomaticTradeScope())recordAutomaticMode(snapshot.getAccountId(),snapshot.getObservedAt());
			checkpoint.reached("ACCOUNT_EVENT_RECORDED");
			SnapshotReconciliationResult result = new SnapshotReconciliationResult();
			Map<Integer, PositionSnapshot> seen = view.byItem();
			if (!snapshot.isAutomaticTradeScope())
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
			RiskWindow window = startRiskWindowIfProven(snapshot,evaluatedAt);
			connection.commit();
			cacheRiskWindow(window);
            if(snapshot.isAutomaticTradeScope())automaticModes.put(snapshot.getAccountId(),true);
			return result;
		}
		catch (Exception failure)
		{
			connection.rollback(); discardCanonicalHistory();
			throw failure;
		}
		finally { connection.setAutoCommit(autoCommit); }
	}

	private void reconcileSnapshotPosition(AccountSnapshot snapshot, PositionSnapshot incoming,
		SnapshotReconciliationResult result) throws Exception
	{
		PositionProjection current = positionProjection(incoming.getItemId());
		long latestEventAt = -1;
		long latestDisposalAt = -1;
		try (PreparedStatement s = connection.prepareStatement(
			"SELECT MAX(observed_at), MAX(CASE WHEN effect_type='DISPOSE' THEN observed_at END) FROM position_event WHERE item_id=?"))
		{
			s.setInt(1, incoming.getItemId());
			try (ResultSet r = s.executeQuery())
			{
				if (r.next())
				{
					long value = r.getLong(1);
					if (!r.wasNull()) latestEventAt = value;
					long disposal = r.getLong(2);
					if (!r.wasNull()) latestDisposalAt = disposal;
				}
			}
		}
		if (snapshot.getObservedAt() < latestEventAt
			|| snapshot.getObservedAt() <= latestDisposalAt
			|| (current != null && snapshot.getObservedAt() < current.getUpdatedAt()))
		{
			audit(snapshot, incoming.getItemId(), "UNCHANGED",
				current == null ? null : current.getConsistencyState().name(),
				current == null ? null : current.getConsistencyState().name(),
				"snapshot does not postdate durable position evidence");
			if (current != null) result.unchanged(current);
			return;
		}
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

	private BuyLimitProjection applyBuyLimitEffect(OfferEvent event, OfferLifecycleProjection previous,
		OfferLifecycleTransition transition) throws Exception
	{
		if (event == null || transition == null || !transition.isAccepted()
			|| !event.isBuying() || event.getFilledQuantity() <= 0) return null;
		// Counters are cumulative, including repeated observations with distinct event IDs. An
		// initial filled baseline is still evidence of limit use, even without known acquisition cost.
		boolean sameOffer = previous != null && previous.isBuying()
			&& previous.getItemId() == event.getItemId()
			&& java.util.Objects.equals(previous.getOfferIdentity(), event.getOfferIdentity());
		int quantity = sameOffer ? event.getFilledQuantity() - previous.getFilledQuantity()
			: event.getFilledQuantity();
		if (quantity <= 0) return null;
		BuyLimitProjection current = buyLimitProjection(event.getItemId());
		long started = current == null || current.hasExpired(event.getObservedAt())
			? event.getObservedAt() : current.getStartedAt();
		int prior = current == null || current.hasExpired(event.getObservedAt())
			? 0 : current.getUsedQuantity();
		BuyLimitProjection next = new BuyLimitProjection(event.getItemId(), started,
			Math.addExact(prior, quantity), event.getObservedAt(), event.getEventId(), null);
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

	/** Joins canonical acceptance, or owns a transaction for a standalone observation. */
	synchronized boolean recordExecutionObservation(String identity, long observedAt, int itemId,
		boolean completed, double fillMinutes, double predictedMinutes, double openMinutes,
		double openPredictedMinutes) throws Exception
	{
		boolean ownsTransaction = connection.getAutoCommit();
		if (ownsTransaction) connection.setAutoCommit(false);
		try
		{
			boolean claimed = claimOffer(identity, observedAt);
			if (claimed)
			{
				checkpoint.reached("EXECUTION_CLAIM_RECORDED");
				recordExecution(itemId, completed, fillMinutes, predictedMinutes, openMinutes, openPredictedMinutes);
				checkpoint.reached("EXECUTION_STATS_WRITTEN");
			}
			if (ownsTransaction) connection.commit();
			return claimed;
		}
		catch (Exception failure)
		{
			if (ownsTransaction) connection.rollback(); discardCanonicalHistory();
			throw failure;
		}
		finally
		{
			if (ownsTransaction) connection.setAutoCommit(true);
		}
	}

	/**
	 * Bounds old observation snapshots, preserving financial identities and risk anchors.
	 * <p>
     * Account snapshots are a forensic record and can expire. Accepted financial identities, offer
     * provenance and risk markers cannot: removing them could recount a duplicate or reset losses.
     * This helper does not prune financial history or rewrite any projection.
	 */
	synchronized int pruneEvents(int keepDays) throws Exception
	{
		long cutoff = Instant.now().getEpochSecond() - (long) keepDays * 86_400;
		try (PreparedStatement statement = connection.prepareStatement(
			"DELETE FROM event_log WHERE observed_at < ? AND event_type='ACCOUNT_STATE' "
				+ "AND (event_id IS NULL OR event_id='') "
                + "AND id NOT IN (SELECT account_row_id FROM sale_policy_receipt) "
                + "AND id NOT IN (SELECT MIN(a.id) FROM event_log a WHERE a.event_type='ACCOUNT_STATE' "
                + "AND CASE WHEN json_valid(a.payload) THEN json_extract(a.payload,'$.automaticTradeScope') END=1 "
                + "AND NOT EXISTS (SELECT 1 FROM event_log m WHERE m.event_type='AUTOMATIC_ACCOUNTING_V1' "
                + "AND CASE WHEN json_valid(m.payload) THEN json_extract(m.payload,'$.version') END=1 "
                + "AND json_extract(m.payload,'$.accountId')=json_extract(a.payload,'$.accountId')) "
                + "GROUP BY json_extract(a.payload,'$.accountId'))"))
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
			"SELECT payload FROM event_log WHERE observed_at >= ? "
                + "AND event_type IN ('EMPTY','BUYING','BOUGHT','CANCELLED_BUY','SELLING','SOLD','CANCELLED_SELL') "
                + "ORDER BY observed_at ASC, id ASC"))
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
