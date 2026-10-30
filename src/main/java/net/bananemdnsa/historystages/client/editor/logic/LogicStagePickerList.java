package net.bananemdnsa.historystages.client.editor.logic;

import net.bananemdnsa.historystages.api.editor.widget.AbstractSearchableList;
import net.bananemdnsa.historystages.api.editor.widget.SearchBar;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Picks the stage a logic term refers to.
 *
 * <p>Unlike the dependency picker this lists both trees at once for an individual stage, since its
 * conditions may look at either, and it keeps temporary stages: "blocked while a temporary stage
 * is active" is a perfectly good rule. A global stage only ever sees global stages. The stage being
 * edited is left out; a rule about itself cannot change anything.
 *
 * <p>Selections come back as {@code g:<id>} or {@code i:<id>}; see {@link #parse}.
 */
public class LogicStagePickerList extends AbstractSearchableList<LogicStagePickerList.Entry> {

    public static final String GLOBAL_PREFIX = "g:";
    public static final String INDIVIDUAL_PREFIX = "i:";

    private final StageScope owner;
    private final String ownerId;

    public LogicStagePickerList(Consumer<String> onSelect, StageScope owner, String ownerId) {
        super(Component.translatable("editor.historystages.search.placeholder.stages").getString(), onSelect, null);
        this.owner = owner;
        this.ownerId = ownerId;
    }

    /** The picked stage as (scope, id). */
    public record Picked(StageScope scope, String id) {}

    public static Picked parse(String selection) {
        if (selection.startsWith(INDIVIDUAL_PREFIX)) {
            return new Picked(StageScope.INDIVIDUAL, selection.substring(INDIVIDUAL_PREFIX.length()));
        }
        String id = selection.startsWith(GLOBAL_PREFIX) ? selection.substring(GLOBAL_PREFIX.length()) : selection;
        return new Picked(StageScope.GLOBAL, id);
    }

    @Override
    protected void configureFilters(SearchBar bar) {
        // Stage ids are pack-defined; the vanilla/modded filters mean nothing here.
    }

    @Override
    protected List<Entry> loadEntries() {
        List<Entry> list = new ArrayList<>();
        add(list, StageManager.getStages(), StageScope.GLOBAL);
        if (owner == StageScope.INDIVIDUAL) add(list, StageManager.getIndividualStages(), StageScope.INDIVIDUAL);
        list.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
        return list;
    }

    private void add(List<Entry> out, Map<String, StageEntry> stages, StageScope scope) {
        for (Map.Entry<String, StageEntry> e : stages.entrySet()) {
            if (scope == owner && e.getKey().equals(ownerId)) continue;
            out.add(new Entry(e.getKey(), e.getValue().getDisplayName(), scope));
        }
    }

    @Override
    protected String getIdForFilter(Entry entry) {
        return entry.id;
    }

    @Override
    protected boolean matchesQuery(Entry entry, String lowerCaseQuery) {
        return entry.id.toLowerCase(Locale.ROOT).contains(lowerCaseQuery)
                || entry.displayName.toLowerCase(Locale.ROOT).contains(lowerCaseQuery);
    }

    @Override
    protected String selectionValueOf(Entry entry) {
        return (entry.scope == StageScope.INDIVIDUAL ? INDIVIDUAL_PREFIX : GLOBAL_PREFIX) + entry.id;
    }

    @Override
    protected void renderRow(GuiGraphics g, Font font, Entry entry, int x, int y, int w, int h,
                             boolean hovered, int rowIndex) {
        String tag = owner == StageScope.INDIVIDUAL
                ? " " + Component.translatable(entry.scope == StageScope.GLOBAL
                        ? "editor.historystages.logic.scope_tag.global"
                        : "editor.historystages.logic.scope_tag.individual").getString()
                : "";
        drawRowMarqueeText(g, font, entry.displayName + tag + " §7(" + entry.id + ")", x, y, w, h, hovered, rowIndex);
    }

    public record Entry(String id, String displayName, StageScope scope) {}
}
