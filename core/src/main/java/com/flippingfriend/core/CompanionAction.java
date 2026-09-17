package com.flippingfriend.core;

/** Immutable single action returned by the companion decision boundary. */
public final class CompanionAction
{
        private final CompanionActionType type;
        private final int itemId;
        private final String itemName;
        private final int slot;
        private final int quantity;
        private final String headline;
        private final String detail;

        private CompanionAction(CompanionActionType type, int itemId, String itemName,
                int slot, int quantity, String headline, String detail)
        {
                this.type = type == null ? CompanionActionType.WAIT : type;
                this.itemId = itemId;
                this.itemName = itemName;
                this.slot = slot;
                this.quantity = Math.max(0, quantity);
                this.headline = headline;
                this.detail = detail;
        }

        public static CompanionAction waiting(String headline, String detail)
        {
                return new CompanionAction(
                        CompanionActionType.WAIT, -1, null, -1, 0, headline, detail);
        }

        public static CompanionAction collect(int itemId, String itemName, int slot,
                int quantity, String headline, String detail)
        {
                return new CompanionAction(
                        CompanionActionType.COLLECT, itemId, itemName, slot,
                        quantity, headline, detail);
        }

        public CompanionActionType getType()
        {
                return type == null ? CompanionActionType.WAIT : type;
        }

        public int getItemId()
        {
                return itemId;
        }

        public String getItemName()
        {
                return itemName == null && itemId > 0 ? "Item " + itemId : itemName;
        }

        public int getSlot()
        {
                return slot;
        }

        public int getQuantity()
        {
                return quantity;
        }

        public String getHeadline()
        {
                return headline == null ? "" : headline;
        }

        public String getDetail()
        {
                return detail == null ? "" : detail;
        }

        public boolean isActionable()
        {
                return getType() != CompanionActionType.WAIT;
        }
}
