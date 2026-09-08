package com.flippingfriend;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * Asserts that the scheduled heartbeat never touches the game client directly.
 *
 * <p>{@code tick()} carries {@code asynchronous = true}, so RuneLite runs it on a scheduler pool
 * thread, and its own javadoc has always said it "must never touch the client directly". Reading a
 * widget from there is not a warning — {@code Widget.isHidden()} asserts, and the
 * {@code AssertionError} propagates out of the method.
 *
 * <p>That is what makes this worth a mechanical check rather than a comment. The failure is not
 * confined to the offending line: the throw abandons the whole tick, so every call <em>after</em>
 * the bad one silently stops running. When {@code settleAbandonedBuys} began reading the offer
 * panel it took the account refresh, the engine refresh, the periodic save and the learning poll
 * down with it, every two seconds, for as long as the client was logged in. Nothing failed
 * visibly. The plugin simply stopped producing advice except on offer events, and the only symptom
 * a player could describe was that a reprice appeared for a moment and never came back.
 *
 * <p>The permitted way to ask the client anything from here is {@code clientThread.invokeLater},
 * which is how {@link FlippingFriendPlugin#refreshAccountState} and
 * {@code requestEngineRefresh} already do it, so lambdas handed to it are excluded below.
 */
public class HeartbeatStaysOffTheClientThreadTest
{
	/** Ways of reaching the game that assert on the client thread. */
	private static final Pattern REACHES_THE_CLIENT =
		Pattern.compile("\\b(client|widgetResolver)\\s*\\.");

	/**
	 * Client calls that genuinely are safe from any thread, listed rather than assumed.
	 *
	 * <p>The assertion lives on the widget API — {@code Widget.isHidden()} and its neighbours check
	 * the calling thread and throw. A game-state read is a plain field access with no such check,
	 * and the tick's very first line is a logged-in guard that has always been there.
	 *
	 * <p>Kept as a short explicit list so that adding to it stays a decision somebody makes on
	 * purpose, rather than the rule quietly eroding into "anything that starts with client".
	 */
	private static final Pattern SAFE_FROM_ANY_THREAD =
		Pattern.compile("\\bclient\\s*\\.getGameState\\s*\\(\\s*\\)");

	private static String source()
	{
		Path dir = Paths.get("").toAbsolutePath();
		for (int i = 0; i < 4 && dir != null; i++, dir = dir.getParent())
		{
			Path candidate = dir.resolve("src/main/java/com/flippingfriend/FlippingFriendPlugin.java");
			if (Files.isRegularFile(candidate))
			{
				try
				{
					return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
				}
				catch (Exception unreadable)
				{
					throw new AssertionError("could not read the plugin source", unreadable);
				}
			}
		}
		throw new AssertionError("could not find FlippingFriendPlugin.java");
	}

	/** The body of one method, by brace matching from its declaration. */
	private static String bodyOf(String source, String method)
	{
		Matcher declaration = Pattern.compile(
			"(?m)^\\tprivate (?:static )?[\\w.<>\\[\\], ]+ " + Pattern.quote(method) + "\\s*\\([^)]*\\)\\s*\\{")
			.matcher(source);
		if (!declaration.find())
		{
			// public, for tick() itself
			declaration = Pattern.compile(
				"(?m)^\\tpublic (?:static )?[\\w.<>\\[\\], ]+ " + Pattern.quote(method) + "\\s*\\([^)]*\\)\\s*\\{")
				.matcher(source);
			if (!declaration.find())
			{
				return null;
			}
		}

		int open = source.indexOf('{', declaration.start());
		int depth = 0;
		for (int i = open; i < source.length(); i++)
		{
			char c = source.charAt(i);
			if (c == '{')
			{
				depth++;
			}
			else if (c == '}' && --depth == 0)
			{
				return source.substring(open, i + 1);
			}
		}
		return null;
	}

	/** The same text with every {@code clientThread.invoke...( ... )} argument removed. */
	private static String withoutClientThreadWork(String body)
	{
		StringBuilder kept = new StringBuilder();
		int i = 0;
		while (i < body.length())
		{
			int call = body.indexOf("clientThread.invoke", i);
			if (call < 0)
			{
				kept.append(body, i, body.length());
				break;
			}
			kept.append(body, i, call);
			int paren = body.indexOf('(', call);
			int depth = 0;
			int j = paren;
			for (; j < body.length(); j++)
			{
				char c = body.charAt(j);
				if (c == '(')
				{
					depth++;
				}
				else if (c == ')' && --depth == 0)
				{
					break;
				}
			}
			i = Math.min(j + 1, body.length());
		}
		return kept.toString();
	}

	/** Every private method named inside a body, which for this file is the call graph that matters. */
	private static Set<String> callsMadeBy(String source, String body)
	{
		Set<String> declared = new LinkedHashSet<>();
		Matcher m = Pattern.compile("(?m)^\\tprivate (?:static )?[\\w.<>\\[\\], ]+ (\\w+)\\s*\\(").matcher(source);
		while (m.find())
		{
			declared.add(m.group(1));
		}

		Set<String> called = new LinkedHashSet<>();
		Matcher used = Pattern.compile("\\b(\\w+)\\s*\\(").matcher(body);
		while (used.find())
		{
			if (declared.contains(used.group(1)))
			{
				called.add(used.group(1));
			}
		}
		return called;
	}

	@Test
	public void theTickAndEverythingItCallsLeaveTheClientAlone()
	{
		String source = source();
		String tick = bodyOf(source, "tick");
		assertTrue("tick() must be findable, or this test proves nothing", tick != null);

		Set<String> toCheck = new LinkedHashSet<>();
		toCheck.add("tick");
		toCheck.addAll(callsMadeBy(source, tick));

		List<String> offenders = new ArrayList<>();
		for (String method : toCheck)
		{
			String body = bodyOf(source, method);
			if (body == null)
			{
				continue;
			}
			String risky = SAFE_FROM_ANY_THREAD.matcher(withoutClientThreadWork(body)).replaceAll("");
			Matcher hit = REACHES_THE_CLIENT.matcher(risky);
			if (hit.find())
			{
				offenders.add(method + "() reaches " + hit.group(1) + " outside clientThread.invoke");
			}
		}

		if (!offenders.isEmpty())
		{
			fail("The scheduled tick runs off the client thread and an AssertionError from it "
				+ "abandons the rest of the heartbeat. Move these behind clientThread.invokeLater:"
				+ System.lineSeparator() + "  " + String.join(System.lineSeparator() + "  ", offenders));
		}
	}

	@Test
	public void theScanIsLookingAtSomething()
	{
		// A scan that finds nothing passes for the wrong reason. These pin that the call graph is
		// actually being walked and that the exclusion is not simply swallowing the whole file.
		String source = source();
		Set<String> reached = callsMadeBy(source, bodyOf(source, "tick"));

		assertTrue("the tick's own callees should be found: " + reached,
			reached.contains("settleAbandonedBuys") && reached.contains("requestEngineRefresh"));

		assertTrue("requestEngineRefresh does reach the client, inside clientThread.invokeLater",
			REACHES_THE_CLIENT.matcher(bodyOf(source, "requestEngineRefresh")).find());
		assertFalse("...and that is exactly what the exclusion is expected to remove",
			REACHES_THE_CLIENT.matcher(
				withoutClientThreadWork(bodyOf(source, "requestEngineRefresh"))).find());
	}
}
