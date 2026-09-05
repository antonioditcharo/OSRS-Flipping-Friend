package com.flippingfriend.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cancelled buys whose meaning is not settled yet.
 *
 * <p>There is no "modify" in the Grand Exchange. Changing a live offer means aborting it and placing
 * another, so a modification and an abandonment arrive as the same event and cannot be told apart at
 * the moment they happen. {@link SkipList#isRejection} then treats any part-filled buy that was
 * cancelled as a rejection, so adjusting the price of an order that had begun to fill put the item on
 * an eight-hour cooldown every time: the plan dropped it and moved on while the player was still
 * halfway through re-placing it.
 *
 * <p>The plugin used to guess from the interface at the instant the cancel fired — is the setup panel
 * open, is it showing this item — which is a race the client usually wins, because the offer is
 * cancelled first and the panel opens afterwards. Hence three different ways of cancelling an offer
 * (the modify button, an abort from the slot's right-click menu, an abort from inside the offer
 * window) that all behaved differently and mostly badly.
 *
 * <p>So the question is deferred. What separates a modification from a rejection is what the player
 * does <em>next</em>, and that is the same signal by every route: another offer for the same item.
 * Waiting is close to free — a genuine rejection becomes a cooldown that starts a minute late, while
 * a false one takes the item off the table for eight hours.
 *
 * <p>No RuneLite types here on purpose. This is the whole of the rule, and it belongs somewhere a
 * test can reach without constructing a client.
 */
public final class AbandonedBuys
{
	/** Long enough to abort, collect, reopen the item and type a new price without being rushed. */
	public static final long DEFAULT_GRACE_MILLIS = 60_000;

	private final long graceMillis;
	private final Map<Integer, Long> pending = new ConcurrentHashMap<>();

	public AbandonedBuys()
	{
		this(DEFAULT_GRACE_MILLIS);
	}

	public AbandonedBuys(long graceMillis)
	{
		this.graceMillis = graceMillis;
	}

	/**
	 * A buy on this item was cancelled and looks like a rejection. Ask again shortly.
	 *
	 * <p>Re-arming an item that is already pending keeps the ORIGINAL deadline rather than extending
	 * it, so a player repeatedly aborting the same item cannot hold the judgement open for ever.
	 */
	public void cancelled(int itemId, long now)
	{
		pending.putIfAbsent(itemId, now + graceMillis);
	}

	/**
	 * The player is buying this item again, so the cancellation was a modification.
	 *
	 * <p>Blind to price, quantity and slot. Someone re-placing an order may change any of them — that
	 * is what modifying an offer is — and may land in a different slot, because the original one is
	 * still holding the collectable remains of the offer they cancelled. That they are still buying
	 * the item is the whole of the signal.
	 *
	 * @return true if a judgement was withdrawn, for logging
	 */
	public boolean replaced(int itemId)
	{
		return pending.remove(itemId) != null;
	}

	/** Items still awaiting a verdict, so the caller can check the ones it has more to say about. */
	public List<Integer> awaiting()
	{
		return new ArrayList<>(pending.keySet());
	}

	public boolean isAwaiting(int itemId)
	{
		return pending.containsKey(itemId);
	}

	/**
	 * The items the player has not gone back to, removed from the list as they are returned.
	 *
	 * <p>Called on a timer rather than from an event, because what is being waited for is the player
	 * doing nothing, and nothing raises no event.
	 */
	public List<Integer> due(long now)
	{
		List<Integer> ready = new ArrayList<>();
		for (Map.Entry<Integer, Long> entry : new ArrayList<>(pending.entrySet()))
		{
			if (now >= entry.getValue() && pending.remove(entry.getKey()) != null)
			{
				ready.add(entry.getKey());
			}
		}
		return ready;
	}

	/** Forgets everything, for a logout or an account switch. */
	public void clear()
	{
		pending.clear();
	}
}
