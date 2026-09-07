mpackage com.flippingfriend.session;

import com.flippingfriend.model.FlipV2;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.game.ItemManager;
import com.flippingfriend.session.TradePlans.PlannedExit;

@Singleton
public class TradeJournal
{
	private final LocalFlipManager flipManager;
	private final ItemManager itemManager;
	private final AccountMonitor accountMonitor;
	private final TradePlans tradePlans;

	private Instant sessionStart = Instant.now();

	@Inject
	public TradeJournal(LocalFlipManager flipManager, ItemManager itemManager, AccountMonitor accountMonitor, TradePlans tradePlans)
	{
		this.flipManager = flipManager;
		this.itemManager = itemManager;
		this.accountMonitor = accountMonitor;
		this.tradePlans = tradePlans;
	}

	public void load() {}
	public void startSession() { sessionStart = Instant.now(); }
	public SessionStats sessionStats() { return computeStats(getSessionFlips()); }
	public SessionStats lifetimeStats() { return computeStats(getHistory()); }

	public List<FlipRecord> getHistory()
	{
		List<FlipRecord> history = new ArrayList<>();
		List<FlipV2> allFlips = flipManager.getAllFlips();
		
		for (FlipV2 f : allFlips)
		{
			if (f.getStatus() == FlipV2.FlipStatus.FINISHED)
			{
				history.add(convertToRecord(f));
			}
		}
		
		history.sort((a, b) -> Long.compare(a.getSoldAt(), b.getSoldAt()));
		return history;
	}

	public List<FlipRecord> getSessionFlips()
	{
		List<FlipRecord> session = new ArrayList<>();
		long startSec = sessionStart.getEpochSecond();
		for (FlipRecord r : getHistory())
		{
			if (r.getSoldAt() >= startSec)
			{
				session.add(r);
			}
		}
		return session;
	}

	private FlipRecord convertToRecord(FlipV2 f)
	{
		int qty = f.getClosedQuantity();
		int buyPrice = f.getOpenedQuantity() > 0 ? (int)(f.getSpent() / f.getOpenedQuantity()) : 0;
		int sellPrice = qty > 0 ? (int)((f.getReceivedPostTax() + f.getTaxPaid()) / qty) : 0;
		
		String itemName = itemManager.getItemComposition(f.getItemId()).getName();

		FlipRecord r = new FlipRecord(
			f.getItemId(),
			itemName,
			qty,
			buyPrice,
			sellPrice,
			f.getTaxPaid(),
			f.getProfit(),
			f.getOpenedTime(),
			f.getClosedTime(),
			0.0, 
			0.0, 
			"N/A"
		);
		
		PlannedExit plan = tradePlans.get(f.getItemId());
		if (plan != null) {
		    r = new FlipRecord(
		        f.getItemId(),
		        itemName,
		        qty,
		        buyPrice,
		        sellPrice,
		        f.getTaxPaid(),
		        f.getProfit(),
		        f.getOpenedTime(),
		        f.getClosedTime(),
		        plan.getPredictedMinutes(),
		        plan.getPredictedProfit(),
		        "N/A"
		    );
		}
		return r;
	}

	private SessionStats computeStats(List<FlipRecord> flips)
	{
		int count = 0;
		int wins = 0;
		long profit = 0;
		long tax = 0;
		double minutes = 0;
		long earliest = Long.MAX_VALUE;
		long latest = 0;
		java.util.Map<com.flippingfriend.model.MarketSector, Long> sectorProfits = new java.util.HashMap<>();
		
		for (FlipRecord f : flips)
		{
			count++;
			if (f.isWin()) wins++;
			profit += f.getProfit();
			tax += f.getTax();
			minutes += f.actualMinutes();
			earliest = Math.min(earliest, f.getBoughtAt());
			latest = Math.max(latest, f.getSoldAt());
			
			com.flippingfriend.model.MarketSector sector = com.flippingfriend.model.SectorMapper.getSector(f.getItemId(), f.getItemName());
			sectorProfits.put(sector, sectorProfits.getOrDefault(sector, 0L) + f.getProfit());
		}
		long elapsed = earliest < latest ? latest - earliest : 0;
		return new SessionStats(count, wins, profit, tax, minutes, elapsed, sectorProfits, 0L);
	}

	public java.nio.file.Path archiveAndReset() { return null; }

	public void deleteFlip(FlipRecord record) {}
	public void updateFlip(FlipRecord old, FlipRecord newR) {}
	public List<FlipRecord> recentFlips(int count) {
		List<FlipRecord> hist = getHistory();
		int size = hist.size();
		return hist.subList(Math.max(0, size - count), size);
	}
	public void touch() {}
}
