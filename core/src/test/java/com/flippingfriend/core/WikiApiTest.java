package com.flippingfriend.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * Guards the one identity every client presents to the wiki.
 *
 * <p>The wiki blocks generic agents automatically — {@code curl/8.0} returns 403 where a descriptive
 * agent returns 200 — and the failure mode is the entire product going quiet at once, since every
 * price in the system comes from this one feed. These tests are cheap insurance against the two ways
 * that has nearly happened already: an agent with no way to reach us, and four clients disagreeing
 * about what they are called.
 */
public class WikiApiTest
{
	@Test
	public void agentNamesTheProductAndCarriesAReachableContact()
	{
		String agent = WikiApi.USER_AGENT;

		assertTrue("must name the product: " + agent, agent.startsWith("FlippingFriend/"));
		assertTrue("must carry a contact in parentheses: " + agent,
			agent.contains("(") && agent.contains(")"));
		assertTrue("the contact must be reachable, not a placeholder: " + agent,
			agent.contains("@"));

		// The exact strings that were shipping before 2 September 2026. Each looked like compliance
		// and provided no way to reach anyone.
		assertTrue("'contact local user' is not a contact", !agent.contains("contact local user"));
		assertTrue("the old GitHub URL did not resolve",
			!agent.contains("github.com/osrs-flipping-friend"));
	}

	@Test
	public void agentIsNotOneTheWikiBlocks()
	{
		String agent = WikiApi.USER_AGENT.toLowerCase();
		// The wiki pre-emptively blocks default library agents.
		for (String generic : new String[]{ "okhttp", "java/", "python-requests", "curl/", "wget" })
		{
			assertTrue("looks like a generic library agent: " + WikiApi.USER_AGENT,
				!agent.startsWith(generic));
		}
	}

	@Test
	public void baseUrlAndVersionAgree()
	{
		assertTrue(WikiApi.BASE.startsWith("https://"));
		assertTrue("BASE must carry the version segment",
			WikiApi.BASE.contains("/api/" + WikiApi.VERSION + "/"));
		assertTrue("the stream sits outside the versioned tree",
			WikiApi.WEBSOCKET.startsWith("wss://") && !WikiApi.WEBSOCKET.contains(WikiApi.VERSION));
	}

	/**
	 * The Python forecaster cannot import a Java constant, so {@code ml-forecaster/wiki_api.py}
	 * duplicates these values. Duplication is fine; silent divergence is not — a Python client
	 * identifying itself differently is exactly the situation this whole change removed.
	 *
	 * <p>Skips rather than fails when the file cannot be located, so the test is useful in a checkout
	 * and harmless in an environment that only has {@code :core}.
	 */
	@Test
	public void pythonClientAgreesWithJava() throws IOException
	{
		Path py = locate("ml-forecaster/wiki_api.py");
		if (py == null)
		{
			return;
		}
		String source = new String(Files.readAllBytes(py), StandardCharsets.UTF_8);

		assertEquals("VERSION drifted between wiki_api.py and WikiApi.java",
			WikiApi.VERSION, extract(source, "VERSION\\s*=\\s*\"([^\"]+)\""));
		assertEquals("VERSION_TAG drifted between wiki_api.py and WikiApi.java",
			versionTagOf(WikiApi.USER_AGENT), extract(source, "VERSION_TAG\\s*=\\s*\"([^\"]+)\""));
		assertEquals("CONTACT drifted between wiki_api.py and WikiApi.java",
			contactOf(WikiApi.USER_AGENT), extract(source, "CONTACT\\s*=\\s*\"([^\"]+)\""));
	}

	private static String versionTagOf(String agent)
	{
		return agent.substring(agent.indexOf('/') + 1, agent.indexOf(' '));
	}

	private static String contactOf(String agent)
	{
		return agent.substring(agent.indexOf('(') + 1, agent.lastIndexOf(')'));
	}

	private static String extract(String source, String regex)
	{
		Matcher m = Pattern.compile(regex).matcher(source);
		assertTrue("wiki_api.py no longer defines " + regex, m.find());
		return m.group(1);
	}

	/** Walks up from the working directory, since tests may run from the module or the root. */
	private static Path locate(String relative)
	{
		Path dir = Paths.get("").toAbsolutePath();
		for (int i = 0; i < 4 && dir != null; i++, dir = dir.getParent())
		{
			Path candidate = dir.resolve(relative);
			if (Files.isRegularFile(candidate))
			{
				return candidate;
			}
		}
		return null;
	}
}
