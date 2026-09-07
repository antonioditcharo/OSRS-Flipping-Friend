package com.flippingfriend.session;

import com.flippingfriend.model.FlipV2;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.game.ItemManager;
import com.flippingfriend.session.TradePlans.PlannedExit;

@Singleton
public class PositionBook
{
	private final LocalFlipManager flipManager;
	private final ItemManager itemManager;
	private final TradePlans tradePlans;

	@Inject
	public PositionBook(LocalFlipManager flipManager, ItemManager itemManager, TradePlans tradePlans)
	{
		this.flipManager = flipManager;
		this.itemManager = itemManager;
		this.tradePlans = tradePlans;
	}

	public void load() {}
	public void save() {}

	public Collection<Position> all()
	{
		List<Position> positions = new ArrayList<>();
		List<FlipV2> allFlips = flipManager.getAllFlips();
		
		for (FlipV2 f : allFlips)
		{
			if (f.getStatus() == FlipV2.FlipStatus.BUYING)
			{
				positions.add(convertToPosition(f));
			}
		}
		return positions;
	}

	public Position get(int itemId)
	{
		List<FlipV2> itemFlips = flipManager.getFlipsForItem(itemId);
		for (FlipV2 f : itemFlips)
		{
			if (f.getStatus() == FlipV2.FlipStatus.BUYING)
			{
				return convertToPosition(f);
			}
		}
		return null;
	}

	public boolean isEmpty()
	{
		return all().isEmpty();
	}

	public int size()
	{
		return all().size();
	}

	private Position convertToPosition(FlipV2 f)
	{
		Position pos = new Position();
		
		int itemId = f.getItemId();
		pos.setItemName(itemManager.getItemComposition(itemId).getName());
		pos.setQuantity(f.getOpenedQuantity() - f.getClosedQuantity());
		
		int avgBuyPrice = f.getOpenedQuantity() > 0 ? (int)(f.getSpent() / f.getOpenedQuantity()) : 0;
		pos.setTotalCost((long) avgBuyPrice * pos.getQuantity());
		
		pos.addFill(0, 0); // sets costKnown = true internally
		
		PlannedExit plan = tradePlans.get(itemId);
		if (plan != null)
		{
			pos.setTargetSellPrice(plan.getTargetSellPrice());
			pos.setStopPrice(plan.getStopPrice());
			pos.setPredictedSellMinutes(plan.getPredictedMinutes());
		}
		
		return pos;
	}
	
	public void setQuantity(int itemId, int quantity) {}
	public void setCostAndQuantity(int itemId, int quantity, int newBuyPrice) {}
	public boolean close(int itemId) { return false; }
}
