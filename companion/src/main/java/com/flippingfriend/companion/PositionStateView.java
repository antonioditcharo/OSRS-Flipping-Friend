package com.flippingfriend.companion;

import com.flippingfriend.core.AccountSnapshot;
import com.flippingfriend.core.PositionSnapshot;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only validated view of the durable positions in the latest account snapshot. */
final class PositionStateView
{
        private final Map<Integer, PositionSnapshot> byItem;
        private final long totalQuantity;
        private final long knownCostCapital;
        private final int unknownCostPositions;

        private PositionStateView(Map<Integer, PositionSnapshot> positions)
        {
                this.byItem = Collections.unmodifiableMap(new LinkedHashMap<>(positions));

                long quantity = 0;
                long knownCapital = 0;
                int unknownCosts = 0;
                for (PositionSnapshot position : positions.values())
                {
                        quantity += position.getQuantity();
                        if (position.isCostKnown())
                        {
                                knownCapital += position.getTotalCost();
                        }
                        else
                        {
                                unknownCosts++;
                        }
                }

                this.totalQuantity = quantity;
                this.knownCostCapital = knownCapital;
                this.unknownCostPositions = unknownCosts;
        }

        static PositionStateView from(AccountSnapshot account)
        {
                Map<Integer, PositionSnapshot> positions = new LinkedHashMap<>();
                if (account != null)
                {
                        for (PositionSnapshot position : account.getPositions())
                        {
                                if (position == null || position.getItemId() <= 0 || position.getQuantity() <= 0)
                                {
                                        continue;
                                }

                                // One position per item. If malformed input contains duplicates, the latest wins.
                                positions.put(position.getItemId(), position);
                        }
                }
                return new PositionStateView(positions);
        }

        Map<Integer, PositionSnapshot> byItem()
        {
                return byItem;
        }
        PositionSnapshot get(int itemId)
        {
                return byItem.get(itemId);
        }

        Collection<PositionSnapshot> all()
        {
                return byItem.values();
        }

        int size()
        {
                return byItem.size();
        }

        long totalQuantity()
        {
                return totalQuantity;
        }

        long knownCostCapital()
        {
                return knownCostCapital;
        }

        int unknownCostPositions()
        {
                return unknownCostPositions;
        }
}
