package net.bananemdnsa.historystages.data.graph;

import net.bananemdnsa.historystages.util.DebugLogger;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Checks a per-stage style override against the spec of the block it will be layered onto,
 * dropping any field that does not belong there.
 *
 * <p>{@code graph.toml} is protected by {@code ValueSpec.test} on its save path.
 * {@code graph_stages.json} has nothing of the kind, so this is the only thing standing between
 * a modified client and a {@code "size": 400} that wrecks a node for every player on the server.
 *
 * <p>Free of Minecraft and NeoForge imports on purpose — that is what lets JUnit reach it. The
 * spec knowledge arrives as {@link GraphKey}s from
 * {@code GraphConfigEntries.styleKeys}, which does the NeoForge-side walk.
 *
 * <p>A bad field is dropped, not fatal. Rejecting the whole payload would turn one bad value
 * into a silent no-op the author has no way to explain.
 */
public final class StageStyleValidator {

    private StageStyleValidator() {}

    /**
     * @param keys the {@link GraphKey}s of one style block; a leaf absent from this list is not
     *             editable and its value is dropped
     * @return a new style carrying only the fields that survived
     */
    public static StageStyle sanitize(StageStyle style, List<GraphKey> keys) {
        StageStyle out = new StageStyle();
        if (style == null || keys == null) return out;

        for (GraphKey key : keys) {
            String leaf = key.leaf();
            String value = StageStyleFields.get(style, leaf);
            if (value == null) continue;

            String checked = check(key, value);
            if (checked == null) {
                DebugLogger.error("Stage Graph",
                        "Dropped invalid style override " + leaf + " = " + value);
                continue;
            }
            StageStyleFields.set(out, leaf, checked);
        }
        return out;
    }

    private static final Pattern TEXTURE_PATH = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9_./-]+");

    /**
     * Same treatment for a stage's canvas background block.
     *
     * @param modes the names of {@code GraphConfig.CanvasBackground}, passed in because that enum
     *              is out of JUnit's reach
     */
    public static CanvasBackgroundStyle sanitizeBackground(CanvasBackgroundStyle style, List<String> modes) {
        CanvasBackgroundStyle out = new CanvasBackgroundStyle();
        if (style == null) return out;

        if (style.mode != null) {
            String upper = style.mode.trim().toUpperCase(Locale.ROOT);
            if (modes.contains(upper)) out.mode = upper;
            else dropped("background.mode", style.mode);
        }
        if (style.texture != null) {
            if (isTexturePath(style.texture)) out.texture = style.texture.trim();
            else dropped("background.texture", style.texture);
        }
        if (style.color != null) {
            if (GraphColors.isValid(style.color)) out.color = style.color.trim().toUpperCase(Locale.ROOT);
            else dropped("background.color", style.color);
        }
        return out;
    }

    /**
     * ResourceLocation's character rules, checked without loading ResourceLocation. A path that
     * fails them would throw the moment the canvas tried to bind it, for everyone on the server.
     */
    public static boolean isTexturePath(String text) {
        if (text == null) return false;
        String t = text.trim();
        return !t.isEmpty() && t.length() <= 256 && !t.contains("..")
                && TEXTURE_PATH.matcher(t).matches();
    }

    private static void dropped(String field, String value) {
        DebugLogger.error("Stage Graph", "Dropped invalid style override " + field + " = " + value);
    }

    /** The value to store, or null when it may not be stored at all. */
    private static String check(GraphKey key, String value) {
        return switch (key.kind()) {
            case ENUM -> {
                String upper = value.trim().toUpperCase(Locale.ROOT);
                yield key.enumConstants().contains(upper) ? upper : null;
            }
            case COLOR -> GraphColors.isValid(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
            case BOOLEAN -> {
                String lower = value.trim().toLowerCase(Locale.ROOT);
                yield ("true".equals(lower) || "false".equals(lower)) ? lower : null;
            }
            case INTEGER, DOUBLE -> inRange(key, value);
            // No style leaf is text or a texture; if one ever is, it arrives unchecked
            // and this arm is where to add its rule.
            case STRING, RICH_TEXT, TEXTURE -> value;
        };
    }

    private static String inRange(GraphKey key, String value) {
        double number;
        try {
            number = Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
        // NaN loses every comparison, so it would slip past both bounds below — and Gson takes
        // both NaN and "NaN" into a Double field, which puts it within reach of a modified
        // client. Math.round(NaN) is 0, i.e. a node collapsed to a dot for every player.
        if (!Double.isFinite(number)) return null;
        if (key.min() != null && number < key.min()) return null;
        if (key.max() != null && number > key.max()) return null;
        return value.trim();
    }
}
