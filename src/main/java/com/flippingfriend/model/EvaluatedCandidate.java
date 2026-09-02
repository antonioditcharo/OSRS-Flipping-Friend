package com.flippingfriend.model;

/**
 * One item the engine looked at on a scoring pass, and what it decided.
 * <p>
 * Rejected items are published alongside accepted ones on purpose. The shadow trader needs both:
 * watching only what was approved would confirm the model's judgement without ever testing it, in
 * the same way that a shop only counting the customers it served learns nothing about the queue it
 * turned away.
 */
public class EvaluatedCandidate
{
	private final int itemId;
	private final String itemName;
	private final int buyPrice;
	private final int sellPrice;
	private final int quantity;
	private final double score;
	private final double confidence;
	private final double predictedBuyMinutes;
	private final double predictedSellMinutes;
	private final boolean accepted;
	private final String rejectionReason;
	private final double[] features;

	private EvaluatedCandidate(int itemId, String itemName, int buyPrice, int sellPrice, int quantity,
		double score, double confidence, double predictedBuyMinutes, double predictedSellMinutes,
		boolean accepted, String rejectionReason, double[] features)
	{
		this.features = features;
		this.itemId = itemId;
		this.itemName = itemName;
		this.buyPrice = buyPrice;
		this.sellPrice = sellPrice;
		this.quantity = quantity;
		this.score = score;
		this.confidence = confidence;
		this.predictedBuyMinutes = predictedBuyMinutes;
		this.predictedSellMinutes = predictedSellMinutes;
		this.accepted = accepted;
		this.rejectionReason = rejectionReason;
	}

	/** A fully scored trade the engine was willing to suggest. */
	public static EvaluatedCandidate accepted(Candidate candidate, double[] features)
	{
		return new EvaluatedCandidate(candidate.getItemId(), candidate.getItemName(),
			candidate.getBuyPrice(), candidate.getSellPrice(), candidate.getQuantity(),
			candidate.getScore(), candidate.getConfidence(),
			candidate.getBuyFill().getExpectedMinutes(), candidate.getSellFill().getExpectedMinutes(),
			true, null, features);
	}

	/**
	 * An item that was screened out. Priced at the obvious spread, since the scorer never ran — the
	 * question being tested is "what would have happened had we simply traded this?".
	 */
	public static EvaluatedCandidate rejected(int itemId, String itemName, int buyPrice, int sellPrice,
		int quantity, String reason)
	{
		return rejected(itemId, itemName, buyPrice, sellPrice, quantity, reason, null);
	}

	public static EvaluatedCandidate rejected(int itemId, String itemName, int buyPrice, int sellPrice,
		int quantity, String reason, double[] features)
	{
		return new EvaluatedCandidate(itemId, itemName, buyPrice, sellPrice, quantity, 0, 0, 0, 0,
			false, reason, features);
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

	public double getScore()
	{
		return score;
	}

	public double getConfidence()
	{
		return confidence;
	}

	public double getPredictedBuyMinutes()
	{
		return predictedBuyMinutes;
	}

	public double getPredictedSellMinutes()
	{
		return predictedSellMinutes;
	}

	public boolean isAccepted()
	{
		return accepted;
	}

	public String getRejectionReason()
	{
		return rejectionReason;
	}

	/** The setup as the learned model sees it, carried so the outcome can train it. */
	public double[] getFeatures()
	{
		return features;
	}
}
