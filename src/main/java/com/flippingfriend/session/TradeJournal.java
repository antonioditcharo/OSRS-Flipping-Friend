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
	 * This is a local history/compatibility cache. The companion ledger is authoritative for money.
	 * It is never rewritten in place; a corrupt line cannot discard the rest of the local history.
 */
@Singleton
public class TradeJournal
{
	private static final Logger log = LoggerFactory.getLogger(TradeJournal.class);
	private static final String FILE_NAME = "journal.jsonl";
	/** Enough history to calibrate against without reading a huge file at every login. */
	private static final int MAX_LOADED = 2000;

	private final PluginStorage storage;
	private final ItemManager itemManager;
	private final AccountMonitor accountMonitor;

	private final List<FlipRecord> history = Collections.synchronizedList(new ArrayList<>());
	private static final class LocalSession
	{
		final List<FlipRecord> flips = new ArrayList<>();
		final Instant startedAt = Instant.now();
	}
	/** Per-run local views only; durable recommendation risk has its own account window. */
	private final Map<Path, LocalSession> sessions = new java.util.HashMap<>();

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
		sessions.computeIfAbsent(file, ignored -> new LocalSession());
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

	public synchronized void record(FlipRecord record)
	{
		Path file = storage.accountDir().resolve(FILE_NAME);
		if (loadedFrom == null) load();
		if (!file.equals(loadedFrom))
		{
			log.warn("not recording a local journal update across an unbound account switch");
			return;
		}
		long liquidValue = calculateLiquidValue();
		record.setLiquidValue(liquidValue);
		accumulateLifetime(record);
		history.add(record);
		sessions.get(file).flips.add(record);
		storage.appendLine(file, storage.gson().toJson(record));
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

	public synchronized List<FlipRecord> getSessionFlips()
	{
		LocalSession current = sessions.get(loadedFrom);
		return current == null ? Collections.emptyList() : new ArrayList<>(current.flips);
	}

	public synchronized void startSession()
	{
		if (loadedFrom != null) sessions.put(loadedFrom, new LocalSession());
		sessionStart = Instant.now();
	}

	public synchronized SessionStats sessionStats()
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

		long elapsed = Instant.now().getEpochSecond() - getSessionStart().getEpochSecond();
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

	public synchronized Instant getSessionStart()
	{
		LocalSession current = sessions.get(loadedFrom);
		return current == null ? sessionStart : current.startedAt;
	}
}
