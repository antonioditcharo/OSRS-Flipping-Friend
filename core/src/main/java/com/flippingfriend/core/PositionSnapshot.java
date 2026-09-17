package com.flippingfriend.core;

/** Immutable durable position state copied from the plugin to the companion. */
public final class PositionSnapshot
{
        private final int itemId;
        private final String itemName;
        private final int quantity;
        private final long totalCost;
        private final boolean costKnown;
        private final long openedAt;
        private final int targetSellPrice;
        private final int stopPrice;
        private final double predictedSellMinutes;

        public PositionSnapshot(int itemId, String itemName, int quantity, long totalCost,
                boolean costKnown, long openedAt, int targetSellPrice, int stopPrice,
                double predictedSellMinutes)
        {
                this.itemId = itemId;
                this.itemName = itemName;
                this.quantity = Math.max(0, quantity);
                this.totalCost = Math.max(0, totalCost);
                this.costKnown = costKnown;
                this.openedAt = Math.max(0, openedAt);
                this.targetSellPrice = Math.max(0, targetSellPrice);
                this.stopPrice = Math.max(0, stopPrice);
                this.predictedSellMinutes = Math.max(0, predictedSellMinutes);
        }

        public int getItemId() { return itemId; }
        public String getItemName() { return itemName == null ? "Item " + itemId : itemName; }
        public int getQuantity() { return quantity; }
        public long getTotalCost() { return totalCost; }
        public boolean isCostKnown() { return costKnown && quantity > 0 && totalCost > 0; }
        public int getAverageCost() { return isCostKnown() ? (int) (totalCost / quantity) : 0; }
        public long getOpenedAt() { return openedAt; }
        public int getTargetSellPrice() { return targetSellPrice; }
        public int getStopPrice() { return stopPrice; }
        public double getPredictedSellMinutes() { return predictedSellMinutes; }
}
