package com.flippingfriend.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Why the plan looks the way it does, including — especially — why capital was left unallocated.
 * <p>
 * An empty plan is the single most confusing thing this system can produce. "No candidate clears
 * portfolio safety constraints" is honest but useless: it does not distinguish a genuinely dead
 * market from a filter that has quietly rejected everything, and those two require opposite
 * responses from the player. Carrying the funnel with the plan means the difference is always
 * visible, in the panel and in the stored history, rather than needing a debugger attached to a
 * process that is not running any more.
 * <p>
 * The counts describe a funnel: every item in the feed, those with a believable two-sided quote,
 * those that survived the cheap throughput screen, those given real history, and finally the
 * tactics that came out. {@link #getVetoCounts()} explains the drop between the last two.
 */
public final class PlanDiagnostics
{
	private final int itemsInFeed;
	private final int itemsQuoted;
	private final int itemsShortlisted;
	private final int itemsAnalysed;
	private final int tacticsGenerated;
	private final int slotsFilled;
	private final long capitalAllocated;
	private final long capitalAvailable;
	private final Map<String, Integer> vetoCounts;
	/** A few of the items behind each reason, so a count can be checked rather than taken on trust. */
	private final Map<String, List<String>> vetoExamples;

	// The account as it stood when the plan was built, as distinct from what the plan proposed to do
	// about it. The three fields above describe a proposal -- slotsFilled is how many slots this plan
	// would take, capitalAllocated is what it would spend -- and those are undefined for a plan that
	// could not be made. Utilisation is not: an account with every slot busy is fully used whether or
	// not there was room to suggest anything, and charting the proposal in its place understated slot
	// use by half, because the plans dropped were exactly the ones from when the account was busiest.
	private final int slotsOccupied;
	private final int slotsTotal;
	private final long capitalHeld;

	public PlanDiagnostics(int itemsInFeed, int itemsQuoted, int itemsShortlisted, int itemsAnalysed,
		int tacticsGenerated, int slotsFilled, long capitalAllocated, long capitalAvailable,
		Map<String, Integer> vetoCounts)
	{
		this(itemsInFeed, itemsQuoted, itemsShortlisted, itemsAnalysed, tacticsGenerated, slotsFilled,
			capitalAllocated, capitalAvailable, vetoCounts, Collections.emptyMap());
	}

	public PlanDiagnostics(int itemsInFeed, int itemsQuoted, int itemsShortlisted, int itemsAnalysed,
		int tacticsGenerated, int slotsFilled, long capitalAllocated, long capitalAvailable,
		Map<String, Integer> vetoCounts, Map<String, List<String>> vetoExamples)
	{
		this(itemsInFeed, itemsQuoted, itemsShortlisted, itemsAnalysed, tacticsGenerated, slotsFilled,
			capitalAllocated, capitalAvailable, vetoCounts, vetoExamples, 0, 0, 0);
	}

	public PlanDiagnostics(int itemsInFeed, int itemsQuoted, int itemsShortlisted, int itemsAnalysed,
		int tacticsGenerated, int slotsFilled, long capitalAllocated, long capitalAvailable,
		Map<String, Integer> vetoCounts, Map<String, List<String>> vetoExamples,
		int slotsOccupied, int slotsTotal, long capitalHeld)
	{
		this.slotsOccupied = slotsOccupied;
		this.slotsTotal = slotsTotal;
		this.capitalHeld = capitalHeld;
		this.vetoExamples = vetoExamples == null
			? Collections.emptyMap()
			: Collections.unmodifiableMap(new LinkedHashMap<>(vetoExamples));
		this.itemsInFeed = itemsInFeed;
		this.itemsQuoted = itemsQuoted;
		this.itemsShortlisted = itemsShortlisted;
		this.itemsAnalysed = itemsAnalysed;
		this.tacticsGenerated = tacticsGenerated;
		this.slotsFilled = slotsFilled;
		this.capitalAllocated = capitalAllocated;
		this.capitalAvailable = capitalAvailable;
		this.vetoCounts = vetoCounts == null
			? Collections.emptyMap()
			: Collections.unmodifiableMap(new LinkedHashMap<>(vetoCounts));
	}

	public int getItemsInFeed() { return itemsInFeed; }
	public int getItemsQuoted() { return itemsQuoted; }
	public int getItemsShortlisted() { return itemsShortlisted; }
	public int getItemsAnalysed() { return itemsAnalysed; }
	public int getTacticsGenerated() { return tacticsGenerated; }
	public int getSlotsFilled() { return slotsFilled; }
	public long getCapitalAllocated() { return capitalAllocated; }
	public long getCapitalAvailable() { return capitalAvailable; }
	public int getSlotsOccupied() { return slotsOccupied; }
	public int getSlotsTotal() { return slotsTotal; }
	public long getCapitalHeld() { return capitalHeld; }

	/**
	 * Veto reason to the number of items it rejected, most common first.
	 * <p>
	 * Never null even on an instance Gson built, because Gson sets fields directly and skips the
	 * constructor that would otherwise guarantee it.
	 */
	public Map<String, Integer> getVetoCounts()
	{
		return vetoCounts == null ? Collections.emptyMap() : vetoCounts;
	}

	/**
	 * Example item names per veto reason, most common reason first. Same null guard and same reason
	 * for it: an instance Gson built never ran the constructor, and an older companion will not send
	 * this field at all.
	 */
	public Map<String, List<String>> getVetoExamples()
	{
		return vetoExamples == null ? Collections.emptyMap() : vetoExamples;
	}


	/** A copy with the allocation outcome filled in, since that is only known after optimizing. */
	public PlanDiagnostics withOutcome(int slotsFilled, long capitalAllocated)
	{
		return new PlanDiagnostics(itemsInFeed, itemsQuoted, itemsShortlisted, itemsAnalysed,
			tacticsGenerated, slotsFilled, capitalAllocated, capitalAvailable, getVetoCounts(),
			getVetoExamples(), slotsOccupied, slotsTotal, capitalHeld);
	}

	/**
	 * A copy carrying why the surviving tactics were not allocated.
	 *
	 * <p>Folded into the same counts the screen's vetoes use, because a player asking "why is nothing
	 * being suggested" does not care which stage turned an item away — they care which threshold to
	 * change. Keeping two lists would make them read the funnel twice and subtract.
	 *
	 * <p>This is the half that was missing. The funnel could account for every one of four thousand
	 * items the screen discarded, then reported "81 tactics generated, 1 slot filled" and explained
	 * none of the eighty — which is the stretch where every number a player can actually set lives.
	 */
	public PlanDiagnostics withRejections(Map<String, Integer> rejections)
	{
		if (rejections == null || rejections.isEmpty())
		{
			return this;
		}
		Map<String, Integer> merged = new LinkedHashMap<>(getVetoCounts());
		for (Map.Entry<String, Integer> entry : rejections.entrySet())
		{
			merged.merge(entry.getKey(), entry.getValue(), Integer::sum);
		}
		return new PlanDiagnostics(itemsInFeed, itemsQuoted, itemsShortlisted, itemsAnalysed,
			tacticsGenerated, slotsFilled, capitalAllocated, capitalAvailable, merged,
			getVetoExamples(), slotsOccupied, slotsTotal, capitalHeld);
	}

	/**
	 * A copy describing the account the plan was built against.
	 * <p>
	 * Attached to every plan including the ones that could not be made, because "there was no plan"
	 * and "there was nothing to say about the account" are different statements and only the first is
	 * true when every slot is busy.
	 */
	public PlanDiagnostics withAccount(int occupied, int total, long held)
	{
		return new PlanDiagnostics(itemsInFeed, itemsQuoted, itemsShortlisted, itemsAnalysed,
			tacticsGenerated, slotsFilled, capitalAllocated, capitalAvailable, getVetoCounts(),
			getVetoExamples(), occupied, total, held);
	}

}
