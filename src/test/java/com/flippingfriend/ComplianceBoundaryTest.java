package com.flippingfriend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Test;

/**
 * Protects manual offer actions and the sole explicitly requested, player-triggered prompt writer.
 */
public class ComplianceBoundaryTest
{
        @Test
        public void onlyThePlayerTriggeredPromptControllerMayWriteInputText() throws IOException
        {
                Path sourceRoot = Paths.get("src", "main", "java");

                Assert.assertTrue(
                        "Production source directory is missing",
                        Files.isDirectory(sourceRoot));

                List<String> violations = new ArrayList<>();

                try (Stream<Path> files = Files.walk(sourceRoot))
                {
                        files.filter(Files::isRegularFile)
                                .filter(path -> path.toString().endsWith(".java"))
                                .forEach(path -> inspect(path, violations));
                }

                Assert.assertTrue(
                        "Production game-input writes found: " + violations,
                        violations.isEmpty());
        }

        private static void inspect(Path path, List<String> violations)
        {
                try
                {
                        String source = new String(
                                Files.readAllBytes(path),
                                java.nio.charset.StandardCharsets.UTF_8);

                        boolean promptController = path.endsWith(Paths.get("overlay", "GeInputAssist.java"));
                        boolean allowedPromptWrite = promptController
                                && source.split("setVarcStrValue", -1).length == 2
                                && source.contains("client.setVarcStrValue(VarClientStr.INPUT_TEXT, text)");
                        if ((source.contains("setVarcStrValue") && !allowedPromptWrite)
                                || source.contains("setVarcIntValue(")
                                || source.contains("invokeMenuAction(") || source.contains("menuAction(")
                                || source.contains("runScript(") || source.contains("setVarpValue(")
                                || source.contains("setVarpLongValue(") || source.contains("setVarbitValue(")
                                || source.contains("java.awt.Robot"))
                        {
                                violations.add(path.toString());
                        }
                }
                catch (IOException exception)
                {
                        throw new IllegalStateException(
                                "Could not inspect production source: " + path,
                                exception);
                }
        }
}
