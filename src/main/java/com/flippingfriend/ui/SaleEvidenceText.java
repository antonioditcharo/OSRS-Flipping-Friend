package com.flippingfriend.ui;

/** Player-facing explanations for recorded evidence, shared by the sale list and details. */
final class SaleEvidenceText {
    static String status(String reason) {
        if(reason.contains("COST_BASIS_UNRESOLVED"))return "Cost unmatched";
        if(reason.contains("PROCEEDS_UNRESOLVED"))return "Proceeds unclear";
        if(reason.contains("PLACEMENT_TAX_RULE_UNPROVEN"))return "Tax rule unproven";
        return "Unknown";
    }
    static String explain(String reason) {
        if(reason.contains("disposal has no durable position"))
            return "The sale was recorded, but its acquisition cost was not matched by the accounting path used at acceptance. This does not mean the purchase record was deleted. Profit is excluded from totals.";
        if(reason.contains("COST_BASIS_UNRESOLVED"))
            return "The sale was recorded without complete acquisition-cost coverage. Matched cost can cover only part of the sale; total profit remains unknown.";
        if(reason.contains("PROCEEDS_UNRESOLVED"))return "The recorded proceeds do not establish a complete sale amount. Profit remains unknown.";
        if(reason.contains("PLACEMENT_TAX_RULE_UNPROVEN"))return "The original placement does not establish the applicable tax rule. The recorded tax and profit bounds retain that uncertainty.";
        if(reason.contains("AGGREGATE_FILL_TAX_RANGE"))return "Combined fills establish a tax and profit range, rather than one exact figure.";
        if(reason.contains("EXACT_UNDER_RECORDED_RULE") || reason.contains("EXEMPT_UNDER_RECORDED_RULE"))
            return "Proceeds, acquisition cost and tax are matched under the recorded tax rule.";
        return reason.isBlank()?"Accounting evidence is incomplete.":reason;
    }
    static String html(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\n","<br>");}
}
