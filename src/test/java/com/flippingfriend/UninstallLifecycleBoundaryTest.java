package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

public class UninstallLifecycleBoundaryTest
{
        @Test
        public void uninstallRemovesRuntimeComponentsAndPreservesUserData() throws Exception
        {
                Path root = Paths.get("").toAbsolutePath();
                Assert.assertTrue(Files.exists(root.resolve("5 - Uninstall.bat")));
                Path script = root.resolve("tools/uninstall.ps1");
                Assert.assertTrue(Files.exists(script));
                String uninstall = new String(Files.readAllBytes(script), StandardCharsets.UTF_8);
                Assert.assertTrue(uninstall.contains("uninstall-companion.ps1"));
                Assert.assertTrue(uninstall.contains("sideloaded-plugins"));
                Assert.assertTrue(uninstall.contains("osrs-flipping-friend.jar"));
                Assert.assertTrue(uninstall.contains("Flipping Friend.lnk"));
                Assert.assertTrue(uninstall.contains("Remove-Item $pluginJar"));
                Assert.assertTrue(uninstall.contains("Remove-Item $shortcut"));
                Assert.assertTrue(uninstall.contains("Preserved companion data"));
                Assert.assertTrue(uninstall.contains("Preserved RuneLite login credentials"));
                Assert.assertTrue(uninstall.contains("tools\\cleanup.ps1"));
                Assert.assertFalse(uninstall.contains("Remove-Item $companionData"));
                Assert.assertFalse(uninstall.contains("Remove-Item $credentials"));
                Assert.assertFalse(uninstall.contains("Remove-Item $PSScriptRoot"));
        }
}
