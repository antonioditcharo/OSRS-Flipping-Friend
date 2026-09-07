package com.flippingfriend.model;


import java.util.UUID;

public class FlipV2 {

    public enum FlipStatus {
        BUYING,
        FINISHED,
        UNKNOWN;
        
        public static FlipStatus fromValue(String val) {
            try {
                return valueOf(val.toUpperCase());
            } catch (Exception e) {
                return UNKNOWN;
            }
        }
    }

    private UUID id;
    private int accountId;
    private int itemId;
    private int openedTime;
    private int openedQuantity;
    private long spent;
    private int closedTime;
    private int closedQuantity;
    private long receivedPostTax;
    private long profit;
    private long taxPaid;
    private FlipStatus status;
    private int updatedTime;
    private boolean deleted;
    private int portfolioId;
    private long seqNo;
    private int userId;

    private String cachedItemName;

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public int getAccountId() { return accountId; }
    public void setAccountId(int accountId) { this.accountId = accountId; }
    public int getItemId() { return itemId; }
    public void setItemId(int itemId) { this.itemId = itemId; }
    public int getOpenedTime() { return openedTime; }
    public void setOpenedTime(int openedTime) { this.openedTime = openedTime; }
    public int getOpenedQuantity() { return openedQuantity; }
    public void setOpenedQuantity(int openedQuantity) { this.openedQuantity = openedQuantity; }
    public long getSpent() { return spent; }
    public void setSpent(long spent) { this.spent = spent; }
    public int getClosedTime() { return closedTime; }
    public void setClosedTime(int closedTime) { this.closedTime = closedTime; }
    public int getClosedQuantity() { return closedQuantity; }
    public void setClosedQuantity(int closedQuantity) { this.closedQuantity = closedQuantity; }
    public long getReceivedPostTax() { return receivedPostTax; }
    public void setReceivedPostTax(long receivedPostTax) { this.receivedPostTax = receivedPostTax; }
    public long getProfit() { return profit; }
    public void setProfit(long profit) { this.profit = profit; }
    public long getTaxPaid() { return taxPaid; }
    public void setTaxPaid(long taxPaid) { this.taxPaid = taxPaid; }
    public FlipStatus getStatus() { return status; }
    public void setStatus(FlipStatus status) { this.status = status; }
    public int getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(int updatedTime) { this.updatedTime = updatedTime; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
    public int getPortfolioId() { return portfolioId; }
    public void setPortfolioId(int portfolioId) { this.portfolioId = portfolioId; }
    public long getSeqNo() { return seqNo; }
    public void setSeqNo(long seqNo) { this.seqNo = seqNo; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getCachedItemName() { return cachedItemName; }

    public FlipV2 setCachedItemName(String cachedItemName) {
        this.cachedItemName = cachedItemName;
        return this;
    }

    public long getAvgBuyPrice() {
        if (spent == 0 || openedQuantity == 0) {
            return 0;
        }
        return spent / openedQuantity;
    }

    public long getAvgSellPrice() {
        if (receivedPostTax == 0 || closedQuantity == 0) {
            return 0;
        }
        return (receivedPostTax + taxPaid) / closedQuantity;
    }

    public boolean isClosed() {
        return status == FlipStatus.FINISHED;
    }

    public int lastTransactionTime() {
        return closedTime == 0 ? openedTime : closedTime;
    }

    public boolean isNewer(FlipV2 o) {
        if (updatedTime == o.updatedTime) {
            return closedQuantity > o.closedQuantity || (closedQuantity == o.closedQuantity && openedQuantity >= o.openedQuantity);
        }
        return updatedTime > o.updatedTime;
    }
}
