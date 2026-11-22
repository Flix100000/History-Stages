package net.bananemdnsa.historystages.client.editor.tab;

import java.util.ArrayList;
import java.util.List;
import net.bananemdnsa.historystages.api.editor.CategoryTab;
import net.bananemdnsa.historystages.api.editor.TabInputContext;
import net.bananemdnsa.historystages.api.editor.TabRenderContext;
import net.bananemdnsa.historystages.api.editor.widget.EditorRowList;
import net.bananemdnsa.historystages.api.editor.widget.PickerOverlay;
import net.bananemdnsa.historystages.api.lock.LockCategory;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.client.editor.SlotPickerScreen;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableSlotTargetList;
import net.bananemdnsa.historystages.client.slotlock.ClientSlotPick;
import net.bananemdnsa.historystages.client.slotlock.SlotSummary;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.SlotLockEntry;
import net.bananemdnsa.historystages.data.lock.SlotLockMode;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The slot locks of one stage. Built like the zones tab: a slot lock is clicked together on a GUI
 * rather than picked from a list, so its rows open a screen of their own.
 */
public class SlotCategoryTab implements CategoryTab {

    private static final int ROW_HEIGHT = 32;
    private static final int HIDE_COLOUR = 0xFF5555;
    private static final int OVERLAY_COLOUR = 0xFFCC00;

    private final LockCategory<SlotLockEntry> category;
    private final Runnable onChanged;
    private final ZoneCategoryTab.ScreenOpener openScreen;
    private final StageScope scope;

    private final EditorRowList rows = new EditorRowList(ROW_HEIGHT);
    private final List<SlotLockEntry> edit = new ArrayList<>();
    @Nullable private SearchableSlotTargetList chooser;

    public SlotCategoryTab(LockCategory<SlotLockEntry> category, Runnable onChanged,
                           ZoneCategoryTab.ScreenOpener openScreen, StageScope scope) {
        this.category = category;
        this.onChanged = onChanged;
        this.openScreen = openScreen;
        this.scope = scope;
    }

    @Override public String categoryId() { return category.id(); }
    @Override public String tabLangKey() { return category.tabLangKey(); }
    @Override public String tooltipLangKey() { return category.tooltipLangKey(); }

    /** Player slots are exactly what a per-player stage gates; container targets are offered only globally. */
    @Override public boolean availableForIndividualStages() { return true; }

    @Override
    public List<String> entries() {
        List<String> out = new ArrayList<>(edit.size());
        for (SlotLockEntry entry : edit) out.add(targetName(entry.target()));
        return out;
    }

    @Override
    public int contentHeight(int width) {
        return rows.heightForRows(edit.size());
    }

    @Override
    public void removeAt(int index) {
        if (index < 0 || index >= edit.size()) return;
        edit.remove(index);
        onChanged.run();
    }

    /** For the host's right-click menu. */
    public void duplicateAt(int index) {
        if (index < 0 || index >= edit.size()) return;
        edit.add(index + 1, edit.get(index).copy());
        onChanged.run();
    }

    @Override
    public void load(StageEntry stage) {
        edit.clear();
        for (SlotLockEntry entry : category.read(stage)) edit.add(entry.copy());
    }

    @Override
    public void store(StageEntry stage) {
        category.write(stage, new ArrayList<>(edit));
    }

    @Override
    public boolean renderContent(TabRenderContext ctx) {
        rows.render(ctx, edit.size(), (row, i) -> {
            SlotLockEntry entry = edit.get(i);
            ItemStack icon = iconFor(entry.target());
            row.leading(16, (g, x, y, w, h) -> g.renderItem(icon, x, y + (h - 16) / 2));
            String id = entry.isPlayerTarget() ? "" : " §8" + entry.target();
            row.text(targetName(entry.target()) + id);
            row.subtitle(summary(entry));
            boolean hide = entry.mode() == SlotLockMode.HIDE;
            row.badge(Component.translatable(hide
                            ? "editor.historystages.slots.mode.hide"
                            : "editor.historystages.slots.mode.overlay").getString(),
                    hide ? HIDE_COLOUR : OVERLAY_COLOUR, 70);
        });
        return true;
    }

    public static String summary(SlotLockEntry entry) {
        StringBuilder out = new StringBuilder();
        for (SlotSummary.Part part : SlotSummary.describe(entry)) {
            if (!out.isEmpty()) out.append(" · ");
            out.append(part.arg() == null
                    ? Component.translatable(part.langKey()).getString()
                    : Component.translatable(part.langKey(), part.arg()).getString());
        }
        return out.toString();
    }

    public static String targetName(String target) {
        if (SlotLockEntry.PLAYER.equals(target)) {
            return Component.translatable("editor.historystages.slots.target.player").getString();
        }
        return SearchableSlotTargetList.displayName(target);
    }

    private static ItemStack iconFor(String target) {
        if (SlotLockEntry.PLAYER.equals(target)) return new ItemStack(Items.PLAYER_HEAD);
        ResourceLocation id = ResourceLocation.tryParse(target);
        if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
            ItemStack stack = new ItemStack(BuiltInRegistries.BLOCK.get(id));
            if (!stack.isEmpty()) return stack;
        }
        return new ItemStack(Items.CHEST);
    }

    @Override
    public boolean mouseClicked(TabInputContext ctx, int button) {
        if (button != 0) return false;
        if (rows.mouseClicked(ctx)) return true;
        int index = rows.rowAt(ctx, edit.size());
        if (index < 0 || index >= edit.size()) return false;
        openAt(index);
        return true;
    }

    /** Opens the entry at {@code index} in the picker; also the host's "edit" menu entry. */
    public void openAt(int index) {
        if (index < 0 || index >= edit.size()) return;
        open(edit.get(index).copy(), index);
    }

    @Override
    public int rowAt(TabInputContext ctx) {
        return rows.rowAt(ctx, edit.size());
    }

    @Override
    public void onShown() {
        rows.resetSlideIn();
    }

    @Override
    @Nullable
    public PickerOverlay activeOverlay() {
        return chooser != null && chooser.isVisible() ? chooser : null;
    }

    @Override
    public void rebuildPicker() {
        chooser = null;
    }

    /**
     * Add. On an individual stage only the player's inventory can be locked, so it goes straight
     * there; otherwise the target is chosen first.
     */
    @Override
    public void openPicker(int centerX, int centerY, int parentWidth) {
        boolean containers = scope == StageScope.GLOBAL;
        if (!containers) {
            openNew(SlotLockEntry.PLAYER);
            return;
        }
        chooser = new SearchableSlotTargetList(this::openNew, () -> openNew(SlotLockEntry.PLAYER),
                this::pickInWorld, true);
        chooser.show(centerX, centerY, parentWidth);
    }

    private void openNew(String target) {
        open(new SlotLockEntry(target, new ArrayList<>(), SlotLockMode.OVERLAY), -1);
    }

    private void open(SlotLockEntry entry, int index) {
        openScreen.open(parent -> new SlotPickerScreen(parent, entry, edited -> store(edited, index)));
    }

    private void store(SlotLockEntry edited, int index) {
        if (index < 0) {
            edit.add(edited);
        } else {
            edit.set(index, edited);
        }
        onChanged.run();
    }

    /** Closes the editor; the pick mode brings it back and opens the picker on what was picked. */
    private void pickInWorld() {
        openScreen.open(parent -> {
            ClientSlotPick.start(parent, picked -> {
                edit.add(picked);
                onChanged.run();
                open(picked.copy(), edit.size() - 1);
            });
            return null;
        });
    }
}
