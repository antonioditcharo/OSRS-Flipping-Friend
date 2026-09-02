package com.flippingfriend.core;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The plan as it actually arrives: reflectively, from JSON, with every constructor guard bypassed.
 * <p>
 * These classes are built by a constructor that clamps probabilities, defaults strings and wraps
 * lists — and none of that runs on the plugin side, because Gson writes the fields directly. Three
 * separate javadocs in these files record that this has already caused a bug, and the only existing
 * tests build the objects through the constructor: precisely the path that cannot fail. A malformed
 * plan reaching the panel used to blank the sidebar for the rest of the session.
 */
public class PlanWireFormatTest
{
	private final Gson gson = new Gson();

	@Test
	public void anAccountSnapshotSurvivesTheWireWithItsNewFields()
	{
		// Gson sets fields directly and skips every constructor guard, which has already produced a
		// null-pointer in this project that reached the player as a bare "null". These maps are read
		// on the companion side to seed exposure ceilings and to correct the buy-limit ledger, so a
		// null here would be an NPE on the planning path.
		AccountSnapshot snapshot = gson.fromJson(
			"{\"correlationId\":\"c\",\"spendableCoins\":1000,\"freeSlots\":8,"
				+ "\"committedByItem\":{\"4151\":250000},\"minProfitPerFlip\":5000,"
				+ "\"buyLimitUsed\":{\"561\":3000}}", AccountSnapshot.class);

		assertNotNull(snapshot);
		assertEquals(Long.valueOf(250_000), snapshot.getCommittedByItem().get(4151));
		assertEquals(Integer.valueOf(3_000), snapshot.getBuyLimitUsed().get(561));
		assertEquals(5_000, snapshot.getMinProfitPerFlip());
	}

	@Test
	public void anAccountSnapshotMissingTheNewFieldsIsStillSafe()
	{
		AccountSnapshot snapshot = gson.fromJson(
			"{\"correlationId\":\"c\",\"spendableCoins\":1000}", AccountSnapshot.class);

		assertNotNull("a missing map must not become a null map", snapshot.getCommittedByItem());
		assertTrue(snapshot.getCommittedByItem().isEmpty());
		assertNotNull(snapshot.getBuyLimitUsed());
		assertTrue(snapshot.getBuyLimitUsed().isEmpty());
		assertEquals(0, snapshot.getMinProfitPerFlip());
	}

	@Test
	public void aPlanWithNoAllocationsArrayStillAnswersSafely()
	{
		PortfolioPlan plan = gson.fromJson(
			"{\"correlationId\":\"c\",\"status\":\"READY\",\"reason\":\"ok\"}", PortfolioPlan.class);

		assertNotNull(plan);
		assertNotNull("a missing array must not become a null list", plan.getAllocations());
		assertTrue(plan.getAllocations().isEmpty());
	}

	@Test
	public void anAllocationWithNoCandidateIsDetectable()
	{
		// The panel iterates allocations and dereferences the candidate. If Gson can produce a null
		// one, callers have to be able to see it coming.
		PortfolioPlan plan = gson.fromJson(
			"{\"status\":\"READY\",\"allocations\":[{\"rank\":1,\"action\":\"PLACE_BUY\"}]}",
			PortfolioPlan.class);

		assertEquals(1, plan.getAllocations().size());
		PortfolioAllocation allocation = plan.getAllocations().get(0);
		assertNotNull(allocation);
		// Null is the honest answer here; the contract is that reading it does not throw.
		allocation.getCandidate();
	}

	@Test
	public void aCandidateWithNoStringsReturnsEmptyRatherThanNull()
	{
		PortfolioPlan plan = gson.fromJson(
			"{\"status\":\"READY\",\"allocations\":[{\"rank\":1,\"candidate\":{\"itemId\":4151}}]}",
			PortfolioPlan.class);

		PortfolioCandidate candidate = plan.getAllocations().get(0).getCandidate();
		assertNotNull(candidate);
		assertEquals("", candidate.getItemName());
		assertEquals("", candidate.getGroup());
	}

	@Test
	public void diagnosticsSurviveBeingAbsent()
	{
		PortfolioPlan plan = gson.fromJson("{\"status\":\"READY\"}", PortfolioPlan.class);

		PlanDiagnostics diagnostics = plan.getDiagnostics();
		if (diagnostics != null)
		{
			assertNotNull(diagnostics.getVetoCounts());
		}
	}

	@Test
	public void aPlanSurvivesTheRoundTripItActuallyMakes()
	{
		// Serialise exactly as the companion does, parse exactly as the plugin does.
		PortfolioCandidate candidate = new PortfolioCandidate(4151, "Abyssal whip", "weapons",
			1_000_000, 1_000_000, 1_000_000, 1_050_000, 1_050_000, 1_050_000, 5, 8, 200_000, 5_000, 10_000, 0.9, 0.85, 0.4, 0.6, 4.0,
			Long.MAX_VALUE);
		PortfolioPlan original = new PortfolioPlan("c", 1, Long.MAX_VALUE, "READY", "ok", 1234.5,
			java.util.Collections.singletonList(
				new PortfolioAllocation(1, candidate, "PLACE_BUY")));

		PortfolioPlan parsed = gson.fromJson(gson.toJson(original), PortfolioPlan.class);

		assertEquals(1, parsed.getAllocations().size());
		PortfolioCandidate landed = parsed.getAllocations().get(0).getCandidate();
		assertEquals("Abyssal whip", landed.getItemName());
		assertEquals(1_000_000, landed.getTargetBuyPrice());
		assertEquals(5, landed.getQuantity());
		assertEquals(original.getExpectedGpPerSlotHour(), parsed.getExpectedGpPerSlotHour(), 1e-9);
		assertEquals(candidate.expectedProfit(), landed.expectedProfit(), 1e-6);
	}
}
