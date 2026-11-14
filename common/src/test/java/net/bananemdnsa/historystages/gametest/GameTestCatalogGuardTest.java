package net.bananemdnsa.historystages.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every class with an {@code @HsGameTest} method is in {@link HistoryStagesGameTests}' list. Nothing
 * scans for the annotation, so a class left out would simply never run, with every run green.
 */
class GameTestCatalogGuardTest {

    private static final Path DIR = Path.of("src", "main", "java", "net", "bananemdnsa", "historystages", "gametest");

    @Test
    void everyTestClassIsInTheCatalog() throws IOException {
        String catalog = Files.readString(DIR.resolve("HistoryStagesGameTests.java"));
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.list(DIR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString().replace(".java", "");
                if (name.equals("HsGameTest")) continue;
                if (Files.readString(file).contains("@HsGameTest") && !catalog.contains(name + ".class")) {
                    missing.add(name);
                }
            }
        }
        assertTrue(missing.isEmpty(), "test classes that never run, add them to HistoryStagesGameTests: " + missing);
    }
}
