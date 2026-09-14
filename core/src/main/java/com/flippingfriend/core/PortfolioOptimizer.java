package com.flippingfriend.core;

import java.util.ArrayList;
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
		// Counted, not just filtered.
		//
		// Every rejection below used to be silent, and all of them came out as the single sentence
		// "No candidate clears portfolio safety constraints" -- which names the one cause that is
		// usually not the reason. A player looking at ninety-four generated tactics, eight free slots
		// and a hundred and twenty-eight million gp, being told about safety constraints, has been
		// told nothing. These counts say which gate actually closed.
		List<PortfolioCandidate> candidates = new ArrayList<>();
		int belowMinimum = 0;
		int noCapital = 0;
		int expired = 0;
		int cannotComplete = 0;
		int notWorthDoing = 0;
		for (PortfolioCandidate candidate : raw)
		{
			// The player's floor on what a flip is worth doing. This lived only in the built-in engine,
			// whose buy result is replaced by this plan on every cycle, so the setting had no effect on
			// anything actually suggested -- a 5,000 gp floor still produced 200 gp flips.
			if (candidate.getNetProfit() < Math.max(1, constraints.getMinProfitPerFlip()))
			{
				belowMinimum++;
			}
			else if (candidate.getCapitalRequired() <= 0)
			{
				noCapital++;
			}
			else if (candidate.getExpiresAt() <= now)
			{
				expired++;
			}
			else if (candidate.getCompletionProbability() <= 0)
			{
				cannotComplete++;
			}
			else
			{
				// The gate nothing was counting, and the one that silently emptied whole plans. The
				// search only ever keeps a set that scores above zero, and a candidate scores its
				// expected profit per slot-hour -- which is the profit if it works, less the cost of
				// unwinding it if the sell leg does not, weighted by how likely each is. A candidate
				// whose unwind cost outweighs its margin scores negative and can never be chosen, so
				// counting it among the survivors only makes the plan look fuller than it is.
				if (candidate.expectedGpPerSlotHour() <= 0)
				{
					notWorthDoing++;
				}
				candidates.add(candidate);
			}
		}
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
			return PortfolioPlan.unavailable(correlationId,
				whyNothingWasChosen(raw.size(), candidates.size(), belowMinimum, noCapital, expired,
					cannotComplete, notWorthDoing, constraints),
				now);
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

	/**
	 * What to tell the player when the plan comes back empty.
	 * <p>
	 * The old answer was always "No candidate clears portfolio safety constraints", whatever had
	 * happened. It sent a real investigation down the wrong path for an hour: the safety constraints
	 * were fine, the capital was fine, the slots were free, and the actual reason -- every tactic
	 * expected to lose money once the cost of an unsold position was counted -- was not mentioned
	 * anywhere. A message that names the wrong cause is worse than one that admits it does not know.
	 */
	private static String whyNothingWasChosen(int generated, int survived, int belowMinimum,
		int noCapital, int expired, int cannotComplete, int notWorthDoing,
		PortfolioConstraints constraints)
	{
		if (generated == 0)
		{
			return "Nothing on the market got as far as being priced. The item filters above explain "
				+ "which items were ruled out and why.";
		}
		if (belowMinimum == generated)
		{
			return "All " + generated + " trades found are worth less than your minimum of "
				+ constraints.getMinProfitPerFlip() + " gp per flip. Lower it to see them.";
		}
		if (survived == 0)
		{
			StringBuilder why = new StringBuilder("None of the " + generated
				+ " trades found could be offered: ");
			why.append(belowMinimum).append(" make less than your ")
				.append(constraints.getMinProfitPerFlip()).append(" gp minimum");
			if (cannotComplete > 0)
			{
				why.append(", ").append(cannotComplete).append(" are not expected to fill");
			}
			if (expired > 0)
			{
				why.append(", ").append(expired).append(" were priced too long ago");
			}
			if (noCapital > 0)
			{
				why.append(", ").append(noCapital).append(" need no capital at all");
			}
			return why.append('.').toString();
		}
		if (notWorthDoing == survived)
		{
			return "All " + survived + " trades that clear your minimum are expected to lose money "
				+ "once the cost of a position that does not sell is counted against them. That "
				+ "usually means the horizon is too long for what the market is offering: a shorter "
				+ "\"how long a flip should take\", or a lower risk level, will shorten it.";
		}
		if (constraints.getFreeSlots() <= 0)
		{
			return "Every Grand Exchange slot is occupied.";
		}
		return survived + " trades were considered and none of them fits inside the limits on "
			+ "capital, exposure or session loss. There is "
			+ constraints.getFreeCoins() + " gp free across "
			+ constraints.getFreeSlots() + " slots.";
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
