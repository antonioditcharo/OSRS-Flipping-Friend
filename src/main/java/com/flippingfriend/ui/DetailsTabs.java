package com.flippingfriend.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import net.runelite.client.ui.FontManager;

/** The modeless records window uses the same dark palette as its product content. */
final class DetailsTabs extends JTabbedPane {
    DetailsTabs() {
        setBackground(UiUtils.CARD);setForeground(UiUtils.TEXT);setFont(FontManager.getRunescapeSmallFont());
        setTabLayoutPolicy(SCROLL_TAB_LAYOUT);
        setUI(new BasicTabbedPaneUI() {
            @Override protected void installDefaults(){super.installDefaults();contentBorderInsets=new Insets(0,0,0,0);tabAreaInsets=new Insets(0,0,0,0);tabInsets=new Insets(7,10,7,10);}
            @Override protected int calculateTabHeight(int placement,int index,int fontHeight){return Math.max(30,super.calculateTabHeight(placement,index,fontHeight));}
            @Override protected void paintTabBackground(Graphics g,int placement,int index,int x,int y,int width,int height,boolean selected){g.setColor(UiUtils.CARD);g.fillRect(x,y,width,height);}
            @Override protected void paintTabBorder(Graphics g,int placement,int index,int x,int y,int width,int height,boolean selected){
                g.setColor(selected?new Color(235,171,20):UiUtils.DIVIDER);g.fillRect(x,y+height-2,width,selected?2:1);
            }
            @Override protected void paintContentBorder(Graphics g,int placement,int selected){ }
        });
    }
}
