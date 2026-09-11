package com.flippingfriend.data;

import com.google.gson.Gson;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Everything the plugin writes to disk goes through here.
 * <p>
 * Market data is shared between accounts because prices are, but positions, buy-limit ledgers and
 * trade journals are scoped to the RuneLite RS profile key so two accounts on one install never see
 * each other's trades or corrupt each other's buy-limit accounting.
 */
@Singleton
public class PluginStorage
{
	private static final Logger log = LoggerFactory.getLogger(PluginStorage.class);
	private static final String DIR_NAME = "osrs-flipping-friend";
	private static final String NO_PROFILE = "default";

	private final ConfigManager configManager;
	private final Gson gson;
	private final Path root;

	@Inject
	public PluginStorage(ConfigManager configManager, Gson gson)
	{
		this(configManager, gson, RuneLite.RUNELITE_DIR.toPath().resolve(DIR_NAME));
	}

	/**
	 * Visible for testing, so persistence can be exercised against a temporary directory rather
	 * than the player's real RuneLite folder. Trade history is the one thing here that cannot be
	 * regenerated, so it deserves a test that actually writes and reads it back.
	 */
	PluginStorage(ConfigManager configManager, Gson gson, Path root)
	{
		this.configManager = configManager;
		this.gson = gson;
		this.root = root;
	}

	public Path root()
	{
		return ensure(root);
	}

	/** Price caches, which are identical for every account. */
	public Path sharedDir()
	{
		return ensure(root.resolve("market-cache"));
	}

	/**
	 * Per-account directory. Falls back to a shared "default" folder before login, which is
	 * harmless because nothing account-specific is written until we know who is logged in.
	 */
	public Path accountDir()
	{
		String profile = configManager.getRSProfileKey();
		String safe = profile == null || profile.isEmpty() ? NO_PROFILE : sanitise(profile);
		return ensure(root.resolve("accounts").resolve(safe));
	}

	public boolean hasAccount()
	{
		String profile = configManager.getRSProfileKey();
		return profile != null && !profile.isEmpty();
	}

	public <T> T readJson(Path file, Type type, T fallback)
	{
		if (!Files.isRegularFile(file))
		{
			return fallback;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
		{
			T value = gson.fromJson(reader, type);
			return value == null ? fallback : value;
		}
		catch (Exception ex)
		{
			log.warn("could not read {}: {}", file.getFileName(), ex.getMessage());
			return fallback;
		}
	}

	/**
	 * Writes through a temporary file and then moves it into place, so a crash mid-write leaves the
	 * previous good copy rather than a truncated one. Losing a session of trade history to a bad
	 * shutdown would also mean losing the calibration built from it.
	 */
	public void writeJson(Path file, Object value, Type type)
	{
		writeJsonChecked(file, value, type);
	}

	/**
	 * Atomically writes JSON and reports whether the new copy reached its final path.
	 *
	 * <p>The ordinary {@link #writeJson(Path, Object, Type)} method remains appropriate for
	 * rebuildable state. Delivery queues need a result, however, because an event must not be
	 * transmitted and removed from memory when its durable copy was never written.</p>
	 *
	 * @return true only when the temporary file was successfully moved into place
	 */
	public boolean writeJsonChecked(Path file, Object value, Type type)
	{
		try
		{
			Files.createDirectories(file.getParent());
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8))
			{
				gson.toJson(value, type, writer);
			}
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			return true;
		}
		catch (Exception ex)
		{
			log.warn("could not write {}: {}", file.getFileName(), ex.getMessage());
			return false;
		}
	}

	/** Appends one JSON object per line. Used by the trade journal, which is only ever added to. */
	public void appendLine(Path file, String line)
	{
		try
		{
			Files.createDirectories(file.getParent());
			Files.write(file, (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		}
		catch (IOException ex)
		{
			log.warn("could not append to {}: {}", file.getFileName(), ex.getMessage());
		}
	}

	public List<String> readLines(Path file)
	{
		if (!Files.isRegularFile(file))
		{
			return java.util.Collections.emptyList();
		}
		try
		{
			return Files.readAllLines(file, StandardCharsets.UTF_8);
		}
		catch (IOException ex)
		{
			log.warn("could not read {}: {}", file.getFileName(), ex.getMessage());
			return java.util.Collections.emptyList();
		}
	}

	public Gson gson()
	{
		return gson;
	}

	private static Path ensure(Path dir)
	{
		try
		{
			Files.createDirectories(dir);
		}
		catch (IOException ex)
		{
			log.warn("could not create {}: {}", dir, ex.getMessage());
		}
		return dir;
	}

	private static String sanitise(String value)
	{
		return value.replaceAll("[^a-zA-Z0-9_-]", "_");
	}
}
