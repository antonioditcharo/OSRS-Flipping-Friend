package com.flippingfriend.session;

import com.flippingfriend.data.PluginStorage;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Remembers the exit the engine had in mind when it suggested a buy, so that when the buy actually
 * fills the resulting position carries the target it was bought for.
 * <p>
 * Without this the sell side would have to re-derive a target from whatever the market looks like
 * minutes later, and the journal could never compare a prediction against its outcome — which would
 * leave the calibrator with nothing to learn from.
 */
@Singleton
public class TradePlans
{
	private static final Logger log = LoggerFactory.getLogger(TradePlans.class);
	private static final String FILE_NAME = "trade-plans.json";
	private static final Type PLAN_MAP = new TypeToken<Map<Integer, PlannedExit>>()
	{
	}.getType();

	/** Plans do not survive long; a stale intention is worse than none. */
	private static final Duration TTL = Duration.ofHours(6);

	private final PluginStorage storage;
	private final Map<Integer, PlannedExit> plans = new ConcurrentHashMap<>();

	private Path loadedFrom;

	@Inject
	public TradePlans(PluginStorage storage)
	{
		this.storage = storage;
	}

	public synchronized void load()
	{
		Path file = storage.accountDir().resolve(FILE_NAME);
		if (file.equals(loadedFrom))
		{
			return;
		}

		plans.clear();
		Map<Integer, PlannedExit> loaded = storage.readJson(file, PLAN_MAP, null);
		if (loaded != null)
		{
			long now = Instant.now().getEpochSecond();
			long ttlSeconds = TTL.getSeconds();
			for (Map.Entry<Integer, PlannedExit> entry : loaded.entrySet())
			{
				if (entry.getValue() != null && entry.getValue().createdAt + ttlSeconds > now)
				{
					plans.put(entry.getKey(), entry.getValue());
				}
			}
		}

		loadedFrom = file;
	}

	public synchronized void save()
	{
		if (loadedFrom == null || !storage.hasAccount())
		{
			return;
		}
		
		// Prune before saving
		long now = Instant.now().getEpochSecond();
		long ttlSeconds = TTL.getSeconds();
		plans.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().createdAt + ttlSeconds <= now);

		storage.writeJson(storage.accountDir().resolve(FILE_NAME), plans, PLAN_MAP);
	}

	public void plan(int itemId, int targetSellPrice, int stopPrice, double predictedMinutes,
		long predictedProfit)
	{
		plans.put(itemId, new PlannedExit(targetSellPrice, stopPrice, predictedMinutes, predictedProfit,
			Instant.now().getEpochSecond()));
	}

	public PlannedExit get(int itemId)
	{
		PlannedExit plan = plans.get(itemId);
		if (plan == null)
		{
			return null;
		}
		if (plan.createdAt + TTL.getSeconds() <= Instant.now().getEpochSecond())
		{
			plans.remove(itemId);
			return null;
		}
		return plan;
	}

	public void clear(int itemId)
	{
		plans.remove(itemId);
	}

	public void clear()
	{
		plans.clear();
		save(); // clear persisted as well
	}

	/** Applies a remembered plan to a freshly opened position, if there is one. */
	public void applyTo(Position position)
	{
		if (position == null)
		{
			return;
		}
		PlannedExit plan = get(position.getItemId());
		if (plan == null)
		{
			return;
		}
		// Each field on its own terms.
		//
		// All three used to hang off whether the TARGET was unset, which coupled them for no reason
		// -- and now that a live sell offer writes the target, a position could arrive here with a
		// target already set and never be given the stop or the duration it was missing. The graph's
		// stop line was undrawn for exactly that class of reason once already: a field nothing
		// happened to set, because the thing that sets it was gated on a different field.
		if (position.getTargetSellPrice() <= 0)
		{
			position.setTargetSellPrice(plan.targetSellPrice);
		}
		// The level at which this stops being a trade to manage and becomes a loss to cut. It was
		// decided when the trade was chosen, so it belongs to the plan.
		if (position.getStopPrice() <= 0)
		{
			position.setStopPrice(plan.stopPrice);
		}
		if (position.getPredictedSellMinutes() <= 0)
		{
			position.setPredictedSellMinutes(plan.predictedMinutes);
		}
	}

	public static final class PlannedExit
	{
		private final int targetSellPrice;
		private final int stopPrice;
		private final double predictedMinutes;
		private final long predictedProfit;
		private final long createdAt;

		PlannedExit(int targetSellPrice, int stopPrice, double predictedMinutes, long predictedProfit,
			long createdAt)
		{
			this.targetSellPrice = targetSellPrice;
			this.stopPrice = stopPrice;
			this.predictedMinutes = predictedMinutes;
			this.predictedProfit = predictedProfit;
			this.createdAt = createdAt;
		}

		public int getTargetSellPrice()
		{
			return targetSellPrice;
		}

		public double getPredictedMinutes()
		{
			return predictedMinutes;
		}

		public long getPredictedProfit()
		{
			return predictedProfit;
		}
	}
}
