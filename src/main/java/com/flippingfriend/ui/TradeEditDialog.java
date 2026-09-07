package com.flippingfriend.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

public class TradeEditDialog extends JDialog {

	public static class EditResult {
		public final int quantity;
		public final int avgBuyPrice;
		public final int avgSellPrice;
		public final long exactTax;
		public final long exactProfit;

		public EditResult(int quantity, int avgBuyPrice, int avgSellPrice, long exactTax, long exactProfit) {
			this.quantity = quantity;
			this.avgBuyPrice = avgBuyPrice;
			this.avgSellPrice = avgSellPrice;
			this.exactTax = exactTax;
			this.exactProfit = exactProfit;
		}
	}

	private final boolean isOngoing;
	private final int itemId;
	private final com.flippingfriend.model.TaxCalculator taxCalculator;
	private final JPanel buysPanel = new JPanel();
	private final JPanel sellsPanel = new JPanel();
	private final List<TradeRow> buyRows = new ArrayList<>();
	private final List<TradeRow> sellRows = new ArrayList<>();
	
	private EditResult result;

	public TradeEditDialog(java.awt.Component parent, String title, int itemId, int initialQty, int initialBuy, int initialSell, boolean isOngoing, com.flippingfriend.model.TaxCalculator taxCalculator) {
		setTitle(title);
		setModal(true);
		this.itemId = itemId;
		this.isOngoing = isOngoing;
		this.taxCalculator = taxCalculator;
		
		JPanel main = new JPanel();
		main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
		
		buysPanel.setLayout(new BoxLayout(buysPanel, BoxLayout.Y_AXIS));
		sellsPanel.setLayout(new BoxLayout(sellsPanel, BoxLayout.Y_AXIS));
		
		addBuyRow(initialQty, initialBuy);
		if (!isOngoing) {
			addSellRow(initialQty, initialSell);
		}
		
		JButton addBuyBtn = new JButton("+ Add Buy");
		addBuyBtn.addActionListener(e -> {
			addBuyRow(0, 0);
			pack();
		});
		
		JButton addSellBtn = new JButton("+ Add Sell");
		addSellBtn.addActionListener(e -> {
			addSellRow(0, 0);
			pack();
		});
		
		JPanel buysContainer = new JPanel(new BorderLayout());
		buysContainer.setBorder(javax.swing.BorderFactory.createTitledBorder("Buys"));
		buysContainer.add(buysPanel, BorderLayout.CENTER);
		JPanel buyBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		buyBtnPanel.add(addBuyBtn);
		buysContainer.add(buyBtnPanel, BorderLayout.SOUTH);
		main.add(buysContainer);
		
		if (!isOngoing) {
			JPanel sellsContainer = new JPanel(new BorderLayout());
			sellsContainer.setBorder(javax.swing.BorderFactory.createTitledBorder("Sells"));
			sellsContainer.add(sellsPanel, BorderLayout.CENTER);
			JPanel sellBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
			sellBtnPanel.add(addSellBtn);
			sellsContainer.add(sellBtnPanel, BorderLayout.SOUTH);
			main.add(sellsContainer);
		}
		
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton okBtn = new JButton("Save");
		okBtn.addActionListener(e -> save());
		JButton cancelBtn = new JButton("Cancel");
		cancelBtn.addActionListener(e -> dispose());
		buttons.add(okBtn);
		buttons.add(cancelBtn);
		main.add(buttons);
		
		JScrollPane scroll = new JScrollPane(main);
		scroll.setPreferredSize(new Dimension(350, 400));
		add(scroll);
		
		pack();
		setLocationRelativeTo(parent);
	}
	
	private void addBuyRow(int qty, int price) {
		TradeRow row = new TradeRow(qty, price, () -> {
			buyRows.removeIf(r -> r.panel.getParent() == null);
			pack();
		});
		buyRows.add(row);
		buysPanel.add(row.panel);
		buysPanel.revalidate();
		buysPanel.repaint();
	}

	private void addSellRow(int qty, int price) {
		TradeRow row = new TradeRow(qty, price, () -> {
			sellRows.removeIf(r -> r.panel.getParent() == null);
			pack();
		});
		sellRows.add(row);
		sellsPanel.add(row.panel);
		sellsPanel.revalidate();
		sellsPanel.repaint();
	}
	
	private void save() {
		try {
			long totalBuyQty = 0;
			long totalBuyCost = 0;
			for (TradeRow row : buyRows) {
				int q = row.getQty();
				int p = row.getPrice();
				if (q > 0) {
					totalBuyQty += q;
					totalBuyCost += (long) q * p;
				}
			}
			
			long totalSellQty = 0;
			long exactTax = 0;
			long totalSellCost = 0;
			if (!isOngoing) {
				for (TradeRow row : sellRows) {
					int q = row.getQty();
					int p = row.getPrice();
					if (q > 0) {
						totalSellQty += q;
						totalSellCost += (long) q * p;
						exactTax += taxCalculator.taxFor(itemId, p, q);
					}
				}
				
				if (totalBuyQty != totalSellQty) {
					JOptionPane.showMessageDialog(this, "Total Buy Quantity (" + totalBuyQty + ") must equal Total Sell Quantity (" + totalSellQty + ").", "Error", JOptionPane.ERROR_MESSAGE);
					return;
				}
			}
			
			if (totalBuyQty == 0) {
				JOptionPane.showMessageDialog(this, "Quantity cannot be zero.", "Error", JOptionPane.ERROR_MESSAGE);
				return;
			}
			
			int avgBuy = (int) (totalBuyCost / totalBuyQty);
			int avgSell = isOngoing ? 0 : (int) (totalSellCost / totalSellQty);
			long exactProfit = totalSellCost - totalBuyCost - exactTax;
			
			this.result = new EditResult((int) totalBuyQty, avgBuy, avgSell, exactTax, exactProfit);
			dispose();
		} catch (NumberFormatException e) {
			JOptionPane.showMessageDialog(this, "Please enter valid numbers.", "Error", JOptionPane.ERROR_MESSAGE);
		}
	}
	
	public EditResult getResult() {
		return result;
	}

	private static class TradeRow {
		final JPanel panel;
		final JTextField qtyField;
		final JTextField priceField;
		
		TradeRow(int qty, int price, Runnable onRemove) {
			panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
			panel.add(new JLabel("Qty:"));
			qtyField = new JTextField(qty == 0 ? "" : String.valueOf(qty), 5);
			panel.add(qtyField);
			
			panel.add(new JLabel("Price ea:"));
			priceField = new JTextField(price == 0 ? "" : String.valueOf(price), 8);
			panel.add(priceField);
			
			JButton removeBtn = new JButton("X");
			removeBtn.addActionListener(e -> {
				java.awt.Container parent = panel.getParent();
				if (parent != null) {
					parent.remove(panel);
					parent.revalidate();
					parent.repaint();
					onRemove.run();
				}
			});
			panel.add(removeBtn);
		}
		
		int getQty() {
			String text = qtyField.getText().trim();
			if (text.isEmpty()) return 0;
			return Integer.parseInt(text.replace(",", ""));
		}
		
		int getPrice() {
			String text = priceField.getText().trim();
			if (text.isEmpty()) return 0;
			return Integer.parseInt(text.replace(",", ""));
		}
	}
}
