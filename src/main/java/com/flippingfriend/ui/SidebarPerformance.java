package com.flippingfriend.ui;

import com.flippingfriend.companion.CompanionClient;
import com.flippingfriend.core.*;
import com.flippingfriend.model.Explainer;
import java.awt.*;
import java.util.*;
import javax.swing.*;
import net.runelite.client.ui.FontManager;

/** Native account measurements and paged durable sales, available beside the current action. */
final class SidebarPerformance extends JPanel {
    private final CompanionClient companion;
    private final Explainer explainer;
    private final JComboBox<String> scope=new JComboBox<>(new String[]{"Session","Last 24 hours","Last 7 days","Recorded history"});
    private final JLabel account=new JLabel("Current account"),caption=new JLabel("Recorded profit"),coverage=new JLabel();
    private final JTextArea profit=CompactSuggestionPanel.text(UiUtils.TEXT);
    private final JPanel stats=new JPanel(new GridLayout(0,1)),sales=new JPanel();
    private final Map<String,JLabel> values=new LinkedHashMap<>();
    private final JLabel page=new JLabel("Page 1 of 1");
    private final JButton previous,next,reset;
    private final JPanel footer;
    private final Runnable investigate;
    private int pageNumber;
    private String saleSignature="";
    SidebarPerformance(CompanionClient companion,Explainer explainer,Runnable investigate,Runnable portfolio,java.util.function.IntConsumer graph) {
        this.companion=companion;this.explainer=explainer;this.investigate=investigate;
        setName("sidebar-performance");setOpaque(false);setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
        scope.setName("performance-scope");scope.setFont(FontManager.getRunescapeSmallFont());
        scope.setToolTipText("Measurements use durable account sale updates. Historical coverage may be incomplete.");
        reset=new JButton("Reset session");reset.setName("reset-session");reset.setFont(FontManager.getRunescapeSmallFont());
        reset.setBorder(BorderFactory.createEmptyBorder(4,2,4,2));reset.setBackground(UiUtils.CARD);
        reset.setToolTipText("Start a new measurement window. Keeps every trade, confirmed loss and risk anchor.");
        reset.addActionListener(e->{companion.resetPerformanceSession();pageNumber=0;});
        JPanel select=row();select.add(scope,BorderLayout.CENTER);select.add(reset,BorderLayout.EAST);addLine(select);
        style(account);account.setName("performance-account");account.setToolTipText("This account only. Unassigned legacy records and other accounts are excluded.");addLine(account);
        scope.addActionListener(e->{pageNumber=0;companion.setPerformanceScope(new String[]{"SESSION","DAY","WEEK","ALL"}[scope.getSelectedIndex()]);});
        JPanel heading=row();style(caption);caption.setFont(FontManager.getRunescapeBoldFont());heading.add(caption);
        heading.add(PanelIcons.button("performance-action","open_in_new","Open recorded performance",investigate),BorderLayout.EAST);addLine(heading);
        profit.setName("measured-profit");profit.setFont(FontManager.getRunescapeBoldFont().deriveFont(20f));addLine(profit);
        style(coverage);coverage.setFont(FontManager.getRunescapeSmallFont());addLine(coverage);
        stats.setOpaque(false);
        for(String key:new String[]{"Available cash","Stock purchase cost","Unrealized profit","Flips made","ROI","Session time","Hourly profit","Portfolio value"}) {
            JPanel line=row();JLabel label=new JLabel(key+":");style(label);label.setForeground(UiUtils.MUTED);line.add(label,BorderLayout.WEST);
            JLabel value=new JLabel("Unavailable");style(value);value.setName("metric-"+key.toLowerCase(Locale.ROOT).replace(' ','-'));
            line.add(value,BorderLayout.EAST);values.put(key,value);stats.add(line);
            if(key.equals("Portfolio value") || key.equals("Unrealized profit")) {
                line.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                line.addMouseListener(new java.awt.event.MouseAdapter(){@Override public void mouseClicked(java.awt.event.MouseEvent e){portfolio.run();}});
                line.setToolTipText("Open managed holdings. Conservative taxed value; unpriced stock remains unknown, personal belongings excluded.");
            }
        }
        stats.setBorder(BorderFactory.createEmptyBorder(5,0,7,0));addLine(stats);
        sales.setLayout(new BoxLayout(sales,BoxLayout.Y_AXIS));sales.setOpaque(false);addLine(sales);
        previous=PanelIcons.button("sales-previous","chevron_left","Previous recorded sales page",()->{pageNumber=Math.max(0,pageNumber-1);saleSignature="";update(companion.performance(),null,true,graph);});
        next=PanelIcons.button("sales-next","chevron_right","Next recorded sales page",()->{pageNumber++;saleSignature="";update(companion.performance(),null,true,graph);});
        JPanel pages=row();footer=pages;JPanel controls=new JPanel(new FlowLayout(FlowLayout.CENTER,0,0));controls.setOpaque(false);style(page);
        controls.add(previous);controls.add(page);controls.add(next);pages.add(controls);
        pages.add(PanelIcons.button("sales-expand","open_in_new","Investigate recorded sales",investigate),BorderLayout.EAST);
    }
    JComponent footer(){return footer;}
    private static void style(JLabel label){label.setFont(FontManager.getRunescapeSmallFont());label.setForeground(UiUtils.TEXT);}
    private static JPanel row(){JPanel p=new JPanel(new BorderLayout(4,0));p.setOpaque(false);return p;}
    private void addLine(JComponent c){c.setAlignmentX(LEFT_ALIGNMENT);if(!(c instanceof JTextArea) && !(c instanceof JLabel))c.setMaximumSize(new Dimension(Integer.MAX_VALUE,c.getPreferredSize().height));add(c);}
    @Override public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}
    void update(PerformanceSnapshot measurement,AccountLedgerSnapshot ledger,boolean loggedIn,java.util.function.IntConsumer graph) {
        reset.setEnabled(loggedIn);scope.setEnabled(loggedIn);account.setText(loggedIn?"Current account":"Log in to see account guidance");
        boolean available=loggedIn && measurement!=null;
        caption.setText(available?(measurement.unresolvedSales>0?"Known profit":"Profit")
            :ledger!=null && ledger.getUnresolvedSales()>0?"Known recorded profit":"Recorded profit");
        long low=available?measurement.profitLower:ledger==null?0:ledger.getProfitLower();
        long high=available?measurement.profitUpper:ledger==null?0:ledger.getProfitUpper();
        profit.setText(available?measurement.saleUpdates>0 && measurement.unresolvedSales==measurement.saleUpdates?"Unresolved":range(low,high)
            :ledger==null?"Unavailable":ledger.getBoundedSales()==0 && ledger.getSales()>0?"Unresolved":range(low,high));
        profit.setForeground(low>0?UiUtils.profitColor(1):high<0?UiUtils.profitColor(-1):UiUtils.TEXT);
        profit.setToolTipText("Realized after-tax profit from durable sales in the selected measurement window. Unknown profit is excluded and counted below.");
        coverage.setText(available?measurement.saleUpdates+" updates · "+measurement.unresolvedSales+" unresolved":ledger==null?"Waiting for account records":ledger.getSales()+" updates · "+ledger.getUnresolvedSales()+" unresolved");
        coverage.setToolTipText(Objects.toString(companion.performanceError(),"")+" Sale timestamps are observations; partial fills count once per completed sell offer. Historical coverage may be incomplete.");
        for(JLabel value:values.values()){value.setText("Unavailable");value.setForeground(UiUtils.TEXT);value.setToolTipText(null);}
        if(available) {
            if(measurement.spendableCoins!=null) {
                values.get("Available cash").setText(explainer.formatGp(measurement.spendableCoins));
                values.get("Available cash").setToolTipText(explainer.formatNumber(measurement.spendableCoins)+" GP from current copied account evidence. Resale estimates are not spendable coins.");
                values.get("Stock purchase cost").setText(explainer.formatGp(measurement.managedAcquisitionCost));
                values.get("Stock purchase cost").setToolTipText(valuationTip(measurement));
            }
            values.get("Flips made").setText(Long.toString(measurement.completedFlips));
            values.get("Flips made").setToolTipText("Fully completed, cost-matched sell orders. Partial updates contribute profit but do not inflate this count.");
            if(measurement.appliedCost>0)values.get("ROI").setText(percent(low,measurement.appliedCost)+(low==high?"":" to "+percent(high,measurement.appliedCost)));
            Color realizedColor=low>0?UiUtils.profitColor(1):high<0?UiUtils.profitColor(-1):UiUtils.TEXT;
            values.get("ROI").setForeground(realizedColor);
            values.get("ROI").setToolTipText("Known net realized profit / exact applied acquisition cost of priced sales; unknown sales excluded.");
            if("SESSION".equals(measurement.scope) && measurement.sessionId!=null) {
                long seconds=Math.max(0,java.time.Instant.now().getEpochSecond()-measurement.startedAt);
                values.get("Session time").setText(String.format(Locale.ROOT,"%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60));
                values.get("Session time").setForeground(UiUtils.WARNING);
                values.get("Session time").setToolTipText("Elapsed wall time since the durable measurement start, including offline time. Reset never resets risk.");
                values.get("Hourly profit").setText(seconds<60?"Measuring":rate(low,high,seconds));
                values.get("Hourly profit").setForeground(realizedColor);
                values.get("Hourly profit").setToolTipText("Known realized net profit / elapsed wall time, not slot-hour estimates; unknown sales excluded.");
            }
            if(measurement.portfolioValue!=null) {
                boolean partial=measurement.unpricedCost>0;
                values.get("Portfolio value").setText("≈ "+explainer.formatGp(measurement.portfolioValue)+(partial?"*":""));
                values.get("Unrealized profit").setText(explainer.formatGp(measurement.unrealizedProfit)+(partial?"*":""));
                String tip=valuationTip(measurement);values.get("Portfolio value").setToolTipText(tip);
                values.get("Unrealized profit").setToolTipText(tip);
            }
        }
        int size=available?measurement.sales.size():0,totalPages=Math.max(1,(size+9)/10);pageNumber=Math.min(pageNumber,totalPages-1);
        page.setText("Page "+(pageNumber+1)+" of "+totalPages);previous.setEnabled(available && pageNumber>0);next.setEnabled(available && pageNumber+1<totalPages);
        String signature=available?measurement.accountId+measurement.scope+measurement.sessionId+pageNumber+measurement.sales.toString():"none";
        // Serialization compares values, so a two-second update does not rebuild unchanged rows or reset focus.
        if(available)signature=new com.google.gson.Gson().toJson(measurement.sales)+measurement.scope+measurement.accountId+pageNumber;
        if(signature.equals(saleSignature))return;saleSignature=signature;sales.removeAll();
        if(size==0){JLabel empty=new JLabel(available?"No recorded sales in this window":"Waiting for recorded sales");style(empty);addSale(empty);}
        else for(int i=pageNumber*10;i<Math.min(size,(pageNumber+1)*10);i++) {
            var sale=measurement.sales.get(i);JPanel line=row();
            JLabel name=new JLabel(sale.quantity+" × "+sale.itemName);style(name);
            JLabel result=new JLabel(sale.profitLower==null?"Unknown":shortRange(sale.profitLower,sale.profitUpper));style(result);
            if(sale.profitLower!=null)result.setForeground(sale.profitLower>0?UiUtils.profitColor(1):sale.profitUpper<0?UiUtils.profitColor(-1):UiUtils.TEXT);
            line.add(name);line.add(result,BorderLayout.EAST);
            String tip=sale.itemName+" · "+sale.updates+" sale updates · "+(sale.completed?"Completed":"Partial/unconfirmed completion")+" · "+Objects.toString(sale.offerIdentity,"Unknown offer identity");
            line.setToolTipText(tip);name.setToolTipText(tip);result.setToolTipText(tip);
            line.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            java.awt.event.MouseAdapter click=new java.awt.event.MouseAdapter(){@Override public void mouseClicked(java.awt.event.MouseEvent e){graph.accept(sale.itemId);}};
            line.addMouseListener(click);name.addMouseListener(click);result.addMouseListener(click);addSale(line);
        }
        sales.setMaximumSize(new Dimension(Integer.MAX_VALUE,sales.getPreferredSize().height));revalidate();repaint();
    }
    private String valuationTip(PerformanceSnapshot measurement) {
        StringBuilder tip=new StringBuilder("<html>Last-quoted after-tax estimates for observed trading stock; no guaranteed fill.<br>Cash and exact purchase costs are separate. Estimates never authorize buys.<br>");
        if(measurement.unpricedCost!=null && measurement.unpricedCost>0)
            tip.append("* Priced portion only; ").append(explainer.formatNumber(measurement.unpricedCost)).append(" GP of acquisition cost is unpriced, not lost.<br>");
        if(measurement.stockValuations!=null)for(var stock:measurement.stockValuations) {
            tip.append(stock.quantity).append(" × ").append(Objects.toString(stock.itemName,"Item "+stock.itemId).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"))
                .append(": ").append(explainer.formatNumber(stock.totalCost)).append(" GP paid; ")
                .append(stock.observedQuantity).append(" currently observed; ").append(stock.pricedQuantity).append(" priced");
            if(stock.quotedAt>0)tip.append(" at ").append(explainer.formatNumber(stock.price)).append(" GP, trade ")
                .append(Math.max(0,measurement.observedAt-stock.quotedAt)).append("s before this estimate");
            if(!stock.unpricedReason.isEmpty())tip.append(". ").append(stock.unpricedReason);
            tip.append("<br>");
        }
        return tip.append("</html>").toString();
    }
    private void addSale(JComponent c){c.setAlignmentX(LEFT_ALIGNMENT);c.setMaximumSize(new Dimension(Integer.MAX_VALUE,c.getPreferredSize().height));sales.add(c);}
    private String shortRange(long a,long b){return a==b?explainer.formatGp(a):explainer.formatGp(a)+"…"+explainer.formatGp(b);}
    private String range(long a,long b){return a==b?explainer.formatNumber(a)+" gp":explainer.formatNumber(a)+" to "+explainer.formatNumber(b)+" gp";}
    private static String percent(long p,long cost){return String.format(Locale.ROOT,"%.3f%%",p*100.0/cost);}
    private String rate(long low,long high,long seconds){return shortRange(Math.round(low*3600.0/seconds),Math.round(high*3600.0/seconds))+"/h";}
}
