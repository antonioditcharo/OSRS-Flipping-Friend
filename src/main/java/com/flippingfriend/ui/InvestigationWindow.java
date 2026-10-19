package com.flippingfriend.ui;

import java.awt.*;
import javax.swing.*;

/** Native, resizable investigation windows open maximized on RuneLite's monitor. */
final class InvestigationWindow {
    private InvestigationWindow() { }

    static JFrame create(Window owner, String title, JComponent content) {
        GraphicsConfiguration display=owner==null?null:owner.getGraphicsConfiguration();
        JFrame frame=display==null?new JFrame(title):new JFrame(title,display);
        frame.setUndecorated(false);
        frame.getRootPane().setWindowDecorationStyle(JRootPane.NONE);
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        frame.setContentPane(content);
        if(owner!=null)frame.setIconImages(owner.getIconImages());
        // These are restore bounds; the OS supplies the maximized work area and native controls.
        Rectangle screen=display==null?GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds()
            :display.getBounds();
        frame.setSize(Math.min(1100,Math.max(400,screen.width*4/5)),Math.min(800,Math.max(300,screen.height*4/5)));
        frame.setLocationRelativeTo(owner);
        return frame;
    }

    static void show(JFrame frame) {
        frame.setExtendedState(Frame.MAXIMIZED_BOTH);
        frame.setVisible(true);
    }
}
