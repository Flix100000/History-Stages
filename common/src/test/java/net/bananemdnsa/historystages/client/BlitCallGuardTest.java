package net.bananemdnsa.historystages.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 26.2 kept a nine-argument {@code blit(texture, ...)}, but its numbers are corners and UV
 * bounds ({@code x0, y0, x1, y1, u0, u1, v0, v1}), not 1.21.1's {@code x, y, u, v, width, height,
 * textureWidth, textureHeight}. Old calls still compile and draw garbage: the pedestal sheet came
 * out as a screen-wide stripe and the lock icon as a zero-sized quad.
 *
 * <p>So a texture blit goes through {@link LegacyGui#blit} or names its pipeline. The corner form
 * is allowed only in {@code NodeTextures}, which draws a scaled unit quad on purpose.
 */
class BlitCallGuardTest {

    private static final Set<String> CORNER_FORM_ALLOWED = Set.of("NodeTextures.java", "LegacyGui.java");

    @Test
    void noBlitUsesTheCornerFormByAccident() throws IOException {
        Path root = Path.of("src", "main", "java");
        assertTrue(Files.isDirectory(root), "expected the test to run from the project root");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (CORNER_FORM_ALLOWED.contains(file.getFileName().toString())) continue;
                String src = Files.readString(file);
                int at = 0;
                while ((at = src.indexOf(".blit(", at)) >= 0) {
                    int line = 1 + (int) src.substring(0, at).chars().filter(c -> c == '\n').count();
                    boolean viaHelper = src.startsWith("LegacyGui", Math.max(0, at - "LegacyGui".length()))
                            || src.startsWith("NodeTextures", Math.max(0, at - "NodeTextures".length()));
                    String firstArg = firstArgument(src, at + ".blit(".length());
                    if (!viaHelper && !firstArg.contains("RenderPipeline")) {
                        offenders.add(file.getFileName() + ":" + line);
                    }
                    at += ".blit(".length();
                }
            }
        }
        assertTrue(offenders.isEmpty(), "blit without a pipeline takes corners and UV bounds in 26.2;"
                + " use LegacyGui.blit:\n" + String.join("\n", offenders));
    }

    private static String firstArgument(String src, int from) {
        int depth = 0;
        for (int i = from; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                if (depth == 0) return src.substring(from, i);
                depth--;
            } else if (c == ',' && depth == 0) {
                return src.substring(from, i);
            }
        }
        return src.substring(from);
    }
}
