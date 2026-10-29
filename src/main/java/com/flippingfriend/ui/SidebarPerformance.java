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
    private final java.util.function.Consumer<String> investigateSale;
    private int pageNumber;
    private String saleSignature="";
    SidebarPerformance(CompanionClient companion,Explainer explainer,Runnable investigate,Runnable portfolio,java.util.function.Consumer<String> investigateSale,java.util.function.IntConsumer graph) {
        this.companion=companion;this.explainer=explainer;this.investigate=investigate;
        this.investigateSale=investigateSale;
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
        for(String key:new String[]{"Available cash","Stock purchase cost","Unrealized profit","Instant-sale P/L","Flips made","ROI","Session time","Hourly profit","Portfolio value"}) {
            JPanel line=row();JLabel label=new JLabel(key+":");style(label);label.setForeground(UiUtils.MUTED);line.add(label,BorderLayout.WEST);
            JLabel value=new JLabel("Unavailable");style(value);value.setName("metric-"+key.toLowerCase(Locale.ROOT).replace(' ','-'));
            line.add(value,BorderLayout.EAST);values.put(key,value);stats.add(line);
            if(key.equals("Portfolio value") || key.equals("Unrealized profit")) {
                line.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                line.addMouseListener(new java.awt.event.MouseAdapter(){@Override public void mouseClicked(java.awt.event.MouseEvent e){portfolio.run();}});
                line.setToolTipText("Open managed holdings. Expected exits and instant-sale estimates are separate; personal belongings excluded.");
            }
        }
        stats.setBorder(BorderFactory.createEmptyBorder(5,0,7,0));addLine(stats);
        sales.setLayout(new BoxLayout(sales,BoxLayout.Y_AXIS));sales.setOpaque(false);addLine(sales);
        previous=PanelIcons.button("sales-previous","chevron_left","Previous recorded sales page",()->{pageNumber=Math.max(0,pageNumber-1);saleSignature="";update(companion.performance(),null,true,graph);});
        next=PanelIcons.button("sales-next","chevron_right","Next recorded sales page",()->{pageNumber++;saleSignature="";update(companion.performance(),null,true,graph);});
        JPanel pages=row();footer=pages;JPanel controls=new JPanel(new FlowLayout(FlowLayout.CENTER,0,0));controls.setOpaque(false);style(page);
        controls.add(previous);controls.add(page);controls.add(next);pages.add(controls);
        pages.add(PanelIcons.button("sales-expand","open_in_new","Investigate recorded sales",()->investigateSale.accept(null)),BorderLayout.EAST);
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
        String profitText=available?measurement.saleUpdates>0 && measurement.unresolvedSales==measurement.saleUpdates?"Unresolved":range(low,high)
            :ledger==null?"Unavailable":ledger.getBoundedSales()==0 && ledger.getSales()>0?"Unresolved":range(low,high);
        if(!profitText.equals(profit.getText()))profit.setText(profitText);
        profit.setForeground(low>0?UiUtils.profitColor(1):high<0?UiUtils.profitColor(-1):UiUtils.TEXT);
        profit.setToolTipText("Realized after-tax profit from durable sales in the selected measurement window. Unknown profit is excluded and counted below.");
        coverage.setText(available?measurement.archivedUnresolvedSales>0?"<html>"+measurement.saleUpdates+" updates · "+measurement.unresolvedSales+" unresolved<br>"+measurement.archivedUnresolvedSales+" archived (kept in coverage)</html>":measurement.saleUpdates+" updates · "+measurement.unresolvedSales+" unresolved":ledger==null?"Waiting for account records":ledger.getSales()+" updates · "+ledger.getUnresolvedSales()+" unresolved");
        coverage.setToolTipText(Objects.toString(companion.performanceError(),"")+" Sale timestamps are observations; partial fills count once per completed sell offer. Historical coverage may be incomplete.");
        Map<String,Metric> desired=new LinkedHashMap<>();values.keySet().forEach(key->desired.put(key,new Metric()));
        if(available) {
            if(measurement.spendableCoins!=null) {
                desired.get("Available cash").setText(explainer.formatGp(measurement.spendableCoins));
                desired.get("Available cash").setToolTipText(explainer.formatNumber(measurement.spendableCoins)+" GP from current copied account evidence. Resale estimates are not spendable coins.");
                desired.get("Stock purchase cost").setText(explainer.formatGp(measurement.managedAcquisitionCost));
                desired.get("Stock purchase cost").setToolTipText(valuationTip(measurement));
            }
            desired.get("Flips made").setText(Long.toString(measurement.completedFlips));
            desired.get("Flips made").setToolTipText("Fully completed, cost-matched sell orders. Partial updates contribute profit but do not inflate this count.");
            if(measurement.appliedCost>0)desired.get("ROI").setText((low==high?"":"Est. ")+percent(RoundedEstimate.midpoint(low,high),measurement.appliedCost));
            Color realizedColor=low>0?UiUtils.profitColor(1):high<0?UiUtils.profitColor(-1):UiUtils.TEXT;
            desired.get("ROI").setForeground(realizedColor);
            desired.get("ROI").setToolTipText("Known net realized profit / exact applied acquisition cost of priced sales; unknown sales excluded.");
            if("SESSION".equals(measurement.scope) && measurement.sessionId!=null) {
                long seconds=Math.max(0,java.time.Instant.now().getEpochSecond()-measurement.startedAt);
                desired.get("Session time").setText(String.format(Locale.ROOT,"%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60));
                desired.get("Session time").setForeground(UiUtils.WARNING);
                desired.get("Session time").setToolTipText("Elapsed wall time since the durable measurement start, including offline time. Reset never resets risk.");
                desired.get("Hourly profit").setText(seconds<60?"Measuring":rate(low,high,seconds));
                desired.get("Hourly profit").setForeground(realizedColor);
                desired.get("Hourly profit").setToolTipText("Known realized net profit / elapsed wall time, not slot-hour estimates; unknown sales excluded.");
            }
            if(measurement.portfolioValue!=null) {
                boolean partial=measurement.unpricedCost>0;
                desired.get("Portfolio value").setText("≈ "+explainer.formatGp(measurement.portfolioValue)+(partial?"*":""));
                boolean planned=measurement.intendedExitValue!=null;
                boolean missingPlan=planned && measurement.unplannedCost>0;
                desired.get("Unrealized profit").setText(planned && missingPlan && measurement.unplannedCost.equals(measurement.managedAcquisitionCost)
                    ?"Target unknown":explainer.formatGp(measurement.unrealizedProfit)+((planned?missingPlan:partial)?"*":""));
                desired.get("Unrealized profit").setForeground(UiUtils.profitColor(Long.compare(measurement.unrealizedProfit,0)));
                if(measurement.instantSaleProfit!=null) {
                    desired.get("Instant-sale P/L").setText(partial && measurement.unpricedCost.equals(measurement.managedAcquisitionCost)
                        ?"Unpriced":explainer.formatGp(measurement.instantSaleProfit)+(partial?"*":""));
                    desired.get("Instant-sale P/L").setForeground(UiUtils.profitColor(Long.compare(measurement.instantSaleProfit,0)));
                    desired.get("Instant-sale P/L").setToolTipText(valuationTip(measurement));
                }
                String tip=valuationTip(measurement);desired.get("Portfolio value").setToolTipText(tip);
                desired.get("Unrealized profit").setToolTipText(tip);
            }
        }
        desired.forEach((key,value)->value.apply(values.get(key)));
        java.util.List<PerformanceSnapshot.Sale> visible=available?measurement.sales.stream().filter(s->!s.archived).collect(java.util.stream.Collectors.toList()):java.util.List.of();
        int size=visible.size(),totalPages=Math.max(1,(size+9)/10);pageNumber=Math.min(pageNumber,totalPages-1);
        page.setText("Page "+(pageNumber+1)+" of "+totalPages);previous.setEnabled(available && pageNumber>0);next.setEnabled(available && pageNumber+1<totalPages);
        String signature="none";
        // Serialization compares values, so a two-second update does not rebuild unchanged rows or reset focus.
        if(available)signature=new com.google.gson.Gson().toJson(visible.subList(pageNumber*10,Math.min(size,(pageNumber+1)*10)))+measurement.scope+measurement.accountId+pageNumber+":"+totalPages+":"+measurement.archivedUnresolvedSales;
        if(signature.equals(saleSignature))return;saleSignature=signature;sales.removeAll();
        if(size==0){JLabel empty=new JLabel(available?measurement.archivedUnresolvedSales>0?"Archived sales: see Missed flips":"No recorded sales in this window":"Waiting for recorded sales");style(empty);addSale(empty);}
        else for(int i=pageNumber*10;i<Math.min(size,(pageNumber+1)*10);i++) {
            var sale=visible.get(i);JPanel line=row();
            JLabel name=new JLabel(sale.quantity+" × "+sale.itemName);style(name);
            String reasons=sale.accountingReasons==null?"":String.join("; ",sale.accountingReasons);
            JLabel result=new JLabel(sale.profitLower==null?SaleEvidenceText.status(reasons):shortRange(sale.profitLower,sale.profitUpper));style(result);
            if(sale.profitLower!=null)result.setForeground(sale.profitLower>0?UiUtils.profitColor(1):sale.profitUpper<0?UiUtils.profitColor(-1):UiUtils.TEXT);
            line.add(name);line.add(result,BorderLayout.EAST);
            String detail=sale.itemName+" · "+sale.updates+" sale updates · "+(sale.completed?"Completed":"Partial/unconfirmed completion")
                +"\nGross: "+amount(sale.gross)+" · Matched cost: "+amount(sale.matchedCost)
                +"\nTax: "+(sale.taxLower==null?"Unknown":range(sale.taxLower,sale.taxUpper))
                +"\n"+SaleEvidenceText.explain(reasons)+"\nOpen the recorded sale evidence.";
            String tip="<html>"+SaleEvidenceText.html(detail)+"</html>";
            line.setToolTipText(tip);name.setToolTipText(tip);result.setToolTipText(tip);
            line.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            java.awt.event.MouseAdapter click=new java.awt.event.MouseAdapter(){@Override public void mouseClicked(java.awt.event.MouseEvent e){investigateSale.accept(sale.offerIdentity);}};
            line.addMouseListener(click);name.addMouseListener(click);result.addMouseListener(click);addSale(line);
        }
        sales.setMaximumSize(new Dimension(Integer.MAX_VALUE,sales.getPreferredSize().height));revalidate();repaint();
    }
    private String valuationTip(PerformanceSnapshot measurement) {
        StringBuilder tip=new StringBuilder("<html>Unrealized profit: expected after-tax profit at current sell offers, then retained original targets for unlisted units.<br>Portfolio value and instant-sale P/L use last-quoted bids. No guaranteed fill.<br>Cash and exact purchase costs are separate. Estimates never authorize buys.<br>");
        if(measurement.unplannedCost!=null && measurement.unplannedCost>0)
            tip.append("* Expected exits cover only the planned portion; ").append(explainer.formatNumber(measurement.unplannedCost)).append(" GP of cost has an unknown target or unseen units, not a loss.<br>");
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
            if(stock.exitQuotes!=null)for(var exit:stock.exitQuotes)tip.append(exit.source).append(": ").append(exit.quantity)
                .append(" × ").append(explainer.formatNumber(exit.price)).append(" GP; ").append(explainer.formatNumber(exit.netValue)).append(" GP after tax.<br>");
            if(stock.exitContexts!=null)for(var origin:stock.exitContexts) {
                tip.append("Acquisition plan: ").append(origin.getMinimumRemaining());
                if(origin.getMaximumRemaining()!=origin.getMinimumRemaining())tip.append("–").append(origin.getMaximumRemaining());
                tip.append(" possibly remaining units; ");
                var context=origin.getContext();
                if(context==null)tip.append("original target, stop and timing unknown");
                else tip.append("target ").append(explainer.formatNumber(context.getTargetSellPrice())).append(" GP; stop ")
                    .append(context.getStopPrice()>0?explainer.formatNumber(context.getStopPrice())+" GP":"unknown")
                    .append("; original sell estimate ").append(String.format(Locale.ROOT,"%.1f",context.getPredictedSellMinutes())).append("m (unvalidated)");
                tip.append(". Remaining origin ranges do not assign sold units to a lot.<br>");
            }
        }
        return tip.append("</html>").toString();
    }
    private static final class Metric {
        String text="Unavailable",tooltip;Color foreground=UiUtils.TEXT;
        void setText(String value){text=value;}
        void setForeground(Color value){foreground=value;}
        void setToolTipText(String value){tooltip=value;}
        void apply(JLabel label) {
            label.setText(text);label.setForeground(foreground);label.setToolTipText(tooltip);
        }
    }
    private void addSale(JComponent c){c.setAlignmentX(LEFT_ALIGNMENT);c.setMaximumSize(new Dimension(Integer.MAX_VALUE,c.getPreferredSize().height));sales.add(c);}
    private String amount(Long amount){return amount==null?"Unknown":explainer.formatNumber(amount)+" GP";}
    private String shortRange(long a,long b){return (a==b?"":"Est. ")+explainer.formatNumber(RoundedEstimate.midpoint(a,b))+" gp";}
    private String range(long a,long b){return (a==b?"":"Est. ")+explainer.formatNumber(RoundedEstimate.midpoint(a,b))+" gp";}
    private static String percent(long p,long cost){return String.format(Locale.ROOT,"%.3f%%",p*100.0/cost);}
    private String rate(long low,long high,long seconds){return (low==high?"":"Est. ")
        +String.format(Locale.ROOT,"%,d",RoundedEstimate.hourlyMidpoint(low,high,seconds))+" gp/h";}
}
