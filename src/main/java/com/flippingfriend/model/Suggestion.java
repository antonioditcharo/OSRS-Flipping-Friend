package com.flippingfriend.model;


/**
 * A single instruction, ready to be shown in the panel and drawn over the Grand Exchange.
 * <p>
 * Everything the overlay needs to guide someone through the trade is on this object, so the
 * walkthrough and the panel can never disagree about the price or quantity being asked for.
 */
public class Suggestion
{
	// Says what it is waiting for, and does not promise a number it cannot keep.
	//
	// "Usually takes a few seconds" was written when the plugin fetched one price feed. It then
	// waited on the item mapping, the price poll behind it and sixty per-item history requests, all
	// on one thread -- minutes, described as seconds, with no indication of progress. The wait itself
	// is now mostly gone; the wording no longer under-promises what is left either.
	private static final Suggestion IDLE = waiting("Getting the latest prices",
		"Loading the price feed. If the companion is running this is nearly instant; otherwise the "
			+ "plugin is fetching it from the wiki and will start as soon as it lands.");

	private final SuggestionType type;
	private final int itemId;
	private final String itemName;
	private final int price;
	private final int quantity;
	private final int slot;
	private final long expectedProfit;
	private final double confidence;
	private final double expectedMinutes;
	private final double buyFillMinutes;
	private final double sellFillMinutes;

	private final int breakEvenPrice;
	private final int targetSellPrice;
	private final String headline;
	private final String detail;
	/**
	 * Set when this sale exists to stop a loss growing rather than to bank a profit.
	 * <p>
	 * This used to be carried by giving the suggestion the CANCEL type, which means "abandon the
	 * offer" everywhere else and left no type free for actually abandoning one. A loss cut is a sell:
	 * the player places a sell offer to do it. So it stays a SELL and says what it is here.
	 */
	private boolean lossCut;

	private Suggestion(SuggestionType type, int itemId, String itemName, int price, int quantity, int slot,
		long expectedProfit, double confidence, double expectedMinutes, double buyFillMinutes,
		double sellFillMinutes, int breakEvenPrice, int targetSellPrice, String headline, String detail)
	{
		this.buyFillMinutes = buyFillMinutes;
		this.sellFillMinutes = sellFillMinutes;
		this.type = type;
		this.itemId = itemId;
		this.itemName = itemName;
		this.price = price;
		this.quantity = quantity;
		this.slot = slot;
		this.expectedProfit = expectedProfit;
		this.confidence = confidence;
		this.expectedMinutes = expectedMinutes;
		this.breakEvenPrice = breakEvenPrice;
		this.targetSellPrice = targetSellPrice;
		this.headline = headline;
		this.detail = detail;
	}

	public static Suggestion idle()
	{
		return IDLE;
	}

	public static Suggestion waiting(String headline, String detail)
	{
		return new Suggestion(SuggestionType.WAIT, -1, null, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0,
			headline, detail);
	}


	public static Builder builder(SuggestionType type)
	{
		return new Builder(type);
	}

	public SuggestionType getType()
	{
		return type;
	}

	public int getItemId()
	{
		return itemId;
	}

	public String getItemName()
	{
		return itemName;
	}

	/** The exact price to type into the offer. */
	public int getPrice()
	{
		return price;
	}

	/** The exact quantity to type into the offer. */
	public int getQuantity()
	{
		return quantity;
	}

	/** Grand Exchange slot this refers to, or -1 when any free slot will do. */
	public int getSlot()
	{
		return slot;
	}

	public long getExpectedProfit()
	{
		return expectedProfit;
	}

	public double getConfidence()
	{
		return confidence;
	}

	/** Estimated total time for the whole flip, both legs. */
	public double getExpectedMinutes()
	{
		return expectedMinutes;
	}

	/** Estimated time for the buy offer alone to fill. */
	public double getBuyFillMinutes()
	{
		return buyFillMinutes;
	}

	/** Estimated time to sell once bought. The number people actually want before committing. */
	public double getSellFillMinutes()
	{
		return sellFillMinutes;
	}



	public int getBreakEvenPrice()
	{
		return breakEvenPrice;
	}

	public int getTargetSellPrice()
	{
		return targetSellPrice;
	}

	/** One line, suitable for a heading. */
	/** True when this sale is cutting a loss rather than taking a profit. */
	public boolean isLossCut()
	{
		return lossCut;
	}

	public String getHeadline()
	{
		return headline;
	}

	/** The "why this trade?" body text. */
	public String getDetail()
	{
		return detail;
	}


	public long getCapitalRequired()
	{
		return (long) price * quantity;
	}

	public boolean isActionable()
	{
		return type.isActionable();
	}

	/**
	 * Whether this is materially the same instruction as another, used to avoid resetting the
	 * walkthrough every time prices tick while the user is mid-trade.
	 */
	public boolean sameAs(Suggestion other)
	{
		return other != null
			&& other.type == type
			&& other.itemId == itemId
			&& other.price == price
			&& other.quantity == quantity
			&& other.slot == slot;
	}

	/**
	 * Whether this is the same trade as another, ignoring the numbers.
	 * <p>
	 * The distinction that matters while an offer is being typed: a change to the price or quantity
	 * of the trade already in progress is a <em>correction</em> and must be applied, or the player
	 * places a stale number. A change to a different item or a different kind of action is an
	 * <em>interruption</em>, and applying that mid-entry is how the wrong offer gets placed.
	 */
	public boolean isSameTradeAs(Suggestion other)
	{
		return other != null && other.type == type && other.itemId == itemId
			&& other.slot == slot;
	}

	public static final class Builder
	{
		private final SuggestionType type;
		private int itemId = -1;
		private String itemName;
		private int price;
		private int quantity;
		private int slot = -1;
		private long expectedProfit;
		private double confidence;
		private double expectedMinutes;
		private double buyFillMinutes;
		private double sellFillMinutes;

		private int breakEvenPrice;
		private int targetSellPrice;
		private String headline = "";
		private String detail = "";

		private Builder(SuggestionType type)
		{
			this.type = type;
		}

		public Builder item(int itemId, String itemName)
		{
			this.itemId = itemId;
			this.itemName = itemName;
			return this;
		}

		public Builder price(int price)
		{
			this.price = price;
			return this;
		}

		public Builder quantity(int quantity)
		{
			this.quantity = quantity;
			return this;
		}

		public Builder slot(int slot)
		{
			this.slot = slot;
			return this;
		}

		private boolean lossCut;

		public Builder lossCut(boolean lossCut)
		{
			this.lossCut = lossCut;
			return this;
		}

		public Builder expectedProfit(long expectedProfit)
		{
			this.expectedProfit = expectedProfit;
			return this;
		}

		public Builder confidence(double confidence)
		{
			this.confidence = confidence;
			return this;
		}

		public Builder expectedMinutes(double expectedMinutes)
		{
			this.expectedMinutes = expectedMinutes;
			return this;
		}

		public Builder fillMinutes(double buyMinutes, double sellMinutes)
		{
			this.buyFillMinutes = buyMinutes;
			this.sellFillMinutes = sellMinutes;
			return this;
		}



		public Builder breakEvenPrice(int breakEvenPrice)
		{
			this.breakEvenPrice = breakEvenPrice;
			return this;
		}

		public Builder targetSellPrice(int targetSellPrice)
		{
			this.targetSellPrice = targetSellPrice;
			return this;
		}

		public Builder headline(String headline)
		{
			this.headline = headline;
			return this;
		}

		public Builder detail(String detail)
		{
			this.detail = detail;
			return this;
		}


		public Suggestion build()
		{
			Suggestion suggestion = new Suggestion(type, itemId, itemName, price, quantity, slot,
				expectedProfit, confidence, expectedMinutes, buyFillMinutes, sellFillMinutes,
				breakEvenPrice, targetSellPrice, headline, detail);
			suggestion.lossCut = lossCut;
			return suggestion;
		}
	}
}
