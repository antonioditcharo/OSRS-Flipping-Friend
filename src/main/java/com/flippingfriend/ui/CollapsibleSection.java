package com.flippingfriend.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.FontManager;

/**
 * A collapsible accordion-style panel section.
 * <p>
 * Contains a clickable header that toggles the visibility of its content, 
 * helping to keep the side panel clean and prevent vertical clipping.
 */
public class CollapsibleSection extends JPanel
{
	private final JPanel content;
	private final JLabel titleLabel;
	private final JLabel arrowLabel;
	private boolean collapsed;

	public CollapsibleSection(String title, Component contentComponent, boolean startCollapsed)
	{
		setLayout(new BorderLayout());
		setBackground(UiUtils.BACKGROUND);
		setAlignmentX(Component.LEFT_ALIGNMENT);
		
		// Fix maximum size to allow vertical stretching when expanded, but prevent width expansion.
		// We'll override getMaximumSize dynamically so it can recalculate if content grows.

		JPanel header = new JPanel(new BorderLayout(UiUtils.SPACE_S, 0));
		header.setBackground(UiUtils.BACKGROUND);
		header.setBorder(BorderFactory.createEmptyBorder(UiUtils.SPACE_L, 0, UiUtils.SPACE_S, 0));

		titleLabel = new JLabel(title.toUpperCase());
		titleLabel.setFont(FontManager.getRunescapeSmallFont());
		titleLabel.setForeground(UiUtils.MUTED);

		arrowLabel = new JLabel();
		arrowLabel.setFont(FontManager.getRunescapeSmallFont());
		arrowLabel.setForeground(UiUtils.MUTED);

		header.add(titleLabel, BorderLayout.WEST);
		header.add(arrowLabel, BorderLayout.EAST);

		JPanel rule = new JPanel();
		rule.setBackground(UiUtils.DIVIDER);
		rule.setPreferredSize(new Dimension(0, 1));

		JPanel ruleHolder = new JPanel(new BorderLayout());
		ruleHolder.setBackground(UiUtils.BACKGROUND);
		ruleHolder.setBorder(BorderFactory.createEmptyBorder(7, 0, 0, 0));
		ruleHolder.add(rule, BorderLayout.CENTER);
		header.add(ruleHolder, BorderLayout.CENTER);

		content = new JPanel(new BorderLayout());
		content.setBackground(UiUtils.BACKGROUND);
		content.add(contentComponent, BorderLayout.CENTER);

		add(header, BorderLayout.NORTH);
		add(content, BorderLayout.CENTER);

		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				toggle();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				titleLabel.setForeground(UiUtils.TEXT);
				arrowLabel.setForeground(UiUtils.TEXT);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				titleLabel.setForeground(UiUtils.MUTED);
				arrowLabel.setForeground(UiUtils.MUTED);
			}
		});

		this.collapsed = startCollapsed;
		updateState();
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	private void toggle()
	{
		collapsed = !collapsed;
		updateState();
	}

	private void updateState()
	{
		content.setVisible(!collapsed);
		arrowLabel.setText(collapsed ? "▼" : "▲");
		revalidate();
		repaint();
	}
}
