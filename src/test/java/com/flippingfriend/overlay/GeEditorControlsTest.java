package com.flippingfriend.overlay;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
public class GeEditorControlsTest {
    @Test public void confirmIsResolvedFromItsActionInTheDynamicEditor() {
        Client client=mock(Client.class); Widget panel=mock(Widget.class), confirm=mock(Widget.class);
        when(client.getWidget(InterfaceID.GeOffers.SETUP)).thenReturn(panel);
        when(panel.getDynamicChildren()).thenReturn(new Widget[]{confirm});
        when(confirm.getActions()).thenReturn(new String[]{"Confirm"});
        assertSame(confirm,new GeWidgetResolver(client,mock(ItemManager.class)).getConfirmButton());
    }
    @Test public void quantityCaptionCannotBeMistakenForTheClickableControl() {
        Client client=mock(Client.class); Widget panel=mock(Widget.class), label=mock(Widget.class), control=mock(Widget.class);
        when(client.getWidget(InterfaceID.GeOffers.SETUP)).thenReturn(panel);
        when(panel.getDynamicChildren()).thenReturn(new Widget[]{label,control});
        when(label.getText()).thenReturn("Enter quantity");
        when(control.getActions()).thenReturn(new String[]{"Enter quantity"});
        assertSame(control,new GeWidgetResolver(client,mock(ItemManager.class)).getQuantityControl());
    }
    @Test public void hiddenSubtreeCannotSupplyAVisibleHighlight() {
        Client client=mock(Client.class); Widget panel=mock(Widget.class), hidden=mock(Widget.class), control=mock(Widget.class);
        when(client.getWidget(InterfaceID.GeOffers.SETUP)).thenReturn(panel);
        when(panel.getDynamicChildren()).thenReturn(new Widget[]{hidden}); when(hidden.isHidden()).thenReturn(true);
        when(hidden.getDynamicChildren()).thenReturn(new Widget[]{control});
        when(control.getActions()).thenReturn(new String[]{"Enter price"});
        assertNull(new GeWidgetResolver(client,mock(ItemManager.class)).getPriceControl());
    }
}
