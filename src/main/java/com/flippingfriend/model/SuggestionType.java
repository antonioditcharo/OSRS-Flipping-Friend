package com.flippingfriend.model;

/**
 * The one thing the plugin wants you to do next.
 * <p>
 * Deliberately a single action rather than a list. Someone who has never flipped does not need
 * eight ranked opportunities; they need to know which button to press, and the ordering the engine
 * applies — collect, then fix, then sell, then buy — is itself part of the advice.
 */
public enum SuggestionType
{
	/** Place a buy offer. */
	BUY("Buy"),
	/** Place a sell offer for something already held. */
	SELL("Sell"),
	/**
	 * A buy offer is priced too low to fill and should be cancelled and re-placed higher.
	 * <p>
	 * Separate from {@link #MODIFY_SELL} because the two carry opposite rules and one type carrying
	 * both is how they got confused: repricing a sale has to stop at what the position cost, and
	 * repricing a purchase has no such floor. While they shared a type, the buy-shaped rule reached
	 * the sell branch and walked three positions below cost for a total of 40,540 gp.
	 */
	MODIFY_BUY("Adjust"),
	/** A sell offer is priced above what buyers are paying and should be cancelled and re-placed lower. */
	MODIFY_SELL("Adjust"),
	/**
	 * An existing offer should be abandoned outright, freeing its slot and returning its coins.
	 * <p>
	 * For a long time this was used instead to mean "cut a loss", which is a sell -- so the type said
	 * cancel, the walkthrough guided a sell, and nothing was left to describe actually cancelling an
	 * offer. Cutting a loss is now a SELL carrying {@code isLossCut()}.
	 */
	CANCEL("Cancel"),
	/** Finished offers are waiting in the collection box, tying up a slot. */
	COLLECT("Collect"),
	/** Buy components and pack into a set at the Grand Exchange clerk. */
	PACK("Pack"),
	/** Buy a set and unpack into components at the Grand Exchange clerk. */
	UNPACK("Unpack"),
	/** Buy doses and decant at Bob Barter. */
	DECANT("Decant"),
	/** Nothing worth doing right now, with a reason why. */
	WAIT("Wait");

	private final String label;

	SuggestionType(String label)
	{
		this.label = label;
	}

	public String getLabel()
	{
		return label;
	}

	public boolean isActionable()
	{
		return this != WAIT;
	}

	/** True for either side of a repricing, where the offer is pulled and put back at a new price. */
	public boolean isModify()
	{
		return this == MODIFY_BUY || this == MODIFY_SELL;
	}
}
