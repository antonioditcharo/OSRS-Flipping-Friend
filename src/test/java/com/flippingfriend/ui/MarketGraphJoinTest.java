package com.flippingfriend.ui;

import com.flippingfriend.data.*;
import com.flippingfriend.model.MarketPriceProjection;
import com.google.gson.Gson;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class MarketGraphJoinTest {
    private final long now=1800000000,snapshotAt=now+60;
    private List<Candle> bars() {
        var values=new ArrayList<Candle>();for(int i=0;i<40;i++)values.add(new Candle(now-(40-i)*300L,130+i,100+i,10,10));return values;
    }
    private LatestPrice quote(){return new LatestPrice(215,now-20,190,now-30);}
    private MarketPriceProjection.Projection projection(LatestPrice quote,long asOf) {
        var model=new MarketPriceProjection();return model.anchor(model.project(bars(),300,now,4),quote,asOf,asOf);
    }
    private MarketGraphCanvas chart(List<Candle> bars,MarketPriceProjection.Projection projection,LatestPrice quote) {
        var chart=new MarketGraphCanvas();chart.setSize(1000,600);
        chart.setData(bars,300,projection,0,0,now-3600,now+1800,true,quote,snapshotAt);return chart;
    }
    private int x(MarketGraphCanvas canvas,long at)throws Exception {
        var m=MarketGraphCanvas.class.getDeclaredMethod("x",double.class);m.setAccessible(true);return (int)m.invoke(canvas,(double)at);
    }
    private int y(MarketGraphCanvas canvas,int price)throws Exception {
        var b=MarketGraphCanvas.class.getDeclaredMethod("priceBounds");b.setAccessible(true);var limits=(double[])b.invoke(canvas);
        var m=MarketGraphCanvas.class.getDeclaredMethod("y",double.class,double.class,double.class,int.class);m.setAccessible(true);
        return (int)m.invoke(canvas,(double)price,limits[0],limits[1],(int)((600-14-40)*.76));
    }
    @Test public void quoteEndpointJoinsTheActualBandWithoutChangingItsModelValues()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var p=projection(quote(),snapshotAt);String original=new Gson().toJson(p);var canvas=chart(bars(),p,quote());
            var low=canvas.quoteJoin(true);assertNotNull(low);assertTrue(low.bridge);assertEquals(now,low.fromAt);assertEquals(139,low.fromPrice);
            assertEquals(190,low.anchor.price);assertEquals(now-30,low.anchor.tradeAt);assertEquals(snapshotAt,low.anchor.asOf);
            assertEquals(p.points.get(0).low.median,low.anchor.price,0);assertEquals(p.points.get(0).low.lower,p.points.get(0).low.upper,0);
            var high=canvas.quoteJoin(false);assertTrue(high.bridge);assertEquals(215,high.anchor.price);assertEquals(now-20,high.anchor.tradeAt);
            var image=new BufferedImage(1000,600,BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();canvas.printAll(graphics);graphics.dispose();
            try {
                assertEquals(MarketGraphCanvas.LOW.getRGB()&0xffffff,image.getRGB(x(canvas,snapshotAt),y(canvas,190))&0xffffff);
                Files.createDirectories(Path.of("build/ui"));ImageIO.write(image,"png",Path.of("build/ui/latest-quote-join.png").toFile());
                String tip=canvas.getToolTipText(new MouseEvent(canvas,MouseEvent.MOUSE_MOVED,0,0,x(canvas,snapshotAt),y(canvas,190),0,false));
                assertTrue(tip,tip.contains("Latest low trade"));assertTrue(tip,tip.contains("Trade time:"));assertTrue(tip,tip.contains("Snapshot as of:"));
            }catch(Exception e){throw new AssertionError(e);}
            assertEquals("Drawing the join cannot rescale a forecast to hide different inputs",original,new Gson().toJson(p));
        });
    }
    @Test public void longMissingHistoryAndOlderQuotesAreNotInventedAsConnectingTrades() {
        var p=projection(quote(),snapshotAt);var shortened=bars();shortened=shortened.subList(0,shortened.size()-4);
        var longGap=chart(shortened,p,quote());assertFalse(longGap.quoteJoin(true).bridge);assertNotNull(longGap.quoteJoin(true).anchor);
        var oldQuote=new LatestPrice(215,now-700,190,now-700);var stale=chart(bars(),projection(oldQuote,snapshotAt),oldQuote);
        assertFalse(stale.quoteJoin(true).bridge);assertFalse(stale.quoteJoin(false).bridge);
    }
    @Test public void absentSideHistoryKeepsTheOtherSidesIndependentEndpoint() {
        var data=bars();for(int i=0;i<data.size();i++) {
            var bar=data.get(i);data.set(i,new Candle(bar.getTimestamp(),bar.getAvgHighPrice(),null,bar.getHighPriceVolume(),0));
        }
        var canvas=chart(data,projection(quote(),snapshotAt),quote());assertFalse(canvas.quoteJoin(true).bridge);
        assertTrue(canvas.quoteJoin(false).bridge);assertEquals(190,canvas.quoteJoin(true).anchor.price);
    }
    @Test public void mismatchedFutureOrMissingQuoteCannotCreateAPhantomAnchor() {
        var p=projection(quote(),snapshotAt);
        for(var q:Arrays.asList((LatestPrice)null,new LatestPrice(216,now-20,191,now-30),new LatestPrice(215,snapshotAt+1,190,snapshotAt+1))) {
            var canvas=chart(bars(),p,q);assertNull(canvas.quoteJoin(true));assertNull(canvas.quoteJoin(false));
        }
    }
    @Test public void changingQuotesPreservesManualZoom() {
        var canvas=chart(bars(),projection(quote(),snapshotAt),quote());canvas.zoom(.6);double[] before=canvas.window();
        canvas.setData(bars(),300,projection(quote(),snapshotAt),0,0,now-3600,now+1800,false,quote(),snapshotAt);
        assertArrayEquals(before,canvas.window(),.01);
    }
}
