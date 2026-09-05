package com.flippingfriend.session;

import com.flippingfriend.data.PluginStorage;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.runelite.client.game.ItemManager;
import java.util.Map;

/**
 * Append-only record of every completed flip, one JSON object per line.
 * <p>
 * Append-only matters: this file is the plugin's only source of ground truth about how its own
 * predictions turned out, so it is never rewritten in place and a corrupt line is skipped rather
 * than allowed to take the rest of the history with it.
 */
@Singleton
public class TradeJournal
{
	private static final Logger log = LoggerFactory.getLogger(TradeJournal.class);
	private static final String FILE_NAME = "journal.jsonl";
	/** Where the session boundary is kept, so closing the client does not end the session. */
	private static final String SESSION_FILE_NAME = "session.json";
	/** Enough history to calibrate against without reading a huge file at every login. */
	private static final int MAX_LOADED = 2000;
	/**
	 * How long a break can be before reopening counts as a new session rather than a continuation.
	 *
	 * <p>Long enough that closing the client for a meal, a crash, or a plugin reload keeps the
	 * session that was in progress; short enough that yesterday's losses are not still being charged
	 * against today's drawdown budget. Six hours also sits clear of the four-hour buy limit window,
	 * so a resumed session is never one whose limits have silently rolled over mid-count.
	 */
	private static final long RESUME_WINDOW_SECONDS = 6 * 60 * 60;

	private final PluginStorage storage;
	private final ItemManager itemManager;
	private final AccountMonitor accountMonitor;

	private final List<FlipRecord> history = Collections.synchronizedList(new ArrayList<>());
	private final List<FlipRecord> session = Collections.synchronizedList(new ArrayList<>());

	private int lifetimeFlips = 0;
	private int lifetimeWins = 0;
	private long lifetimeProfit = 0;
	private long lifetimeTax = 0;
	private double lifetimeMinutes = 0;
	private long lifetimeEarliest = Long.MAX_VALUE;
	private long lifetimeLatest = 0;
	private final java.util.Map<com.flippingfriend.model.MarketSector, Long> lifetimeSectorProfits = new java.util.HashMap<>();

	private Path loadedFrom;
	private Instant sessionStart = Instant.now();

	/**
	 * The session boundary on disk.
	 *
	 * <p>Only the boundary, not the flips: every completed flip is already durable in the journal, so
	 * writing the session's own copy of them would be a second record of the same thing that could
	 * disagree with the first. The session view is derived from the journal on the way back in.
	 */
	private static final class SessionMark
	{
		private long startedAt;
		private long lastSeenAt;
	}

	@Inject
	public TradeJournal(PluginStorage storage, ItemManager itemManager, AccountMonitor accountMonitor)
	{
		this.storage = storage;
		this.itemManager = itemManager;
		this.accountMonitor = accountMonitor;
	}

	public synchronized void load()
	{
		Path file = storage.accountDir().resolve(FILE_NAME);
		if (file.equals(loadedFrom))
		{
			return;
		}

		history.clear();
		lifetimeFlips = 0;
		lifetimeWins = 0;
		lifetimeProfit = 0;
		lifetimeTax = 0;
		lifetimeMinutes = 0;
		lifetimeEarliest = Long.MAX_VALUE;
		lifetimeLatest = 0;
		lifetimeSectorProfits.clear();

		session.clear();

		List<String> lines = storage.readLines(file);
		int from = Math.max(0, lines.size() - MAX_LOADED);
		for (int i = 0; i < lines.size(); i++)
		{
			String line = lines.get(i).trim();
			if (line.isEmpty())
			{
				continue;
			}
			try
			{
				FlipRecord record = storage.gson().fromJson(line, FlipRecord.class);
				if (record != null)
				{
					accumulateLifetime(record);
					if (i >= from)
					{
						history.add(record);
					}
				}
			}
			catch (Exception ex)
			{
				log.debug("skipping unreadable journal line {}", i);
			}
		}
		loadedFrom = file;
		resumeSession();
	}

	/**
	 * Pick the session back up where the client left it.
	 *
	 * <p>{@code sessionStart} was a field initialised to {@code Instant.now()} and never read back
	 * from anywhere, so every restart began a brand new session: flips 0, profit 0, elapsed 0, and --
	 * the part that matters -- a drawdown circuit breaker that had forgotten every loss it was
	 * holding. A player who closed the client after a bad hour reopened it with a full loss budget
	 * and no memory of why it should not be.
	 *
	 * <p>The flips come back from the journal rather than from a saved copy of the session list.
	 * There is one durable record of what happened and this reads it; a parallel file would be a
	 * second answer to the same question, free to drift from the first.
	 */
	private void resumeSession()
	{
		Path mark = storage.accountDir().resolve(SESSION_FILE_NAME);
		SessionMark saved = storage.readJson(mark, SessionMark.class, null);
		long now = Instant.now().getEpochSecond();

		if (saved == null || saved.startedAt <= 0
			|| now - saved.lastSeenAt > RESUME_WINDOW_SECONDS)
		{
			startSession();
			return;
		}

		sessionStart = Instant.ofEpochSecond(saved.startedAt);
		synchronized (history)
		{
			for (FlipRecord record : history)
			{
				if (record.getSoldAt() >= saved.startedAt)
				{
					session.add(record);
				}
			}
		}
		touch();
	}

	/**
	 * Write down that the session is still going.
	 *
	 * <p>Called wherever the plugin persists the rest of its state, so a session that is open for
	 * hours without completing a flip is not mistaken for one that was abandoned.
	 */
	public synchronized void touch()
	{
		SessionMark mark = new SessionMark();
		mark.startedAt = sessionStart.getEpochSecond();
		mark.lastSeenAt = Instant.now().getEpochSecond();
		storage.writeJson(storage.accountDir().resolve(SESSION_FILE_NAME), mark, SessionMark.class);
	}

	private void accumulateLifetime(FlipRecord record)
	{
		lifetimeFlips++;
		if (record.isWin())
		{
			lifetimeWins++;
		}
		lifetimeProfit += record.getProfit();
		lifetimeTax += record.getTax();
		lifetimeMinutes += record.actualMinutes();
		lifetimeEarliest = Math.min(lifetimeEarliest, record.getBoughtAt());
		lifetimeLatest = Math.max(lifetimeLatest, record.getSoldAt());
		
		com.flippingfriend.model.MarketSector sector = com.flippingfriend.model.SectorMapper.getSector(record.getItemId(), record.getItemName());
		lifetimeSectorProfits.put(sector, lifetimeSectorProfits.getOrDefault(sector, 0L) + record.getProfit());
	}

	public void record(FlipRecord record)
	{
		long liquidValue = calculateLiquidValue();
		record.setLiquidValue(liquidValue);

		synchronized (this)
		{
			accumulateLifetime(record);
		}
		history.add(record);
		session.add(record);
		storage.appendLine(storage.accountDir().resolve(FILE_NAME), storage.gson().toJson(record));
		// The session is demonstrably alive, and this is the cheapest moment to say so.
		touch();
	}

	private long calculateLiquidValue()
	{
		AccountState state = accountMonitor.getState();
		if (!state.isLoggedIn()) return 0;
		long value = state.getInventoryCoins() + state.getBankCoins() + state.getCollectionBoxCoins() + state.getCommittedCoins();
		for (Map.Entry<Integer, Integer> entry : state.getHoldings().entrySet())
		{
			value += (long) itemManager.getItemPrice(entry.getKey()) * entry.getValue();
		}
		return value;
	}

	/** Full loaded history, used for calibration. */
	public List<FlipRecord> getHistory()
	{
		synchronized (history)
		{
			return new ArrayList<>(history);
		}
	}

	public List<FlipRecord> getSessionFlips()
	{
		synchronized (session)
		{
			return new ArrayList<>(session);
		}
	}

	public void startSession()
	{
		session.clear();
		sessionStart = Instant.now();
		touch();
	}

	/**
	 * Starts the all-time figures again, keeping the trades themselves.
	 *
	 * <p>The journal is renamed, not deleted. It is the only record of how this plugin's own
	 * predictions turned out -- the calibrator is built from it and nothing else -- and a flip that
	 * was not recorded is gone for good. Wanting the counter back at zero is not the same as wanting
	 * the evidence destroyed, so the file moves aside with a timestamp and a fresh one starts.
	 *
	 * <p>Restoring is renaming it back. The plugin reads whatever {@code journal.jsonl} contains at
	 * load, so nothing else needs to happen.
	 *
	 * @return where the old journal was put, or null if there was nothing to move
	 */
	public synchronized Path archiveAndReset()
	{
		Path file = storage.accountDir().resolve(FILE_NAME);
		Path archived = null;
		try
		{
			if (java.nio.file.Files.exists(file))
			{
				archived = storage.accountDir().resolve(FILE_NAME + ".before-reset-"
					+ java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
						.withZone(java.time.ZoneId.systemDefault()).format(Instant.now()));
				java.nio.file.Files.move(file, archived);
			}
		}
		catch (Exception ex)
		{
			log.warn("could not archive the journal; leaving it alone rather than clearing it", ex);
			return null;
		}

		history.clear();
		session.clear();
		lifetimeFlips = 0;
		lifetimeWins = 0;
		lifetimeProfit = 0;
		lifetimeTax = 0;
		lifetimeMinutes = 0;
		lifetimeEarliest = Long.MAX_VALUE;
		lifetimeLatest = 0;
		lifetimeSectorProfits.clear();
		// So a later load() reads the new, empty file rather than short-circuiting on the path it
		// believes it has already read.
		loadedFrom = null;
		startSession();
		return archived;
	}

	public SessionStats sessionStats()
	{
		int flips = 0;
		int wins = 0;
		long profit = 0;
		long tax = 0;
		double minutes = 0;
		java.util.Map<com.flippingfriend.model.MarketSector, Long> sectorProfits = new java.util.HashMap<>();

		for (FlipRecord record : getSessionFlips())
		{
			flips++;
			if (record.isWin())
			{
				wins++;
			}
			profit += record.getProfit();
			tax += record.getTax();
			minutes += record.actualMinutes();
			
			com.flippingfriend.model.MarketSector sector = com.flippingfriend.model.SectorMapper.getSector(record.getItemId(), record.getItemName());
			sectorProfits.put(sector, sectorProfits.getOrDefault(sector, 0L) + record.getProfit());
		}

		long elapsed = Instant.now().getEpochSecond() - sessionStart.getEpochSecond();
		return new SessionStats(flips, wins, profit, tax, minutes, elapsed, sectorProfits);
	}

	/**
	 * Totals across every flip still on disk, not just this session.
	 * <p>
	 * Worth showing separately: a session figure swings wildly on a handful of trades, so it says
	 * very little about whether the thing is actually working. The lifetime figure is what answers
	 * that, and it is the reason the journal is append-only rather than reset on login.
	 */
	public synchronized SessionStats lifetimeStats()
	{
		// Elapsed is measured across the span the trades actually cover, so an idle week between
		// sessions does not drag the hourly rate towards zero.
		long elapsed = lifetimeFlips == 0 || lifetimeEarliest == Long.MAX_VALUE ? 0 : Math.max(0, lifetimeLatest - lifetimeEarliest);
		return new SessionStats(lifetimeFlips, lifetimeWins, lifetimeProfit, lifetimeTax, lifetimeMinutes, elapsed, new java.util.HashMap<>(lifetimeSectorProfits));
	}

	/** The most recent completed flips, newest first. */
	public List<FlipRecord> recentFlips(int limit)
	{
		List<FlipRecord> all = getHistory();
		List<FlipRecord> recent = new ArrayList<>();
		for (int i = all.size() - 1; i >= 0 && recent.size() < limit; i--)
		{
			recent.add(all.get(i));
		}
		return recent;
	}

	public boolean hasHistory()
	{
		return !history.isEmpty();
	}

	public Instant getSessionStart()
	{
		return sessionStart;
	}
}
