package com.flippingfriend.ui;

import com.flippingfriend.data.*;
import com.google.gson.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import net.runelite.client.ui.FontManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import static org.mockito.Mockito.*;

/** Explicit offscreen visual QA of the real Swing view, with a frozen public-only capture. */
public final class MarketGraphRender {
    public static void main(String[] args)throws Exception {
        Path source=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(out);Gson json=new Gson();
        SwingUtilities.invokeAndWait(()->{if(!net.runelite.client.ui.laf.RuneLiteLAF.setup())throw new IllegalStateException("RuneLite LAF unavailable");});
        ItemMetadata item=json.fromJson(Files.readString(source.resolve("item.json")),ItemMetadata.class);
        long now=json.fromJson(Files.readString(source.resolve("captured-at.json")),JsonObject.class).get("observedAt").getAsLong();
        Map<String,List<Candle>> histories=new HashMap<>();for(String step:new String[]{"5m","1h","6h"})
            histories.put(step,Arrays.asList(json.fromJson(json.fromJson(Files.readString(source.resolve("history-"+step+".json")),JsonObject.class).get("data"),Candle[].class)));
        LatestPrice quote=json.fromJson(json.fromJson(Files.readString(source.resolve("latest.json")),JsonObject.class).getAsJsonObject("data").get(""+item.getId()),LatestPrice.class);
        MarketDataService market=mock(MarketDataService.class);
        when(market.getSnapshot()).thenReturn(new MarketSnapshot(Map.of(item.getId(),item),Map.of(item.getId(),quote),Map.of(),Map.of(),Instant.ofEpochSecond(now)));
        when(market.getGraphSeries(anyInt(),anyString())).thenAnswer(call->histories.getOrDefault(call.getArgument(1),List.of()));
        ItemManager items=mock(ItemManager.class);
        if(Files.isRegularFile(source.resolve("item-icon.png"))) {
            var sprite=mock(AsyncBufferedImage.class);var image=ImageIO.read(source.resolve("item-icon.png").toFile());
            doAnswer(call->{((JLabel)call.getArgument(0)).setIcon(new ImageIcon(image));return null;}).when(sprite).addTo(any(JLabel.class));
            when(items.getImage(item.getId())).thenReturn(sprite);
        }
        for(int[] size:new int[][]{{1905,1000},{1000,680},{730,540}})SwingUtilities.invokeAndWait(()->{
            try {
                var graph=new MarketGraphView(market,()->now);graph.setItemManager(items);graph.showItem(item.getId());
                var tabs=new DetailsTabs();
                tabs.addTab("Recommendation",new JPanel());tabs.addTab("Price graph",graph);tabs.addTab("Plan",new JPanel());tabs.addTab("Holdings",new JPanel());tabs.addTab("Performance",new JPanel());tabs.setSelectedIndex(1);
                tabs.setSize(size[0],size[1]);layout(tabs);
                BufferedImage image=new BufferedImage(size[0],size[1],BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);tabs.printAll(g);g.dispose();
                ImageIO.write(image,"png",out.resolve("graph-"+size[0]+".png").toFile());graph.dispose();
            }catch(Exception failure){throw new RuntimeException(failure);}
        });
        System.out.println("Actual Swing charts rendered at 1905, 1000 and 730 pixels; public history only.");
    }
    private static void layout(Container parent){parent.doLayout();for(Component c:parent.getComponents())if(c instanceof Container)layout((Container)c);}
}
