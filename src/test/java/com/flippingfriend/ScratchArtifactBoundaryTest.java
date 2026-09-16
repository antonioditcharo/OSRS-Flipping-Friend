package com.flippingfriend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

public class ScratchArtifactBoundaryTest
{
        @Test
        public void scratchArtifactsStayRemoved() throws Exception
        {
                Path root = Paths.get("").toAbsolutePath();
                Assert.assertFalse(Files.exists(root.resolve("WSTest.java")));
                Assert.assertFalse(Files.exists(root.resolve("TextComponent.java")));
                Assert.assertFalse(Files.exists(root.resolve("check.py")));
                Assert.assertFalse(Files.exists(root.resolve("codebase-review.txt")));
                Assert.assertFalse(Files.exists(root.resolve("dashboard.html")));
                Assert.assertFalse(Files.exists(root.resolve("scratch")));
                Assert.assertTrue(Files.exists(root.resolve("tools/bundle-source.ps1")));
                Assert.assertFalse(Files.exists(root.resolve("3 - Background Learning.bat")));
                Assert.assertFalse(Files.exists(root.resolve("tools/install-daemon.ps1")));
        }
}
