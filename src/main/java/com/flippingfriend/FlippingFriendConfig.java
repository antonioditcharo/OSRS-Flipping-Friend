package com.flippingfriend;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Notification;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;
import net.runelite.client.config.Keybind;

@ConfigGroup(FlippingFriendConfig.GROUP)
public interface FlippingFriendConfig extends Config
{
	String GROUP = "flippingfriend";

	@ConfigSection(
		name = "Trading",
		description = "How adventurous the suggestions should be.",
		position = 0
	)
	String tradingSection = "trading";

	@ConfigSection(
		name = "Guidance",
		description = "The highlights and instructions drawn on the Grand Exchange.",
		position = 1
	)
	String guidanceSection = "guidance";

	@ConfigSection(
		name = "Notifications",
		description = "When to be told something happened.",
		position = 2
	)
	String notificationSection = "notifications";

	@ConfigSection(
		name = "Advanced",
		description = "You should not need to touch anything in here.",
		position = 3,
		closedByDefault = true
	)
	String advancedSection = "advanced";

	// ------------------------------------------------------------------ trading

	@ConfigItem(
		keyName = "riskProfile",
		name = "Risk level",
		description = "Low sticks to safe, busy items. Moderate is a good all-round default. "
			+ "High chases bigger margins and will lose some trades.",
		position = 0,
		section = tradingSection
	)
	default RiskProfile riskProfile()
	{
		return RiskProfile.MODERATE;
	}

	@ConfigItem(
		keyName = "accountMode",
		name = "Account type",
		description = "Free-to-play accounts can only buy free-to-play items and get 3 Grand Exchange "
			+ "slots instead of 8. Selling is never restricted, so members' items you already own "
			+ "will still be suggested for sale either way.",
		position = 1,
		section = tradingSection
	)
	default AccountMode accountMode()
	{
		return AccountMode.AUTOMATIC;
	}

	@ConfigItem(
		keyName = "checkInterval",
		name = "How often you check the GE",
		description = "How often you realistically come back to the Grand Exchange. This changes "
			+ "what gets suggested: check rarely and you get fewer, larger, wider-margin offers "
			+ "that should be finished when you return, instead of quick flips that sit completed "
			+ "while you are away.",
		position = 2,
		section = tradingSection
	)
	default CheckInterval checkInterval()
	{
		return CheckInterval.FIFTEEN_MINUTES;
	}

	@ConfigItem(
		keyName = "bankrollCap",
		name = "Spending cap",
		description = "Largest amount of your coins the plugin is allowed to plan around. "
			+ "Leave at 0 to use everything it can see.",
		position = 2,
		section = tradingSection
	)
	default long bankrollCap()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "targetHoldMinutes",
		name = "How long a flip should take",
		description = "Roughly how long you want a trade to take, start to finish, in minutes. "
			+ "Bigger numbers mean patience: chunkier orders, and holding out longer for a better "
			+ "price. Set to 0 to let the risk level decide. This is separate from how often you "
			+ "check — you can want slow flips while standing at the Exchange.",
		position = 2,
		section = tradingSection
	)
	@Range(min = 0, max = 1440)
	default int targetHoldMinutes()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "minProfitPerFlip",
		name = "Minimum profit per flip",
		description = "Ignore suggestions worth less than this in total profit. Stops the panel "
			+ "filling up with trades that are not worth the clicks.",
		position = 3,
		section = tradingSection
	)
	default int minProfitPerFlip()
	{
		return 50000;
	}

	@ConfigItem(
		keyName = "benchmarkProfitPerFlip",
		name = "Benchmark profit per flip",
		description = "A benchmark to shoot for. Trades below this profit will be penalized in scoring, "
			+ "encouraging the engine to find trades closer to this target.",
		position = 4,
		section = tradingSection
	)
	default int benchmarkProfitPerFlip()
	{
		return 100000;
	}

	@ConfigItem(
		keyName = "includeBankValue",
		name = "Count bank coins",
		description = "Include coins seen in your bank when working out what you can afford. "
			+ "Only counts after you have opened your bank at least once this session.",
		position = 5,
		section = tradingSection
	)
	default boolean includeBankValue()
	{
		return true;
	}

	@ConfigItem(
		keyName = "adoptExistingItems",
		name = "Also sell items I already own",
		description = "Off by default. When on, tradeable items already in your bank and inventory "
			+ "are treated as things to sell. Most people do not want this: your bank is your gear "
			+ "and supplies, not flipping stock. Items the plugin buys for you are always tracked "
			+ "either way.",
		position = 5,
		section = tradingSection
	)
	default boolean adoptExistingItems()
	{
		return false;
	}

	@ConfigItem(
		keyName = "blockedItems",
		name = "Never trade these",
		description = "Comma separated item names to exclude entirely.",
		position = 6,
		section = tradingSection
	)
	default String blockedItems()
	{
		return "";
	}

	// ----------------------------------------------------------------- guidance

	@ConfigItem(
		keyName = "showOverlay",
		name = "Highlight the next step",
		description = "Draw a highlight around whatever you need to click next in the Grand Exchange.",
		position = 0,
		section = guidanceSection
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showInstructionCard",
		name = "Show instruction card",
		description = "Show a floating card telling you exactly what to type or click.",
		position = 1,
		section = guidanceSection
	)
	default boolean showInstructionCard()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOfferEditorHints",
		name = "Show numbers on the offer editor",
		description = "Print the exact price and quantity above the game's own quantity and price buttons.",
		position = 2,
		section = guidanceSection
	)
	default boolean showOfferEditorHints()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightColor",
		name = "Highlight colour",
		description = "Colour of the guidance highlight.",
		position = 3,
		section = guidanceSection
	)
	default java.awt.Color highlightColor()
	{
		return new java.awt.Color(0, 220, 130);
	}

	@ConfigItem(
		keyName = "pulseHighlight",
		name = "Pulse the highlight",
		description = "Animate the highlight so it is easy to spot. Turn off if you find it distracting.",
		position = 4,
		section = guidanceSection
	)
	default boolean pulseHighlight()
	{
		return true;
	}

	@ConfigItem(
		keyName = "autoPopulateHotkey",
		name = "Auto-populate Hotkey",
		description = "Press this hotkey while in the GE to automatically fill the current suggestion's price and quantity.",
		position = 5,
		section = guidanceSection
	)
	default Keybind autoPopulateHotkey()
	{
		return Keybind.NOT_SET;
	}

	// ------------------------------------------------------------ notifications

	@ConfigItem(
		keyName = "notifyOnFill",
		name = "Offer completed",
		description = "Notify when one of your offers finishes.",
		position = 0,
		section = notificationSection
	)
	default Notification notifyOnFill()
	{
		return Notification.OFF;
	}

	@ConfigItem(
		keyName = "notifyOnSellSignal",
		name = "Time to sell",
		description = "Notify when an item you are holding reaches its sell target.",
		position = 1,
		section = notificationSection
	)
	default Notification notifyOnSellSignal()
	{
		return Notification.OFF;
	}

	@ConfigItem(
		keyName = "notifyOnStaleOffer",
		name = "Offer is not filling",
		description = "Notify when an offer has sat unfilled for too long and should be adjusted.",
		position = 2,
		section = notificationSection
	)
	default Notification notifyOnStaleOffer()
	{
		return Notification.OFF;
	}

	@ConfigItem(
		keyName = "discordWebhookUrl",
		name = "Discord Webhook (Price Dumps)",
		description = "Enter a Discord Webhook URL to receive remote alerts for sudden market crashes of held items.",
		position = 3,
		section = notificationSection
	)
	default String discordWebhookUrl()
	{
		return "";
	}

	// --------------------------------------------------------------- advanced

	@Range(min = 5, max = 300)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "refreshSeconds",
		name = "Refresh interval",
		description = "How often to re-price the market and revisit the current suggestion.",
		position = 0,
		section = advancedSection
	)
	default int refreshSeconds()
	{
		return 30;
	}

	@ConfigItem(
		keyName = "shadowTrading",
		name = "Keep learning in the background",
		description = "Continuously tracks trades it did not place — including ones it decided "
			+ "against — against the real market, to see how they would have turned out. No coins "
			+ "move and nothing is placed in game. It is how the plugin gets smarter between your "
			+ "actual flips, and how it finds out whether its own filters are any good.",
		position = 3,
		section = advancedSection
	)
	default boolean shadowTrading()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useCalibration",
		name = "Learn from my trades",
		description = "Let the plugin adjust its own estimates using how your past flips actually went. "
			+ "Recommended: it gets more accurate the longer you use it.",
		position = 2,
		section = advancedSection
	)
	default boolean useCalibration()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOnboarding",
		name = "Show the intro card",
		description = "Show the short explanation of what flipping is at the top of the panel.",
		position = 4,
		section = advancedSection
	)
	default boolean showOnboarding()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOnboarding",
		name = "",
		description = "",
		hidden = true
	)
	void setShowOnboarding(boolean value);
}
