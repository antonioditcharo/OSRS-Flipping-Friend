package com.flippingfriend.ui;

import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.FontManager;

/**
 * A short explanation shown until it is dismissed.
 * <p>
 * The plugin assumes the reader has never flipped, so it should say what flipping is before it
 * starts issuing instructions. It also states plainly what the plugin will not do, because a tool
 * that tells you what to click in a game is exactly the kind of thing a player is right to be
 * cautious about.
 */
class OnboardingCard extends JPanel
{
	private static final String INTRO =
		"Flipping means buying an item on the Grand Exchange for a little less than people are "
			+ "paying for it, then selling it back at the higher price. The difference, minus the "
			+ "2% sale tax, is your profit.\n\n"
			+ "This panel tells you what to buy, how many, and at what price. When you open the "
			+ "Grand Exchange it highlights exactly what to click. It watches what you own and tells "
			+ "you when to sell.\n\n"
			+ "It never clicks or types for you. You place every offer yourself.";

	OnboardingCard(Runnable onDismiss)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(UiUtils.CARD);
		setBorder(UiUtils.cardBorder());
		setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel title = UiUtils.title("New to flipping?");
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(title);
		add(UiUtils.gap(UiUtils.SPACE_S));

		add(UiUtils.mutedText(INTRO));

		JButton dismiss = new JButton("Got it");
		dismiss.setFont(FontManager.getRunescapeSmallFont());
		dismiss.setForeground(UiUtils.ACCENT);
		dismiss.setBackground(UiUtils.CARD);
		dismiss.setBorderPainted(false);
		dismiss.setFocusPainted(false);
		dismiss.setContentAreaFilled(false);
		dismiss.setHorizontalAlignment(SwingConstants.LEFT);
		dismiss.setBorder(BorderFactory.createEmptyBorder(UiUtils.SPACE_S, 0, 0, 0));
		dismiss.setAlignmentX(Component.LEFT_ALIGNMENT);
		dismiss.addActionListener(e -> onDismiss.run());
		add(dismiss);

		UiUtils.constrainWidth(this);
	}
}
