package com.flippingfriend;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Free-to-play handling is asymmetric in a way that is easy to get backwards: buying members' items
 * is blocked, selling them never is. These cases pin down the resolution and the slot counts.
 */
public class AccountModeTest
{
	@Test
	public void automaticFollowsTheWorld()
	{
		assertTrue(AccountMode.AUTOMATIC.resolveMembers(true));
		assertFalse(AccountMode.AUTOMATIC.resolveMembers(false));
	}

	@Test
	public void freeToPlayOverridesAMembersWorld()
	{
		// Someone about to lose membership can plan ahead while still on a members world.
		assertFalse(AccountMode.FREE_TO_PLAY.resolveMembers(true));
		assertFalse(AccountMode.FREE_TO_PLAY.resolveMembers(false));
	}

	@Test
	public void membersOverrideAFreeWorld()
	{
		assertTrue(AccountMode.MEMBERS.resolveMembers(false));
		assertTrue(AccountMode.MEMBERS.resolveMembers(true));
	}

	@Test
	public void slotCountsMatchTheGame()
	{
		assertEquals(8, AccountMode.slotsFor(true));
		assertEquals(3, AccountMode.slotsFor(false));
	}

	@Test
	public void everyModeIsDescribedForTheUser()
	{
		for (AccountMode mode : AccountMode.values())
		{
			assertFalse(mode.getDisplayName().isEmpty());
			assertFalse(mode.getDescription().isEmpty());
		}
	}
}
