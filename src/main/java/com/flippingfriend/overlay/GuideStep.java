package com.flippingfriend.overlay;

/**
 * Where the player is in carrying out the current suggestion.
 * <p>
 * Each step knows the one sentence that should be on screen while it is active. Someone who has
 * never used the Grand Exchange should be able to follow these from start to finish without knowing
 * anything about flipping.
 */
public enum GuideStep
{
	/** Nothing to do, or nothing to point at. */
	NONE(""),
	OPEN_GE("Open the Grand Exchange"),
	PICK_SLOT("Click an empty Grand Exchange slot"),
	CHOOSE_BUY("Click the buy button"),
	SEARCH_ITEM("Search for the item"),
	PICK_SEARCH_RESULT("Click this item in the list"),
	SET_QUANTITY("Set the quantity"),
	SET_PRICE("Set the price"),
	CONFIRM("Confirm the offer"),
	SELL_FROM_INVENTORY("Click the item in the panel on the right"),
	COLLECT("Collect your finished offer"),
	CANCEL_OFFER("Cancel this offer so it can be replaced"),
	/** Distinct from CANCEL_OFFER: nothing is placed afterwards. */
	ABANDON_OFFER("Cancel this offer to free the slot"),
	WAITING("Waiting for the offer to fill");

	private final String instruction;

	GuideStep(String instruction)
	{
		this.instruction = instruction;
	}

	public String getInstruction()
	{
		return instruction;
	}
}
