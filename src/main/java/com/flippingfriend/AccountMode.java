package com.flippingfriend;

/**
 * Whether to trade as a member or as a free player.
 * <p>
 * The two rules that matter are not symmetric, and getting that wrong costs a free player money.
 * Free accounts may only <em>buy</em> free-to-play items — but there is no restriction at all on
 * <em>selling</em>, whatever your membership status. So someone whose membership has lapsed while
 * holding members' gear can still sell all of it, and should be told to.
 * <p>
 * Slot counts differ too: eight for members, three for free players.
 */
public enum AccountMode
{
	/** Work it out from the world you are logged into. Right for almost everyone. */
	AUTOMATIC("Detect automatically",
		"Works out whether you are a member from the world you are on."),

	/** Free-to-play: three slots, and only free-to-play items are suggested to buy. */
	FREE_TO_PLAY("Free-to-play",
		"Only suggests buying items you can trade without membership, and plans around 3 slots."),

	/** Members: eight slots, every tradeable item considered. */
	MEMBERS("Members",
		"Considers every item and plans around all 8 slots.");

	public static final int MEMBER_SLOTS = 8;
	public static final int FREE_SLOTS = 3;

	private final String displayName;
	private final String description;

	AccountMode(String displayName, String description)
	{
		this.displayName = displayName;
		this.description = description;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public String getDescription()
	{
		return description;
	}

	/**
	 * Resolves this setting against the world actually being played on.
	 *
	 * @param membersWorld whether the current world is a members world
	 * @return true when members' items may be bought and eight slots are available
	 */
	public boolean resolveMembers(boolean membersWorld)
	{
		switch (this)
		{
			case FREE_TO_PLAY:
				return false;
			case MEMBERS:
				return true;
			default:
				return membersWorld;
		}
	}

	public static int slotsFor(boolean members)
	{
		return members ? MEMBER_SLOTS : FREE_SLOTS;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
