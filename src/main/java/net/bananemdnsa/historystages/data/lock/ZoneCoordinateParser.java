package net.bananemdnsa.historystages.data.lock;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a position out of whatever a player pasted into a coordinate field.
 *
 * <p>The case this exists for is F3+C: Minecraft puts a whole teleport command on the clipboard,
 * and retyping three numbers out of it by hand is exactly the friction the editor is meant to
 * remove. Bare numbers, commas and a Discord copy-paste fall out of the same reading.
 *
 * <p>No Minecraft imports. {@code ResourceLocation} would be the obvious way to validate the
 * dimension and would take this class out of reach of a plain unit test — the verifier loads a
 * class as soon as a Minecraft type is named in it. The dimension is handed back as text and
 * checked by whoever uses it.
 */
public final class ZoneCoordinateParser {

    /** Signed, optionally fractional. Deliberately not anchored — it reads out of running text. */
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    /** A namespaced id, the shape {@code minecraft:the_nether} has. */
    private static final Pattern DIMENSION =
            Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    /** A parsed position. {@code dimension} is null when the text named none. */
    public record Result(int x, int y, int z, String dimension) {}

    private ZoneCoordinateParser() {}

    /** The position in this text, or null when it does not contain at least three numbers. */
    public static Result parse(String text) {
        if (text == null || text.isBlank()) return null;

        Matcher numbers = NUMBER.matcher(text);
        int[] found = new int[3];
        int firstNumberAt = -1;
        int count = 0;

        while (count < 3 && numbers.find()) {
            if (firstNumberAt < 0) firstNumberAt = numbers.start();
            found[count++] = toBlock(numbers.group());
        }
        if (count < 3) return null;

        return new Result(found[0], found[1], found[2], dimensionBefore(text, firstNumberAt));
    }

    /**
     * The block a coordinate lies in.
     *
     * <p>{@code floor}, not a cast. A cast rounds towards zero, so -99.1 would become -99 — one
     * block off, and only on the negative side of the map.
     */
    private static int toBlock(String number) {
        return (int) Math.floor(Double.parseDouble(number));
    }

    /**
     * The first namespaced id standing <em>before</em> the coordinates, or null.
     *
     * <p>The position matters. In {@code /execute in minecraft:the_nether run tp @s 100 64 200}
     * the dimension leads; anything colon-shaped that turns up after the numbers is a version, a
     * ratio or a timestamp, and reading it as a dimension would move the zone to a world nobody
     * named.
     */
    private static String dimensionBefore(String text, int firstNumberAt) {
        Matcher matcher = DIMENSION.matcher(text.substring(0, Math.max(0, firstNumberAt)));
        return matcher.find() ? matcher.group() : null;
    }
}
