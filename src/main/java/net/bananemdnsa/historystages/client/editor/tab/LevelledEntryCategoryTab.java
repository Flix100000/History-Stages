package net.bananemdnsa.historystages.client.editor.tab;

import java.util.function.Function;

import net.bananemdnsa.historystages.api.editor.AbstractCategoryTab;
import net.bananemdnsa.historystages.api.lock.LockCategory;
import net.bananemdnsa.historystages.data.LevelledLockEntry;
import net.bananemdnsa.historystages.data.StageEntry;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The enchantment or the effect section: ids picked from a list, each with a lowest locked level
 * and, for enchantments, whether items already carrying it are locked too.
 *
 * <p>The host draws the rows. This tab answers what goes on them — an icon, the localised name,
 * and a badge for whatever differs from "every level, items too" — and the screen's right-click
 * menu changes the two settings through {@link #setMinLevel} and {@link #toggleLockItems}.
 */
public final class LevelledEntryCategoryTab extends AbstractCategoryTab {

    private final LockCategory<LevelledLockEntry> category;
    private final LevelledRows rows = new LevelledRows();
    private final String iconItemId;
    private final Function<String, String> displayName;
    private final boolean hasLockItemsSwitch;

    public LevelledEntryCategoryTab(LockCategory<LevelledLockEntry> category,
                                    PickerFactory pickerFactory, Runnable onChanged,
                                    String iconItemId, Function<String, String> displayName,
                                    boolean hasLockItemsSwitch) {
        super(category, pickerFactory, onChanged);
        this.category = category;
        this.iconItemId = iconItemId;
        this.displayName = displayName;
        this.hasLockItemsSwitch = hasLockItemsSwitch;
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

    public boolean hasLockItemsSwitch() {
        return hasLockItemsSwitch;
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

    public boolean lockItems(int index) {
        return rows.lockItems(entries().get(index));
    }

    public void toggleLockItems(int index) {
        rows.toggleLockItems(entries().get(index));
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
        String id = entries().get(index);
        Integer min = rows.minLevel(id);
        String level = min == null ? null
                : Component.translatable("editor.historystages.levelled.from_level",
                        Component.translatable("enchantment.level." + min)).getString();
        String items = hasLockItemsSwitch && !rows.lockItems(id)
                ? Component.translatable("editor.historystages.levelled.items_free").getString() : null;
        if (level == null) return items;
        return items == null ? level : level + " · " + items;
    }

    @Override
    @Nullable
    public String badgeTooltip(int index) {
        return badgeText(index) == null ? null
                : Component.translatable("editor.historystages.levelled.badge.desc").getString();
    }
}
