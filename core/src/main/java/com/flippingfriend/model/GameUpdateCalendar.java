package com.flippingfriend.model;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * When the game changes underneath the market.
 *
 * <p>Every model here reads price history and infers what the market is doing. That works because
 * the market is mostly a market — supply, demand, and people flipping. Once a week it stops being
 * one: an update lands, and a drop rate, a recipe, or a piece of content changes what an item
 * <em>is</em>. Prices then move for a reason no amount of price history contains, and every mean
 * reverting fit in this codebase is briefly describing an item that no longer exists.
 *
 * <p>The system already notices this, a day late, and its only response is to go blind:
 * {@link MarketContext#hasStructuralBreak()} compares the last day against the one before it and
 * {@link ManipulationFilter} rejects the item. That is the right response to a surprise. It is the
 * wrong response to something that happens on a schedule, in public, every week.
 *
 * <p><b>Two things follow from simply knowing the time.</b>
 *
 * <ul>
 *   <li><b>A break can be attributed.</b> A three-sigma level shift the morning after an update is
 *       a repricing: the item is worth something different now, the old history is stale, and it
 *       will settle. The same shift on a quiet Sunday night is not a repricing — it is a squeeze, a
 *       manipulation attempt, or bad data, and it deserves a different amount of suspicion. Both
 *       currently produce the same sentence.</li>
 *   <li><b>A position can be out before it lands.</b> A holding whose horizon spans the window is
 *       carrying a risk the forecast cannot price, because the thing that will move the price has
 *       not happened yet and is not in the history. The horizon that matters is the time until the
 *       game changes, not the time until the player's patience runs out.</li>
 * </ul>
 *
 * <h2>What is actually known</h2>
 *
 * <p>The weekly cadence is the reliable part: Old School has shipped its game update on a Wednesday
 * for its entire life, and the schedule is announced. <b>The hour is not reliable</b> — it moves
 * with the release, and a delayed update or a same-day hotfix lands outside any fixed slot. So the
 * window here is deliberately wide rather than precise, and it is a window rather than an instant.
 * Claiming a minute this class cannot know would be worse than claiming a morning it can.
 *
 * <p>Anything off the weekly rhythm — a league start, a Deadman final, a major release on a day of
 * its own — is a date somebody has to supply. {@link #withKnownUpdates} takes them, so a curated
 * list can be fed in without this class pretending to know things it does not. With no list it
 * still has the weekly schedule, which is most of the value and costs no network call, no parsing,
 * and nothing that can go stale without anyone noticing.
 */
public final class GameUpdateCalendar
{
	/** Old School's weekly game update has landed on a Wednesday for the game's entire life. */
	private static final DayOfWeek UPDATE_DAY = DayOfWeek.WEDNESDAY;

	/**
	 * Start of the window, UTC.
	 * <p>
	 * The update typically lands late morning UTC, but the exact time moves and a delayed release
	 * can arrive hours later. This is the beginning of the period where one becomes likely, not a
	 * scheduled moment.
	 */
	private static final int WINDOW_START_HOUR_UTC = 10;

	/**
	 * How long the window stays open.
	 * <p>
	 * Wide on purpose. A narrow window that misses a late update is worse than a wide one that
	 * includes a quiet hour, because the failure mode of missing it is holding a position through
	 * exactly the event this exists to avoid.
	 */
	private static final int WINDOW_HOURS = 6;

	/**
	 * How long after a window a level shift can still be laid at its door.
	 * <p>
	 * A day. The market does not reprice the instant the servers come back: word spreads, drop
	 * tables get tested, and the new level emerges over hours. Beyond a day the connection is a
	 * guess, and an unexplained break wrongly called explained is the more expensive error — it
	 * treats a manipulation as routine.
	 */
	private static final long ATTRIBUTION_HOURS = 24;

	private static final GameUpdateCalendar WEEKLY =
		new GameUpdateCalendar(Collections.<Instant>emptyList());

	/** Extra window starts, sorted, for updates that did not land on the weekly rhythm. */
	private final List<Instant> extra;

	private GameUpdateCalendar(List<Instant> extra)
	{
		this.extra = extra;
	}

	/** The standing weekly schedule, with no curated additions. */
	public static GameUpdateCalendar weekly()
	{
		return WEEKLY;
	}

	/**
	 * The weekly schedule plus dates somebody knows about — a league start, a Deadman final, a
	 * release moved off its usual day.
	 *
	 * @param windowStarts when each of those windows opens; nulls and duplicates are ignored
	 */
	public GameUpdateCalendar withKnownUpdates(Collection<Instant> windowStarts)
	{
		if (windowStarts == null || windowStarts.isEmpty())
		{
			return this;
		}
		List<Instant> merged = new ArrayList<>(extra);
		for (Instant start : windowStarts)
		{
			if (start != null && !merged.contains(start))
			{
				merged.add(start);
			}
		}
		Collections.sort(merged);
		return new GameUpdateCalendar(Collections.unmodifiableList(merged));
	}

	/** True while an update is plausibly landing, so nothing derived from price history is safe. */
	public boolean isInWindow(Instant at)
	{
		if (at == null)
		{
			return false;
		}
		Instant start = previousWindowStart(at);
		return start != null && at.isBefore(start.plus(Duration.ofHours(WINDOW_HOURS)));
	}

	/**
	 * When the next window opens, at or after {@code from}.
	 * <p>
	 * If a window is already open this returns the one after it: callers asking "how long have I
	 * got" during an update have already run out of time, and {@link #isInWindow} is the question
	 * they should be asking.
	 */
	public Instant nextWindowStart(Instant from)
	{
		if (from == null)
		{
			return null;
		}
		Instant weekly = nextWeeklyStart(from);
		Instant best = weekly;
		for (Instant candidate : extra)
		{
			if (!candidate.isBefore(from) && candidate.isBefore(best))
			{
				best = candidate;
			}
		}
		return best;
	}

	/** When the most recent window opened, or null if none has within a fortnight of {@code from}. */
	public Instant previousWindowStart(Instant from)
	{
		if (from == null)
		{
			return null;
		}
		Instant best = previousWeeklyStart(from);
		for (Instant candidate : extra)
		{
			if (!candidate.isAfter(from) && (best == null || candidate.isAfter(best)))
			{
				best = candidate;
			}
		}
		return best;
	}

	/** Hours until the next window opens. Zero while one is open, so callers cannot plan past it. */
	public double hoursUntilNext(Instant from)
	{
		if (from == null)
		{
			return Double.MAX_VALUE;
		}
		if (isInWindow(from))
		{
			return 0;
		}
		Instant next = nextWindowStart(from);
		return next == null ? Double.MAX_VALUE
			: Math.max(0, Duration.between(from, next).toMillis() / 3_600_000.0);
	}

	/** Hours since the last window opened, or {@link Double#MAX_VALUE} if there is none in range. */
	public double hoursSinceLast(Instant from)
	{
		Instant previous = previousWindowStart(from);
		return previous == null ? Double.MAX_VALUE
			: Math.max(0, Duration.between(previous, from).toMillis() / 3_600_000.0);
	}

	/**
	 * Whether a level shift observed at {@code breakAt} is accounted for by an update.
	 * <p>
	 * The question a veto should be able to answer. "This item's price moved to a new level" is a
	 * description; "the update on Wednesday morning changed what it is worth" is a reason, and the
	 * two call for different amounts of caution about coming back to it.
	 */
	public boolean explains(Instant breakAt)
	{
		if (breakAt == null)
		{
			return false;
		}
		Instant previous = previousWindowStart(breakAt);
		if (previous == null)
		{
			return false;
		}
		// From the moment the window opens, not from when it closes: an update that lands at the
		// start of the window has repriced the item for the whole of it.
		return Duration.between(previous, breakAt).toHours() <= ATTRIBUTION_HOURS;
	}

	/**
	 * The horizon a plan may actually rely on: the shorter of what was asked for and the time until
	 * the game changes.
	 * <p>
	 * Not a veto, and not a schedule bolted onto the exit logic. Everything downstream already
	 * reasons about "the time remaining", and this is the honest value of that quantity — planning
	 * six hours ahead through an update landing in forty minutes is planning about a market that
	 * will not exist. The forecast cannot price the event because the event has not happened and is
	 * therefore not in the history it was fitted to.
	 *
	 * @return the usable horizon in hours, never negative
	 */
	public double usableHorizonHours(Instant from, double wantedHours)
	{
		if (wantedHours <= 0)
		{
			return 0;
		}
		return Math.max(0, Math.min(wantedHours, hoursUntilNext(from)));
	}

	private static Instant nextWeeklyStart(Instant from)
	{
		ZonedDateTime moment = from.atZone(ZoneOffset.UTC);
		ZonedDateTime candidate = moment
			.withHour(WINDOW_START_HOUR_UTC).withMinute(0).withSecond(0).withNano(0);
		while (candidate.getDayOfWeek() != UPDATE_DAY || !candidate.toInstant().isAfter(from))
		{
			candidate = candidate.plusDays(1);
		}
		return candidate.toInstant();
	}

	private static Instant previousWeeklyStart(Instant from)
	{
		ZonedDateTime moment = from.atZone(ZoneOffset.UTC);
		ZonedDateTime candidate = moment
			.withHour(WINDOW_START_HOUR_UTC).withMinute(0).withSecond(0).withNano(0);
		while (candidate.getDayOfWeek() != UPDATE_DAY || candidate.toInstant().isAfter(from))
		{
			candidate = candidate.minusDays(1);
		}
		return candidate.toInstant();
	}
}
