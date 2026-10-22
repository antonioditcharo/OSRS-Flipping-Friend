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
	 * outcomes can be associated with the original observation. Association does not prove player
	 * intent. Unknown associations are worth keeping too — an unprompted trade is evidence about
 * the market just as much as a prompted one.
 */
public final class OfferEvent
{
	private final String schemaVersion;
	private final String accountId;
	private final String correlationId;
	/** Stable identity of one logical delivery event, or null for legacy version-one payloads. */
	private final String eventId;
	/** Durable ordering scope for event delivery, or null for legacy version-one payloads. */
	private final String sessionId;
	/** Stable identity of the observed offer instance, or null when not yet supplied. */
	private final String offerIdentity;
	/** Copied from an authenticated client slot, never reconstructed from a saved local offer. */
	private final boolean authenticatedObservation;
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
    /** Optional prospective provenance. Missing legacy fields do not invent an original prediction. */
    private final String predictionEvidenceVersion;
    private final String predictionSourceEventId;
    private final Long adviceObservedAt;
    private final Boolean predictionComparable;
    private final ExecutionForecast executionForecast;
    private final AcquisitionExitContext exitContext;
    private final AcceptedAdviceProof acceptedAdviceProof;
    private final ContinuationSuccessorProof continuationSuccessorProof;

	private OfferEvent(Builder builder)
	{
		this.schemaVersion = builder.schemaVersion;
		this.accountId = builder.accountId;
		this.correlationId = builder.correlationId;
		this.eventId = builder.eventId;
		this.sessionId = builder.sessionId;
		this.offerIdentity = builder.offerIdentity;
		this.authenticatedObservation = builder.authenticatedObservation;
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
        this.predictionEvidenceVersion=builder.predictionEvidenceVersion;
        this.predictionSourceEventId=builder.predictionSourceEventId;
        this.adviceObservedAt=builder.adviceObservedAt;
        this.predictionComparable=builder.predictionComparable;
        this.executionForecast=builder.executionForecast;
        this.exitContext=builder.exitContext;
        this.acceptedAdviceProof=builder.acceptedAdviceProof;
        this.continuationSuccessorProof=builder.continuationSuccessorProof;
	}

	public static Builder builder(String correlationId, long observedAt, String eventType)
	{
		return new Builder(correlationId, observedAt, eventType);
	}

	public String getSchemaVersion() { return schemaVersion; }
	public String getCorrelationId() { return correlationId; }
	public String getEventId() { return eventId; }
	public String getSessionId() { return sessionId; }
	public String getAccountId() { return accountId; }

	/** Adds account scope without changing delivery identity or economic evidence. */
	public OfferEvent withAccountId(String accountId)
	{
		if (!"1".equals(schemaVersion)) throw new IllegalArgumentException("unsupported offer schema");
		return copyBuilder()
			.accountId(AccountIdentity.require(accountId)).eventIdentity(eventId, sessionId, offerIdentity)
			.authenticatedObservation(authenticatedObservation)
			.slot(slot).item(itemId, itemName).buying(buying).price(price)
			.quantities(totalQuantity, filledQuantity).spent(spent).sequence(sequence)
			.firstSeenAt(firstSeenAt).recommendation(recommendationId, suggestedPrice, suggestedQuantity,
				quoteAgeSeconds, predictedMinutes, predictedCompletion).build();
	}
	public String getOfferIdentity() { return offerIdentity; }
	public boolean isAuthenticatedObservation() { return authenticatedObservation; }
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
    public String getPredictionEvidenceVersion() { return predictionEvidenceVersion; }
    public String getPredictionSourceEventId() { return predictionSourceEventId; }
    public long getAdviceObservedAt() { return adviceObservedAt==null?0:adviceObservedAt; }
    public boolean isPredictionComparable() { return Boolean.TRUE.equals(predictionComparable); }
    public ExecutionForecast getExecutionForecast(){return executionForecast;}
    public AcquisitionExitContext getExitContext(){return exitContext;}
    public AcceptedAdviceProof getAcceptedAdviceProof(){return acceptedAdviceProof;}
    public ContinuationSuccessorProof getContinuationSuccessorProof(){return continuationSuccessorProof;}
    public OfferEvent withContinuationSuccessorProof(ContinuationSuccessorProof proof){
        Builder copy=copyBuilder();copy.continuationSuccessorProof=proof;return copy.build();
    }
    /** Prospective accepted order evidence independent of prediction scoring eligibility. */
    public boolean hasAcceptedExitProof(){return acceptedAdviceProof!=null && acceptedAdviceProof.isValid();}

    private Builder copyBuilder() {
        Builder copy=builder(correlationId,observedAt,eventType).eventIdentity(eventId,sessionId,offerIdentity)
            .authenticatedObservation(authenticatedObservation).slot(slot).item(itemId,itemName).buying(buying)
            .price(price).quantities(totalQuantity,filledQuantity).spent(spent).sequence(sequence).firstSeenAt(firstSeenAt)
            .recommendation(recommendationId,suggestedPrice,suggestedQuantity,quoteAgeSeconds,predictedMinutes,predictedCompletion);
        if(accountId!=null)copy.accountId(accountId);
        copy.predictionEvidenceVersion=predictionEvidenceVersion;copy.predictionSourceEventId=predictionSourceEventId;
        copy.adviceObservedAt=adviceObservedAt;copy.predictionComparable=predictionComparable;
        copy.executionForecast=executionForecast;
        copy.exitContext=exitContext;
        copy.acceptedAdviceProof=acceptedAdviceProof;
        copy.continuationSuccessorProof=continuationSuccessorProof;
        return copy;
    }
    /** Freezes observational attribution, not proof of player intent. Economic/delivery fields stay intact. */
    public OfferEvent withBoundPrediction(OfferEvent origin,boolean comparable) {
        Builder copy=copyBuilder();
        boolean matched=origin!=null && origin.wasRecommended();
        if(origin!=null && (!java.util.Objects.equals(accountId,origin.accountId)
            || !java.util.Objects.equals(sessionId,origin.sessionId)
            || !java.util.Objects.equals(offerIdentity,origin.offerIdentity) || slot!=origin.slot
            || itemId!=origin.itemId || buying!=origin.buying || firstSeenAt!=origin.firstSeenAt))
            throw new IllegalArgumentException("prediction origin belongs to another offer");
        comparable=comparable && matched && origin.price==price && origin.totalQuantity==totalQuantity
            && origin.suggestedPrice==price && origin.suggestedQuantity==totalQuantity
            && Double.isFinite(origin.predictedMinutes) && origin.predictedMinutes>0
            && Double.isFinite(origin.predictedCompletion) && origin.predictedCompletion>=0 && origin.predictedCompletion<=1;
        comparable=comparable && (origin.executionForecast==null || origin.executionForecast.matches(origin));
        // Same exact prospective binding as the original advice. Later item advice, changed
        // orders and missing originals cannot supply exit context. No lifetime expiry once bound.
        String exitSource=origin==null?null:origin.predictionSourceEventId==null?origin.eventId:origin.predictionSourceEventId;
        boolean accepted=matched && origin.hasAcceptedExitProof() && origin.authenticatedObservation
            && origin.buying && origin.filledQuantity==0 && "BUYING".equals(origin.eventType)
            && origin.firstSeenAt>0 && origin.firstSeenAt==origin.observedAt
            && origin.getAdviceObservedAt()>0 && origin.getAdviceObservedAt()<=origin.firstSeenAt
            && origin.firstSeenAt-origin.getAdviceObservedAt()<=600
            && origin.price==price && origin.totalQuantity==totalQuantity
            && origin.suggestedPrice==price && origin.suggestedQuantity==totalQuantity;
        copy.acceptedAdviceProof=accepted?origin.acceptedAdviceProof:null;
        copy.exitContext=(comparable || accepted) && buying && origin.exitContext!=null && origin.exitContext.isValid()
            && (origin.exitContext.getSourceEventId()==null || origin.exitContext.getSourceEventId().equals(exitSource))
            ? origin.exitContext.bind(origin.predictionSourceEventId==null?origin.eventId:origin.predictionSourceEventId):null;
        copy.executionForecast=matched?origin.executionForecast:null;
        copy.recommendation(matched?origin.recommendationId:null,matched?origin.suggestedPrice:0,
            matched?origin.suggestedQuantity:0,matched?origin.quoteAgeSeconds:0,
            comparable?origin.predictedMinutes:0,comparable?origin.predictedCompletion:0);
        copy.predictionEvidence(matched?(origin.predictionSourceEventId==null?origin.eventId:origin.predictionSourceEventId):null,
            matched?origin.getAdviceObservedAt():0,comparable);
        return copy.build();
    }

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

	/** An advice association is present; this alone proves neither intent nor prediction comparability. */
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
		private String accountId;
		private final String correlationId;
		private String eventId;
		private String sessionId;
		private String offerIdentity;
		private boolean authenticatedObservation;
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
        private String predictionEvidenceVersion,predictionSourceEventId;
        private Long adviceObservedAt;
        private Boolean predictionComparable;
        private ExecutionForecast executionForecast;
        private AcquisitionExitContext exitContext;
        private AcceptedAdviceProof acceptedAdviceProof;
        private ContinuationSuccessorProof continuationSuccessorProof;

		private Builder(String correlationId, long observedAt, String eventType)
		{
			this.correlationId = correlationId;
			this.observedAt = observedAt;
			this.eventType = eventType;
		}

		public Builder accountId(String value) { this.accountId = AccountIdentity.require(value); return this; }
		public Builder eventId(String value) { this.eventId = value; return this; }
		public Builder sessionId(String value) { this.sessionId = value; return this; }
		public Builder offerIdentity(String value) { this.offerIdentity = value; return this; }
		public Builder authenticatedObservation(boolean value) { this.authenticatedObservation = value; return this; }
		public Builder eventIdentity(String eventId, String sessionId, String offerIdentity)
		{
			this.eventId = eventId;
			this.sessionId = sessionId;
			this.offerIdentity = offerIdentity;
			return this;
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
        public Builder predictionEvidence(String source,long advisedAt,boolean comparable) {
            predictionEvidenceVersion="1";predictionSourceEventId=source;adviceObservedAt=advisedAt;
            predictionComparable=comparable;return this;
        }
        public Builder executionForecast(ExecutionForecast forecast){executionForecast=forecast;return this;}
        public Builder exitContext(AcquisitionExitContext value){exitContext=value;return this;}
        public Builder acceptedAdviceProof(AcceptedAdviceProof value){acceptedAdviceProof=value;return this;}
        public Builder continuationSuccessorProof(ContinuationSuccessorProof value){continuationSuccessorProof=value;return this;}

		/** Links this offer to the advice that prompted it. */
		public Builder recommendation(String id, int price, int quantity, long quoteAge,
			double predictedMinutes)
		{
			return recommendation(id, price, quantity, quoteAge, predictedMinutes, 0);
		}

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
