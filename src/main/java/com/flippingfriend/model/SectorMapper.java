package com.flippingfriend.model;

import net.runelite.api.ItemID;

public class SectorMapper
{
	public static MarketSector getSector(int itemId, String itemName)
	{
		if (itemName == null) return MarketSector.OTHER;
		
		String lower = itemName.toLowerCase();
		
		if (lower.contains("rune") || lower.contains("staff") || lower.contains("wand"))
		{
			return MarketSector.RUNES;
		}
		if (lower.contains("potion") || lower.contains("herb") || lower.contains("weed") || lower.contains("seed"))
		{
			return MarketSector.HERBLORE;
		}
		if (lower.contains("log") || lower.contains("ore") || lower.contains("bar") || lower.contains("leather"))
		{
			return MarketSector.SKILLING;
		}
		if (lower.contains("sword") || lower.contains("bow") || lower.contains("armour") || lower.contains("shield") || lower.contains("plate"))
		{
			return MarketSector.PVM;
		}
		if (lower.contains("shark") || lower.contains("karambwan") || lower.contains("food") || lower.contains("pie"))
		{
			return MarketSector.FOOD;
		}
		
		return MarketSector.OTHER;
	}
}
