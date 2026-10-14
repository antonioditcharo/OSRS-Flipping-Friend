package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.Test;
import static org.junit.Assert.assertFalse;

/** Windows default text encoding must not corrupt UTF-8 source and player-visible strings. */
public class EditedSourceEncodingTest {
    @Test public void automaticScopeSourceAndDocumentationRemainUtf8() throws Exception {
        Pattern garbled = Pattern.compile("\u00c3|\u00c2|\u00e2[\u20ac\u0080]");
        for (String directory : new String[]{"src/main/java", "core/src/main/java", "companion/src/main/java", "docs"}) {
            try (var paths = Files.walk(Path.of(directory))) {
                for (Path path : (Iterable<Path>) paths.filter(Files::isRegularFile)::iterator) {
                    if (!path.toString().endsWith(".java") && !path.toString().endsWith(".md")) continue;
                    assertFalse("Garbled UTF-8 in " + path, garbled.matcher(Files.readString(path, StandardCharsets.UTF_8)).find());
                }
            }
        }
        for (String name : new String[]{"AGENTS.md","HANDOFF.md","TASKS.md"})
            assertFalse(name, garbled.matcher(Files.readString(Path.of(name),StandardCharsets.UTF_8)).find());
    }
}
