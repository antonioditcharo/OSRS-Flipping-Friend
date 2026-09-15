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
                Assert.assertFalse(Files.exists(root.resolve("ml-forecaster/api.py")));
                Assert.assertFalse(Files.exists(root.resolve("src/main/java/com/flippingfriend/model/LstmForecasterClient.java")));
                String requirements = new String(Files.readAllBytes(root.resolve("ml-forecaster/requirements.txt")), java.nio.charset.StandardCharsets.UTF_8);
                Assert.assertFalse(requirements.contains("fastapi"));
                Assert.assertFalse(requirements.contains("uvicorn"));
        }
}
