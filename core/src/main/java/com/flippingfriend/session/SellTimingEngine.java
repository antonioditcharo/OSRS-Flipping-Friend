package com.flippingfriend.session;

import com.flippingfriend.RiskProfile;
import com.flippingfriend.TradingHorizon;
import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import com.flippingfriend.model.FillCurve;
import com.flippingfriend.model.FillEstimate;
import com.flippingfriend.model.FillModel;
import com.flippingfriend.model.ItemFeatures;
import com.flippingfriend.model.Regime;
import com.flippingfriend.model.TaxCalculator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Lists verified stock promptly. Listing and sacrificing the planned profit are separate decisions:
 * a healthy position retains its target throughout the configured holding horizon. An unvalidated
 * fill model's small profit/time ratio cannot override that target. Expiry permits reassessing the
 * target at the current ask. Only fresh stops or explicit wind-down use the urgent price search.
 */
@Singleton
public class SellTimingEngine
{
	/** Sell-price grid, as offsets from the current instant-buy price. */
	private static final double[] SELL_OFFSETS = {-0.010, -0.006, -0.003, -0.001, 0.0, 0.002};

	private final FillModel fillModel;
	private final TaxCalculator taxCalculator;
    private final boolean synchronousAnalysis;

	@Inject
	public SellTimingEngine(FillModel fillModel, TaxCalculator taxCalculator)
	{
		this(fillModel,taxCalculator,false);
	}
    /** Explicit offline harness mode; the price and financial rules are identical. */
    public SellTimingEngine(FillModel fillModel,TaxCalculator taxCalculator,boolean synchronousAnalysis) {
		this.fillModel = fillModel;
		this.taxCalculator = taxCalculator;
		this.synchronousAnalysis=synchronousAnalysis;
	}

	public SellDecision evaluate(Position position, LatestPrice latest, ItemFeatures features,
		List<Candle> series, TradingHorizon horizon, Instant now, boolean inInventory, boolean sellOnly)
	{
		return evaluate(position, latest, features, series, horizon, now, 0, inInventory, sellOnly, false);
	}

    /**
     * Prices verified, unlisted stock. A quiet last-trade feed is not a reason to leave it unlisted.
     * With aged trades only a non-loss limit is supported; stops and spread crossing need fresh
     * evidence. The caller must separately verify current holdings, acquisition and feed freshness.
     */
    public SellDecision evaluateManagedExit(Position position, LatestPrice latest, ItemFeatures features,
        List<Candle> series, TradingHorizon horizon, Instant now, long minProfitPerFlip,
        boolean inInventory, boolean sellOnly, boolean isSkipped)
    {
        if (position == null || !position.isCostKnown() || position.getQuantity() <= 0)
            return SellDecision.hold("Waiting for verified acquisition cost and remaining stock.", Double.NaN);
        if (latest == null || !latest.isComplete() || latest.secondsSinceHigh(now) < 0
            || latest.secondsSinceLow(now) < 0 || latest.getHighTime() == null || latest.getLowTime() == null
            || latest.getHighTime() <= 0 || latest.getLowTime() <= 0)
            return SellDecision.hold("Waiting for a supported market quote.", Double.NaN);
        if (latest.stalestSideSeconds(now) <= 300)
            return evaluate(position, latest, features, series, horizon, now, minProfitPerFlip,
                inInventory, sellOnly, isSkipped);

        int floor = taxCalculator.breakEvenSellPriceForCost(position.getItemId(),
            position.getTotalCost(), position.getQuantity());
        if (floor <= 0)
            return SellDecision.hold("No legal Grand Exchange price covers this position's recorded cost after tax.", Double.NaN);
        int price = Math.max(protectedPrice(position,horizon,now,floor), latest.getHigh());
        return new SellDecision(SellDecision.Action.SELL, price,
            "Market trades are quiet (last sell-side trade " + latest.secondsSinceHigh(now) / 60
                + "m ago; buy-side trade " + latest.secondsSinceLow(now) / 60
                + "m ago). This limit preserves the planned target while within its holding horizon and covers the exact recorded cost after tax. A fill is not guaranteed.",
            Double.NaN, taxCalculator.netProceeds(position.getItemId(), price, position.getQuantity()) - position.getTotalCost());
    }

	/**
	 * @param minProfitPerFlip kept on the signature for callers that still pass it. It no longer
	 *                         gates anything: an exit worth very little is still an exit, and the
	 *                         question of whether a trade is worth a slot belongs to opening one.
	 */
	public SellDecision evaluate(Position position, LatestPrice latest, ItemFeatures features,
		List<Candle> series, TradingHorizon horizon, Instant now, long minProfitPerFlip,
		boolean inInventory, boolean sellOnly, boolean isSkipped)
	{
		RiskProfile profile = horizon.getProfile();
		if (position == null || position.getQuantity() <= 0)
		{
			// Not a decision about a holding. There is no holding.
			return SellDecision.hold("Nothing held.", 0);
		}

		int itemId = position.getItemId();
		int quantity = position.getQuantity();
		long minutesHeld = position.minutesHeld(now.getEpochSecond());

		int marketSell = quoteOrLastTraded(latest, series, position);
		if (marketSell <= 0)
		{
			// The one honest exception, and it is not a decision to hold: there is no price anywhere
			// -- not in the quote, not in the history, not even a cost basis -- so there is no number
			// to put in the offer. Anything else here would be inventing one.
			return SellDecision.hold("Waiting for a current price on this item.", 0);
		}

		boolean throughStop = StopLossEvidence.confirmed(position,latest,series,now);
		// How far the pricing may reach to get the offer filled. Crossing the spread costs real gold,
		// so reserve the modeled discount for a fresh stop or explicit player wind-down.
		boolean urgent = sellOnly || throughStop;

		int marketBuy = latest == null || latest.getLow() == null ? 0 : latest.getLow();
		// Below this the sale realises a loss, whatever the fill model thinks of the price.
		int breakEven = position.isCostKnown() ? taxCalculator.breakEvenSellPriceForCost(itemId,position.getTotalCost(),quantity) : 0;
		// Only these two authorise one. Time passing does not: a position past its patience budget
		// is still a position above its stop, and the stop is where the risk control lives.
		boolean lossAllowed = throughStop || sellOnly;
        if(position.isCostKnown() && breakEven==0 && !lossAllowed)
            return SellDecision.hold("No legal Grand Exchange price covers this position's recorded cost after tax.",Double.NaN);
		PricedExit exit;
        if (!urgent) {
            // Put the order on the market now, at its intended limit. A quiet/temporarily lower
            // last trade says nothing about what a patient listing will receive.
            int price=Math.max(marketSell,protectedPrice(position,horizon,now,breakEven));
            price=predictiveNonLossLimit(position,latest,series,horizon,now,price,breakEven);
            var forecast=forecastForOrder(itemId,price,quantity,series,horizon);
            exit=new PricedExit(price,forecast==null?Double.NaN:forecast.getExpectedTotalSeconds()/60.0);
        } else exit = bestExit(series, itemId, marketSell, marketBuy, quantity, horizon,
			position.isCostKnown()?position.getTotalCost():0, true, breakEven, position.getTargetSellPrice(), lossAllowed,
            throughStop?latest.getLow():1);

		long expectedProfit = position.isCostKnown()
			? taxCalculator.netProceeds(itemId,exit.price,quantity)-position.getTotalCost()
			: taxCalculator.netProceeds(itemId, exit.price, quantity);

		if (throughStop)
		{
			// Still a sale -- the player places a sell offer to do it -- and it happens now like every
			// other one. The action differs only so the card and the overlay can warn that this one
			// realises a loss.
			return new SellDecision(SellDecision.Action.CUT, exit.price,
				"Fresh trades on both sides and a completed five-minute volume bucket confirm the recorded stop loss. "
                    + "This exit may realize a loss; its limit is not discounted below the latest reported instant-sell price. A fill is not guaranteed.", exit.expectedMinutes, expectedProfit);
		}

		return new SellDecision(SellDecision.Action.SELL, exit.price,
			(exit.price!=Math.max(marketSell,protectedPrice(position,horizon,now,breakEven)) && !urgent
                ? "Completed public price history supports this limit in the near-term forecast. Price alternatives were compared using quantity, estimated fill time and exact after-tax proceeds; the original target and cost floor remain protected. " : "")
                +reasonFor(position, features, minutesHeld, horizon, inInventory, sellOnly, marketSell),
			exit.expectedMinutes, expectedProfit);
	}

    private int predictiveNonLossLimit(Position position,LatestPrice latest,List<Candle> series,
            TradingHorizon horizon,Instant now,int fallback,int floor) {
        return predictiveNonLossLimit(position,latest,series,horizon,now,fallback,
            protectedPrice(position,horizon,now,floor),Integer.MAX_VALUE);
    }
    /** Called only after working-sale patience, recent progress and repeated-market proof pass. */
    public int maintenanceLimit(Position position,int remaining,LatestPrice latest,List<Candle> series,
            TradingHorizon horizon,Instant now,int fallback,int floor,int ceiling) {
        if(position==null || !position.isCostKnown() || remaining<=0 || remaining>position.getQuantity())return fallback;
        long cost=com.flippingfriend.core.SaleAccounting.allocateCost(position.getTotalCost(),position.getQuantity(),remaining);
        Position subset=new Position(position.getItemId(),position.getItemName(),remaining,cost,position.getOpenedAt(),true);
        return predictiveNonLossLimit(subset,latest,series,horizon,now,fallback,floor,ceiling);
    }
    private int predictiveNonLossLimit(Position position,LatestPrice latest,List<Candle> series,
            TradingHorizon horizon,Instant now,int fallback,int floor,int ceiling) {
        if(!position.isCostKnown() || floor<=0 || latest==null || !latest.isComplete() || series==null)return fallback;
        int seconds=Math.min(1800,com.flippingfriend.model.PredictivePriceChoices.horizon(horizon.legHorizonHours()));
        var forecast=synchronousAnalysis?com.flippingfriend.model.PublicPriceForecast.fit(series,List.of(),now.getEpochSecond())
            :com.flippingfriend.model.PublicPriceForecast.ready(series,List.of(),now.getEpochSecond());
        int[] prices=com.flippingfriend.model.PredictivePriceChoices.prices(false,latest,series,forecast,seconds,new double[]{0},now.getEpochSecond());
        if(prices.length<2)return fallback;
        FillCurve curve=FillCurve.overRecentHistory(series);double best=nonLossRate(position,fallback,curve,horizon);int chosen=fallback;
        for(int price:prices)if(price>=floor && price<=ceiling) {
            double rate=nonLossRate(position,price,curve,horizon);
            if(rate>best*1.10 && rate>0){best=rate;chosen=price;}
        }
        return chosen;
    }
    private double nonLossRate(Position position,int price,FillCurve curve,TradingHorizon horizon) {
        long profit=taxCalculator.netProceeds(position.getItemId(),price,position.getQuantity())-position.getTotalCost();
        if(profit<=0)return 0;
        FillEstimate fill=fillModel.estimateSell(curve,price,position.getQuantity(),horizon.legHorizonHours());
        return fill.isPlausible()?profit*fill.getProbability()/Math.max(1.0/60,fill.getExpectedHours()):0;
    }

	/**
	 * Why this is being sold now. Every branch ends in a sale; only the wording differs.
	 * <p>
	 * The wording is not decoration. A player who is told to sell wants to know whether the plan
	 * worked, whether it went wrong, or whether this is simply the rule -- and those three read very
	 * differently on a card.
	 */
	private static String reasonFor(Position position, ItemFeatures features, long minutesHeld,
		TradingHorizon horizon, boolean inInventory, boolean sellOnly, int marketSell)
	{
		if (sellOnly)
		{
			return "Sell-only mode is on, so this position is being closed out.";
		}
		if (!position.isCostKnown())
		{
			return inInventory
				? "This is in your inventory, so it is being listed for sale."
				: "Listing this banked item.";
		}
		int target = position.getTargetSellPrice();
		if (target > 0 && marketSell >= target)
		{
			return "Target price reached. Selling.";
		}
		if (minutesHeld >= horizon.maxHoldMinutes())
		{
			return "The configured holding horizon has elapsed. Reassessing at the current sell-side limit while preserving the exact after-tax cost floor.";
		}
        String trend=features!=null && features.isUsable() && features.getRegime()==Regime.FALLING
            ? " The recent trend is falling; the target and stop remain in force." : "";
        return (target>0 ? "Listing at the planned target within the configured holding horizon. A lower last trade is not a reason to give away the planned profit; a fill is not guaranteed."
            : "Listing at the current sell-side limit without discounting it for a modeled faster fill. No original target is known; a fill is not guaranteed.")+trend;
	}

    /** Price guard only; never a valuation, execution promise or invented historical target. */
    public static int protectedPrice(Position position,TradingHorizon horizon,Instant now,int costFloor) {
        if(position!=null && position.isCostKnown())
            return Math.max(Math.max(1,costFloor),position.protectedOriginalTarget(now.getEpochSecond(),horizon.maxHoldMinutes()));
        return Math.max(1,costFloor);
    }

	/**
	 * A price to quote the offer at, from the live quote if there is one and from the last bar that
	 * traded if there is not.
	 * <p>
	 * "Nobody is buying this at the moment" used to be a reason to hold, and it is not one: a quote
	 * that has gone quiet is missing information, not evidence that the item cannot be sold. The
	 * history is the next best witness, and the price paid is the last -- an offer at cost is a
	 * worse trade than an offer at the market, but it is enormously better than a position nobody
	 * ever lists.
	 */
	private static int quoteOrLastTraded(LatestPrice latest, List<Candle> series, Position position)
	{
		if (latest != null && latest.getHigh() != null && latest.getHigh() > 0)
		{
			return latest.getHigh();
		}
		if (series != null)
		{
			for (int i = series.size() - 1; i >= 0; i--)
			{
				Candle bar = series.get(i);
				if (bar.getAvgHighPrice() != null && bar.getAvgHighPrice() > 0)
				{
					return bar.getAvgHighPrice();
				}
				if (bar.getAvgLowPrice() != null && bar.getAvgLowPrice() > 0)
				{
					return bar.getAvgLowPrice();
				}
			}
		}
		return position.isCostKnown() ? position.getAverageCost() : 0;
	}

	private PricedExit bestExit(List<Candle> series, int itemId, int marketSell, int marketBuy,
		int quantity, TradingHorizon horizon, long totalCost, boolean urgent, int breakEven,
		int target, boolean lossAllowed,int observedSellFloor)
	{
		// The cheapest price this sale is allowed to name. See exitPrices: the grid is anchored on
		// the market, and a position whose break-even sits above the market has no profitable price
		// in it at all -- so the floor is what stops the search returning the least-bad loss.
		int floor = Math.max(observedSellFloor,lossAllowed || breakEven <= 0 ? 1 : breakEven);

		if (series == null || series.isEmpty())
		{
			return new PricedExit(Math.max(floor, marketSell), Double.NaN);
		}

		// Whatever happens below, this is what comes back. The search used to be able to return null
		// when no price on the grid looked plausible, and the caller read that as "nobody is buying
		// this, hold until they are" -- which is a position never listed at all on the strength of a
		// fill model declining to commit. The offer goes on at the market price instead.
		PricedExit fallback = new PricedExit(Math.max(floor, marketSell), Double.NaN);

		double horizonHours = horizon.legHorizonHours();
		FillCurve curve = FillCurve.overRecentHistory(series);
		PricedExit best = null;
		// A position already under water has no positive-gain exit, so the best available one is
		// still the one to take; starting the search at zero would return nothing and hold forever.
		double bestRate = -Double.MAX_VALUE;

		for (int price : exitPrices(marketSell, marketBuy, urgent, breakEven, target))
		{
			if (price < floor)
			{
				continue;
			}
			FillEstimate fill = fillModel.estimateSell(curve, price, quantity, horizonHours);
			if (!fill.isPlausible())
			{
				continue;
			}

			long proceeds = taxCalculator.netProceeds(itemId, price, quantity);
			long gain = proceeds-totalCost;
			double hours = Math.max(fill.getExpectedHours(), 1.0 / 60.0);
			double impatience = urgent ? 1.5 : 1.0;

			double rate;
			if (gain > 0)
			{
				rate = gain * fill.getProbability() / Math.pow(hours, impatience);
			}
			else
			{
				// For a negative gain the rate is maximised by making it as close to zero as
				// possible, so the two penalties invert: a long wait and a low probability both
				// make the number more negative rather than less.
				rate = gain * Math.pow(hours, impatience) / fill.getProbability();
			}

			if (rate > bestRate)
			{
				bestRate = rate;
				best = new PricedExit(price, fill.getExpectedMinutes());
			}
		}

		return best == null ? fallback : best;
	}

    /** Re-evaluate the actual displayed order after quantity caps or price stabilization. */
    public com.flippingfriend.core.ExecutionForecast forecastForOrder(int item, int price, int quantity,
            List<Candle> series, TradingHorizon horizon) {
        if (series == null || series.isEmpty() || item <= 0 || price <= 0 || quantity <= 0) return null;
        FillCurve curve = FillCurve.overRecentHistory(series);
        FillEstimate fill = fillModel.estimateSell(curve,price,quantity,horizon.legHorizonHours());
        if (!fill.isPlausible()) return null;
        var forecast = fillModel.forecast(item,false,curve,price,quantity,horizon.legHorizonHours(),1,fill,1,fill.getExpectedHours());
        return forecast.isValid() ? forecast : null;
    }

	/**
	 * The prices the exit search may consider.
	 * <p>
	 * The ordinary grid runs from one percent under the instant-buy price to a fraction over it,
	 * which is the right range for a sale that is wanted rather than needed: it trades a little
	 * margin for a faster fill without giving the spread away.
	 * <p>
	 * It is the wrong range for an exit that has to happen. A fresh stop and an explicit
	 * sell-only wind-down need to be able to <em>cross</em> the spread,
	 * and on an item quoting five percent wide an ask one percent under the top of the book is
	 * still queued behind everybody. So those cases get the standing bid, a coin under it and
	 * the midpoint added to the search. The fill model still judges every one of them, so a slower
	 * price that keeps more of the position's value can still win.
	 *
	 * @param marketBuy the instant-sell price, or 0 when the quote has no bid side
	 */
	static List<Integer> exitPrices(int marketSell, int marketBuy, boolean urgent)
	{
		return exitPrices(marketSell, marketBuy, urgent, 0, 0);
	}

	/**
	 * @param breakEven the price at which this position stops losing money, or 0 when unknown
	 * @param target    the exit the trade was opened for, or 0 when there was never a plan
	 */
	static List<Integer> exitPrices(int marketSell, int marketBuy, boolean urgent, int breakEven,
		int target)
	{
		List<Integer> prices = new ArrayList<>(SELL_OFFSETS.length + 8);
		for (double offset : SELL_OFFSETS)
		{
			add(prices, marketSell + (int) Math.round(marketSell * offset));
		}

		// The two prices that decide whether this trade made money, neither of which the offsets
		// above can reach.
		//
		// <b>The grid tops out a fifth of a percent over the market, and break-even is cost plus two
		// percent of tax.</b> So unless the price had already risen since the buy, no price in the
		// search made money and the best the engine could do was pick the smallest loss -- which it
		// then reported as the recommendation. Live, on 696 Anti-venom(4) bought at 10,767 with the
		// market at 10,871: break-even 10,986, grid maximum 10,893, recommendation 10,893, expected
		// profit <b>minus 63,300 gp</b>, on a position eighteen minutes old whose stop was more than
		// a thousand gp lower. That was not a judgement about this item; it is arithmetic, and it
		// applied to every position the market had not already carried upwards.
		//
		// Adding them puts a profitable answer in front of the search. Whether it wins is still the
		// fill model's call -- a break-even price nobody will pay scores badly and loses to a better
		// one -- but it can no longer be absent from the question.
		if (breakEven > 0)
		{
			add(prices, breakEven);
			add(prices, breakEven + (int) Math.round(breakEven * 0.002));
			add(prices, breakEven + (int) Math.round(breakEven * 0.006));
		}
		if (target > breakEven && target > 0)
		{
			add(prices, target);
			// Between break-even and the target, so giving up on the plan is not all-or-nothing.
			if (breakEven > 0)
			{
				add(prices, (breakEven + target) / 2);
			}
		}

		if (urgent && marketBuy > 0 && marketBuy < marketSell)
		{
			add(prices, (marketBuy + marketSell) / 2);
			add(prices, marketBuy);
			add(prices, marketBuy - 1);
		}
		return prices;
	}

	private static void add(List<Integer> prices, int price)
	{
		int clamped = Math.max(1, price);
		if (!prices.contains(clamped))
		{
			prices.add(clamped);
		}
	}

	private static final class PricedExit
	{
		private final int price;
		private final double expectedMinutes;

		PricedExit(int price, double expectedMinutes)
		{
			this.price = price;
			this.expectedMinutes = expectedMinutes;
		}
	}
}
