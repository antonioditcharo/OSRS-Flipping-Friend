package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import java.util.Collections;
import java.util.Set;

/**
 * Turns the stream of offer transitions into per-item evidence about how offers actually behave.
 * <p>
 * Everything downstream — calibration, the survival model, deciding whether a fill estimate can be
 * trusted — needs to compare predicted fills against real ones, and this is where real ones come
 * from. The fill model is currently derived entirely from public price history, which says what the
 * market did but nothing about what happened to <em>our</em> orders sitting in the queue behind
 * everyone else's.
 * <p>
 * Two counting hazards make this less trivial than it looks, and both have already caused real bugs
 * elsewhere in this codebase:
 * <ul>
 *   <li><b>One offer produces many events.</b> The game reports every partial fill as another
 *       transition on the same offer. Counting events would inflate the sample by the average number
 *       of fills per offer and make everything look far more common than it is, so an offer is only
 *       counted once, when it reaches a terminal state.</li>
 *   <li><b>The game replays offer state on login.</b> A completed offer is re-announced on every
 *       reconnect, so terminal offers already counted are remembered and ignored. That memory lives
 *       in the database rather than in this object, because the reconnect that matters most is the
 *       one right after this process restarts, when an in-memory record would be empty.</li>
 * </ul>
 */
final class ExecutionRecorder
{
	/** Terminal states, after which an offer will not change again. */
	private static final Set<String> SETTLED = Collections.unmodifiableSet(
		new java.util.HashSet<>(java.util.Arrays.asList(
			"BOUGHT", "SOLD", "CANCELLED_BUY", "CANCELLED_SELL")));

	private final SqliteStore store;

	ExecutionRecorder(SqliteStore store)
	{
		this.store = store;
	}

	/**
	 * Folds one offer transition into the execution record, doing nothing until the offer settles.
	 *
	 * @return true when this event settled an offer that had not been counted before
	 */
	synchronized boolean record(OfferEvent event) throws Exception
	{
		if (event == null || event.getItemId() <= 0 || !SETTLED.contains(event.getEventType()))
		{
			return false;
		}

		if (!store.claimOffer(identity(event), event.getObservedAt()))
		{
			return false;
		}

		// Only a completed offer has a meaningful fill time. An offer cancelled after ten minutes
		// did not take ten minutes to fill; it never filled, and averaging it in as though it had
		// would drag the mean towards whatever the player's patience happens to be.
		boolean complete = event.isComplete();
		double minutes = complete ? event.secondsOpen() / 60.0 : 0;

		// The prediction is only kept for offers that completed and carried one. Pairing a realised
		// duration with a prediction from a different offer, or with no prediction at all, would
		// corrupt the ratio the learner is built on.
		double predicted = complete ? event.getPredictedMinutes() : 0;
		store.recordExecution(event.getItemId(), complete, minutes, predicted);
		return true;
	}

	/**
	 * Identifies the offer rather than the event. The slot plus the moment the offer first appeared
	 * is unique: a slot holds one offer at a time, and a replacement necessarily starts later.
	 */
	private static String identity(OfferEvent event)
	{
		long start = event.getFirstSeenAt() > 0 ? event.getFirstSeenAt() : event.getObservedAt();
		return event.getSlot() + "@" + start + ":" + event.getItemId();
	}
}
