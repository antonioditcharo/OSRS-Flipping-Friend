package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

public class CompanionSetupBoundaryTest
{
        @Test
        public void firstTimeSetupInstallsBothSupportedArtifacts() throws Exception
        {
                Path root = Paths.get("").toAbsolutePath();
                String build = read(root.resolve("tools/build.ps1"));
                String setup = read(root.resolve("tools/setup.ps1"));
                String installer = read(root.resolve("tools/install-companion.ps1"));
                String start = read(root.resolve("tools/start.ps1"));
                Assert.assertTrue(build.contains("jar :companion:shadowJar"));
                Assert.assertTrue(setup.contains("install-companion.ps1"));
                Assert.assertTrue(setup.contains("-SkipBuild"));
                Assert.assertTrue(installer.contains("[switch] $SkipBuild"));
                Assert.assertTrue(installer.contains("FlippingFriendCompanion"));
                Assert.assertTrue(start.contains("limited recovery mode"));
                Assert.assertFalse(start.contains("older, weaker engine"));
                Assert.assertTrue(Files.exists(root.resolve("tools/uninstall-companion.ps1")));
        }

        private static String read(Path path) throws Exception
        {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        }
}
