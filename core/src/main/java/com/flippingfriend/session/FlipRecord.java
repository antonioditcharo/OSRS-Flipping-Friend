package com.flippingfriend.session;

/**
 * One completed flip, written to the journal the moment it closes.
 * <p>
 * The predicted fields are kept alongside the actual ones on purpose. Storing only the outcome
 * would make the journal a profit log; storing both makes it a training set, which is what lets the
 * plugin work out where its own estimates are wrong.
 */
public class FlipRecord
{
	private int itemId;
	private String itemName;
	private int quantity;
	private int buyPrice;
	private int sellPrice;
	private long tax;
	private long profit;
	private long boughtAt;
	private long soldAt;
	private double predictedMinutes;
	private double predictedProfit;
	private String riskProfile;
	private long liquidValue;

	public FlipRecord()
	{
	}

	public FlipRecord(int itemId, String itemName, int quantity, int buyPrice, int sellPrice, long tax,
		long profit, long boughtAt, long soldAt, double predictedMinutes, double predictedProfit,
		String riskProfile)
	{
		this.itemId = itemId;
		this.itemName = itemName;
		this.quantity = quantity;
		this.buyPrice = buyPrice;
		this.sellPrice = sellPrice;
		this.tax = tax;
		this.profit = profit;
		this.boughtAt = boughtAt;
		this.soldAt = soldAt;
		this.predictedMinutes = predictedMinutes;
		this.predictedProfit = predictedProfit;
		this.riskProfile = riskProfile;
	}

	public int getItemId()
	{
		return itemId;
	}

	public String getItemName()
	{
		return itemName == null ? "Item " + itemId : itemName;
	}

	public int getQuantity()
	{
		return quantity;
	}

	public int getBuyPrice()
	{
		return buyPrice;
	}

	public int getSellPrice()
	{
		return sellPrice;
	}

	public long getTax()
	{
		return tax;
	}

	public long getProfit()
	{
		return profit;
	}

	public long getBoughtAt()
	{
		return boughtAt;
	}

	public long getSoldAt()
	{
		return soldAt;
	}

	public double getPredictedMinutes()
	{
		return predictedMinutes;
	}

	public double getPredictedProfit()
	{
		return predictedProfit;
	}

	public String getRiskProfile()
	{
		return riskProfile;
	}

	public double actualMinutes()
	{
		return Math.max(0, (soldAt - boughtAt) / 60.0);
	}

	/**
	 * True when part of this flip's cost had to be reconstructed from what the item recently cost,
	 * because the position that held its price was gone by the time the sale settled.
	 * <p>
	 * Kept so a reconstructed figure can be told apart from one measured end to end -- it is a good
	 * estimate, not a measurement, and the calibrator should not learn from it as though it were.
	 */
	private boolean costReconstructed;

	public boolean isCostReconstructed()
	{
		return costReconstructed;
	}

	public void setCostReconstructed(boolean costReconstructed)
	{
		this.costReconstructed = costReconstructed;
	}

	public boolean isWin()
	{
		return profit > 0;
	}

	public long getLiquidValue()
	{
		return liquidValue;
	}

	public void setLiquidValue(long liquidValue)
	{
		this.liquidValue = liquidValue;
	}
}
