package net.bananemdnsa.historystages.client.editor.tab;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.bananemdnsa.historystages.api.editor.CategoryTab;
import net.bananemdnsa.historystages.api.editor.TabInputContext;
import net.bananemdnsa.historystages.api.editor.TabRenderContext;
import net.bananemdnsa.historystages.api.editor.widget.EditorRowList;
import net.bananemdnsa.historystages.api.editor.widget.PickerOverlay;
import net.bananemdnsa.historystages.api.lock.LockCategory;
import net.bananemdnsa.historystages.client.editor.ZoneEditScreen;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.ZoneEntry;
import net.bananemdnsa.historystages.data.lock.ZoneRowText;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The zones of one stage, as a list of rows.
 *
 * <p>Not built on {@code AbstractCategoryTab}: that one assumes a searchable picker over string
 * ids, and a zone is not something you pick off a list — it is drawn. So the Add button creates an
 * empty zone and opens it, and there is no overlay to speak of.
 *
 * <p>Opening a screen goes through {@code openScreen} rather than
 * {@code Minecraft.getInstance().setScreen}: a tab is not supposed to reach past its host, and
 * the host is the thing that knows what to return to.
 */
public class ZoneCategoryTab implements CategoryTab {

    /**
     * How the tab gets a screen in front of the player.
     *
     * <p>The host builds it rather than merely showing it, because only the host knows what the
     * new screen has to return to when it closes — the tab has no handle on the editor it lives
     * in, and handing it one would be a worse trade than this.
     */
    @FunctionalInterface
    public interface ScreenOpener {
        void open(java.util.function.Function<Screen, Screen> factory);
    }

    private final LockCategory<ZoneEntry> category;
    private final Runnable onChanged;
    private final ScreenOpener openScreen;

    /** Taller than the other tabs': a zone needs a second line to say what it does. */
    private static final int ROW_HEIGHT = 32;

    private final EditorRowList rows = new EditorRowList(ROW_HEIGHT);
    private final List<ZoneEntry> edit = new ArrayList<>();

    public ZoneCategoryTab(LockCategory<ZoneEntry> category, Runnable onChanged,
                           ScreenOpener openScreen) {
        this.category = category;
        this.onChanged = onChanged;
        this.openScreen = openScreen;
    }

    @Override
    public String categoryId() {
        return category.id();
    }

    @Override
    public String tabLangKey() {
        return category.tabLangKey();
    }

    @Override
    public String tooltipLangKey() {
        return category.tooltipLangKey();
    }

    /** Both scopes: a zone gates a place, and a place can be gated per player as well. */
    @Override
    public boolean availableForIndividualStages() {
        return true;
    }

    @Override
    public List<String> entries() {
        List<String> out = new ArrayList<>(edit.size());
        for (ZoneEntry zone : edit) {
            out.add(zone.getName().isEmpty()
                    ? Component.translatable("editor.historystages.zone.unnamed").getString()
                    : zone.getName());
        }
        return out;
    }

    /** The host scrolls by what this says, and it rows at 22 unless told otherwise. */
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

    @Override
    public void load(StageEntry stage) {
        edit.clear();
        for (ZoneEntry zone : category.read(stage)) {
            // A copy, so cancelling the stage editor really does discard the edits.
            edit.add(zone.copy());
        }
    }

    @Override
    public void store(StageEntry stage) {
        category.write(stage, new ArrayList<>(edit));
    }

    @Override
    public boolean renderContent(TabRenderContext ctx) {
        rows.render(ctx, edit.size(), (row, i) -> {
            ZoneEntry zone = edit.get(i);
            row.text(entries().get(i));
            if (zone.hasShapes()) {
                row.subtitle(describe(zone));
            } else {
                row.subtitle(Component.translatable("editor.historystages.zone.summary.no_shape",
                        zone.getDimension()).getString(), 0xCC5555);
            }
        });
        return true;
    }

    /**
     * The second line: where the zone is, then what it does.
     *
     * <p>The words come from {@link ZoneRowText}, which hands back lang keys — it has neither the
     * font nor the language, and this does.
     */
    private String describe(ZoneEntry zone) {
        StringBuilder out = new StringBuilder(zone.getDimension());
        out.append(" · ").append(zone.getShapes().size() == 1
                ? Component.translatable("editor.historystages.zone.summary.one_shape").getString()
                : Component.translatable("editor.historystages.zone.summary.shapes",
                        zone.getShapes().size()).getString());

        List<String> keys = ZoneRowText.summariseRules(zone);
        if (!keys.isEmpty()) {
            out.append(" · ");
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) out.append(", ");
                out.append(Component.translatable(keys.get(i)).getString());
            }
        }
        return out.toString();
    }

    @Override
    public boolean mouseClicked(TabInputContext ctx, int button) {
        if (button != 0) return false;
        // Slots first, then the row. The other way round, a click on a slot this tab grows later
        // would fire the slot and open the zone on top of it.
        if (rows.mouseClicked(ctx)) return true;

        // The row itself opens the zone. A button labelled "edit" on a row whose only purpose is
        // to be edited is a second thing to aim at for nothing.
        int index = rows.rowAt(ctx, edit.size());
        if (index < 0 || index >= edit.size()) return false;
        open(edit.get(index), index);
        return true;
    }

    /**
     * Opens the zone at {@code index}, for a caller outside the tab.
     *
     * <p>The host's right-click menu needs it: the row opens the zone on a left click, and a menu
     * offering only copy and remove would be missing the one thing this row is for.
     */
    public void openAt(int index) {
        if (index < 0 || index >= edit.size()) return;
        open(edit.get(index), index);
    }

    /**
     * Which row the cursor is over.
     *
     * <p>Not optional for a self-drawing tab: the host asks this before it opens the right-click
     * menu, and a tab that leaves it at the default answers for rows of the standard height —
     * these are taller, so every menu opened on the wrong entry or on none at all.
     */
    @Override
    public int rowAt(TabInputContext ctx) {
        return rows.rowAt(ctx, edit.size());
    }

    @Override
    public void onShown() {
        rows.resetSlideIn();
    }

    /** No overlay: this tab's Add opens a screen, and its rows open one too. */
    @Override
    @Nullable
    public PickerOverlay activeOverlay() {
        return null;
    }

    @Override
    public void rebuildPicker() {
        // Nothing to rebuild — see activeOverlay.
    }

    /**
     * The Add button. A zone is drawn rather than picked, so this makes an empty one and opens it
     * instead of putting up a list of things that already exist.
     */
    @Override
    public void openPicker(int centerX, int centerY, int parentWidth) {
        ZoneEntry zone = new ZoneEntry();
        zone.setName(Component.translatable("editor.historystages.zone.default_name", edit.size() + 1)
                .getString());
        open(zone, -1);
    }

    private void open(ZoneEntry zone, int index) {
        openScreen.open(parent -> new ZoneEditScreen(parent, zone, edited -> {
            if (index < 0) {
                edit.add(edited);
            } else {
                edit.set(index, edited);
            }
            onChanged.run();
        }));
    }
}
