package com.flippingfriend.core;

/** Public, non-sensitive service status returned to the plugin. */
public final class CompanionHealth
{
	private final String status;
	private final String reason;
	private final long marketObservedAt;
	private final long modelVersion;
	private final long checkedAt;

	public CompanionHealth(String status, String reason, long marketObservedAt, long modelVersion, long checkedAt)
	{
		this.status = status;
		this.reason = reason;
		this.marketObservedAt = marketObservedAt;
		this.modelVersion = modelVersion;
		this.checkedAt = checkedAt;
	}

	public String getStatus() { return status; }
	public String getReason() { return reason; }
	public long getMarketObservedAt() { return marketObservedAt; }
	public long getModelVersion() { return modelVersion; }
	public long getCheckedAt() { return checkedAt; }
	public boolean isReady() { return "READY".equals(status); }
}
