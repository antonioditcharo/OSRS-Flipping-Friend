package com.flippingfriend;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * Asserts that no code path writes into the game's input, anywhere in the plugin.
 *
 * <p>This is a compliance test rather than a behavioural one, and it is the most important test in
 * the repository. RuneLite's rejected-features list forbids "plugins which programmatically insert
 * text into the user's chatbox input <em>for any reason</em> … this is considered to be autotyping",
 * and Jagex's macro rules forbid software that "generates input to our game applets … mouse clicks
 * or key presses". The plugin previously did both, aimed at the Grand Exchange, on an account also
 * running a trading advisor.
 *
 * <p>The consequence of a regression here is not a failing feature. It is a banned account and a
 * plugin that can never be distributed, since removing this is the precondition for Plugin Hub
 * submission. So it is checked mechanically over the whole source tree rather than trusted to review
 * — a grep a person remembers to run is a grep that eventually nobody runs.
 *
 * <p>The permitted alternative is the clipboard: the plugin puts a number where the player can paste
 * it, and the player performs the paste. That costs one keystroke and is unambiguously allowed.
 */
public class NoAutotypeTest
{
	/**
	 * Ways to write into the client's input or synthesise events. {@code setVarcStrValue} with
	 * {@code INPUT_TEXT} is the specific one this plugin used; the others are the neighbouring
	 * techniques that would attract exactly the same objection.
	 */
	private static final String[] FORBIDDEN = {
		"setVarcStrValue",
		"setVarcIntValue",
		"java.awt.Robot",
		"new Robot(",
		"dispatchEvent(",
		"KeyEvent.KEY_TYPED",
	};

	private static Path sourceRoot()
	{
		Path dir = Paths.get("").toAbsolutePath();
		for (int i = 0; i < 4 && dir != null; i++, dir = dir.getParent())
		{
			Path candidate = dir.resolve("src/main/java/com/flippingfriend");
			if (Files.isDirectory(candidate))
			{
				return candidate;
			}
		}
		return null;
	}

	@Test
	public void noSourceFileWritesToTheGamesInput() throws IOException
	{
		Path root = sourceRoot();
		if (root == null)
		{
			// Running somewhere without the plugin sources. Skipping is right; failing would be noise.
			return;
		}

		List<String> offences = new ArrayList<>();
		try (Stream<Path> files = Files.walk(root))
		{
			for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator)
			{
				List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
				for (int i = 0; i < lines.size(); i++)
				{
					String line = lines.get(i);
					// The explanation of why this is gone necessarily names it.
					String trimmed = line.trim();
					if (trimmed.startsWith("*") || trimmed.startsWith("//"))
					{
						continue;
					}
					for (String forbidden : FORBIDDEN)
					{
						if (line.contains(forbidden))
						{
							offences.add(root.relativize(file) + ":" + (i + 1) + "  " + trimmed);
						}
					}
				}
			}
		}

		assertTrue("The plugin must never write into the game's input. Found:\n  "
			+ String.join("\n  ", offences), offences.isEmpty());
	}

	/** The replacement must still exist, or the ergonomics were removed rather than made compliant. */
	@Test
	public void theClipboardPathIsStillThere() throws IOException
	{
		Path root = sourceRoot();
		if (root == null)
		{
			return;
		}
		String plugin = new String(
			Files.readAllBytes(root.resolve("FlippingFriendPlugin.java")), StandardCharsets.UTF_8);

		assertTrue("the clipboard replacement must exist",
			plugin.contains("copyCurrentStepToClipboard"));
		assertTrue("and be reachable from the card", plugin.contains("setOnCardClicked"));
		assertFalse("the old entry point must be gone",
			plugin.contains("populateInputFromCard"));
	}
}
