package com.flippingfriend.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Shared look and feel for the side panel.
 * <p>
 * Two Swing traps caused most of the clipping this replaces, and the helpers here exist to avoid
 * both.
 * <p>
 * <b>Wrapped text.</b> An HTML label decides its height from its <em>preferred</em> width, not the
 * width it is actually given, so in a narrow column it reports the height of one line and then
 * draws three. The extra lines are simply cut off. {@link #wrappedText} uses a word-wrapping text
 * area instead, which measures against the width it really has.
 * <p>
 * <b>Fixed heights.</b> Pinning a row to a pixel height guarantees clipping the moment the content
 * needs one more line, which a long item name or a larger font will do. In a vertical stack it is
 * the width that needs constraining; height should follow the content.
 */
final class UiUtils
{
	// Derived rather than guessed: the usable column is what remains after the panel border and the
	// scrollbar have taken their share. Laying text out wider than this is half the clipping problem.
	static final int PANEL_BORDER = 8;
	static final int CARD_PADDING = 10;
	static final int SCROLLBAR_ALLOWANCE = 12;
	static final int CONTENT_WIDTH = PluginPanel.PANEL_WIDTH - (PANEL_BORDER * 2) - SCROLLBAR_ALLOWANCE;
	static final int CARD_TEXT_WIDTH = CONTENT_WIDTH - (CARD_PADDING * 2);

	/** One spacing scale, so vertical rhythm is consistent instead of eyeballed per panel. */
	static final int SPACE_XS = 3;
	static final int SPACE_S = 6;
	static final int SPACE_M = 10;
	static final int SPACE_L = 16;

	static final Color BACKGROUND = ColorScheme.DARK_GRAY_COLOR;
	static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
	static final Color CARD_HOVER = ColorScheme.DARKER_GRAY_HOVER_COLOR;
	static final Color DIVIDER = new Color(60, 63, 70);
	static final Color TEXT = new Color(224, 226, 231);
	static final Color MUTED = new Color(148, 154, 163);
	static final Color ACCENT = new Color(0, 214, 126);
	static final Color PROFIT = new Color(88, 214, 141);
	static final Color LOSS = new Color(233, 108, 100);
	static final Color WARNING = new Color(240, 178, 78);
	static final Color BUY = new Color(84, 168, 240);

	private UiUtils()
	{
	}

	static Border cardBorder()
	{
		return BorderFactory.createEmptyBorder(CARD_PADDING, CARD_PADDING, CARD_PADDING, CARD_PADDING);
	}

	/** A card surface with consistent padding. */
	static JPanel card()
	{
		JPanel panel = new JPanel();
		panel.setBackground(CARD);
		panel.setBorder(cardBorder());
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	/** A small upper-case heading with a rule, separating sections of the panel. */
	static JPanel sectionHeader(String title)
	{
		JPanel panel = new JPanel(new BorderLayout(SPACE_S, 0));
		panel.setBackground(BACKGROUND);
		panel.setBorder(BorderFactory.createEmptyBorder(SPACE_L, 0, SPACE_S, 0));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel label = new JLabel(title.toUpperCase());
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(MUTED);
		panel.add(label, BorderLayout.WEST);

		JPanel rule = new JPanel();
		rule.setBackground(DIVIDER);
		rule.setPreferredSize(new Dimension(0, 1));

		JPanel ruleHolder = new JPanel(new BorderLayout());
		ruleHolder.setBackground(BACKGROUND);
		ruleHolder.setBorder(BorderFactory.createEmptyBorder(7, 0, 0, 0));
		ruleHolder.add(rule, BorderLayout.CENTER);
		panel.add(ruleHolder, BorderLayout.CENTER);

		return panel;
	}

	static JLabel label(String text, Color color, Font font)
	{
		JLabel label = new JLabel(text);
		label.setForeground(color);
		label.setFont(font);
		return label;
	}

	static JLabel title(String text)
	{
		return label(text, TEXT, FontManager.getRunescapeBoldFont());
	}

	static JLabel body(String text)
	{
		return label(text, TEXT, FontManager.getRunescapeFont());
	}

	static JLabel small(String text)
	{
		return label(text, MUTED, FontManager.getRunescapeSmallFont());
	}

	/**
	 * Multi-line text that wraps to the width it is actually given and reports an honest height for
	 * it. Use this anywhere the content is a sentence rather than a value.
	 */
	static JTextArea wrappedText(String text, Color color, Font font, int width)
	{
		JTextArea area = new JTextArea(text == null ? "" : text);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setEditable(false);
		area.setFocusable(false);
		// Stop the caret dragging the scroll pane around.
		//
		// A JTextArea keeps a live caret even when it is read-only and unfocusable. setText moves
		// that caret, and JTextComponent responds by calling scrollRectToVisible on itself, which
		// walks up to the enclosing viewport and scrolls it -- every refresh, whether or not the text
		// changed. The last component to do it wins, and that is the learning verdict near the bottom
		// of the column, so the sidebar jumped to the bottom on every single update.
		if (area.getCaret() instanceof javax.swing.text.DefaultCaret)
		{
			((javax.swing.text.DefaultCaret) area.getCaret())
				.setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
		}
		area.setOpaque(false);
		area.setBorder(null);
		area.setForeground(color);
		area.setFont(font);
		area.setAlignmentX(Component.LEFT_ALIGNMENT);
		sizeToWidth(area, width);
		return area;
	}

	static JTextArea bodyText(String text)
	{
		return wrappedText(text, TEXT, FontManager.getRunescapeFont(), CARD_TEXT_WIDTH);
	}

	static JTextArea mutedText(String text)
	{
		return wrappedText(text, MUTED, FontManager.getRunescapeSmallFont(), CARD_TEXT_WIDTH);
	}

	/** Replaces wrapped text and lets the component re-measure its height for the new content. */
	static void setWrappedText(JTextArea area, String text)
	{
		area.setText(text == null ? "" : text);
		sizeToWidth(area, area.getWidth() > 0 ? area.getWidth() : CARD_TEXT_WIDTH);
		area.revalidate();
	}

	/**
	 * Pins the wrapping width so the text area measures its height against the column it will
	 * really occupy, rather than reporting one very long line.
	 */
	private static void sizeToWidth(JTextArea area, int width)
	{
		area.setSize(new Dimension(width, Short.MAX_VALUE));
		int height = area.getPreferredSize().height;
		area.setPreferredSize(new Dimension(width, height));
		area.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
	}

	/** A short tag describing one reason behind a suggestion. */
	static JLabel chip(String text)
	{
		JLabel label = new JLabel(text)
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(MUTED);
		label.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(DIVIDER, 1, true),
			BorderFactory.createEmptyBorder(3, 7, 3, 7)));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setHorizontalAlignment(SwingConstants.LEFT);
		return label;
	}

	static Component gap(int height)
	{
		return Box.createRigidArea(new Dimension(0, height));
	}

	static void constrainWidth(JComponent component)
	{
		// Deprecated in favor of anonymous subclassing with getMaximumSize override, 
		// but retained for any existing components that still use it.
		// Note that this permanently fixes the height to its current preferred height!
		component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
	}

	static JPanel row(Component left, Component right)
	{
		JPanel row = new JPanel(new BorderLayout(SPACE_S, 0))
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		if (left != null)
		{
			row.add(left, BorderLayout.WEST);
		}
		if (right != null)
		{
			row.add(right, BorderLayout.EAST);
		}
		return row;
	}

	static Color profitColor(long amount)
	{
		if (amount > 0)
		{
			return PROFIT;
		}
		return amount < 0 ? LOSS : MUTED;
	}

	/**
	 * A stack that fills the viewport width instead of scrolling sideways.
	 * <p>
	 * A plain panel inside a scroll pane keeps its own preferred width and lets the viewport scroll
	 * horizontally to reach the rest, which is exactly how a narrow side panel ends up with its
	 * right edge cut off. Declaring that the view tracks the viewport width forces content to wrap
	 * into the space available and leaves only vertical scrolling.
	 */
	static class ScrollableColumn extends JPanel implements Scrollable
	{
		ScrollableColumn()
		{
			setBackground(BACKGROUND);
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return visibleRect.height;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}
}
