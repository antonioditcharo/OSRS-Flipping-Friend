package com.flippingfriend.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

/**
 * The one input to this system that is not derived from price history.
 *
 * <p>Everything else here infers what the market is doing from what it has done. An update is the
 * exception: it is knowable in advance, it is not in any history because it has not happened yet,
 * and it is the reason a mean-reverting fit is occasionally not merely noisy but pointed at a price
 * the game has deleted.
 */
public class GameUpdateCalendarTest
{
	private final GameUpdateCalendar calendar = GameUpdateCalendar.weekly();

	private static Instant utc(String isoInstant)
	{
		return Instant.parse(isoInstant);
	}

	@Test
	public void theWeeklyWindowLandsOnAWednesday()
	{
		Instant next = calendar.nextWindowStart(utc("2026-09-03T12:00:00Z"));

		assertEquals("Old School has shipped its game update on a Wednesday for the game's life",
			DayOfWeek.WEDNESDAY, next.atZone(ZoneOffset.UTC).getDayOfWeek());
	}

	@Test
	public void windowsAreExactlyAWeekApart()
	{
		Instant first = calendar.nextWindowStart(utc("2026-09-03T12:00:00Z"));
		Instant second = calendar.nextWindowStart(first.plusSeconds(1));

		assertEquals(7 * 24, Duration.between(first, second).toHours());
	}

	@Test
	public void aMomentInsideTheWindowIsRecognised()
	{
		Instant open = calendar.nextWindowStart(utc("2026-09-03T12:00:00Z"));

		assertTrue("the moment it opens", calendar.isInWindow(open));
		assertTrue("an hour in", calendar.isInWindow(open.plus(Duration.ofHours(1))));
		assertFalse("an hour before", calendar.isInWindow(open.minus(Duration.ofHours(1))));
		assertFalse("a day later", calendar.isInWindow(open.plus(Duration.ofHours(24))));
	}

	@Test
	public void timeRunsOutToZeroInsideTheWindowRatherThanJumpingToAWeek()
	{
		// The trap this avoids: asking "how long have I got" during an update and being told six days,
		// because the next window is next Wednesday. A caller inside the window has no time at all,
		// and a plan built on a week of runway would be built through the very event it must dodge.
		Instant open = calendar.nextWindowStart(utc("2026-09-03T12:00:00Z"));

		assertEquals(0, calendar.hoursUntilNext(open.plus(Duration.ofHours(2))), 1e-9);
		assertTrue("and outside it, real time is reported",
			calendar.hoursUntilNext(open.minus(Duration.ofHours(5))) > 4.9);
	}

	@Test
	public void aBreakTheMorningAfterAnUpdateIsExplained()
	{
		Instant open = calendar.nextWindowStart(utc("2026-09-03T12:00:00Z"));

		assertTrue("during it", calendar.explains(open.plus(Duration.ofHours(1))));
		assertTrue("that evening", calendar.explains(open.plus(Duration.ofHours(9))));
		assertTrue("the following morning, because a market takes hours to find the new level",
			calendar.explains(open.plus(Duration.ofHours(23))));
	}

	@Test
	public void aBreakDaysFromAnyUpdateIsNotExplained()
	{
		// The distinction the whole class exists for. A three-sigma shift on a quiet Sunday is a
		// squeeze, a manipulation attempt or bad data — and the right response to that is to stay
		// away, not to wait for it to settle.
		Instant open = calendar.nextWindowStart(utc("2026-09-03T12:00:00Z"));

		assertFalse("two days later", calendar.explains(open.plus(Duration.ofHours(48))));
		assertFalse("three days later", calendar.explains(open.plus(Duration.ofHours(72))));
	}

	@Test
	public void aCuratedDateIsHonouredAlongsideTheWeeklyRhythm()
	{
		// A league start, a Deadman final, a release moved off its usual day: not derivable from a
		// day of the week, and not something this class should pretend to know on its own.
		Instant monday = utc("2026-09-07T09:00:00Z");
		GameUpdateCalendar withLeague =
			calendar.withKnownUpdates(Collections.singletonList(monday));

		assertFalse("the weekly calendar knows nothing about it", calendar.isInWindow(monday));
		assertTrue("the curated one does", withLeague.isInWindow(monday));
		assertTrue("and it explains a break that follows it",
			withLeague.explains(monday.plus(Duration.ofHours(3))));
		assertEquals("while the weekly Wednesday is still there",
			DayOfWeek.WEDNESDAY,
			withLeague.nextWindowStart(monday.plus(Duration.ofDays(1)))
				.atZone(ZoneOffset.UTC).getDayOfWeek());
	}

	@Test
	public void theSoonerOfTheTwoWins()
	{
		Instant tuesday = utc("2026-09-08T09:00:00Z");
		GameUpdateCalendar withHotfix =
			calendar.withKnownUpdates(Arrays.asList(tuesday, utc("2026-10-01T09:00:00Z")));

		Instant from = tuesday.minus(Duration.ofHours(6));

		assertEquals("a hotfix on Tuesday comes before Wednesday's update", tuesday,
			withHotfix.nextWindowStart(from));
	}

	@Test
	public void aHorizonIsCutShortByTheUpdateRatherThanRunningThroughIt()
	{
		// The number that changes a decision. Planning six hours ahead through an update landing in
		// forty minutes is planning about a market that will not exist.
		Instant open = calendar.nextWindowStart(utc("2026-09-03T12:00:00Z"));
		Instant fortyMinutesBefore = open.minus(Duration.ofMinutes(40));

		assertEquals("what is left, not what was wanted", 40.0 / 60.0,
			calendar.usableHorizonHours(fortyMinutesBefore, 6.0), 0.01);
		assertEquals("and a short horizon well clear of one is untouched", 2.0,
			calendar.usableHorizonHours(open.minus(Duration.ofHours(30)), 2.0), 1e-9);
		assertEquals("nothing is usable during the update itself", 0.0,
			calendar.usableHorizonHours(open.plus(Duration.ofHours(1)), 6.0), 1e-9);
	}

	@Test
	public void nullsAreAnsweredRatherThanThrown()
	{
		// This is consulted from the middle of the sell decision, where an exception costs a fill.
		assertFalse(calendar.isInWindow(null));
		assertFalse(calendar.explains(null));
		assertEquals(Double.MAX_VALUE, calendar.hoursUntilNext(null), 1e-9);
		assertEquals(calendar, calendar.withKnownUpdates(null));
	}
}
