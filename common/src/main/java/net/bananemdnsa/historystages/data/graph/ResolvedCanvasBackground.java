package net.bananemdnsa.historystages.data.graph;

import java.util.List;
import java.util.Locale;

/**
 * The canvas background as it gets drawn: graph.toml's three values with a stage's own block
 * laid over them field by field.
 *
 * <p>Modes are strings rather than {@code GraphConfig.CanvasBackground} because that enum lives
 * in a class that loads NeoForge's config types, and this has to stay reachable from JUnit.
 */
public record ResolvedCanvasBackground(String mode, String texture, int rgb) {

    public static final int DEFAULT_RGB = 0x17171A;
    public static final List<String> MODES = List.of("GRID", "SOLID", "TEXTURE");

    public static ResolvedCanvasBackground resolve(String baseMode, String baseTexture,
                                                   String baseColor, CanvasBackgroundStyle override) {
        String mode = override != null && override.mode != null ? override.mode : baseMode;
        String texture = override != null && override.texture != null ? override.texture : baseTexture;
        String color = override != null && override.color != null ? override.color : baseColor;

        String upper = mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
        return new ResolvedCanvasBackground(
                MODES.contains(upper) ? upper : "GRID",
                texture == null ? "" : texture.trim(),
                GraphColors.parse(color, DEFAULT_RGB));
    }
}
