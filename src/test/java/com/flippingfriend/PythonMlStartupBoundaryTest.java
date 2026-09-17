package com.flippingfriend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

public class PythonMlStartupBoundaryTest
{
        @Test
        public void unsupportedPythonServiceCannotStart() throws Exception
        {
                Path root = Paths.get("").toAbsolutePath();
                Assert.assertFalse(Files.exists(root.resolve("4 - ML Forecaster.bat")));
                Assert.assertFalse(Files.exists(root.resolve("tools/install-ml-forecaster.ps1")));
                Assert.assertFalse(Files.exists(root.resolve("tools/uninstall-ml-forecaster.ps1")));
                Assert.assertFalse(Files.exists(root.resolve("ml-forecaster")));
                Assert.assertFalse(Files.exists(root.resolve("src/main/java/com/flippingfriend/model/LstmForecasterClient.java")));
        }
}
