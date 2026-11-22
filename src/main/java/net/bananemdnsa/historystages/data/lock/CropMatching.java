package net.bananemdnsa.historystages.data.lock;

import java.util.Collection;
import java.util.List;

/**
 * Whether a plant block is named by a stage's crop entries: an exact block id, or
 * {@code #tag} when the block carries that tag.
 *
 * <p>Free of Minecraft types so the client (Jade) and the tests can use the same rule as the
 * server's index lookup.
 */
public final class CropMatching {

    private CropMatching() {}

    public static boolean matches(List<String> entries, String blockId, Collection<String> blockTagIds) {
        for (String entry : entries) {
            if (entry == null || entry.isEmpty()) continue;
            if (entry.startsWith("#")) {
                String tag = entry.substring(1);
                if (!tag.isEmpty() && blockTagIds.contains(tag)) return true;
            } else if (entry.equals(blockId)) {
                return true;
            }
        }
        return false;
    }
}
