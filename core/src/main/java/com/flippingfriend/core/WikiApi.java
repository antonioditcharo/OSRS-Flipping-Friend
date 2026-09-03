package com.flippingfriend.core;

/**
 * One place for the wiki price API's address and the identity we present to it.
 *
 * <p>Both live here because both are things the wiki can change without warning, and because every
 * client in this project needs to agree on them. Before 2 September 2026 there were four
 * independently-written User-Agents — two saying {@code "contact local user"}, one pointing at a
 * GitHub URL that did not resolve, and one from the Python service reading
 * {@code "FlippingFriend_ML_Forecaster"} — so there was no way for the wiki to reach us and no way
 * for us to know how much traffic we were sending in total.
 *
 * <h2>Why the agent matters</h2>
 *
 * <p>A descriptive User-Agent with a contact is the only thing the wiki asks in return for the data,
 * and the check is automated rather than a courtesy. Measured from this machine on 2 September 2026:
 *
 * <pre>
 *   curl -A 'curl/8.0'          .../api/v1/osrs/latest   -&gt; 403
 *   curl -A '&lt;a descriptive agent&gt;' .../api/v1/osrs/latest   -&gt; 200
 * </pre>
 *
 * <p>The consequence of getting this wrong is not a warning; it is the whole product going quiet at
 * once, because every price in the system comes from this one feed. The contact is what turns a
 * block into a message.
 *
 * <h2>Version</h2>
 *
 * <p>Bump {@link #VERSION} when request behaviour changes — a new endpoint, a different polling rate,
 * a new consumer — not on every release. It exists so that if our traffic ever causes a problem, the
 * wiki can say which version caused it.
 */
public final class WikiApi
{
	/**
	 * API version segment. The wiki documents {@code v2}, and {@code v1} still serves — both returned
	 * 200 with identical JSON keys when checked on 2 September 2026. They are not interchangeable:
	 * {@code v1} takes {@code timestep=5m}, {@code v2} takes {@code lookback=24h} and rejects
	 * {@code timestep} with a 400. Migrating means changing this constant <em>and</em> the parameter
	 * name together — see audit item 45.
	 */
	public static final String VERSION = "v1";

	public static final String BASE = "https://prices.runescape.wiki/api/" + VERSION + "/osrs";

	/**
	 * Live price stream. Not versioned alongside {@link #BASE} — it sits at {@code /api/ws}, outside
	 * the {@code /osrs} tree — so a v1-to-v2 migration does not touch it. Same host and same
	 * User-Agent policy.
	 */
	public static final String WEBSOCKET = "wss://prices.runescape.wiki/api/ws";

	/** Bump when request behaviour changes, not on every release. */
	private static final String VERSION_TAG = "1.2";

	private static final String CONTACT = "flippingfriend.dev@gmail.com";

	/**
	 * Sent on every outbound request to the wiki, from every client — the plugin, the companion's
	 * ingestion and series cache, and the Python forecaster.
	 *
	 * <p>Format follows the wiki's own documented example, which pairs a name with a way to reach the
	 * author. Keep the contact real: an unreachable one is worse than none, because it looks like
	 * compliance while providing nothing.
	 */
	public static final String USER_AGENT = "FlippingFriend/" + VERSION_TAG + " (" + CONTACT + ")";

	private WikiApi()
	{
	}
}
