package com.flippingfriend.ui;

import com.flippingfriend.model.Suggestion;
import java.awt.*;
import javax.swing.*;

/** One pending, exact presentation. A buffered/test paint never counts as native rendering. */
final class GuidancePaint {
    private GuidanceTiming timing;
    private Suggestion expected;
    private long requested;
    void request(GuidanceTiming next,Suggestion action) {
        finish("SUPERSEDED_BEFORE_PAINT"); timing=next;expected=action;requested=System.nanoTime();
    }
    void validate(JComponent card,Suggestion shown) {
        if(timing==null)return;
        if(shown!=expected){finish("SUPERSEDED_BEFORE_PAINT");return;}
        String unavailable=visibility(card);
        if(unavailable!=null)finish(unavailable);
        else if(System.nanoTime()-requested>TimeUnitSeconds.FIVE)finish("PAINT_TIMEOUT");
    }
    void completed(JComponent card,Suggestion shown,Graphics graphics) {
        if(timing==null)return;
        validate(card,shown);if(timing==null)return;
        if(card.isPaintingForPrint()){finish("PRINT_PAINT");return;}
        Rectangle bounds=new Rectangle(0,0,card.getWidth(),card.getHeight());
        if(card.getWidth()<=0 || card.getHeight()<=0 || !card.getVisibleRect().contains(bounds)
            || graphics.getClipBounds()==null || !graphics.getClipBounds().contains(bounds)) {finish("CLIPPED");return;}
        finish("PAINTED");
    }
    static String visibility(JComponent card) {
        Window window=SwingUtilities.getWindowAncestor(card);
        if(window instanceof Frame && ((((Frame)window).getExtendedState()&Frame.ICONIFIED)!=0))return "MINIMIZED";
        if(!card.isShowing() || window==null || !window.isShowing())return "HIDDEN";
        return null;
    }
    void finish(String outcome) {if(timing!=null)timing.paint(outcome);timing=null;expected=null;}
    boolean pending(){return timing!=null;}
    private static final class TimeUnitSeconds {static final long FIVE=5_000_000_000L;}
}
