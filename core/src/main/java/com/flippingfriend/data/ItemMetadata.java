package com.flippingfriend.data;

/**
 * One entry from the wiki's {@code /mapping} endpoint: the static facts about an item that never
 * change between updates. Deserialised directly by Gson, so the field names must match the JSON.
 */
public class ItemMetadata
{
	private int id;
	private String name;
	private String examine;
	private boolean members;
	private int lowalch;
	private int highalch;
	/** 4-hour Grand Exchange buy limit. Absent in the feed for a handful of items. */
	private Integer limit;
	private int value;
	private String icon;

	public ItemMetadata()
	{
	}

	/** Used by tests and the backtester, which build items directly rather than from the feed. */
	public ItemMetadata(int id, String name, boolean members, Integer limit, int value)
	{
		this.id = id;
		this.name = name;
		this.members = members;
		this.limit = limit;
		this.value = value;
	}

	public int getId()
	{
		return id;
	}

	public String getName()
	{
		return name == null ? "Item " + id : name;
	}

	public String getExamine()
	{
		return examine;
	}

	public boolean isMembers()
	{
		return members;
	}

	public int getLowAlch()
	{
		return lowalch;
	}

	public int getHighAlch()
	{
		return highalch;
	}

	/**
	 * The 4-hour buy limit, or a conservative fallback when the feed does not publish one. Guessing
	 * low is the safe direction: an under-estimate only costs us a smaller suggestion, whereas an
	 * over-estimate produces an offer the game will silently refuse to fill.
	 */
	public int getBuyLimit()
	{
		if (limit != null && limit > 0)
		{
			return limit;
		}
		return value >= 100_000 ? 8 : 100;
	}

	public boolean hasPublishedBuyLimit()
	{
		return limit != null && limit > 0;
	}

	public int getValue()
	{
		return value;
	}

	public String getIcon()
	{
		return icon;
	}
}
