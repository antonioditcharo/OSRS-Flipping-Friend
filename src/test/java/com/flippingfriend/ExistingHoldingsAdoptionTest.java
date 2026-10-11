package com.flippingfriend;

import com.flippingfriend.data.ItemMetadata;
import com.flippingfriend.data.MarketDataService;
import com.flippingfriend.data.MarketSnapshot;
import com.flippingfriend.session.AccountState;
import com.flippingfriend.session.OfferTracker;
import com.flippingfriend.session.PositionBook;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/** Physical ownership must not become permission to sell personal gear and supplies. */
public class ExistingHoldingsAdoptionTest
{
    private static final int STAFF = 21006;
    private static final int RUNE = 561;
    private final FlippingFriendPlugin plugin = new FlippingFriendPlugin();
    private final PositionBook positions = Mockito.mock(PositionBook.class);
    private final FlippingFriendConfig config = Mockito.mock(FlippingFriendConfig.class);
    private final Map<Integer, Integer> inventory = Map.of(STAFF, 1);
    private final Map<Integer, Integer> bank = Map.of(RUNE, 50);
    private final Map<Integer, Integer> holdings = Map.of(STAFF, 1, RUNE, 50);

    @Before
    public void setUp() throws Exception
    {
        MarketDataService marketData = Mockito.mock(MarketDataService.class);
        Mockito.when(marketData.getSnapshot()).thenReturn(new MarketSnapshot(
            Map.of(STAFF, new ItemMetadata(STAFF, "Mystic smoke staff", true, 8, 1),
                RUNE, new ItemMetadata(RUNE, "Nature rune", false, 25000, 1)),
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Instant.EPOCH));
        OfferTracker offerTracker = Mockito.mock(OfferTracker.class);
        Mockito.when(offerTracker.listedForSale()).thenReturn(Collections.emptyMap());
        inject("positions", positions);
        inject("config", config);
        inject("marketData", marketData);
        inject("offerTracker", offerTracker);
    }

    @Test
    public void personalInventoryAndBankStockRequireOptIn() throws Exception
    {
        sync();

        Mockito.verify(positions, Mockito.never()).adoptExisting(Mockito.anyInt(),
            Mockito.anyString(), Mockito.anyInt(), Mockito.any(Instant.class));
        Mockito.verify(positions).reconcile(holdings, Collections.emptyMap(), true);
    }

    @Test
    public void optingInAdoptsEligibleInventoryAndBankStock() throws Exception
    {
        Mockito.when(config.adoptExistingItems()).thenReturn(true);
        sync();

        Mockito.verify(positions).adoptExisting(Mockito.eq(STAFF), Mockito.eq("Mystic smoke staff"),
            Mockito.eq(1), Mockito.any(Instant.class));
        Mockito.verify(positions).adoptExisting(Mockito.eq(RUNE), Mockito.eq("Nature rune"),
            Mockito.eq(50), Mockito.any(Instant.class));
    }

    @Test
    public void optingInStillProtectsBlockedStockWithoutHidingIt() throws Exception
    {
        Mockito.when(config.adoptExistingItems()).thenReturn(true);
        Mockito.when(config.blockedItems()).thenReturn("  MYSTIC SMOKE STAFF  ");
        sync();

        Mockito.verify(positions, Mockito.never()).adoptExisting(Mockito.eq(STAFF),
            Mockito.anyString(), Mockito.anyInt(), Mockito.any(Instant.class));
        Mockito.verify(positions).adoptExisting(Mockito.eq(RUNE), Mockito.eq("Nature rune"),
            Mockito.eq(50), Mockito.any(Instant.class));
        Mockito.verify(positions).reconcile(holdings, Collections.emptyMap(), true);
    }

    private void sync() throws Exception
    {
        AccountState state = new AccountState(true, true, false, 0, 0, 0, 8, 8, 0,
            true, holdings, inventory, bank);
        Method method = FlippingFriendPlugin.class.getDeclaredMethod("syncHoldings", AccountState.class);
        method.setAccessible(true);
        method.invoke(plugin, state);
    }

    private void inject(String name, Object value) throws Exception
    {
        Field field = FlippingFriendPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }
}
