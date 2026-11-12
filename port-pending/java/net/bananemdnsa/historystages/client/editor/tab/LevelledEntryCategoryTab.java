package net.bananemdnsa.historystages.client.editor.tab;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.bananemdnsa.historystages.api.editor.AbstractCategoryTab;
import net.bananemdnsa.historystages.api.lock.LockCategory;
import net.bananemdnsa.historystages.data.LevelledLockEntry;
import net.bananemdnsa.historystages.data.StageEntry;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The enchantment or the effect section: ids picked from a list, each with a level from which it
 * is locked, the actions it locks and the item types it spares.
 *
 * <p>The host draws the rows. This tab answers what goes on them — an icon, the localised name,
 * and a badge for whatever differs from "every level, every action, every item" — and the
 * screen's right-click menu changes the settings through the setters here.
 */
public final class LevelledEntryCategoryTab extends AbstractCategoryTab {

    private final LockCategory<LevelledLockEntry> category;
    private final LevelledRows rows = new LevelledRows();
    private final String iconItemId;
    private final Function<String, String> displayName;
    private final boolean isEnchantments;

    public LevelledEntryCategoryTab(LockCategory<LevelledLockEntry> category,
                                    PickerFactory pickerFactory, Runnable onChanged,
                                    String iconItemId, Function<String, String> displayName,
                                    boolean isEnchantments) {
        super(category, pickerFactory, onChanged);
        this.category = category;
        this.iconItemId = iconItemId;
        this.displayName = displayName;
        this.isEnchantments = isEnchantments;
    }

    @Override
    public void load(StageEntry stage) {
        rows.load(category.read(stage), entries());
    }

    @Override
    public void store(StageEntry stage) {
        category.write(stage, rows.toEntries(entries()));
    }

    @Override
    public void removeAt(int index) {
        if (index < 0 || index >= entries().size()) return;
        rows.forget(entries().get(index));
        super.removeAt(index);
    }

    /** True for the enchantment section; it decides which item types and levels to offer. */
    public boolean isEnchantments() {
        return isEnchantments;
    }

    /** The levels the level menu offers; see {@link LevelChoices}. */
    public List<Integer> levelChoices(int max, int current) {
        return LevelChoices.of(max, current);
    }

    public List<String> vocabulary() {
        return category.lockActions();
    }

    /** 1 when every level is locked. */
    public int minLevel(int index) {
        Integer min = rows.minLevel(entries().get(index));
        return min == null ? 1 : min;
    }

    public void setMinLevel(int index, int level) {
        rows.setMinLevel(entries().get(index), level);
        markChanged();
    }

    /** null when every action is locked. */
    @Nullable
    public List<String> lockActions(int index) {
        return rows.lockActions(entries().get(index));
    }

    /** All locked is what an entry means without a list, so it is stored as no list. */
    public void setLockActions(int index, List<String> locked) {
        rows.setLockActions(entries().get(index),
                locked.size() == vocabulary().size() ? null : new ArrayList<>(locked));
        markChanged();
    }

    public List<String> excludedItemTypes(int index) {
        List<String> excluded = rows.excludedItemTypes(entries().get(index));
        return excluded != null ? excluded : List.of();
    }

    public void setExcludedItemTypes(int index, List<String> excluded) {
        rows.setExcludedItemTypes(entries().get(index), excluded.isEmpty() ? null : excluded);
        markChanged();
    }

    @Override
    @Nullable
    public String iconItemId(int index) {
        return iconItemId;
    }

    @Override
    @Nullable
    public String displayText(int index, String entry) {
        return displayName.apply(entry);
    }

    @Override
    @Nullable
    public String badgeText(int index) {
        List<String> parts = new ArrayList<>(3);
        int min = minLevel(index);
        if (min > 1) {
            parts.add(Component.translatable("editor.historystages.levelled.from_level",
                    Component.translatable("enchantment.level." + min)).getString());
        }
        List<String> actions = lockActions(index);
        if (actions != null) {
            parts.add(Component.translatable("editor.historystages.levelled.badge.actions",
                    actions.size(), vocabulary().size()).getString());
        }
        int spared = excludedItemTypes(index).size();
        if (spared > 0) {
            parts.add(Component.translatable("editor.historystages.levelled.badge.types", spared).getString());
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    @Override
    @Nullable
    public String badgeTooltip(int index) {
        return badgeText(index) == null ? null
                : Component.translatable("editor.historystages.levelled.badge.desc").getString();
    }
}
