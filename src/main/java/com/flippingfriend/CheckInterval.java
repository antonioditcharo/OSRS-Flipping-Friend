package com.flippingfriend;

/**
 * How often the player expects to come back to the Grand Exchange.
 * <p>
 * This is not a cosmetic preference — it is the single most useful thing the plugin can know about
 * how you actually play, and it changes what a good trade looks like. Someone stood at the Exchange
 * wants small, fast offers that fill between clicks. Someone checking once an hour wants fewer,
 * larger, wider-margin offers that are comfortably done by the time they return; suggesting them a
 * four-minute flip just means the offer sits completed for fifty-six minutes, holding a slot and
 * earning nothing.
 * <p>
 * So it feeds the fill horizon (and therefore order size), and it sets how long an offer may sit
 * before the plugin calls it stuck — nagging someone to re-price an offer they cannot see for
 * another hour is noise.
 */
public enum CheckInterval
{
	CONSTANT("I'm at the Exchange", "Standing at the Grand Exchange, checking constantly.", 3),
	FIVE_MINUTES("Every few minutes", "Nearby, glancing back regularly.", 5),
	FIFTEEN_MINUTES("Every 15 minutes", "Doing something else, checking in fairly often.", 15),
	THIRTY_MINUTES("Every 30 minutes", "Off doing content, back every so often.", 30),
	HOURLY("Every hour", "Checking once an hour or so.", 60),
	OCCASIONAL("Every few hours", "Set offers and leave them for a long while.", 180);

	private final String displayName;
	private final String description;
	private final int minutes;

	CheckInterval(String displayName, String description, int minutes)
	{
		this.displayName = displayName;
		this.description = description;
		this.minutes = minutes;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public String getDescription()
	{
		return description;
	}

	public int getMinutes()
	{
		return minutes;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
