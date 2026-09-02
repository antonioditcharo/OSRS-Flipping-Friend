package com.flippingfriend.core;

import java.util.Collections;
import java.util.Map;

/** Plain account state copied by the plugin; contains no credentials or client objects. */
public final class AccountSnapshot
{
	private final String correlationId;
	private final long observedAt;
	private final long spendableCoins;
	private final long committedCoins;
	private final int freeSlots;
	private final int totalSlots;
	private final boolean members;
	private final boolean bankSeen;
	private final double markedSessionDrawdown;
	/** The player's chosen risk appetite; governs what is traded, never how much gold is used. */
	private final String riskAppetite;
	/**
	 * Capital already tied up in each item: positions held, at cost, plus any open buy offer.
	 * <p>
	 * Without this the companion cannot tell that it already holds something. Its per-item and
	 * per-group exposure ceilings were seeded empty on every plan, so they only ever limited one plan
	 * against itself -- collect a large buy and the very next plan could recommend the same item
	 * again, and the whole bankroll could end up in one item or one correlated family.
	 */
	private final Map<Integer, Long> committedByItem;
	/** The floor the player set on what a flip is worth doing; below this, do not suggest it. */
	private final long minProfitPerFlip;
	/**
	 * Units bought of each item inside its current four-hour window, from the plugin's own ledger.
	 * <p>
	 * Offer events travel over loopback with no retry, so a fill that lands while the companion is
	 * restarting never reaches it. The plugin's ledger is durable and survives that, so it comes along
	 * on every snapshot as a floor to correct against.
	 */
	private final Map<Integer, Integer> buyLimitUsed;

	/**
	 * The inverse of the "Learn from my trades" setting, and inverted on purpose.
	 * <p>
	 * Gson sets fields directly, and a boolean it does not find stays false. Carrying this as
	 * {@code useCalibration} would mean any snapshot without the field silently turned learning off --
	 * the opposite of the setting's default. Stored as the negative, a missing field means "not
	 * disabled", which is what the player asked for.
	 */
	private final boolean learningDisabled;
	/**
	 * The player is winding the session down: advise no new positions.
	 * <p>
	 * Sent rather than inferred, because the plugin is the only thing that knows. Without it the
	 * companion would go on producing buy plans that the plugin then had to throw away, and a plan
	 * built and discarded is exactly where the two sides drift apart.
	 */
	private final boolean sellOnly;
	/**
	 * How many minutes the player says they leave between visits to the Grand Exchange.
	 * <p>
	 * Someone who checks back every three hours should be given chunkier orders than someone stood at
	 * the Exchange: an order that completes while they are away holds its slot doing nothing until
	 * they return. Zero means unstated, in which case the risk appetite's own horizon stands alone.
	 */
	private final int checkIntervalMinutes;
	/**
	 * Trades the player has already rejected, so the plan can leave them out rather than be filtered
	 * afterwards.
	 * <p>
	 * These were applied only at selection: the card moved on, while the portfolio list two inches
	 * below went on showing the blocked item as the top-ranked trade, and the plan's headline rate was
	 * computed from trades that were never going to be offered. Excluding them here makes the plan
	 * describe what is actually on the table.
	 */
	private final java.util.Set<String> blockedItems;
	private final java.util.Set<Integer> skippedItems;
	/** Items with a live offer: suggesting one again is telling the player to buy what they just bought. */
	private final java.util.Set<Integer> itemsOnOffer;

	public AccountSnapshot(String correlationId, long observedAt, long spendableCoins, long committedCoins,
		int freeSlots, int totalSlots, boolean members, boolean bankSeen, double markedSessionDrawdown)
	{
		this(correlationId, observedAt, spendableCoins, committedCoins, freeSlots, totalSlots, members,
			bankSeen, markedSessionDrawdown, null, null, 0, null);
	}

	public AccountSnapshot(String correlationId, long observedAt, long spendableCoins, long committedCoins,
		int freeSlots, int totalSlots, boolean members, boolean bankSeen, double markedSessionDrawdown,
		String riskAppetite)
	{
		this(correlationId, observedAt, spendableCoins, committedCoins, freeSlots, totalSlots, members,
			bankSeen, markedSessionDrawdown, riskAppetite, null, 0, null);
	}

	public AccountSnapshot(String correlationId, long observedAt, long spendableCoins, long committedCoins,
		int freeSlots, int totalSlots, boolean members, boolean bankSeen, double markedSessionDrawdown,
		String riskAppetite, Map<Integer, Long> committedByItem, long minProfitPerFlip)
	{
		this(correlationId, observedAt, spendableCoins, committedCoins, freeSlots, totalSlots, members,
			bankSeen, markedSessionDrawdown, riskAppetite, committedByItem, minProfitPerFlip, null);
	}

	public AccountSnapshot(String correlationId, long observedAt, long spendableCoins, long committedCoins,
		int freeSlots, int totalSlots, boolean members, boolean bankSeen, double markedSessionDrawdown,
		String riskAppetite, Map<Integer, Long> committedByItem, long minProfitPerFlip,
		Map<Integer, Integer> buyLimitUsed)
	{
		this(correlationId, observedAt, spendableCoins, committedCoins, freeSlots, totalSlots, members,
			bankSeen, markedSessionDrawdown, riskAppetite, committedByItem, minProfitPerFlip,
			buyLimitUsed, false, 0);
	}

	public AccountSnapshot(String correlationId, long observedAt, long spendableCoins, long committedCoins,
		int freeSlots, int totalSlots, boolean members, boolean bankSeen, double markedSessionDrawdown,
		String riskAppetite, Map<Integer, Long> committedByItem, long minProfitPerFlip,
		Map<Integer, Integer> buyLimitUsed, boolean learningDisabled)
	{
		this(correlationId, observedAt, spendableCoins, committedCoins, freeSlots, totalSlots, members,
			bankSeen, markedSessionDrawdown, riskAppetite, committedByItem, minProfitPerFlip,
			buyLimitUsed, learningDisabled, 0, null, null, null);
	}

	public AccountSnapshot(String correlationId, long observedAt, long spendableCoins, long committedCoins,
		int freeSlots, int totalSlots, boolean members, boolean bankSeen, double markedSessionDrawdown,
		String riskAppetite, Map<Integer, Long> committedByItem, long minProfitPerFlip,
		Map<Integer, Integer> buyLimitUsed, boolean learningDisabled,
		int checkIntervalMinutes)
	{
		this(correlationId, observedAt, spendableCoins, committedCoins, freeSlots, totalSlots, members,
			bankSeen, markedSessionDrawdown, riskAppetite, committedByItem, minProfitPerFlip,
			buyLimitUsed, learningDisabled, checkIntervalMinutes, null, null, null);
	}

	public AccountSnapshot(String correlationId, long observedAt, long spendableCoins, long committedCoins,
		int freeSlots, int totalSlots, boolean members, boolean bankSeen, double markedSessionDrawdown,
		String riskAppetite, Map<Integer, Long> committedByItem, long minProfitPerFlip,
		Map<Integer, Integer> buyLimitUsed, boolean learningDisabled,
		int checkIntervalMinutes, java.util.Set<String> blockedItems,
		java.util.Set<Integer> skippedItems, java.util.Set<Integer> itemsOnOffer)
	{
		this(correlationId, observedAt, spendableCoins, committedCoins, freeSlots, totalSlots, members,
			bankSeen, markedSessionDrawdown, riskAppetite, committedByItem, minProfitPerFlip,
			buyLimitUsed, learningDisabled, checkIntervalMinutes, blockedItems,
			skippedItems, itemsOnOffer, false);
	}

	public AccountSnapshot(String correlationId, long observedAt, long spendableCoins,
		long committedCoins, int freeSlots, int totalSlots, boolean members, boolean bankSeen,
		double markedSessionDrawdown,
		String riskAppetite, Map<Integer, Long> committedByItem, long minProfitPerFlip,
		Map<Integer, Integer> buyLimitUsed, boolean learningDisabled,
		int checkIntervalMinutes, java.util.Set<String> blockedItems,
		java.util.Set<Integer> skippedItems, java.util.Set<Integer> itemsOnOffer, boolean sellOnly)
	{
		this.sellOnly = sellOnly;
		this.blockedItems = blockedItems == null
			? java.util.Collections.emptySet() : java.util.Collections.unmodifiableSet(blockedItems);
		this.skippedItems = skippedItems == null
			? java.util.Collections.emptySet() : java.util.Collections.unmodifiableSet(skippedItems);
		this.itemsOnOffer = itemsOnOffer == null
			? java.util.Collections.emptySet() : java.util.Collections.unmodifiableSet(itemsOnOffer);
		this.checkIntervalMinutes = Math.max(0, checkIntervalMinutes);
		this.learningDisabled = learningDisabled;
		this.buyLimitUsed = buyLimitUsed == null
			? Collections.emptyMap() : Collections.unmodifiableMap(buyLimitUsed);
		this.committedByItem = committedByItem == null
			? Collections.emptyMap() : Collections.unmodifiableMap(committedByItem);
		this.minProfitPerFlip = Math.max(0, minProfitPerFlip);
		this.riskAppetite = riskAppetite;
		this.correlationId = correlationId;
		this.observedAt = observedAt;
		this.spendableCoins = Math.max(0, spendableCoins);
		this.committedCoins = Math.max(0, committedCoins);
		this.freeSlots = Math.max(0, freeSlots);
		this.totalSlots = Math.max(0, totalSlots);
		this.members = members;
		this.bankSeen = bankSeen;
		this.markedSessionDrawdown = Math.max(0, markedSessionDrawdown);
	}

	public String getCorrelationId() { return correlationId; }
	public long getObservedAt() { return observedAt; }
	public long getSpendableCoins() { return spendableCoins; }
	public long getCommittedCoins() { return committedCoins; }
	public int getFreeSlots() { return freeSlots; }
	public int getTotalSlots() { return totalSlots; }
	public boolean isMembers() { return members; }
	public boolean isBankSeen() { return bankSeen; }
	public double getMarkedSessionDrawdown() { return markedSessionDrawdown; }
	public String getRiskAppetite() { return riskAppetite; }

	/**
	 * Gson sets these fields directly and skips the constructor, so the guarantees above do not hold
	 * for a deserialised snapshot. Three separate javadocs in this project record that constructor
	 * bypass has already caused a null-pointer that reached the player as a bare "null".
	 */
	public Map<Integer, Long> getCommittedByItem()
	{
		return committedByItem == null ? Collections.emptyMap() : committedByItem;
	}

	public long getMinProfitPerFlip() { return Math.max(0, minProfitPerFlip); }

	public int getCheckIntervalMinutes() { return Math.max(0, checkIntervalMinutes); }

	/** Lower-cased item names the player has blocked. Never null, even straight out of Gson. */
	public java.util.Set<String> getBlockedItems()
	{
		return blockedItems == null ? java.util.Collections.emptySet() : blockedItems;
	}

	public java.util.Set<Integer> getSkippedItems()
	{
		return skippedItems == null ? java.util.Collections.emptySet() : skippedItems;
	}

	public java.util.Set<Integer> getItemsOnOffer()
	{
		return itemsOnOffer == null ? java.util.Collections.emptySet() : itemsOnOffer;
	}



	/** True when the player has asked the system not to learn from their own trades. */
	public boolean isLearningDisabled() { return learningDisabled; }
	public boolean isSellOnly() { return sellOnly; }

	public Map<Integer, Integer> getBuyLimitUsed()
	{
		return buyLimitUsed == null ? Collections.emptyMap() : buyLimitUsed;
	}
}
