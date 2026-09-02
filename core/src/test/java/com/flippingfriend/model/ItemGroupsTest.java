package com.flippingfriend.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Eight slots holding eight different bars is one position, not eight. The grouping only has to be
 * good enough to catch the families a player actually ends up concentrated in.
 */
public class ItemGroupsTest
{
	@Test
	public void groupsTheBulkSkillingFamilies()
	{
		assertEquals("bars and ores", ItemGroups.groupOf("Steel bar"));
		assertEquals("bars and ores", ItemGroups.groupOf("Adamantite ore"));
		assertEquals("logs", ItemGroups.groupOf("Magic logs"));
		assertEquals("seeds", ItemGroups.groupOf("Ranarr seed"));
		assertEquals("potions", ItemGroups.groupOf("Super combat potion(4)"));
	}

	@Test
	public void ammunitionWinsOverTheMetalItIsMadeOf()
	{
		// "Adamant arrow" contains a metal name, but what moves it is ammunition demand.
		assertEquals("ammunition", ItemGroups.groupOf("Adamant arrow"));
		assertEquals("ammunition", ItemGroups.groupOf("Rune dart"));
		assertEquals("ammunition", ItemGroups.groupOf("Cannonball"));
	}

	@Test
	public void unrecognisedItemsAreLeftUngrouped()
	{
		// The safe failure: an unknown item constrains nothing that was not going to be correlated.
		assertEquals(ItemGroups.UNGROUPED, ItemGroups.groupOf("Twisted bow"));
		assertFalse(ItemGroups.isGrouped("Twisted bow"));
		assertEquals(ItemGroups.UNGROUPED, ItemGroups.groupOf(null));
		assertEquals(ItemGroups.UNGROUPED, ItemGroups.groupOf(""));
	}

	@Test
	public void differentFamiliesDoNotCollide()
	{
		assertNotEquals(ItemGroups.groupOf("Magic logs"), ItemGroups.groupOf("Steel bar"));
		assertNotEquals(ItemGroups.groupOf("Ranarr seed"), ItemGroups.groupOf("Shark"));
	}

	@Test
	public void isCaseInsensitive()
	{
		assertTrue(ItemGroups.isGrouped("MAGIC LOGS"));
		assertEquals(ItemGroups.groupOf("magic logs"), ItemGroups.groupOf("Magic Logs"));
	}
}
