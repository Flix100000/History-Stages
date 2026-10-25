package net.bananemdnsa.historystages.data.disguise;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

/**
 * One line of {@code disguises.json}: the item or tag {@code key} looks like {@code as} while it is
 * locked for the viewer.
 *
 * @param key   item id, or {@code #namespace:path} for an item tag
 * @param as    item id of the disguise
 * @param drops what breaking the block gives the player
 * @param hints whether lock icon, border and "requires stage" lines stay visible
 * @param nbt   item keys only: the rule covers only stacks matching this; never applies in the world
 */
public record DisguiseRule(String key, String as, DropsMode drops, boolean hints, @Nullable JsonObject nbt) {

    public DisguiseRule {
        if (drops == null) drops = DropsMode.REAL;
    }

    public boolean isTag() {
        return key.startsWith("#");
    }

    /** The tag id without the leading {@code #}; only meaningful when {@link #isTag()}. */
    public String tagId() {
        return key.substring(1);
    }

    public boolean hasNbt() {
        return nbt != null && nbt.size() > 0;
    }
}
