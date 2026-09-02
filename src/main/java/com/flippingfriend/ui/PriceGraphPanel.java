package com.flippingfriend.ui;

import com.flippingfriend.data.Candle;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JPanel;
import net.runelite.client.ui.FontManager;

/**
 * A small price chart for one item, painted directly rather than pulled in from a charting library.
 * <p>
 * The point of the chart is not decoration: it is to answer, at a glance, "is what I am holding
 * going up or down, and where am I relative to what I paid?". So the price line is drawn together
 * with the purchase price, the target and the stop, which turns an abstract number in a list into
 * something a player can act on.
 */
class PriceGraphPanel extends JPanel
{
	private static final int HEIGHT = 96;
	private static final int PADDING = 4;
	/** Share of the height given over to the volume bars at the bottom. */
	private static final double VOLUME_SHARE = 0.22;

	private static final Color GRID = new Color(58, 62, 70);
	private static final Color LINE = new Color(120, 190, 255);
	private static final Color FILL = new Color(120, 190, 255, 40);
	private static final Color VOLUME = new Color(90, 96, 108);
	private static final Color ENTRY = new Color(220, 220, 220);
	private static final Color TARGET = new Color(88, 214, 141);
	private static final Color STOP = new Color(233, 108, 100);

	private List<Candle> candles = Collections.emptyList();
	private int entryPrice;
	private int targetPrice;
	private int stopPrice;

	PriceGraphPanel()
	{
		setBackground(UiUtils.CARD);
		setPreferredSize(new Dimension(0, HEIGHT));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
	}

	void setData(List<Candle> candles, int entryPrice, int targetPrice, int stopPrice)
	{
		this.candles = candles == null ? Collections.emptyList() : new ArrayList<>(candles);
		this.entryPrice = entryPrice;
		this.targetPrice = targetPrice;
		this.stopPrice = stopPrice;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);

		Graphics2D graphics = (Graphics2D) g.create();
		try
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			int width = getWidth() - PADDING * 2;
			int height = getHeight() - PADDING * 2;
			if (width <= 8 || height <= 8)
			{
				return;
			}

			List<Point> points = collectPoints();
			if (points.size() < 2)
			{
				graphics.setFont(FontManager.getRunescapeSmallFont());
				graphics.setColor(UiUtils.MUTED);
				graphics.drawString("Loading price history…", PADDING + 4, getHeight() / 2);
				return;
			}

			int volumeHeight = (int) (height * VOLUME_SHARE);
			int priceHeight = height - volumeHeight - 4;

			Bounds bounds = boundsOf(points);
			drawGrid(graphics, width, priceHeight);
			drawVolume(graphics, points, width, priceHeight, volumeHeight);
			drawPriceLine(graphics, points, bounds, width, priceHeight);
			drawMarkers(graphics, bounds, width, priceHeight);
		}
		finally
		{
			graphics.dispose();
		}
	}

	private List<Point> collectPoints()
	{
		List<Point> points = new ArrayList<>(candles.size());
		int maxVolume = 1;
		for (Candle candle : candles)
		{
			double mid = candle.midPrice();
			if (mid <= 0)
			{
				continue;
			}
			maxVolume = Math.max(maxVolume, candle.getTotalVolume());
			points.add(new Point(mid, candle.getTotalVolume()));
		}
		for (Point point : points)
		{
			point.volumeShare = (double) point.volume / maxVolume;
		}
		return points;
	}

	private Bounds boundsOf(List<Point> points)
	{
		double min = Double.MAX_VALUE;
		double max = Double.MIN_VALUE;
		for (Point point : points)
		{
			min = Math.min(min, point.price);
			max = Math.max(max, point.price);
		}

		// Marker lines are only useful if they are actually on the chart.
		for (int marker : new int[]{entryPrice, targetPrice, stopPrice})
		{
			if (marker > 0)
			{
				min = Math.min(min, marker);
				max = Math.max(max, marker);
			}
		}

		if (max - min < 1e-6)
		{
			max = min + 1;
		}
		double margin = (max - min) * 0.08;
		return new Bounds(min - margin, max + margin);
	}

	private void drawGrid(Graphics2D graphics, int width, int priceHeight)
	{
		graphics.setColor(GRID);
		for (int i = 1; i < 3; i++)
		{
			int y = PADDING + priceHeight * i / 3;
			graphics.drawLine(PADDING, y, PADDING + width, y);
		}
	}

	private void drawVolume(Graphics2D graphics, List<Point> points, int width, int priceHeight,
		int volumeHeight)
	{
		graphics.setColor(VOLUME);
		int baseY = PADDING + priceHeight + 4 + volumeHeight;
		double step = (double) width / points.size();

		for (int i = 0; i < points.size(); i++)
		{
			int barHeight = (int) Math.round(points.get(i).volumeShare * volumeHeight);
			if (barHeight <= 0)
			{
				continue;
			}
			int x = PADDING + (int) (i * step);
			int barWidth = Math.max(1, (int) step);
			graphics.fillRect(x, baseY - barHeight, barWidth, barHeight);
		}
	}

	private void drawPriceLine(Graphics2D graphics, List<Point> points, Bounds bounds, int width,
		int priceHeight)
	{
		int count = points.size();
		int[] xs = new int[count];
		int[] ys = new int[count];
		double step = (double) width / Math.max(1, count - 1);

		for (int i = 0; i < count; i++)
		{
			xs[i] = PADDING + (int) Math.round(i * step);
			ys[i] = yFor(points.get(i).price, bounds, priceHeight);
		}

		int[] fillXs = new int[count + 2];
		int[] fillYs = new int[count + 2];
		System.arraycopy(xs, 0, fillXs, 0, count);
		System.arraycopy(ys, 0, fillYs, 0, count);
		fillXs[count] = xs[count - 1];
		fillYs[count] = PADDING + priceHeight;
		fillXs[count + 1] = xs[0];
		fillYs[count + 1] = PADDING + priceHeight;

		graphics.setColor(FILL);
		graphics.fillPolygon(fillXs, fillYs, count + 2);

		Stroke original = graphics.getStroke();
		graphics.setStroke(new BasicStroke(1.6f));
		graphics.setColor(LINE);
		graphics.drawPolyline(xs, ys, count);
		graphics.setStroke(original);
	}

	private void drawMarkers(Graphics2D graphics, Bounds bounds, int width, int priceHeight)
	{
		Stroke original = graphics.getStroke();
		graphics.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f,
			new float[]{3f, 3f}, 0f));

		drawMarker(graphics, entryPrice, ENTRY, "paid", bounds, width, priceHeight);
		drawMarker(graphics, targetPrice, TARGET, "target", bounds, width, priceHeight);
		drawMarker(graphics, stopPrice, STOP, "stop", bounds, width, priceHeight);

		graphics.setStroke(original);
	}

	private void drawMarker(Graphics2D graphics, int price, Color color, String label, Bounds bounds,
		int width, int priceHeight)
	{
		if (price <= 0)
		{
			return;
		}
		int y = yFor(price, bounds, priceHeight);
		graphics.setColor(color);
		graphics.drawLine(PADDING, y, PADDING + width, y);

		graphics.setFont(FontManager.getRunescapeSmallFont());
		graphics.drawString(label, PADDING + 2, Math.max(PADDING + 8, y - 2));
	}

	private int yFor(double price, Bounds bounds, int priceHeight)
	{
		double ratio = (price - bounds.min) / (bounds.max - bounds.min);
		int y = PADDING + (int) Math.round((1 - ratio) * priceHeight);
		return Math.max(PADDING, Math.min(PADDING + priceHeight, y));
	}

	private static final class Point
	{
		private final double price;
		private final int volume;
		private double volumeShare;

		Point(double price, int volume)
		{
			this.price = price;
			this.volume = volume;
		}
	}

	private static final class Bounds
	{
		private final double min;
		private final double max;

		Bounds(double min, double max)
		{
			this.min = min;
			this.max = max;
		}
	}
}
