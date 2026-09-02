package com.flippingfriend.model;

public enum MarketSector
{
	PVM("PvM Gear"),
	SKILLING("Skilling Supplies"),
	HERBLORE("Herblore"),
	RUNES("Runes & Magic"),
	FOOD("Food & Potions"),
	OTHER("General/Other");

	private final String name;

	MarketSector(String name)
	{
		this.name = name;
	}

	public String getName()
	{
		return name;
	}
}
