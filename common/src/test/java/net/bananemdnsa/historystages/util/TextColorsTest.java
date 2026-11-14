package net.bananemdnsa.historystages.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 26.2 skips text whose colour has alpha 0; 1.21.1 made it opaque. Every editor colour written as
 * {@code 0xRRGGBB} would vanish without the 1.21.1 rule.
 */
class TextColorsTest {

    @Test
    void rgbWithoutAlphaBecomesOpaque() {
        assertEquals(0xFFCCCCCC, TextColors.legacy(0xCCCCCC));
        assertEquals(0xFF000000, TextColors.legacy(0));
        // Alpha below 4 counts as unset, as in 1.21.1's Font.adjustColor.
        assertEquals(0xFF123456, TextColors.legacy(0x03123456));
    }

    @Test
    void anExplicitAlphaIsKept() {
        assertEquals(0x80FFFFFF, TextColors.legacy(0x80FFFFFF));
        assertEquals(0x04FFFFFF, TextColors.legacy(0x04FFFFFF));
    }

    private static final Pattern BARE_RGB_TEXT = Pattern.compile(
            "\\.(?:text|centeredText)\\([^;]*,\\s*0x[0-9A-Fa-f]{6}\\s*(?:,\\s*(?:true|false)\\s*)?\\)\\s*;");

    @Test
    void noTextCallPassesABareRgbLiteral() throws IOException {
        Path root = Path.of("src", "main", "java");
        assertTrue(Files.isDirectory(root), "expected the test to run from the project root");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    if (BARE_RGB_TEXT.matcher(lines.get(i)).find()) {
                        offenders.add(file.getFileName() + ":" + (i + 1));
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), "text drawn with an alpha-less colour is invisible in 26.2:\n"
                + String.join("\n", offenders));
    }
}
