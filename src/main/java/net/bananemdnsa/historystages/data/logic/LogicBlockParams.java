package net.bananemdnsa.historystages.data.logic;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The per-type settings a block carries next to its condition: the display mode of
 * {@code hidden_while}, the percentages of {@code cost_while}.
 *
 * <p>Read straight from the block's raw object, so whatever the file says is what is used and
 * anything this version does not know stays where it was.
 */
public final class LogicBlockParams {

    private LogicBlockParams() {}

    public static final String MODE = "mode";
    public static final String TIME = "time";
    public static final String ITEMS = "items";
    public static final String XP = "xp";

    public static final int MIN_PERCENT = 0;
    public static final int MAX_PERCENT = 1000;

    public enum HiddenMode {
        ANONYMOUS, VANISH;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Anonymous unless the block says vanish; an unknown value is read as the safe default. */
    public static HiddenMode hiddenMode(LogicBlock block) {
        String raw = string(block.raw(), MODE);
        return "vanish".equalsIgnoreCase(raw) ? HiddenMode.VANISH : HiddenMode.ANONYMOUS;
    }

    /** A percentage field, 100 when missing or unreadable, clamped to the allowed range. */
    public static int percent(LogicBlock block, String key) {
        return percent(block.raw(), key);
    }

    public static int percent(@Nullable JsonElement raw, String key) {
        if (raw == null || !raw.isJsonObject()) return 100;
        JsonElement value = raw.getAsJsonObject().get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return 100;
        int p = (int) Math.round(value.getAsDouble());
        return Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, p));
    }

    /** Writes a percentage, leaving the key out for 100 so untouched fields stay out of the file. */
    public static void setPercent(JsonObject target, String key, int percent) {
        int p = Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, percent));
        if (p == 100) target.remove(key);
        else target.addProperty(key, p);
    }

    /** Writes the mode, leaving it out for the default. */
    public static void setHiddenMode(JsonObject target, HiddenMode mode) {
        if (mode == HiddenMode.ANONYMOUS) target.remove(MODE);
        else target.addProperty(MODE, mode.id());
    }

    /**
     * A count scaled by a cost factor: rounded up and never below 1, unless the factor is 0,
     * which the author only gets by asking for free on purpose.
     */
    public static int scale(int count, double factor) {
        if (count <= 0) return count;
        if (factor <= 0.0) return 0;
        if (factor == 1.0) return count;
        // The epsilon keeps 16 × 0.75 at 12 instead of letting a stray 12.0000001 round to 13.
        return Math.max(1, (int) Math.ceil(count * factor - 1e-9));
    }

    @Nullable
    private static String string(@Nullable JsonElement raw, String key) {
        if (raw == null || !raw.isJsonObject()) return null;
        JsonElement value = raw.getAsJsonObject().get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }
}
