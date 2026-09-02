package com.flippingfriend.ui;

import com.flippingfriend.companion.CompanionClient;
import com.flippingfriend.core.PortfolioAllocation;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.core.PortfolioPlan;
import com.flippingfriend.model.Explainer;
import java.awt.Component;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.FontManager;

/** Read-only view of all planned slots; the action card remains the manual execution surface. */
class PortfolioPanel extends JPanel
{
	private final CompanionClient companion;
	private final Explainer explainer;

	PortfolioPanel(CompanionClient companion, Explainer explainer)
	{
		this.companion = companion;
		this.explainer = explainer;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(UiUtils.BACKGROUND);
		setAlignmentX(Component.LEFT_ALIGNMENT);
	}

	/**
	 * The plan last drawn, so an unchanged refresh can be skipped rather than blinked through.
	 * <p>
	 * Compared by reference on purpose: a plan is replaced wholesale when a new one arrives and is
	 * never edited in place, so same object means same rows.
	 */
	private PortfolioPlan lastDrawn;
	private boolean everDrawn;

	void refresh()
	{
		PortfolioPlan plan = companion.lastPlan();
		// An expired plan is not a plan. nextBuySuggestion has always checked this; the list did not,
		// so when the companion stopped producing them the panel kept displaying the last one it saw
		// with nothing to indicate the prices were hours old.
		if (plan != null && plan.getExpiresAt() > 0
			&& plan.getExpiresAt() <= java.time.Instant.now().getEpochSecond())
		{
			plan = null;
		}
		if (everDrawn && plan == lastDrawn)
		{
			return;
		}
		lastDrawn = plan;
		everDrawn = true;

		removeAll();

		if (plan == null)
		{
			add(UiUtils.mutedText("Portfolio companion has not returned a plan yet."));
		}
		else if (!"READY".equals(plan.getStatus()))
		{
			add(UiUtils.mutedText(plan.getReason()));
		}
		else
		{
			add(UiUtils.mutedText("Expected " + explainer.formatGp(Math.round(perSlotHour(plan)))
				+ " per occupied slot-hour"));
			// The bench, not just what fits in the slots you have free this second. Those two were the
			// same list, so it shortened as you placed trades and emptied completely once every slot
			// was busy -- which is when knowing what is next is worth most. The order is unchanged at
			// the top: the first row is still the trade the card is telling you to place.
			for (PortfolioAllocation allocation : plan.getBench())
			{
				// Gson fills these reflectively and skips every constructor guard, so a malformed
				// allocation arrives as a null candidate. Dereferencing it threw out of the panel
				// refresh *after* removeAll(), which left this section blank and skipped the bankroll,
				// the selectors and the repaint for every refresh that followed.
				if (allocation == null || allocation.getCandidate() == null)
				{
					continue;
				}
				add(UiUtils.gap(UiUtils.SPACE_S));
				add(row(allocation));
			}
		}
		revalidate(); repaint();
	}

	/**
	 * The plan's rate per trade, rather than the total across them.
	 * <p>
	 * The optimizer maximises a sum, and that sum was being printed under a label that says "per
	 * occupied slot-hour" -- so a plan offering four trades read twice as high as one offering two of
	 * exactly the same quality. Dividing makes the number mean what the line has always claimed.
	 */
	private static double perSlotHour(PortfolioPlan plan)
	{
		int slots = plan.getAllocations().size();
		return slots <= 0 ? 0 : plan.getExpectedGpPerSlotHour() / slots;
	}

	private JPanel row(PortfolioAllocation allocation)
	{
		PortfolioCandidate candidate = allocation.getCandidate();
		JPanel row = UiUtils.card();
		row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
		JLabel title = UiUtils.label("#" + allocation.getRank() + "  " + candidate.getItemName(),
			UiUtils.TEXT, FontManager.getRunescapeBoldFont());
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(title);
		JLabel detail = UiUtils.small(explainer.formatNumber(candidate.getQuantity()) + " at "
			+ explainer.formatNumber(candidate.getBuyPrice()) + " gp  →  "
			+ explainer.formatNumber(candidate.getSellPrice()) + " gp");
		detail.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(detail);
		JLabel value = UiUtils.small(explainer.formatGp(Math.round(candidate.expectedGpPerSlotHour()))
			+ "/slot-hour · " + Math.round(candidate.getCompletionProbability() * 100) + "% completion");
		value.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(value);
		// Both legs, separately: a slow buy and a slow sell need different responses from the player.
		JLabel timing = UiUtils.small("buy ~" + explainer.formatDuration(candidate.getBuyHours() * 60)
			+ " · sell ~" + explainer.formatDuration(candidate.getSellHours() * 60));
		timing.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(timing);
		UiUtils.constrainWidth(row);
		return row;
	}
}
