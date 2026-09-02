package com.flippingfriend.data;

import com.google.gson.Gson;
import java.nio.file.Path;
import net.runelite.client.config.ConfigManager;
import org.mockito.Mockito;

/** Builds a {@link PluginStorage} rooted at a temporary directory for tests. */
public final class TestStorage
{
	private TestStorage()
	{
	}

	public static PluginStorage rootedAt(Path root, String profileKey)
	{
		ConfigManager configManager = Mockito.mock(ConfigManager.class);
		Mockito.when(configManager.getRSProfileKey()).thenReturn(profileKey);
		return new PluginStorage(configManager, new Gson(), root);
	}
}
