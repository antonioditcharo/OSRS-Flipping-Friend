package com.flippingfriend.ui;

import com.flippingfriend.data.*;
import com.flippingfriend.model.*;
import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.LongSupplier;
import javax.swing.*;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;

/** Full market investigation view, deliberately separate from the small holdings sparkline. */
final class MarketGraphView extends JPanel {
    enum Range {
        MONTH("Month",30*86400,"6h",21600,24),WEEK("Week",7*86400,"1h",3600,24),
        DAY("Day",86400,"5m",300,24),EIGHT_HOURS("8h",28800,"5m",300,24);
        final String label,step;final int seconds,bucket,forecastHours;
        Range(String label,int seconds,String step,int bucket,int forecastHours){this.label=label;this.seconds=seconds;this.step=step;this.bucket=bucket;this.forecastHours=forecastHours;}
    }
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("dd MMM HH:mm:ss",Locale.ROOT).withZone(ZoneOffset.UTC);
    private final MarketDataService market;
    private final LongSupplier clock;
    private static final java.util.concurrent.Executor FORECAST_WORKER=new java.util.concurrent.ThreadPoolExecutor(1,1,30,
        java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.ArrayBlockingQueue<>(1),r->{Thread t=new Thread(r,"flipping-friend-chart-forecast");t.setDaemon(true);t.setPriority(Thread.MIN_PRIORITY);return t;},new java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy());
    final MarketGraphCanvas chart=new MarketGraphCanvas();
    final JTextField search=new JTextField();
    final JButton suggested=new JButton("Switch to suggested item");
    final JCheckBox projections=new JCheckBox("Projections",true);
    private final JLabel title=new JLabel("Choose an item");
    private final JTextArea status=CompactSuggestionPanel.text(UiUtils.MUTED);
    private final JTextArea note=CompactSuggestionPanel.text(UiUtils.MUTED);
    private final Map<String,JLabel> stats=new LinkedHashMap<>();
    private final Map<Range,JToggleButton> ranges=new EnumMap<>(Range.class);
    private final javax.swing.Timer timer;
    private ItemManager items;
    private int selected;
    private Range range=Range.DAY;
    private Suggestion advice=Suggestion.idle();
    private List<Candle> lastTraining,lastShortTraining,lastSixHourlyTraining;
    private PublicPriceForecast base;
    private int forecastGeneration;
    private boolean disposed;
    private long trainingHour=-1,defaultStart,defaultEnd;
    private boolean reset=true;
    private Rectangle restoredBounds;
    private String lookupMessage;
    private final JPanel headerSidebarSpace=new JPanel();
    MarketGraphView(MarketDataService market){this(market,()->Instant.now().getEpochSecond());}
    MarketGraphView(MarketDataService market,LongSupplier clock) {
        super(new BorderLayout(12,8));this.market=market;this.clock=clock;
        setBackground(UiUtils.CARD);setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        JPanel header=new JPanel();header.setOpaque(false);header.setLayout(new BoxLayout(header,BoxLayout.Y_AXIS));
        JPanel lookup=new JPanel(new BorderLayout(10,0));lookup.setOpaque(false);
        JLabel label=new JLabel("Search item:");label.setForeground(UiUtils.TEXT);lookup.add(label,BorderLayout.WEST);
        label.setLabelFor(search);
        label.setFont(FontManager.getRunescapeSmallFont());search.setBackground(new Color(30,30,30));search.setForeground(UiUtils.TEXT);
        search.setCaretColor(UiUtils.TEXT);search.setFont(FontManager.getRunescapeSmallFont());search.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(50,50,50)),BorderFactory.createEmptyBorder(6,8,6,8)));
        search.setName("graph-search");search.setToolTipText("Enter an item ID, exact name or distinctive part of its name");
        search.addActionListener(e->search());lookup.add(search,BorderLayout.CENTER);lookup.add(suggested,BorderLayout.EAST);
        suggested.setName("graph-suggested");suggested.setEnabled(false);suggested.addActionListener(e->{if(advice.getItemId()>0){reset=true;showItem(advice.getItemId());}});
        style(suggested);header.add(lookup);header.add(Box.createVerticalStrut(18));
        JPanel chartHeader=new JPanel(new BorderLayout(12,0));chartHeader.setOpaque(false);
        JPanel chartHeaderRows=new JPanel();chartHeaderRows.setOpaque(false);chartHeaderRows.setLayout(new BoxLayout(chartHeaderRows,BoxLayout.Y_AXIS));
        headerSidebarSpace.setOpaque(false);headerSidebarSpace.setPreferredSize(new Dimension(320,0));chartHeader.add(headerSidebarSpace,BorderLayout.EAST);
        chartHeader.add(chartHeaderRows,BorderLayout.CENTER);
        JPanel tools=new JPanel(new FlowLayout(FlowLayout.RIGHT,0,0));tools.setOpaque(false);JPanel periods=new JPanel(new FlowLayout(FlowLayout.LEFT,4,0));periods.setOpaque(false);
        ButtonGroup group=new ButtonGroup();for(Range value:Range.values()) {
            JToggleButton button=new JToggleButton(value.label);button.setName("graph-range-"+value.label);button.setSelected(value==range);
            style(button);button.setPreferredSize(new Dimension(58,26));button.addItemListener(e->{button.setForeground(button.isSelected()?new Color(255,190,70):UiUtils.TEXT);});
            if(value==range)button.setForeground(new Color(255,190,70));
            button.addActionListener(e->{range=value;lookupMessage=null;reset=true;refresh();});group.add(button);periods.add(button);ranges.put(value,button);
        }
        tools.add(periods);
        JPanel navigation=new JPanel(new FlowLayout(FlowLayout.RIGHT,3,0));navigation.setOpaque(false);
        navigation.add(projections);projections.setOpaque(false);projections.setForeground(UiUtils.TEXT);projections.setName("graph-projections");
        projections.setToolTipText("Low/high market forecasts with empirical central 50% residual intervals; future prices and fills remain uncertain");
        projections.addActionListener(e->{chart.showProjections(projections.isSelected());refresh();});
        navigation.add(PanelIcons.button("graph-zoom-out","zoom_out","Zoom out",()->chart.zoom(1.5)));
        navigation.add(PanelIcons.button("graph-zoom-in","zoom_in","Zoom in",()->chart.zoom(1/1.5)));
        navigation.add(PanelIcons.button("graph-reset","home","Reset chart range",()->chart.reset(defaultStart,defaultEnd)));
        navigation.add(PanelIcons.button("graph-expand","open_in_new","Expand or restore graph window",this::expand));
        tools.add(navigation);chartHeaderRows.add(tools);chartHeaderRows.add(Box.createVerticalStrut(8));
        JPanel legend=new JPanel(new FlowLayout(FlowLayout.CENTER,18,0));legend.setOpaque(false);
        legend.add(legend("Low (insta-sell)",MarketGraphCanvas.LOW));legend.add(legend("High (insta-buy)",MarketGraphCanvas.HIGH));
        legend.add(legend("Dashed: market forecast",UiUtils.TEXT));legend.add(legend("Shaded: 50% residual interval",UiUtils.MUTED));
        chartHeaderRows.add(legend);header.add(chartHeader);add(header,BorderLayout.NORTH);
        JPanel plot=new JPanel(new BorderLayout());plot.setOpaque(false);plot.add(chart,BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(6,4,2,0));status.setForeground(UiUtils.MUTED);plot.add(status,BorderLayout.SOUTH);add(plot,BorderLayout.CENTER);
        JPanel sidebar=new JPanel(new BorderLayout(0,15));sidebar.setOpaque(false);sidebar.setPreferredSize(new Dimension(320,0));
        title.setFont(FontManager.getRunescapeBoldFont());title.setForeground(UiUtils.TEXT);title.setBorder(BorderFactory.createEmptyBorder(5,0,10,0));sidebar.add(title,BorderLayout.NORTH);
        JPanel numbers=new JPanel(new GridBagLayout());numbers.setOpaque(false);GridBagConstraints c=new GridBagConstraints();
        c.gridy=0;c.insets=new Insets(8,0,8,0);c.anchor=GridBagConstraints.WEST;c.fill=GridBagConstraints.HORIZONTAL;
        for(String name:new String[]{"24h observed volume","Last low time (UTC)","Last low price","Last high time (UTC)","Last high price","24h change","Week change"}) {
            c.gridx=0;c.weightx=1;JLabel key=legend(name,UiUtils.MUTED);numbers.add(key,c);
            c.gridx=1;c.weightx=0;JLabel value=legend("Unavailable",UiUtils.TEXT);value.setName("graph-stat-"+name);numbers.add(value,c);stats.put(name,value);c.gridy++;
        }
        c.gridx=0;c.gridwidth=2;c.weighty=1;numbers.add(Box.createVerticalGlue(),c);sidebar.add(numbers,BorderLayout.CENTER);add(sidebar,BorderLayout.EAST);
        note.setText("OSRS Wiki / RuneLite market data · completed bucket averages and reported trade volumes. "
            +"Forecasts compare trend, reversion and daily patterns with unchanged prices. Intervals describe earlier forecast errors; future coverage and fills are not guaranteed.");
        note.setBorder(BorderFactory.createEmptyBorder(5,3,2,3));add(note,BorderLayout.SOUTH);
        timer=new javax.swing.Timer(1000,e->{if(isShowing())refresh();});timer.setCoalesce(true);
        setFont(FontManager.getRunescapeSmallFont());
    }
    private static JLabel legend(String text,Color color){JLabel label=new JLabel(text);label.setFont(FontManager.getRunescapeSmallFont());label.setForeground(color);return label;}
    @Override public void doLayout(){boolean wide=getWidth()>=1000;if(headerSidebarSpace.isVisible()!=wide)headerSidebarSpace.setVisible(wide);super.doLayout();}
    private static void style(AbstractButton button) {
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI());button.setFont(FontManager.getRunescapeSmallFont());
        button.setForeground(UiUtils.TEXT);button.setBackground(new Color(65,65,65));button.setOpaque(true);button.setContentAreaFilled(true);
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(45,45,45)),BorderFactory.createEmptyBorder(5,9,5,9)));
    }
    @Override public void addNotify(){super.addNotify();timer.start();}
    @Override public void removeNotify(){timer.stop();super.removeNotify();}
    void dispose(){timer.stop();disposed=true;forecastGeneration++;}
    void setItemManager(ItemManager manager){items=manager;updateTitle();}
    void update(Suggestion current){advice=current==null?Suggestion.idle():current;suggested.setEnabled(advice.getItemId()>0);if(isShowing()){if(selected<=0 && advice.getItemId()>0)showItem(advice.getItemId());else refresh();}}
    void showItem(int item){if(item<=0)return;lookupMessage=null;if(selected!=item){selected=item;reset=true;lastTraining=null;}updateTitle();refresh();}
    int item(){return selected;}
    private void updateTitle() {
        if(selected<=0)return;String name=market.getSnapshot().getItemName(selected);title.setText(name);title.setToolTipText(name+" (#"+selected+")");search.setText(name);
        title.setIcon(null);if(items!=null){var icon=items.getImage(selected);if(icon!=null)icon.addTo(title);}
    }
    private void search() {
        String query=search.getText().trim();var found=new ArrayList<ItemMetadata>();
        for(ItemMetadata item:market.getSnapshot().getMetadata().values()) {
            if(Integer.toString(item.getId()).equals(query) || item.getName().equalsIgnoreCase(query)){showItem(item.getId());return;}
            if(!query.isEmpty() && item.getName().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))found.add(item);
        }
        found.sort(Comparator.comparing(ItemMetadata::getName));
        if(found.size()==1){showItem(found.get(0).getId());return;}
        if(found.isEmpty()){lookupMessage="No item matches that name or ID. The displayed item is unchanged.";status.setText(lookupMessage);return;}
        JPopupMenu menu=new JPopupMenu();for(ItemMetadata item:found.subList(0,Math.min(20,found.size()))) {
            JMenuItem choice=new JMenuItem(item.getName()+" (#"+item.getId()+")");choice.addActionListener(e->showItem(item.getId()));menu.add(choice);
        }
        lookupMessage="Choose a match ("+found.size()+" items). Refine the search to narrow the list.";status.setText(lookupMessage);
        if(search.isShowing())menu.show(search,0,search.getHeight());
    }
    void refresh() {
        if(selected<=0)return;long now=clock.getAsLong();var snapshot=market.getSnapshot();
        if(!snapshot.getItemName(selected).equals(title.getText()))updateTitle();
        List<Candle> visible=completed(market.getGraphSeries(selected,range.step),range.bucket,now);
        List<Candle> hourly=market.getGraphSeries(selected,"1h"),daily=market.getGraphSeries(selected,"5m"),sixHourly=market.getGraphSeries(selected,"6h");
        if(hourly==null)hourly=List.of();if(daily==null)daily=List.of();if(sixHourly==null)sixHourly=List.of();
        if(hourly!=lastTraining || daily!=lastShortTraining || sixHourly!=lastSixHourlyTraining || trainingHour!=now/300) {
            lastTraining=hourly;lastShortTraining=daily;lastSixHourlyTraining=sixHourly;trainingHour=now/300;
            base=PublicPriceForecast.cachedChart(daily,hourly,sixHourly,now);
            int generation=++forecastGeneration;
            if(base==null) {
                List<Candle> shortInput=new ArrayList<>(daily),longInput=new ArrayList<>(hourly),sixHourInput=new ArrayList<>(sixHourly);
                FORECAST_WORKER.execute(()->{
                    var result=PublicPriceForecast.fitChart(shortInput,longInput,sixHourInput,now);
                    SwingUtilities.invokeLater(()->{if(!disposed && generation==forecastGeneration){base=result;refresh();}});
                });
            }
        }
        LatestPrice quote=snapshot.latest(selected);var forecast=base==null?new MarketPriceProjection().project(List.of(),300,now,1)
            :base.projection(quote,snapshot.getCapturedAt().getEpochSecond(),now,range.forecastHours);
        long right=forecast.isAvailable()?forecast.points.get(forecast.points.size()-1).at:now;
        defaultStart=now-range.seconds;defaultEnd=right;int buy=0,sell=0;
        if(advice.getItemId()==selected && (advice.getAdviceExpiresAt()<=0 || advice.getAdviceExpiresAt()>=now)) {
            if(advice.getType()==SuggestionType.BUY)buy=advice.getPrice();
            if(advice.getType()==SuggestionType.SELL || advice.getType()==SuggestionType.MODIFY_SELL)sell=advice.getPrice();
        }
        chart.setData(visible,range.bucket,forecast,buy,sell,defaultStart,defaultEnd,reset,quote,snapshot.getCapturedAt().getEpochSecond());reset=false;
        String problem=market.graphHistoryProblem(selected,range.step);
        status.setText(lookupMessage!=null?lookupMessage:problem!=null?problem:visible.isEmpty()?"Loading completed market history…":
            projections.isSelected()?(forecast.isAvailable()?"Market forecast · "+((forecast.points.get(forecast.points.size()-1).at-forecast.originAt)/3600.0)+"h available · hover for price estimates and comparison counts":base==null?"Analysing completed market history…":forecast.reason)
                :"Observed public market history · drag to pan, scroll to zoom · times in UTC");
        stat("Last low time (UTC)",quote==null?null:time(quote.getLowTime(),now));stat("Last high time (UTC)",quote==null?null:time(quote.getHighTime(),now));
        stat("Last low price",quote==null || quote.getLow()==null || quote.getLow()<=0 || quote.secondsSinceLow(Instant.ofEpochSecond(now))<0?null:String.format(Locale.ROOT,"%,d gp",quote.getLow()));
        stat("Last high price",quote==null || quote.getHigh()==null || quote.getHigh()<=0 || quote.secondsSinceHigh(Instant.ofEpochSecond(now))<0?null:String.format(Locale.ROOT,"%,d gp",quote.getHigh()));
        long volume=0;int count=0;for(Candle c:completed(daily,300,now))if(c.getTimestamp()+300>now-86400 && c.getLowPriceVolume()>=0 && c.getHighPriceVolume()>=0) {
            volume+=(long)c.getLowPriceVolume()+c.getHighPriceVolume();count++;
        }
        stat("24h observed volume",count==0?null:String.format(Locale.ROOT,"%,d%s",volume,count<288?" (partial)":""));
        change("24h change",hourly,now-86400,quote,now);change("Week change",hourly,now-7*86400,quote,now);
    }
    private static List<Candle> completed(List<Candle> rows,int bucket,long now) {
        var result=new ArrayList<Candle>();if(rows!=null)for(Candle c:rows)if(c!=null && c.getTimestamp()>=0 && c.getTimestamp()<=now-bucket)result.add(c);
        result.sort(Comparator.comparingLong(Candle::getTimestamp));return result;
    }
    private static String time(Long value,long now){return value==null || value<=0?null:value>now?"Invalid future time":TIME.format(Instant.ofEpochSecond(value));}
    private void stat(String name,String value){JLabel label=stats.get(name);label.setText(value==null?"Unavailable":value);label.setForeground(UiUtils.TEXT);}
    private void change(String name,List<Candle> rows,long target,LatestPrice q,long now) {
        Candle prior=null;for(Candle c:rows)if(c!=null && c.hasBothSides() && c.getTimestamp()+3600<=target
            && target-(c.getTimestamp()+3600)<=7200 && (prior==null || c.getTimestamp()>prior.getTimestamp()))prior=c;
        if(prior==null || q==null || !q.isComplete() || q.secondsSinceLow(Instant.ofEpochSecond(now))<0 || q.secondsSinceHigh(Instant.ofEpochSecond(now))<0
            || q.stalestSideSeconds(Instant.ofEpochSecond(now))>7200){stat(name,null);return;}
        double before=((long)prior.getAvgLowPrice()+prior.getAvgHighPrice())/2.0,current=((long)q.getLow()+q.getHigh())/2.0;
        double percent=(current/before-1)*100;stat(name,String.format(Locale.ROOT,"%+.2f%%",percent));stats.get(name).setForeground(percent>=0?new Color(70,220,90):new Color(240,100,90));
    }
    private void expand() {
        Window window=SwingUtilities.getWindowAncestor(this);if(window==null)return;
        if(window instanceof Frame) {
            Frame frame=(Frame)window;
            frame.setExtendedState((frame.getExtendedState() & Frame.MAXIMIZED_BOTH)!=0?Frame.NORMAL:Frame.MAXIMIZED_BOTH);
            return;
        }
        if(restoredBounds==null){
            restoredBounds=window.getBounds();GraphicsConfiguration display=window.getGraphicsConfiguration();
            Rectangle area=new Rectangle(display.getBounds());Insets insets=Toolkit.getDefaultToolkit().getScreenInsets(display);
            area.x+=insets.left;area.y+=insets.top;area.width-=insets.left+insets.right;area.height-=insets.top+insets.bottom;window.setBounds(area);
        }
        else {window.setBounds(restoredBounds);restoredBounds=null;}
    }
}
