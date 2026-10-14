package com.flippingfriend.overlay;

import com.flippingfriend.FlippingFriendConfig;
import com.flippingfriend.model.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import javax.imageio.ImageIO;
import net.runelite.api.*;
import net.runelite.api.gameval.*;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Synthetic editor rendering exercises the actual overlays; this is not a live game screenshot. */
public class TradeUiRenderingTest {
    @Test public void mismatchesShowBothHighlightsAndLockThenMatchingValuesShowOnlyConfirm() throws Exception {
        Client client=mock(Client.class); GeWidgetResolver resolver=mock(GeWidgetResolver.class);
        FlippingFriendConfig config=new FlippingFriendConfig() {
            public void setShowOnboarding(boolean value) { }
            public boolean pulseHighlight() { return false; }
        };
        when(client.isClientThread()).thenReturn(true); when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getCanvasWidth()).thenReturn(1100); when(client.getCanvasHeight()).thenReturn(600);
        when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
        when(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH)).thenReturn(561);
        when(client.getVarbitValue(VarbitID.GE_SELECTEDSLOT)).thenReturn(1);
        when(resolver.isSetupOpen()).thenReturn(true); when(resolver.isGeOpen()).thenReturn(true);
        Widget panel=widget(new Rectangle(180,140,480,300));
        Widget quantity=widget(new Rectangle(355,240,35,24));
        Widget price=widget(new Rectangle(590,300,35,24));
        Widget confirm=widget(new Rectangle(360,385,150,38));
        when(resolver.getSetupPanel()).thenReturn(panel); when(resolver.getQuantityControl()).thenReturn(quantity);
        when(resolver.getPriceControl()).thenReturn(price); when(resolver.getConfirmButton()).thenReturn(confirm);
        StepGuide guide=new StepGuide(client,resolver);
        guide.setSuggestion(Suggestion.builder(SuggestionType.BUY).item(561,"Nature rune").quantity(10).price(100).build());
        when(client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY)).thenReturn(9);
        when(client.getVarpLongValue(5753)).thenReturn(99L);
        assertEquals(java.util.List.of(quantity,price),guide.resolve(8,new boolean[8]).getTargets());
        BufferedImage mismatch=render(client,resolver,guide,config);
        assertNotEquals(new Color(100,90,70).getRGB(),mismatch.getRGB(380,393));
        FlippingFriendConfig cardOnly=new FlippingFriendConfig() {
            public void setShowOnboarding(boolean value) { }
            public boolean showOfferEditorHints() { return false; }
            public boolean pulseHighlight() { return false; }
        };
        BufferedImage withoutDuplicateHints=render(client,resolver,guide,cardOnly);
        assertArrayEquals("The visible instruction card already carries the numbers; editor hints must not overprint it",
            withoutDuplicateHints.getRGB(0,0,1100,600,null,0,1100), mismatch.getRGB(0,0,1100,600,null,0,1100));
        when(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH)).thenReturn(4151);
        BufferedImage personalItem=render(client,resolver,guide,config);
        assertEquals("A personal item must still explain the right-click override on the locked button",
            mismatch.getRGB(380,393), personalItem.getRGB(380,393));
        when(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH)).thenReturn(561);
        Path output=Path.of("build","ui"); Files.createDirectories(output);
        ImageIO.write(mismatch,"png",output.resolve("trade-ui-mismatch.png").toFile());
        when(client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY)).thenReturn(10);
        assertEquals(java.util.List.of(price),guide.resolve(8,new boolean[8]).getTargets());
        ImageIO.write(render(client,resolver,guide,config),"png",output.resolve("trade-ui-price-only.png").toFile());
        when(client.getVarpLongValue(5753)).thenReturn(100L);
        assertEquals(java.util.List.of(confirm),guide.resolve(8,new boolean[8]).getTargets());
        ImageIO.write(render(client,resolver,guide,config),"png",output.resolve("trade-ui-confirm-ready.png").toFile());
    }
    private Widget widget(Rectangle bounds) { Widget w=mock(Widget.class); when(w.getBounds()).thenReturn(bounds); return w; }
    private BufferedImage render(Client client,GeWidgetResolver resolver,StepGuide guide,FlippingFriendConfig config) {
        BufferedImage image=new BufferedImage(1100,600,BufferedImage.TYPE_INT_RGB); Graphics2D g=image.createGraphics();
        g.setColor(new Color(32,35,40)); g.fillRect(0,0,1100,600);
        g.setColor(new Color(70,64,52)); g.fillRect(180,140,480,300);
        g.setColor(new Color(100,90,70)); g.fillRect(360,385,150,38);
        g.setColor(Color.WHITE); g.drawString("Synthetic GE editor - recommended 10 Nature runes at 100 gp",200,170);
        g.drawString("Quantity",305,232); g.drawString("Price",545,290); g.drawString("Confirm",410,405);
        g.drawRect(355,240,35,24); g.drawRect(590,300,35,24);
        Explainer explainer=new Explainer();
        new GrandExchangeOverlay(client,resolver,guide,config,explainer).render(g);
        new OfferEditorOverlay(client,guide,resolver,config,explainer).render(g);
        g.dispose(); return image;
    }
}
