package com.flippingfriend.ui;

import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import com.flippingfriend.model.MarketPriceProjection;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import javax.swing.*;
import net.runelite.client.ui.FontManager;

/** Timestamp-scaled market observations and a separate, explicitly experimental future layer. */
final class MarketGraphCanvas extends JPanel {
    static final Color LOW=new Color(0,169,238),HIGH=new Color(255,111,0),GRID=new Color(67,67,67);
    private static final int LEFT=66,RIGHT=14,TOP=14,BOTTOM=40;
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd MMM HH:mm",Locale.ROOT).withZone(ZoneOffset.UTC);
    private List<Candle> bars=List.of();
    private MarketPriceProjection.Projection projection;
    private QuoteAnchor lowAnchor,highAnchor;
    /** Display provenance only; this endpoint is a known quote as of a snapshot, not a new fill. */
    static final class QuoteAnchor {
        final int formatVersion=1,price;final long tradeAt,asOf;
        QuoteAnchor(int price,long tradeAt,long asOf){this.price=price;this.tradeAt=tradeAt;this.asOf=asOf;}
    }
    static final class QuoteJoin {
        final long fromAt;final int fromPrice;final QuoteAnchor anchor;final boolean bridge;
        QuoteJoin(long at,int price,QuoteAnchor anchor,boolean bridge){fromAt=at;fromPrice=price;this.anchor=anchor;this.bridge=bridge;}
    }
    private int bucket=300,buy,sell;
    private double start,end,minimum,maximum,homeStart,homeEnd;
    private int cursor=-1,dragX;
    private double dragStart,dragEnd;
    private boolean projections=true;
    MarketGraphCanvas() {
        setBackground(new Color(48,48,48));setPreferredSize(new Dimension(950,600));setMinimumSize(new Dimension(280,230));
        setToolTipText("Completed public market price buckets; times in UTC");
        setFocusable(true);getAccessibleContext().setAccessibleName("Public market price chart");
        bind("LEFT",()->setWindow(start-(end-start)*.1,end-(end-start)*.1));
        bind("RIGHT",()->setWindow(start+(end-start)*.1,end+(end-start)*.1));
        bind("PLUS",()->zoom(1/1.5));bind("EQUALS",()->zoom(1/1.5));bind("MINUS",()->zoom(1.5));
        bind("HOME",()->setWindow(homeStart,homeEnd));
        var mouse=new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e){cursor=e.getX();repaint();}
            @Override public void mouseExited(MouseEvent e){cursor=-1;repaint();}
            @Override public void mousePressed(MouseEvent e){requestFocusInWindow();dragX=e.getX();dragStart=start;dragEnd=end;}
            @Override public void mouseDragged(MouseEvent e){double shift=(dragX-e.getX())*(dragEnd-dragStart)/Math.max(1,plotWidth());setWindow(dragStart+shift,dragEnd+shift);}
            @Override public void mouseWheelMoved(MouseWheelEvent e){zoom(Math.pow(1.2,e.getPreciseWheelRotation()),fraction(e.getX()));}
        };
        addMouseListener(mouse);addMouseMotionListener(mouse);addMouseWheelListener(mouse);
    }
    private void bind(String key,Runnable action){getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key),key);getActionMap().put(key,new AbstractAction(){@Override public void actionPerformed(ActionEvent e){action.run();}});}
    void setData(List<Candle> data,int bucket,MarketPriceProjection.Projection p,int buy,int sell,
        long defaultStart,long defaultEnd,boolean reset) {
        setData(data,bucket,p,buy,sell,defaultStart,defaultEnd,reset,null,0);
    }
    void setData(List<Candle> data,int bucket,MarketPriceProjection.Projection p,int buy,int sell,
        long defaultStart,long defaultEnd,boolean reset,LatestPrice quote,long snapshotAt) {
        boolean followingHome=end<=start || Math.abs(start-homeStart)<.5 && Math.abs(end-homeEnd)<.5;
        this.bars=List.copyOf(data);this.bucket=bucket;projection=p;this.buy=buy;this.sell=sell;
        lowAnchor=anchor(p,quote,snapshotAt,true);highAnchor=anchor(p,quote,snapshotAt,false);
        homeStart=defaultStart;homeEnd=defaultEnd;
        minimum=bars.isEmpty()?defaultStart:Math.min(defaultStart,bars.get(0).getTimestamp()+bucket);
        maximum=Math.max(defaultEnd,p!=null && p.isAvailable()?p.points.get(p.points.size()-1).at:defaultEnd);
        if(reset || followingHome)setWindow(defaultStart,defaultEnd);else setWindow(start,end);
    }
    private static QuoteAnchor anchor(MarketPriceProjection.Projection p,LatestPrice quote,long at,boolean low) {
        if(p==null || !p.isAvailable() || quote==null || !quote.isComplete() || p.originAt!=at
            || p.observedAt<at || at<=0 || p.observedAt-at>150 || p.points.get(0).at!=at)return null;
        Long tradeAt=low?quote.getLowTime():quote.getHighTime();int price=low?quote.getLow():quote.getHigh();
        if(tradeAt==null || tradeAt<=0 || tradeAt>at || p.observedAt-tradeAt>7200 || price<=0)return null;
        var side=low?p.points.get(0).low:p.points.get(0).high;
        if(side.lower!=price || side.median!=price || side.upper!=price)return null;
        return new QuoteAnchor(price,tradeAt,at);
    }
    QuoteJoin quoteJoin(boolean low) {
        var anchor=low?lowAnchor:highAnchor;if(anchor==null)return null;
        Candle last=null;int price=0;
        for(var bar:bars) {
            Integer value=low?bar.getAvgLowPrice():bar.getAvgHighPrice();long close=bar.getTimestamp()+bucket;
            if(value!=null && value>0 && close<=anchor.asOf && (last==null || bar.getTimestamp()>last.getTimestamp())){last=bar;price=value;}
        }
        if(last==null)return new QuoteJoin(anchor.asOf,anchor.price,anchor,false);
        long close=last.getTimestamp()+bucket;
        // A short, labelled transition between a bucket average and a known quote. Never move
        // old quotes forward as new trades or fill substantial missing-history intervals.
        boolean bridge=bucket>0 && anchor.asOf-close<=2L*bucket && anchor.tradeAt>=last.getTimestamp();
        return new QuoteJoin(close,price,anchor,bridge);
    }
    void showProjections(boolean enabled){projections=enabled;repaint();}
    void zoom(double scale){zoom(scale,.5);}
    private void zoom(double scale,double anchor) {
        if(end<=start || !Double.isFinite(scale) || scale<=0)return;
        double width=Math.max(bucket*6L,Math.min(maximum-minimum,(end-start)*scale));
        double center=start+anchor*(end-start);setWindow(center-anchor*width,center+(1-anchor)*width);
    }
    void reset(long from,long to){setWindow(from,to);}
    double[] window(){return new double[]{start,end};}
    private void setWindow(double from,double to) {
        double span=Math.max(bucket*6L,Math.min(maximum-minimum,to-from));
        start=Math.max(minimum,Math.min(from,maximum-span));end=start+span;repaint();
    }
    private int plotWidth(){return Math.max(1,getWidth()-LEFT-RIGHT);}
    private double fraction(int x){return Math.max(0,Math.min(1,(x-LEFT)/(double)plotWidth()));}
    private double time(int x){return start+fraction(x)*(end-start);}
    private int x(double at){return LEFT+(int)Math.round((at-start)*plotWidth()/Math.max(1,end-start));}
    @Override public String getToolTipText(MouseEvent e) {
        if(e.getX()<LEFT || e.getX()>getWidth()-RIGHT)return null;
        if(projections) {
            var low=quoteJoin(true);var high=quoteJoin(false);var bounds=priceBounds();
            int height=(int)((getHeight()-TOP-BOTTOM)*.76);QuoteJoin nearest=null;double distance=Double.POSITIVE_INFINITY;
            for(var join:new QuoteJoin[]{low,high})if(join!=null) {
                double next=Point2D.distance(e.getX(),e.getY(),x(join.anchor.asOf),y(join.anchor.price,bounds[0],bounds[1],height));
                if(next<=10 && next<distance){nearest=join;distance=next;}
            }
            if(nearest!=null)return String.format(Locale.ROOT,"<html>Latest %s trade: %,d gp<br>Trade time: %s UTC<br>Snapshot as of: %s UTC<br>Forecast starts from this known quote; connector is not observed intervening trades.</html>",
                nearest==low?"low":"high",nearest.anchor.price,DATE.format(Instant.ofEpochSecond(nearest.anchor.tradeAt)),DATE.format(Instant.ofEpochSecond(nearest.anchor.asOf)));
        }
        long at=(long)time(e.getX());
        if(projections && projection!=null && projection.isAvailable() && at>=projection.originAt) {
            if(at>projection.points.get(projection.points.size()-1).at)return "No supported projection at this horizon";
            var point=projection.points.stream().min(Comparator.comparingDouble(p->Math.abs(p.at-at))).orElse(null);
            return point==null?null:String.format(Locale.ROOT,"<html>%s UTC · public-market forecast<br>Estimated low average %s gp<br>Estimated high average %s gp<br>%,d completed historical comparisons; estimates are not guaranteed.</html>",
                DATE.format(Instant.ofEpochSecond(point.at)),estimate(point.low.lower,point.low.upper),
                estimate(point.high.lower,point.high.upper),point.analogues);
        }
        Candle bar=bars.stream().min(Comparator.comparingDouble(c->Math.abs(c.getTimestamp()+bucket-at))).orElse(null);
        if(bar==null || Math.abs(bar.getTimestamp()+bucket-at)>bucket)return "No observed bucket at this time";
        return String.format(Locale.ROOT,"<html>%s UTC · completed bucket averages<br>Low: %s gp · volume %,d<br>High: %s gp · volume %,d</html>",
            DATE.format(Instant.ofEpochSecond(bar.getTimestamp()+bucket)),Objects.toString(bar.getAvgLowPrice(),"Unavailable"),bar.getLowPriceVolume(),
            Objects.toString(bar.getAvgHighPrice(),"Unavailable"),bar.getHighPriceVolume());
    }
    private static String estimate(double lower,double upper) {
        return java.math.BigDecimal.valueOf(lower).add(java.math.BigDecimal.valueOf(upper))
            .divide(java.math.BigDecimal.valueOf(2),0,java.math.RoundingMode.HALF_UP).toPlainString();
    }
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);Graphics2D gg=(Graphics2D)g.create();
        try {
            gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            gg.setFont(FontManager.getRunescapeSmallFont());int available=getHeight()-TOP-BOTTOM;
            if(available<80 || plotWidth()<80)return;
            int priceHeight=(int)(available*.76),volumeTop=TOP+priceHeight+18,volumeHeight=Math.max(20,available-priceHeight-18);
            double[] bounds=priceBounds();double lo=bounds[0],hi=bounds[1];
            gg.setColor(GRID);
            int priceTicks=Math.max(2,Math.min(8,(int)Math.ceil(hi-lo)));
            for(int i=0;i<=priceTicks;i++) {
                int y=TOP+i*priceHeight/priceTicks;gg.drawLine(LEFT,y,getWidth()-RIGHT,y);gg.setColor(new Color(200,200,200));
                labelRight(gg,number(hi-(hi-lo)*i/priceTicks),LEFT-10,y+4);gg.setColor(GRID);
            }
            int timeTicks=Math.max(2,Math.min(8,plotWidth()/150));
            for(int i=0;i<=timeTicks;i++) {
                int xx=LEFT+i*plotWidth()/timeTicks;gg.drawLine(xx,TOP,xx,volumeTop+volumeHeight);
                gg.setColor(new Color(200,200,200));String date=DATE.format(Instant.ofEpochSecond((long)(start+(end-start)*i/timeTicks)));
                int width=gg.getFontMetrics().stringWidth(date);gg.drawString(date,Math.max(LEFT,Math.min(getWidth()-RIGHT-width,xx-width/2)),getHeight()-16);gg.setColor(GRID);
            }
            Shape original=gg.getClip();gg.clipRect(LEFT,TOP,plotWidth()+1,priceHeight+1);
            if(projections && projection!=null && projection.isAvailable()) {
                bands(gg,true,LOW,lo,hi,priceHeight);bands(gg,false,HIGH,lo,hi,priceHeight);
            }
            observed(gg,true,LOW,lo,hi,priceHeight);observed(gg,false,HIGH,lo,hi,priceHeight);
            if(projections){quoteEndpoint(gg,true,LOW,lo,hi,priceHeight);quoteEndpoint(gg,false,HIGH,lo,hi,priceHeight);}
            marker(gg,buy,"Suggested buy price",lo,hi,priceHeight);
            marker(gg,sell,"Suggested sell price",lo,hi,priceHeight);
            gg.setClip(original);volume(gg,volumeTop,volumeHeight);
            if(cursor>=LEFT && cursor<=getWidth()-RIGHT) {
                gg.setColor(new Color(190,190,190,110));gg.drawLine(cursor,TOP,cursor,volumeTop+volumeHeight);
            }
            if(bars.isEmpty()) {gg.setColor(UiUtils.TEXT);gg.drawString("Waiting for completed market history…",LEFT+20,TOP+35);}
        }finally{gg.dispose();}
    }
    private double[] priceBounds() {
        double lo=Double.POSITIVE_INFINITY,hi=0;
        for(Candle c:bars)if(c.getTimestamp()+bucket>=start && c.getTimestamp()+bucket<=end)
            for(Integer p:new Integer[]{c.getAvgLowPrice(),c.getAvgHighPrice()})if(p!=null && p>0){lo=Math.min(lo,p);hi=Math.max(hi,p);}
        if(projections && projection!=null)for(var p:projection.points)if(p.at>=start && p.at<=end) {
            lo=Math.min(lo,Math.min(p.low.lower,p.high.lower));hi=Math.max(hi,Math.max(p.low.upper,p.high.upper));
        }
        for(int marker:new int[]{buy,sell})if(marker>0){lo=Math.min(lo,marker);hi=Math.max(hi,marker);}
        if(!Double.isFinite(lo))return new double[]{0,1};
        double gap=Math.max(1,(hi-lo)*.08);return new double[]{Math.max(0,lo-gap),hi+gap};
    }
    private int y(double price,double lo,double hi,int height){return TOP+(int)Math.round((hi-price)*height/Math.max(1,hi-lo));}
    private void observed(Graphics2D g,boolean low,Color color,double lo,double hi,int height) {
        g.setColor(color);g.setStroke(new BasicStroke(1.2f));Candle previous=null;Integer previousPrice=null;
        for(Candle c:bars) {
            Integer price=low?c.getAvgLowPrice():c.getAvgHighPrice();
            if(price==null || price<=0){previous=null;previousPrice=null;continue;}
            if(previous!=null && c.getTimestamp()-previous.getTimestamp()==bucket)
                g.drawLine(x(previous.getTimestamp()+bucket),y(previousPrice,lo,hi,height),x(c.getTimestamp()+bucket),y(price,lo,hi,height));
            int xx=x(c.getTimestamp()+bucket),yy=y(price,lo,hi,height);g.fillOval(xx-2,yy-2,3,3);previous=c;previousPrice=price;
        }
    }
    private void bands(Graphics2D g,boolean low,Color color,double lo,double hi,int height) {
        var points=projection.points;Path2D area=new Path2D.Double(),line=new Path2D.Double();
        for(int i=0;i<points.size();i++) {var p=points.get(i);var side=low?p.low:p.high;
            if(i==0){area.moveTo(x(p.at),y(side.upper,lo,hi,height));line.moveTo(x(p.at),y(side.median,lo,hi,height));}
            else {area.lineTo(x(p.at),y(side.upper,lo,hi,height));line.lineTo(x(p.at),y(side.median,lo,hi,height));}}
        for(int i=points.size()-1;i>=0;i--){var p=points.get(i);area.lineTo(x(p.at),y((low?p.low:p.high).lower,lo,hi,height));}
        area.closePath();g.setColor(new Color(color.getRed(),color.getGreen(),color.getBlue(),55));g.fill(area);
        g.setColor(color);g.setStroke(new BasicStroke(1.4f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,1,new float[]{5,5},0));g.draw(line);
        g.setColor(new Color(170,170,170));g.setStroke(new BasicStroke(1f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,1,new float[]{2,4},0));
        g.drawLine(x(projection.originAt),TOP,x(projection.originAt),TOP+height);
    }
    private void quoteEndpoint(Graphics2D g,boolean low,Color color,double lo,double hi,int height) {
        var join=quoteJoin(low);if(join==null)return;
        int xx=x(join.anchor.asOf),yy=y(join.anchor.price,lo,hi,height);
        g.setColor(new Color(color.getRed(),color.getGreen(),color.getBlue(),170));
        g.setStroke(new BasicStroke(1.2f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND,1,new float[]{2,3},0));
        if(join.bridge)g.drawLine(x(join.fromAt),y(join.fromPrice,lo,hi,height),xx,yy);
        g.setColor(color);g.fill(new Ellipse2D.Double(xx-3,yy-3,6,6));
    }
    private void marker(Graphics2D g,int price,String label,double lo,double hi,int height) {
        if(price<=0)return;int yy=y(price,lo,hi,height);g.setColor(new Color(225,225,225));
        g.setStroke(new BasicStroke(1,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,1,new float[]{5,4},0));g.drawLine(LEFT,yy,getWidth()-RIGHT,yy);
        labelRight(g,label,getWidth()-RIGHT-8,Math.max(TOP+13,yy-6));
    }
    private void volume(Graphics2D g,int top,int height) {
        long max=1;for(Candle c:bars)if(c.getTimestamp()+bucket>=start && c.getTimestamp()+bucket<=end)
            max=Math.max(max,(long)Math.max(0,c.getHighPriceVolume())+Math.max(0,c.getLowPriceVolume()));
        int base=top+height;g.setColor(GRID);g.drawLine(LEFT,top,getWidth()-RIGHT,top);g.drawLine(LEFT,base,getWidth()-RIGHT,base);
        int volumeTicks=(int)Math.min(3,max);
        for(int i=0;i<=volumeTicks;i++){int yy=base-i*height/volumeTicks;g.setColor(new Color(190,190,190));labelRight(g,number(max*i/(double)volumeTicks),LEFT-10,yy+4);}
        Shape clip=g.getClip();g.clipRect(LEFT,top,plotWidth()+1,height+1);
        for(Candle c:bars) {int xx=x(c.getTimestamp()),right=x(c.getTimestamp()+bucket);
            int low=(int)Math.round(height*Math.max(0,c.getLowPriceVolume())/(double)max),high=(int)Math.round(height*Math.max(0,c.getHighPriceVolume())/(double)max);
            g.setColor(new Color(LOW.getRed(),LOW.getGreen(),LOW.getBlue(),150));g.fillRect(xx,base-low,Math.max(1,right-xx),low);
            g.setColor(new Color(HIGH.getRed(),HIGH.getGreen(),HIGH.getBlue(),150));g.fillRect(xx,base-low-high,Math.max(1,right-xx),high);}
        g.setClip(clip);
    }
    private static void labelRight(Graphics2D g,String text,int right,int y){g.drawString(text,right-g.getFontMetrics().stringWidth(text),y);}
    static String number(double value) {
        double absolute=Math.abs(value);if(absolute>=1000000)return String.format(Locale.ROOT,"%.2fM",value/1000000);
        if(absolute>=10000)return String.format(Locale.ROOT,"%.1fk",value/1000);
        return String.format(Locale.ROOT,"%,.0f",value);
    }
}
