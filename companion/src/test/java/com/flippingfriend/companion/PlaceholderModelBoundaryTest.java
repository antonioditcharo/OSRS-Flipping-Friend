package com.flippingfriend.companion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;

public class PlaceholderModelBoundaryTest
{
        @Test
        public void placeholderModelsCannotEnterProduction() throws Exception
        {
                Path root = repositoryRoot();

                Assert.assertFalse(Files.exists(root.resolve(
                        "companion/src/main/java/com/flippingfriend/companion/ai/OnnxInferenceEngine.java")));
                Assert.assertFalse(Files.exists(root.resolve(
                        "companion/src/main/resources/models/fill_prob_v1.onnx")));
                Assert.assertFalse(Files.exists(root.resolve(
                        "companion/src/main/resources/models/momentum_v1.onnx")));
                Assert.assertFalse(Files.exists(root.resolve(
                        "companion/src/main/resources/models/queue_wait_v1.onnx")));
                Assert.assertFalse(Files.exists(root.resolve(
                        "ml-forecaster/train_models.py")));

                String build = read(root.resolve("companion/build.gradle"));
                String requirements = read(root.resolve("ml-forecaster/requirements.txt"));

                Assert.assertFalse(build.contains("onnxruntime"));
                Assert.assertFalse(requirements.contains("onnx"));
                Assert.assertFalse(requirements.contains("lightgbm"));
        }

        private static Path repositoryRoot()
        {
                Path current = Path.of("").toAbsolutePath();

                while (current != null)
                {
                        if (Files.exists(current.resolve("settings.gradle")))
                        {
                                return current;
                        }

                        current = current.getParent();
                }

                throw new IllegalStateException("Repository root not found");
        }

        private static String read(Path path) throws IOException
        {
                return new String(
                        Files.readAllBytes(path),
                        java.nio.charset.StandardCharsets.UTF_8);
        }
}
