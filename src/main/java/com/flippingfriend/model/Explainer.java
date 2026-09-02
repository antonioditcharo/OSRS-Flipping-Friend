package com.flippingfriend.model;

import com.flippingfriend.RiskProfile;
import com.flippingfriend.TradingHorizon;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Singleton;

/**
 * Turns the model's internal state into sentences someone who has never flipped can act on.
 * <p>
 * The plugin assumes no prior knowledge, so nothing here leans on jargon: no "spread", no
 * "liquidity", no "z-score". A player should be able to read a suggestion and understand both what
 * to do and why, without first having to learn what the plugin means. Every number shown is one
 * that survives contact with the game — profit is always after tax, times are always estimates
 * described as such.
 */
@Singleton
public class Explainer
{
	/** Above this the learned correction is worth mentioning to the user. */
	private static final double NOTABLE_CALIBRATION = 1.15;
	private static final double POOR_CALIBRATION = 0.85;
	private static final int MIN_OBSERVATIONS_TO_MENTION = 3;




	/** "about 4 minutes", "about 2 hours", and honest about not knowing. */
	public String formatDuration(double minutes)
	{
		if (Double.isNaN(minutes) || Double.isInfinite(minutes) || minutes <= 0)
		{
			return "an unknown amount of time";
		}
		if (minutes < 1)
		{
			return "under a minute";
		}
		if (minutes < 90)
		{
			return Math.round(minutes) + (Math.round(minutes) == 1 ? " minute" : " minutes");
		}
		double hours = minutes / 60.0;
		if (hours < 10)
		{
			return String.format("%.1f hours", hours);
		}
		return Math.round(hours) + " hours";
	}

	/** Compact gp for tight spaces: 1.2m, 340k, 950. */
	public String formatGp(long amount)
	{
		long abs = Math.abs(amount);
		String sign = amount < 0 ? "-" : "";
		if (abs >= 1_000_000_000L)
		{
			return sign + trim(abs / 1_000_000_000.0) + "b gp";
		}
		if (abs >= 1_000_000L)
		{
			return sign + trim(abs / 1_000_000.0) + "m gp";
		}
		if (abs >= 10_000L)
		{
			return sign + trim(abs / 1_000.0) + "k gp";
		}
		return sign + formatNumber(abs) + " gp";
	}

	/** Full precision with thousands separators, for numbers the user has to type. */
	public String formatNumber(long value)
	{
		return String.format("%,d", value);
	}

	private static String trim(double value)
	{
		String formatted = String.format("%.1f", value);
		return formatted.endsWith(".0") ? formatted.substring(0, formatted.length() - 2) : formatted;
	}
}
