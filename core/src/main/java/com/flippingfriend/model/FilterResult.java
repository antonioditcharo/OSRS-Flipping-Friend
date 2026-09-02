package com.flippingfriend.model;

/**
 * Outcome of screening one item, carrying a plain-English reason when it is rejected so the panel
 * can explain itself instead of silently showing nothing.
 */
public class FilterResult
{
	private static final FilterResult ACCEPTED = new FilterResult(true, null);

	private final boolean accepted;
	private final String reason;

	private FilterResult(boolean accepted, String reason)
	{
		this.accepted = accepted;
		this.reason = reason;
	}

	public static FilterResult accepted()
	{
		return ACCEPTED;
	}

	public static FilterResult rejected(String reason)
	{
		return new FilterResult(false, reason);
	}

	public boolean isAccepted()
	{
		return accepted;
	}

	public String getReason()
	{
		return reason;
	}
}
