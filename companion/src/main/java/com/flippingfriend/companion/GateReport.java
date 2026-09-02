package com.flippingfriend.companion;

import java.util.ArrayList;
import java.util.List;

/**
 * The promotion gates: what must be true before the optimizer's advice deserves to be trusted.
 * <p>
 * These are written down and evaluated mechanically for one reason — so that the decision to trust
 * the strategy is not made by whoever is looking at the numbers and wants them to be good. A gate
 * that is checked by eye is a gate that gets waived on the day it matters.
 * <p>
 * Every gate is scored on the <b>test</b> period only. Results on data the strategy was developed
 * against say nothing: any strategy can be made to look excellent on the history it was built from,
 * and that is precisely the failure mode walk-forward evaluation exists to catch.
 */
final class GateReport
{
	private final List<Gate> gates = new ArrayList<>();

	void add(String name, boolean passed, String detail)
	{
		gates.add(new Gate(name, passed, detail));
	}

	boolean allPassed()
	{
		return gates.stream().allMatch(gate -> gate.passed);
	}

	int passedCount()
	{
		return (int) gates.stream().filter(gate -> gate.passed).count();
	}

	int total()
	{
		return gates.size();
	}

	List<Gate> getGates()
	{
		return gates;
	}

	/** A fixed-width table, because this is meant to be read in a terminal and pasted into a log. */
	String render()
	{
		StringBuilder text = new StringBuilder();
		text.append(String.format("%-6s  %-34s  %s%n", "RESULT", "GATE", "MEASURED"));
		text.append(String.format("%-6s  %-34s  %s%n", "------", "----------------------------------",
			"--------"));
		for (Gate gate : gates)
		{
			text.append(String.format("%-6s  %-34s  %s%n", gate.passed ? "PASS" : "FAIL", gate.name,
				gate.detail));
		}
		text.append(String.format("%n%d of %d gates passed — %s%n", passedCount(), total(),
			allPassed() ? "the optimizer may be promoted" : "NOT ready for promotion"));
		return text.toString();
	}

	static final class Gate
	{
		private final String name;
		private final boolean passed;
		private final String detail;

		Gate(String name, boolean passed, String detail)
		{
			this.name = name;
			this.passed = passed;
			this.detail = detail;
		}

		String getName() { return name; }
		boolean isPassed() { return passed; }
		String getDetail() { return detail; }
	}
}
