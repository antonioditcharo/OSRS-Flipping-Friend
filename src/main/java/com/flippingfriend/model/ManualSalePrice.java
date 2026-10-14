package com.flippingfriend.model;

import com.flippingfriend.data.MarketSnapshot;
import java.time.Instant;

/** Contextual guidance for a sale chosen by the player, with no acquisition or profit claim. */
public final class ManualSalePrice {
    private ManualSalePrice() { }
    public static Suggestion quote(int item, int quantity, int inInventory, MarketSnapshot market, Instant now) {
        if (item <= 0 || quantity <= 0 || quantity > inInventory || market == null || !market.isUsable()) return null;
        var price = market.latest(item);
        if (price == null || !price.isComplete() || price.getHighTime() == null
                || price.secondsSinceHigh(now) < 0 || price.secondsSinceHigh(now) > 300) return null;
        int sell = Math.max(1, price.getHigh() - 1);
        return Suggestion.builder(SuggestionType.SELL).item(item, market.getItemName(item))
            .quantity(quantity).price(sell).profitKnown(false)
            .headline("Sell price for " + market.getItemName(item))
            .detail("Current price guidance for the sale you selected. Profit for this selected sale is unavailable. "
                + "Once you place the offer, its progress and collection will be tracked.")
            .build();
    }
}
