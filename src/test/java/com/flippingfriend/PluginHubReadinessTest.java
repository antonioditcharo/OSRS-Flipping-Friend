package com.flippingfriend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * The conditions that decide whether anyone can install this.
 *
 * <p>A companion to {@link NoAutotypeTest}, in the same spirit and for the same reason: these are
 * facts about the repository rather than about behaviour, they are checked before a human ever looks
 * at the plugin, and a check somebody remembers to run is a check that eventually nobody runs. Every
 * model in this project can be perfect and none of it reaches a player if the manifest is missing or
 * the jar drags in a dependency the Hub will not build.
 *
 * <p>The outbound-host list is the one worth explaining. A flipping plugin has to talk to the wiki's
 * price API — that is the whole data source — and it talks to a companion process on loopback. Two
 * destinations, both defensible. What it must never do is grow a third that nobody noticed, and the
 * way that happens is not malice but leftovers: writing this test turned up <b>two</b> dead network
 * clients in the plugin, neither of which had an explanation to give a reviewer.
 * {@code LstmForecasterClient} held an HTTP client to {@code localhost:8000} for a model that had
 * been measured as earning nothing — injected, assigned to a field, never read.
 * {@code JagexPriceClient} reached {@code services.runescape.com} and was referenced by nothing at
 * all. Both were deleted rather than explained, and this test is what stops the next one.
 */
public class PluginHubReadinessTest
{
	/**
	 * Where this plugin is allowed to send a request.
	 *
	 * <p>Two of them, and both have to be defensible to a reviewer. The wiki is the price feed every
	 * flipping plugin uses and asks only for a descriptive User-Agent in return. The loopback address
	 * is this project's own companion process, which never leaves the machine. Anything else is a new
	 * claim on the player's trust and should be a deliberate decision, not a merge.
	 */
	private static final Set<String> ALLOWED_HOSTS = new HashSet<>(Arrays.asList(
		"prices.runescape.wiki",
		"127.0.0.1"));

	/**
	 * Where the plugin's code actually lives, for the purposes of what ships.
	 *
	 * <p>Both, because {@code core} is bundled into the plugin jar — the build packages it with
	 * {@code from project(':core').sourceSets.main.output}, since a side-loaded jar is not
	 * dependency-aware. Scanning {@code src/main/java} alone missed the wiki host entirely, which
	 * lives in {@code core}'s {@code WikiApi}, and would have missed a new one added there too.
	 */
	private static final String[] SHIPPED_SOURCES = {"src/main/java", "core/src/main/java"};

	/** Any absolute URL in the plugin's sources. */
	private static final Pattern URL = Pattern.compile("https?://([A-Za-z0-9._-]+)");

	private static Path repositoryRoot()
	{
		Path dir = Paths.get("").toAbsolutePath();
		for (int i = 0; i < 4 && dir != null; i++, dir = dir.getParent())
		{
			if (Files.isRegularFile(dir.resolve("settings.gradle")))
			{
				return dir;
			}
		}
		return null;
	}

	private static Properties manifest() throws IOException
	{
		Path root = repositoryRoot();
		if (root == null)
		{
			return null;
		}
		Path file = root.resolve("runelite-plugin.properties");
		if (!Files.isRegularFile(file))
		{
			return null;
		}
		Properties properties = new Properties();
		try (InputStream in = Files.newInputStream(file))
		{
			properties.load(in);
		}
		return properties;
	}

	@Test
	public void theHubManifestExistsAndIsComplete() throws IOException
	{
		Properties manifest = manifest();
		if (repositoryRoot() == null)
		{
			return;
		}

		assertNotNull("runelite-plugin.properties must exist at the repository root, or the Plugin "
			+ "Hub has nothing to build from and this can only ever be side-loaded by hand", manifest);
		for (String key : new String[]{"displayName", "author", "description", "tags", "plugins"})
		{
			String value = manifest.getProperty(key);
			assertTrue("the manifest needs a " + key, value != null && !value.trim().isEmpty());
		}
	}

	@Test
	public void everyPluginNamedInTheManifestExistsAndIsAPlugin() throws Exception
	{
		Properties manifest = manifest();
		if (manifest == null)
		{
			return;
		}

		for (String className : manifest.getProperty("plugins").split(","))
		{
			String name = className.trim();
			Class<?> type = Class.forName(name);
			assertNotNull("the manifest names " + name + ", which must exist", type);
			assertTrue(name + " must carry @PluginDescriptor or the Hub's build rejects it",
				type.isAnnotationPresent(net.runelite.client.plugins.PluginDescriptor.class));
			assertTrue(name + " must actually be a Plugin",
				net.runelite.client.plugins.Plugin.class.isAssignableFrom(type));
		}
	}

	@Test
	public void theManifestAndTheDescriptorAgree() throws Exception
	{
		Properties manifest = manifest();
		if (manifest == null)
		{
			return;
		}
		// Two places name the plugin, and they drift silently: the Hub's listing comes from the
		// manifest and the client's plugin list from the annotation, so a rename in one leaves a
		// player looking at two different names for the same thing.
		Class<?> type = Class.forName(manifest.getProperty("plugins").trim());
		net.runelite.client.plugins.PluginDescriptor descriptor =
			type.getAnnotation(net.runelite.client.plugins.PluginDescriptor.class);

		assertEquals("the Hub listing and the client's plugin list must say the same name",
			manifest.getProperty("displayName").trim(), descriptor.name());
		assertEquals("and the same description",
			manifest.getProperty("description").trim(), descriptor.description());
	}

	@Test
	public void thePluginTalksOnlyToPlacesItIsSupposedTo() throws IOException
	{
		Path root = repositoryRoot();
		if (root == null)
		{
			return;
		}
		List<String> offences = new ArrayList<>();
		List<String> seen = new ArrayList<>();

		for (String module : SHIPPED_SOURCES)
		{
			Path sources = root.resolve(module);
			if (!Files.isDirectory(sources))
			{
				continue;
			}
			try (Stream<Path> files = Files.walk(sources))
			{
				for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator)
				{
					List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
					for (int i = 0; i < lines.size(); i++)
					{
						String line = lines.get(i);
						String trimmed = line.trim();
						// A javadoc link to a documentation page is not a request. Only code counts.
						if (trimmed.startsWith("*") || trimmed.startsWith("//"))
						{
							continue;
						}
						Matcher matcher = URL.matcher(line);
						while (matcher.find())
						{
							String host = matcher.group(1);
							seen.add(host);
							if (!ALLOWED_HOSTS.contains(host))
							{
								offences.add(module + " " + sources.relativize(file) + ":" + (i + 1)
									+ "  " + host);
							}
						}
					}
				}
			}
		}

		assertTrue("the plugin must only reach hosts it has declared. Found:\n  "
			+ String.join("\n  ", offences), offences.isEmpty());
		// A scan that finds nothing passes for the wrong reason. The wiki is the price feed and it is
		// not optional, so its absence means the scan is looking in the wrong place.
		assertTrue("the scan must actually be reading the shipped sources; it saw " + seen,
			seen.contains("prices.runescape.wiki"));
	}

	@Test
	public void theDeadNetworkClientsAreGone() throws IOException
	{
		// Two of them, and both were found by asking what the plugin talks to rather than by reading
		// it. LstmForecasterClient held an HTTP client to localhost:8000 for a model that had been
		// measured as earning nothing — injected into SuggestionEngine, assigned to a field, never
		// read. JagexPriceClient reached services.runescape.com and was referenced by nothing at all.
		// Neither had an explanation to give a reviewer, and an unexplained network surface is
		// exactly what a reviewer stops on.
		Path root = repositoryRoot();
		if (root == null)
		{
			return;
		}

		assertFalse("LstmForecasterClient was deleted; it must not come back", Files.exists(
			root.resolve("src/main/java/com/flippingfriend/model/LstmForecasterClient.java")));
		assertFalse("JagexPriceClient was deleted; it must not come back", Files.exists(
			root.resolve("src/main/java/com/flippingfriend/data/JagexPriceClient.java")));
	}

	@Test
	public void theLicenceIsOneTheHubAccepts() throws IOException
	{
		Path root = repositoryRoot();
		if (root == null)
		{
			return;
		}
		Path licence = root.resolve("LICENSE");

		assertTrue("the Hub requires the source be licensed, and BSD 2-Clause is what RuneLite uses",
			Files.isRegularFile(licence));
		String text = new String(Files.readAllBytes(licence), StandardCharsets.UTF_8);
		assertTrue("expected BSD 2-Clause, found: " + text.split("\n")[0],
			text.contains("BSD 2-Clause"));
	}

	@Test
	public void thePluginJarCarriesNothingTheClientDoesNotAlreadyHave() throws IOException
	{
		// A side-loaded jar is not dependency-aware and the Hub's build is stricter still: anything
		// the plugin needs at runtime beyond what RuneLite ships has to be bundled, and bundling is
		// what gets a submission turned away. Everything from RuneLite is compileOnly for exactly
		// this reason, and the one module that is bundled must stay dependency-free.
		Path root = repositoryRoot();
		if (root == null)
		{
			return;
		}
		String core = new String(Files.readAllBytes(root.resolve("core/build.gradle")),
			StandardCharsets.UTF_8);

		for (String line : core.split("\n"))
		{
			String trimmed = line.trim();
			if (!trimmed.startsWith("api ") && !trimmed.startsWith("implementation "))
			{
				continue;
			}
			assertTrue("core is bundled into the plugin jar, so a new runtime dependency here ships "
				+ "with it: " + trimmed, trimmed.contains("javax.inject"));
		}
	}
}
