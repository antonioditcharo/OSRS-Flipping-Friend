package com.flippingfriend.model;

/**
 * A fully priced, fully sized trade idea, together with the working that produced it.
 * <p>
 * The intermediate numbers are kept rather than discarded because the panel has to be able to
 * explain itself to someone who has never flipped before, and an explanation reconstructed after
 * the fact is an explanation that can disagree with the decision.
 */
public class Candidate
{
	private final int itemId;
	private final String itemName;
	private final int buyPrice;
	private final int sellPrice;
	private final int quantity;
	private final long netProfit;
	private final double score;
	/**
	 * The score after the learned model has had its say, which is what the ranking is actually done
	 * on. Held here because the adjustment needs the item's market context, and that only exists
	 * inside the loop where the candidate is built.
	 */
	private double adjustedScore;
	private final double confidence;
	private final FillEstimate buyFill;
	private final FillEstimate sellFill;
	private final ItemFeatures features;
	private final int breakEvenPrice;
	private final int buyLimitRemaining;
	private final double calibration;

	public Candidate(int itemId, String itemName, int buyPrice, int sellPrice, int quantity, long netProfit,
		double score, double confidence, FillEstimate buyFill, FillEstimate sellFill,
		ItemFeatures features, int breakEvenPrice, int buyLimitRemaining, double calibration)
	{
		this.itemId = itemId;
		this.itemName = itemName;
		this.buyPrice = buyPrice;
		this.sellPrice = sellPrice;
		this.quantity = quantity;
		this.netProfit = netProfit;
		this.score = score;
		this.confidence = confidence;
		this.buyFill = buyFill;
		this.sellFill = sellFill;
		this.features = features;
		this.breakEvenPrice = breakEvenPrice;
		this.buyLimitRemaining = buyLimitRemaining;
		this.calibration = calibration;
	}

	public int getItemId()
	{
		return itemId;
	}

	public String getItemName()
	{
		return itemName;
	}

	public int getBuyPrice()
	{
		return buyPrice;
	}

	public int getSellPrice()
	{
		return sellPrice;
	}

	public int getQuantity()
	{
		return quantity;
	}

	/** Total profit after Grand Exchange tax, if both sides fill as modelled. */
	public long getNetProfit()
	{
		return netProfit;
	}


	/** Risk-adjusted expected profit per Grand Exchange slot per hour. The ranking key. */
	public double getScore()
	{
		return score;
	}

	public double getAdjustedScore()
	{
		return adjustedScore;
	}

	public void setAdjustedScore(double adjustedScore)
	{
		this.adjustedScore = adjustedScore;
	}

	/** Modelled chance both sides complete, from 0 to 1. */
	public double getConfidence()
	{
		return confidence;
	}

	public FillEstimate getBuyFill()
	{
		return buyFill;
	}

	public FillEstimate getSellFill()
	{
		return sellFill;
	}

	public ItemFeatures getFeatures()
	{
		return features;
	}

	/** Lowest sale price that still covers the purchase and the tax. */
	public int getBreakEvenPrice()
	{
		return breakEvenPrice;
	}

	public int getBuyLimitRemaining()
	{
		return buyLimitRemaining;
	}

	/** The learned correction applied to this item's score, where 1.0 means no correction. */
	public double getCalibration()
	{
		return calibration;
	}

	public long getCapitalRequired()
	{
		return (long) buyPrice * quantity;
	}

	public double getRoundTripMinutes()
	{
		return buyFill.getExpectedMinutes() + sellFill.getExpectedMinutes();
	}

	public double getMarginPct()
	{
		return buyPrice <= 0 ? 0 : (double) netProfit / ((double) buyPrice * Math.max(1, quantity));
	}
}
