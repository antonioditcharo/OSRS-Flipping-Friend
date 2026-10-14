package com.flippingfriend.session;

import net.runelite.api.GrandExchangeOffer;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** The observed 1.13.1 client and its hub plugins link against long GE monetary getters. */
public class RuneLiteOfferApiTest
{
	@Test
	public void buildMatchesTheLiveLongMonetaryOfferApi() throws Exception
	{
		assertEquals("the old int signature cannot link to the current client",
			long.class, GrandExchangeOffer.class.getMethod("getPrice").getReturnType());
		assertEquals(long.class, GrandExchangeOffer.class.getMethod("getSpent").getReturnType());
	}
}
