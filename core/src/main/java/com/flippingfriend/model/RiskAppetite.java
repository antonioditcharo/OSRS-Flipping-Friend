package com.flippingfriend.model;

/**
 * How boldly to trade — which is a different question from how much of the bankroll to risk.
 * <p>
 * <b>Risk levels used to cap your gold, and that was the wrong knob.</b> A cautious setting held back
 * three quarters of the bankroll, which does not make the trades any safer; it makes the same trades
 * smaller and earns less. If a trade is worth doing it is worth doing with the money available, and
 * if it is not worth doing then no fraction of the bankroll makes it so. Every capital fraction here
 * is therefore gone. The bankroll is always fully available at every level.
 * <p>
 * What the level actually changes is what the system is willing to <em>trade</em>:
 * <ul>
 *   <li><b>Which prices it believes.</b> Wide spreads and prices high in their range are where the
 *       money is and also where manipulation lives, so the bar for believing them moves.</li>
 *   <li><b>How much of the flow it claims.</b> The single biggest limit on deploying capital is not
 *       the buy limit — those allow tens of millions per item — but the share of an item's trading
 *       the system assumes it can capture. Claiming more deploys more gold and fills less reliably.</li>
 *   <li><b>How long it will wait, and how far it will move its price</b> to get filled. Paying a
 *       little over the quote reaches far more depth than sitting at it.</li>
 * </ul>
 * <p>
 * <b>What does not change with appetite:</b> liquidity and staleness. Measured against the background
 * learner's record, loosening those two costs about 17 million gp while loosening the price-shaped
 * bars gains about 9 million. An item nobody is trading, or one with a stale side to its book, is not
 * a braver trade — it is a worse one, and it stays vetoed at every level.
 */
public final class RiskAppetite
{
	/**
	 * Careful, but never small for the sake of it. Still deploys the whole bankroll if the market
	 * will absorb it.
	 */
	public static final RiskAppetite CAUTIOUS = new RiskAppetite("Cautious", VetoThresholds.CAUTIOUS,
		0.35, 1.5, new double[]{0.0, 0.0015, 0.004}, new double[]{0.0, -0.0015, -0.004}, 0.35, 0.50,
		0.02);

	/** The default. */
	public static final RiskAppetite BALANCED = new RiskAppetite("Balanced", VetoThresholds.BALANCED,
		0.55, 2.5, new double[]{0.0, 0.002, 0.006, 0.012}, new double[]{0.0, -0.002, -0.006, -0.012},
		0.60, 0.80, 0.05);

	/**
	 * Bold. Claims most of the flow, waits four hours, and will pay well over the quote to get filled
	 * — which is what it takes to put a hundred million to work across eight slots. Exposure ceilings
	 * are lifted entirely: at this level the only limits are the buy limit and what the market will
	 * actually absorb.
	 */
	public static final RiskAppetite AGGRESSIVE = new RiskAppetite("Aggressive",
		VetoThresholds.AGGRESSIVE, 0.85, 4.0,
		new double[]{0.0, 0.004, 0.010, 0.020, 0.035}, new double[]{0.0, -0.004, -0.010, -0.020, -0.035},
		1.0, 1.0, 0.12);

	private final String name;
	private final VetoThresholds vetoes;
	private final double captureShare;
	private final double horizonHours;
	private final double[] buyOffsets;
	private final double[] sellOffsets;
	private final double itemExposureLimit;
	private final double groupExposureLimit;
	private final double lossCutPct;

	private RiskAppetite(String name, VetoThresholds vetoes, double captureShare, double horizonHours,
		double[] buyOffsets, double[] sellOffsets, double itemExposureLimit,
		double groupExposureLimit, double lossCutPct)
	{
		this.lossCutPct = lossCutPct;
		this.name = name;
		this.vetoes = vetoes;
		this.captureShare = captureShare;
		this.horizonHours = horizonHours;
		this.buyOffsets = buyOffsets;
		this.sellOffsets = sellOffsets;
		this.itemExposureLimit = itemExposureLimit;
		this.groupExposureLimit = groupExposureLimit;
	}

	public static RiskAppetite forName(String risk)
	{
		if (risk == null)
		{
			return BALANCED;
		}
		switch (risk.toUpperCase())
		{
			case "LOW":
			case "CAUTIOUS":
				return CAUTIOUS;
			case "HIGH":
			case "AGGRESSIVE":
				return AGGRESSIVE;
			default:
				return BALANCED;
		}
	}

	public String getName() { return name; }
	public VetoThresholds getVetoes() { return vetoes; }

	/**
	 * Share of the flow at our price the system assumes it can take.
	 * <p>
	 * The lever that decides how much gold gets deployed. Raising it is genuinely a risk: the fill
	 * model is already optimistic about completion, and larger orders make that worse before the
	 * learned model catches up. It is here rather than hidden in a constant because that trade —
	 * more capital at work against less reliable fills — is exactly the choice a risk setting is for.
	 */
	public double getCaptureShare() { return captureShare; }

	/** How long a leg is given before it is abandoned. Longer means bigger orders and slower turnover. */
	public double getHorizonHours() { return horizonHours; }

	/** Price steps above the quote to consider when buying; paying up reaches deeper liquidity. */
	public double[] getBuyOffsets() { return buyOffsets.clone(); }

	public double[] getSellOffsets() { return sellOffsets.clone(); }

	/** Ceiling on one item as a fraction of equity. 1.0 means no ceiling beyond the buy limit. */
	public double getItemExposureLimit() { return itemExposureLimit; }

	/** Ceiling on one correlated family as a fraction of equity. */
	public double getGroupExposureLimit() { return groupExposureLimit; }

	/**
	 * How far below the entry a position is written off, which is what the session loss budget is
	 * measured in. Mirrors {@code RiskProfile.getLossCutPct()} on the plugin side: the companion used a
	 * flat 5% at every setting, so on the cautious profile the budget assumed two and a half times the
	 * loss that would really be taken, and on the bold one under half of it.
	 */
	public double getLossCutPct() { return lossCutPct; }
}
