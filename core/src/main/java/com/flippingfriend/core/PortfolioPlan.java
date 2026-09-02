package com.flippingfriend.core;

import java.util.Collections;
import java.util.List;

/** Immutable plan returned by the companion API. */
public final class PortfolioPlan
{
	/**
	 * How long a plan may be acted on or displayed. Quotes move, so a plan is perishable: past this
	 * the panel goes quiet rather than keep recommending a price that no longer exists.
	 */
	public static final long TTL_SECONDS = 90;

	private final String correlationId;
	private final long createdAt;
	private final long expiresAt;
	private final String status;
	private final String reason;
	private final double expectedGpPerSlotHour;
	private final List<PortfolioAllocation> allocations;
	private final List<PortfolioAlert> alerts;
	/** Why the plan is what it is; null on plans built before diagnostics were attached. */
	private final PlanDiagnostics diagnostics;

	/**
	 * What the best full board of trades would earn, whether or not any of it can be placed yet.
	 * <p>
	 * Separate from {@link #expectedGpPerSlotHour}, which describes only the trades this plan is
	 * actually offering — the panel labels that "per occupied slot-hour" and it must stay true.
	 * <p>
	 * This one exists to be measured rather than acted on. The rate used to be whatever the plan
	 * happened to be allocating, so it read a third as much with one slot free as with three, and
	 * nothing at all with none — which made the "is it getting better" chart a picture of how busy the
	 * account was rather than of how well the planner was doing. Ranking a full board every time asks
	 * the same question every time.
	 */
	private double boardGpPerSlotHour;
	/** How many trades that board could field, which is not always the whole board. */
	private int boardSize;

	/**
	 * The full queue of trades, whether or not there is a slot for them yet.
	 * <p>
	 * {@link #allocations} is what you can place right now, so it is as long as you have slots free --
	 * which meant the list emptied as you filled your slots and vanished entirely at zero, exactly
	 * when knowing what is next is most useful. The board behind it was already being ranked to the
	 * full slot count every cycle and then discarded down to two numbers.
	 * <p>
	 * The bench begins with the allocations, in their own order, so the top of the list is always the
	 * trade you are being told to place. Everything after it is the queue.
	 */
	private List<PortfolioAllocation> bench;

	public PortfolioPlan(String correlationId, long createdAt, long expiresAt, String status, String reason,
		double expectedGpPerSlotHour, List<PortfolioAllocation> allocations)
	{
		this(correlationId, createdAt, expiresAt, status, reason, expectedGpPerSlotHour, allocations, Collections.emptyList(), null);
	}

	public PortfolioPlan(String correlationId, long createdAt, long expiresAt, String status, String reason,
		double expectedGpPerSlotHour, List<PortfolioAllocation> allocations, List<PortfolioAlert> alerts, PlanDiagnostics diagnostics)
	{
		this.correlationId = correlationId;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
		this.status = status;
		this.reason = reason;
		this.expectedGpPerSlotHour = expectedGpPerSlotHour;
		this.allocations = Collections.unmodifiableList(allocations);
		this.alerts = alerts == null ? Collections.emptyList() : Collections.unmodifiableList(alerts);
		this.diagnostics = diagnostics;
	}

	/**
	 * The same plan with its funnel attached. Diagnostics are gathered while candidates are built,
	 * but a plan can be refused before that point, so they are bolted on afterwards rather than
	 * threaded through every refusal path.
	 */
	public PortfolioPlan withDiagnostics(PlanDiagnostics attached)
	{
		PortfolioPlan copy = new PortfolioPlan(correlationId, createdAt, expiresAt, status, reason,
			expectedGpPerSlotHour, allocations, alerts, attached);
		copy.boardGpPerSlotHour = boardGpPerSlotHour;
		copy.boardSize = boardSize;
		copy.bench = bench;
		return copy;
	}

	/**
	 * The same plan with the full board's rate attached, for measurement.
	 * <p>
	 * Set after the fact for the same reason diagnostics are: a plan can be refused long before the
	 * board is ranked, and threading it through every refusal path would put a measurement concern
	 * into code whose job is deciding what to do.
	 */
	public PortfolioPlan withBoard(double boardRate, int size)
	{
		return withBoard(boardRate, size, null);
	}

	/**
	 * @param queue the full bench, longest-first in the order it should be worked through, or null to
	 *              leave the plan showing only what it can place
	 */
	public PortfolioPlan withBoard(double boardRate, int size, List<PortfolioAllocation> queue)
	{
		PortfolioPlan copy = new PortfolioPlan(correlationId, createdAt, expiresAt, status, reason,
			expectedGpPerSlotHour, allocations, alerts, diagnostics);
		copy.boardGpPerSlotHour = Math.max(0, boardRate);
		copy.boardSize = Math.max(0, size);
		copy.bench = queue == null ? bench : Collections.unmodifiableList(queue);
		return copy;
	}

	/**
	 * A refusal to plan, which is a decision and must be readable for as long as any other.
	 * <p>
	 * These used to expire the instant they were created, which meant every caller checking
	 * {@code expiresAt} discarded them and substituted a generic "no plan yet" message. The effect
	 * was that the specific reason — the drawdown breaker had tripped, prices were stale, every slot
	 * was busy — was computed correctly and then thrown away every single time, leaving the player
	 * watching buys stop with no explanation available anywhere.
	 */
	public static PortfolioPlan unavailable(String correlationId, String reason, long now)
	{
		return new PortfolioPlan(correlationId, now, now + TTL_SECONDS, "UNAVAILABLE", reason, 0,
			Collections.emptyList(), Collections.emptyList(), null);
	}

	public String getCorrelationId() { return correlationId; }
	public long getCreatedAt() { return createdAt; }
	public long getExpiresAt() { return expiresAt; }
	public String getStatus() { return status; }
	public String getReason() { return reason; }
	public double getExpectedGpPerSlotHour() { return expectedGpPerSlotHour; }

	/** What a full board of the best available trades would earn, placeable or not. */
	public double getBoardGpPerSlotHour() { return boardGpPerSlotHour; }

	public int getBoardSize() { return boardSize; }
	/**
	 * Never null, even on an instance Gson built.
	 * <p>
	 * The constructor wraps this in an unmodifiable list, and none of that runs when Gson sets the
	 * field directly — which is exactly how every plan arrives on the plugin side. A missing or
	 * malformed allocations array therefore produced a null here, and the resulting
	 * NullPointerException carries no message, so it surfaced to the player as
	 * "Companion unavailable ... null". PlanDiagnostics already guards its own map for this reason;
	 * its siblings on the same wire did not.
	 */
	public List<PortfolioAllocation> getAllocations()
	{
		return allocations == null ? java.util.Collections.emptyList() : allocations;
	}
	/**
	 * Everything lined up, placeable or not, with what you can place now at the front.
	 * <p>
	 * Falls back to the allocations, so a plan from an older build -- or one Gson filled without ever
	 * running a constructor -- still renders the list it does have rather than nothing.
	 */
	public List<PortfolioAllocation> getBench()
	{
		return bench == null || bench.isEmpty() ? getAllocations() : bench;
	}

	public PlanDiagnostics getDiagnostics() { return diagnostics; }

	public List<PortfolioAlert> getAlerts() { return alerts == null ? Collections.emptyList() : alerts; }
}
