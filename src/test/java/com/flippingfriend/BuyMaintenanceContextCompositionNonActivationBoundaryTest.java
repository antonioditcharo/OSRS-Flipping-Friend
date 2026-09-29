package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;

public class BuyMaintenanceContextCompositionNonActivationBoundaryTest
{
    @Test public void compositionRemainsOutsideLivePluginAndApi() throws Exception
    {
        Path root = root();
        String plugin = read(root, "src/main/java/com/flippingfriend/FlippingFriendPlugin.java");
        String api = read(root, "companion/src/main/java/com/flippingfriend/companion/ApiServer.java");
        String service = read(root, "companion/src/main/java/com/flippingfriend/companion/CompanionService.java");
        Assert.assertTrue(plugin.contains("engine.refresh(false)"));
        Assert.assertTrue(plugin.contains("companion.nextEntrySuggestion("));
        Assert.assertFalse(plugin.contains("BuyMaintenanceContextComposer"));
        Assert.assertFalse(api.contains("policy/buy-maintenance"));
        Assert.assertFalse(service.contains("BuyMaintenanceContextComposer"));
    }
    private static String read(Path root, String file) throws Exception
    {
        return Files.readString(root.resolve(file), StandardCharsets.UTF_8);
    }
    private static Path root()
    {
        Path current=Path.of("").toAbsolutePath();
        while(current!=null&&!Files.exists(current.resolve("settings.gradle"))) current=current.getParent();
        return current;
    }
}
