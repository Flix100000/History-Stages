package net.bananemdnsa.historystages.client.editor.tab;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.bananemdnsa.historystages.data.LevelledLockEntry;
import org.jetbrains.annotations.Nullable;

/**
 * The per-row settings of the enchantment and effect tabs, kept by id.
 *
 * <p>By id rather than by position, unlike the item tab's extras: these tabs add each id once, so
 * the id is a stable key and removing a row needs no shifting. No Minecraft types, so the rules
 * can be pinned without a game.
 */
final class LevelledRows {

    private final Map<String, LevelledLockEntry> settings = new HashMap<>();

    /** Fills {@code ids} with the stored rows and remembers their settings. */
    void load(List<LevelledLockEntry> stored, List<String> ids) {
        settings.clear();
        ids.clear();
        for (LevelledLockEntry entry : stored) {
            if (ids.contains(entry.getId())) continue;
            ids.add(entry.getId());
            settings.put(entry.getId(), entry);
        }
    }

    @Nullable
    Integer minLevel(String id) {
        return get(id).getMinLevel();
    }

    boolean lockItems(String id) {
        return get(id).isLockItems();
    }

    void setMinLevel(String id, int level) {
        LevelledLockEntry current = get(id);
        settings.put(id, new LevelledLockEntry(id, level > 1 ? level : null, current.isLockItems()));
    }

    void toggleLockItems(String id) {
        LevelledLockEntry current = get(id);
        settings.put(id, new LevelledLockEntry(id, current.getMinLevel(), !current.isLockItems()));
    }

    void forget(String id) {
        settings.remove(id);
    }

    List<LevelledLockEntry> toEntries(List<String> ids) {
        List<LevelledLockEntry> entries = new ArrayList<>(ids.size());
        for (String id : ids) entries.add(get(id));
        return entries;
    }

    private LevelledLockEntry get(String id) {
        LevelledLockEntry entry = settings.get(id);
        return entry != null ? entry : new LevelledLockEntry(id);
    }
}
