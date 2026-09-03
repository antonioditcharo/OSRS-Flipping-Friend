package com.flippingfriend.core;

/**
 * Immutable telemetry event for an observed manual GE offer transition.
 * <p>
 * The fields beyond the offer's own state exist so that a recommendation can be joined to what
 * actually happened to it. Without that join the event log records only that offers occurred, which
 * is enough to reconstruct a bank balance and nothing else: the model can never find out whether the
 * trades it advised filled, how long they really took, or what price they really got. Every question
 * worth asking of this data is a comparison between what was suggested and what followed.
 * <p>
 * {@link #getRecommendationId()} carries the correlation id of the plan that advised the trade, so
 * outcomes land against the decision that caused them. It is null for offers the player placed on
 * their own, and those are worth keeping too — an unprompted trade that does well is evidence about
 * the market just as much as a prompted one.
 */
public final class OfferEvent
{
	private final String schemaVersion;
	private final String correlationId;
	private final long observedAt;
	private final String eventType;
	private final int slot;
	private final int itemId;
	private final String itemName;
	private final boolean buying;
	private final int price;
	private final int totalQuantity;
	private final int filledQuantity;
	private final long spent;

	/** Monotonic within a session, so events that arrive out of order can still be ordered. */
	private final long sequence;
	/** Plan that recommended this trade, or null when the player acted unprompted. */
	private final String recommendationId;
	private final int suggestedPrice;
	private final int suggestedQuantity;
	/** How stale the market quote was when the advice was given. */
	private final long quoteAgeSeconds;
	/** How long the model said this would take, so the outcome can be scored against the claim. */
	private final double predictedMinutes;
	/**
	 * The completion probability the model actually claimed for this leg when it advised the trade.
	 * <p>
	 * The calibrator exists to learn "the model said 93% and it happened 74%", and it was never being
	 * shown the first half. A figure was derived instead from the predicted duration -- a linear ramp
	 * of minutes against the horizon -- which is not a probability of anything and cannot express the
	 * error the calibrator is there to find. The real number is produced during planning and is now
	 * carried alongside the advice rather than reconstructed after the fact.
	 */
	private final double predictedCompletion;
	/** When this offer first appeared, so elapsed fill time is computable from any later event. */
	private final long firstSeenAt;

	private OfferEvent(Builder builder)
	{
		this.schemaVersion = builder.schemaVersion;
		this.correlationId = builder.correlationId;
		this.observedAt = builder.observedAt;
		this.eventType = builder.eventType;
		this.slot = builder.slot;
		this.itemId = builder.itemId;
		this.itemName = builder.itemName;
		this.buying = builder.buying;
		this.price = builder.price;
		this.totalQuantity = builder.totalQuantity;
		this.filledQuantity = builder.filledQuantity;
		this.spent = builder.spent;
		this.sequence = builder.sequence;
		this.recommendationId = builder.recommendationId;
		this.suggestedPrice = builder.suggestedPrice;
		this.suggestedQuantity = builder.suggestedQuantity;
		this.quoteAgeSeconds = builder.quoteAgeSeconds;
		this.predictedMinutes = builder.predictedMinutes;
		this.predictedCompletion = builder.predictedCompletion;
		this.firstSeenAt = builder.firstSeenAt;
	}

	public static Builder builder(String correlationId, long observedAt, String eventType)
	{
		return new Builder(correlationId, observedAt, eventType);
	}

	public String getSchemaVersion() { return schemaVersion; }
	public String getCorrelationId() { return correlationId; }
	public long getObservedAt() { return observedAt; }
	public String getEventType() { return eventType; }
	public int getSlot() { return slot; }
	public int getItemId() { return itemId; }
	public String getItemName() { return itemName; }
	public boolean isBuying() { return buying; }
	public int getPrice() { return price; }
	public int getTotalQuantity() { return totalQuantity; }
	public int getFilledQuantity() { return filledQuantity; }
	public long getSpent() { return spent; }
	public long getSequence() { return sequence; }
	public String getRecommendationId() { return recommendationId; }
	public int getSuggestedPrice() { return suggestedPrice; }
	public int getSuggestedQuantity() { return suggestedQuantity; }
	public long getQuoteAgeSeconds() { return quoteAgeSeconds; }
	public double getPredictedMinutes() { return predictedMinutes; }

	/** Zero when no model-produced probability accompanied the advice, in which case do not calibrate. */
	public double getPredictedCompletion() { return predictedCompletion; }
	public long getFirstSeenAt() { return firstSeenAt; }

	/**
	 * What the fills actually averaged, which is not the offer price: a buy at 100 can fill cheaper
	 * when sellers are already queued below. The difference is slippage, and it is the only direct
	 * evidence of whether the quoted price was real.
	 */
	public int averageFillPrice()
	{
		return filledQuantity <= 0 ? 0 : (int) (spent / filledQuantity);
	}

	/** Seconds this offer has been open, or 0 when the plugin never saw it appear. */
	public long secondsOpen()
	{
		return firstSeenAt <= 0 ? 0 : Math.max(0, observedAt - firstSeenAt);
	}

	/** True when the player acted on advice rather than independently. */
	public boolean wasRecommended()
	{
		return recommendationId != null && !recommendationId.isEmpty();
	}

	public boolean isComplete()
	{
		return totalQuantity > 0 && filledQuantity >= totalQuantity;
	}

	public static final class Builder
	{
		private final String schemaVersion = "1";
		private final String correlationId;
		private final long observedAt;
		private final String eventType;
		private int slot;
		private int itemId;
		private String itemName;
		private boolean buying;
		private int price;
		private int totalQuantity;
		private int filledQuantity;
		private long spent;
		private long sequence;
		private String recommendationId;
		private int suggestedPrice;
		private int suggestedQuantity;
		private long quoteAgeSeconds;
		private double predictedMinutes;
		private double predictedCompletion;
		private long firstSeenAt;

		private Builder(String correlationId, long observedAt, String eventType)
		{
			this.correlationId = correlationId;
			this.observedAt = observedAt;
			this.eventType = eventType;
		}

		public Builder slot(int value) { this.slot = value; return this; }
		public Builder item(int id, String name) { this.itemId = id; this.itemName = name; return this; }
		public Builder buying(boolean value) { this.buying = value; return this; }
		public Builder price(int value) { this.price = value; return this; }
		public Builder quantities(int total, int filled)
		{
			this.totalQuantity = total;
			this.filledQuantity = filled;
			return this;
		}
		public Builder spent(long value) { this.spent = value; return this; }
		public Builder sequence(long value) { this.sequence = value; return this; }
		public Builder firstSeenAt(long value) { this.firstSeenAt = value; return this; }

		/**
		 * Links this offer to the advice that prompted it.
		 * <p>
		 * There was a five-argument form of this method that defaulted {@code predictedCompletion}
		 * to zero. It was deleted on 2 September 2026 rather than left as a convenience, because the
		 * convenience was the bug: {@code CompanionService.predictedCompletion} treats zero as "no
		 * prediction was made" and declines to calibrate, so every offer routed through the short
		 * form silently opted out of the comparison the system exists to make. There is now one way
		 * to attach advice to an offer, and it requires saying what the model actually claimed.
		 */
		public Builder recommendation(String id, int price, int quantity, long quoteAge,
			double predictedMinutes, double predictedCompletion)
		{
			this.recommendationId = id;
			this.suggestedPrice = price;
			this.suggestedQuantity = quantity;
			this.quoteAgeSeconds = quoteAge;
			this.predictedMinutes = predictedMinutes;
			this.predictedCompletion = predictedCompletion;
			return this;
		}

		public OfferEvent build() { return new OfferEvent(this); }
	}
}
