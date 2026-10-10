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

    void setMinLevel(String id, int level) {
        settings.put(id, get(id).withMinLevel(level > 1 ? level : null));
    }

    @Nullable
    List<String> lockActions(String id) {
        return get(id).getLockActions();
    }

    void setLockActions(String id, @Nullable List<String> actions) {
        settings.put(id, get(id).withLockActions(actions));
    }

    @Nullable
    List<String> excludedItemTypes(String id) {
        return get(id).getExcludedItemTypes();
    }

    void setExcludedItemTypes(String id, @Nullable List<String> excluded) {
        settings.put(id, get(id).withExcludedItemTypes(excluded));
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
