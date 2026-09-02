package com.flippingfriend.core;

/** A selected candidate assigned to an available slot, in manual-action order. */
public final class PortfolioAllocation
{
	private final int rank;
	private final PortfolioCandidate candidate;
	private final String action;

	public PortfolioAllocation(int rank, PortfolioCandidate candidate, String action)
	{
		this.rank = rank;
		this.candidate = candidate;
		this.action = action;
	}

	public int getRank() { return rank; }
	public PortfolioCandidate getCandidate() { return candidate; }
	public String getAction() { return action; }
}
