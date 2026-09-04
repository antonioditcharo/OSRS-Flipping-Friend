package com.flippingfriend.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exact branch-and-bound selection for the small number of GE slots. It deliberately chooses a
 * portfolio rather than greedily repeating the highest-scoring item.
 */
public final class PortfolioOptimizer
{
	/**
	 * Why generated tactics did not become allocations, from the last run.
	 *
	 * <p>The funnel could account for every item the screen threw away and then lost its thread at
	 * exactly the point where the money is decided: "81 tactics generated, 1 slot filled" and no
	 * explanation for the other eighty. Every filter below is a threshold somebody can change, and a
	 * player with idle slots cannot change the right one without being told which one fired.
	 *
	 * <p>Not thread-safe by design, and it does not need to be: one planner thread owns this
	 * optimizer and reads the counts immediately after the run that produced them.
	 */
	private final Map<String, Integer> lastRejections = new LinkedHashMap<>();

	private int lastConsidered;

	/** Why the last run's tactics were turned away, and how many for each reason. */
	public Map<String, Integer> lastRejections()
	{
		return new LinkedHashMap<>(lastRejections);
	}

	/** Tactics that survived every filter and were offered to the search. */
	public int lastConsidered()
	{
		return lastConsidered;
	}

	public PortfolioPlan optimize(List<PortfolioCandidate> raw, PortfolioConstraints constraints,
		String correlationId, long now)
	{
		if (constraints.getFreeSlots() <= 0)
		{
			// Worded to match what the planner said before this check became the one that answers, so
			// the message the player reads does not change under them.
			return PortfolioPlan.unavailable(correlationId,
				"Every Grand Exchange slot is occupied.", now);
		}
		int belowHurdle = 0;
		lastRejections.clear();
		List<PortfolioCandidate> candidates = new ArrayList<>();
		for (PortfolioCandidate candidate : raw)
		{
			// The player's floor on what a flip is worth doing. This lived only in the built-in engine,
			// whose buy result is replaced by this plan on every cycle, so the setting had no effect on
			// anything actually suggested -- a 5,000 gp floor still produced 200 gp flips.
			//
			// Counted, now, and not only applied. On live data this one filter removed 80 of 81
			// generated tactics and nothing anywhere said so: the plan reported "81 tactics, 1 slot
			// filled" and left a player with eight free slots and forty-seven million idle coins to
			// guess which of a dozen thresholds had done it. Its default is 50,000, which on that
			// market would have removed all 81.
			long floor = Math.max(1, constraints.getMinProfitPerFlip());
			if (candidate.getNetProfit() < floor)
			{
				lastRejections.merge("Worth less than your " + floor + " gp minimum profit per flip.",
					1, Integer::sum);
				continue;
			}
			if (candidate.getCapitalRequired() <= 0)
			{
				lastRejections.merge("No capital required, so nothing to place.", 1, Integer::sum);
				continue;
			}
			if (candidate.getExpiresAt() <= now)
			{
				lastRejections.merge("Priced from a quote that has already expired.", 1, Integer::sum);
				continue;
			}
			if (candidate.getCompletionProbability() <= 0)
			{
				lastRejections.merge("No chance of completing within the horizon.", 1, Integer::sum);
				continue;
			}
			// The floor that decides whether a slot is worth occupying at all. Without it the
			// search maximises a sum, so any positive rate improves the total and eight slots
			// happily fill with trades earning a fraction of what a slot is worth -- each one
			// holding its slot for a full horizon. A refused slot is re-planned next cycle.
			if (candidate.expectedGpPerSlotHour() < constraints.getHurdleGpPerSlotHour())
			{
				belowHurdle++;
				lastRejections.merge("Worth less than leaving the slot free for a better trade.",
					1, Integer::sum);
				continue;
			}
			candidates.add(candidate);
		}
		lastConsidered = candidates.size();
		candidates.sort(Comparator.comparingDouble(PortfolioCandidate::expectedGpPerSlotHour).reversed());
		Search best = new Search();
		// Seeded with what is already held, not empty. Starting from zero meant the exposure ceilings
		// only ever limited a plan against itself: the book could hold thirty million of one item and
		// the next plan would happily add more, because as far as the search was concerned nothing had
		// been bought yet.
		search(candidates, 0, constraints, new ArrayList<>(),
			new HashMap<>(constraints.getCommittedByItem()),
			new HashMap<>(constraints.getCommittedByGroup()),
			new HashSet<>(), 0, 0, 0, best);
		if (best.selected.isEmpty())
		{
			// Say which floor turned everything away. "Nothing worth trading" reads as a dead market
			// when it is really the hurdle doing its job, and the two call for opposite responses.
			return PortfolioPlan.unavailable(correlationId, belowHurdle > 0
				? belowHurdle + (belowHurdle == 1 ? " trade was" : " trades were")
					+ " worth less than leaving the slot free for a better one."
				: "No candidate clears portfolio safety constraints.", now);
		}
		List<PortfolioAllocation> allocations = new ArrayList<>();
		for (int i = 0; i < best.selected.size(); i++)
		{
			allocations.add(new PortfolioAllocation(i + 1, best.selected.get(i), "PLACE_BUY"));
		}
		return new PortfolioPlan(correlationId, now, now + PortfolioPlan.TTL_SECONDS, "READY",
			"Portfolio optimized for net GP per slot-hour.",
			best.score, allocations);
	}

	private void search(List<PortfolioCandidate> candidates, int index, PortfolioConstraints limits,
		List<PortfolioCandidate> selected, Map<Integer, Long> itemCapital, Map<String, Long> groupCapital,
		Set<Integer> itemIds, long coins, long loss, double score, Search best)
	{
		if (score > best.score)
		{
			best.score = score;
			best.selected = new ArrayList<>(selected);
		}
		if (index >= candidates.size() || selected.size() >= limits.getFreeSlots())
		{
			return;
		}
		// Optimistic bound: remaining candidates could all be selected. Prune only when that cannot win.
		double bound = score;
		for (int i = index, slots = limits.getFreeSlots() - selected.size(); i < candidates.size() && slots > 0; i++, slots--)
		{
			bound += Math.max(0, candidates.get(i).expectedGpPerSlotHour());
		}
		if (bound <= best.score)
		{
			return;
		}

		PortfolioCandidate candidate = candidates.get(index);
		long nextCoins = coins + candidate.getCapitalRequired();
		long nextLoss = loss + candidate.getWorstLoss();
		long item = itemCapital.getOrDefault(candidate.getItemId(), 0L) + candidate.getCapitalRequired();
		long group = groupCapital.getOrDefault(candidate.getGroup(), 0L) + candidate.getCapitalRequired();
		boolean groupAllowed = candidate.getGroup().isEmpty() || limits.getPerGroupCapitalCap() == 0
			|| group <= limits.getPerGroupCapitalCap();
		boolean itemAllowed = limits.getPerItemCapitalCap() == 0 || item <= limits.getPerItemCapitalCap();
		if (!itemIds.contains(candidate.getItemId()) && nextCoins <= limits.getFreeCoins()
			&& nextLoss <= limits.getSessionLossBudget() && itemAllowed && groupAllowed)
		{
			selected.add(candidate);
			itemIds.add(candidate.getItemId());
			itemCapital.put(candidate.getItemId(), item);
			groupCapital.put(candidate.getGroup(), group);
			search(candidates, index + 1, limits, selected, itemCapital, groupCapital, itemIds,
				nextCoins, nextLoss, score + candidate.expectedGpPerSlotHour(), best);
			selected.remove(selected.size() - 1);
			itemIds.remove(candidate.getItemId());
			restore(itemCapital, candidate.getItemId(), item - candidate.getCapitalRequired());
			restore(groupCapital, candidate.getGroup(), group - candidate.getCapitalRequired());
		}
		search(candidates, index + 1, limits, selected, itemCapital, groupCapital, itemIds, coins, loss, score, best);
	}

	private static <T> void restore(Map<T, Long> map, T key, long value)
	{
		if (value == 0) { map.remove(key); } else { map.put(key, value); }
	}

	private static final class Search
	{
		private double score;
		private List<PortfolioCandidate> selected = new ArrayList<>();
	}
}
