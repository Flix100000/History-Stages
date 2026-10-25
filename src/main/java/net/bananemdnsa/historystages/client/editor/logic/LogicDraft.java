package net.bananemdnsa.historystages.client.editor.logic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.data.logic.Condition;
import net.bananemdnsa.historystages.data.logic.LogicBlock;
import net.bananemdnsa.historystages.data.logic.LogicBlockParams;
import net.bananemdnsa.historystages.data.logic.LogicBlockTypes;
import net.bananemdnsa.historystages.data.logic.LogicCodec;
import net.bananemdnsa.historystages.data.logic.LogicGroups;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The logic screen's working copy of a stage's blocks: mutable, and shaped the way the editor
 * draws them.
 *
 * <p>A block the editor can show becomes {@link Block#editable} groups and terms. A block it cannot
 * — an unknown type, or a hand-written tree deeper than groups — stays exactly as read and is
 * written back untouched; the screen shows it as a read-only card. Free of Minecraft so the
 * round trip is unit-tested.
 */
public final class LogicDraft {

    public final List<Block> blocks = new ArrayList<>();

    public static final class Block {
        public final String type;
        /** Null for a read-only block. */
        @Nullable public final List<Group> groups;
        /**
         * The block's own fields besides type and condition: the settings the editor changes
         * (mode, percentages) and anything a newer version put there. Written back as the base
         * of the block, so none of it is lost.
         */
        final JsonObject extra;
        /** Set for a read-only block: written back verbatim. */
        @Nullable final LogicBlock original;

        private Block(String type, @Nullable List<Group> groups, @Nullable JsonElement raw,
                      @Nullable LogicBlock original) {
            this.type = type;
            this.groups = groups;
            this.extra = raw != null && raw.isJsonObject() ? raw.getAsJsonObject().deepCopy() : new JsonObject();
            this.original = original;
        }

        public LogicBlockParams.HiddenMode hiddenMode() {
            return LogicBlockParams.hiddenMode(new LogicBlock(type, new Condition.All(List.of()), extra));
        }

        public void setHiddenMode(LogicBlockParams.HiddenMode mode) {
            LogicBlockParams.setHiddenMode(extra, mode);
        }

        public int percent(String key) {
            return LogicBlockParams.percent(extra, key);
        }

        public void setPercent(String key, int percent) {
            LogicBlockParams.setPercent(extra, key, percent);
        }

        public static Block newBlock(String type) {
            List<Group> groups = new ArrayList<>();
            groups.add(new Group(true));
            return new Block(type, groups, null, null);
        }

        public boolean editable() {
            return groups != null;
        }

        /** The block as the evaluator sees it; for a read-only block, the original. */
        public LogicBlock toLogicBlock() {
            if (original != null) return original;
            return new LogicBlock(type, toGroups().toCondition(), extra);
        }

        LogicGroups toGroups() {
            List<LogicGroups.Group> out = new ArrayList<>();
            for (Group group : groups) {
                List<LogicGroups.Term> terms = new ArrayList<>();
                for (Term term : group.terms) {
                    if (term.stageId == null || term.stageId.isBlank()) continue;
                    terms.add(new LogicGroups.Term(term.stageId, term.scope, term.unlocked));
                }
                if (!terms.isEmpty()) out.add(new LogicGroups.Group(group.all, List.copyOf(terms)));
            }
            return new LogicGroups(List.copyOf(out));
        }

        /** A block with no filled-in term would mean "always", which no one ever wants. */
        public boolean isEmpty() {
            return editable() && toGroups().groups().isEmpty();
        }
    }

    public static final class Group {
        public boolean all;
        public final List<Term> terms = new ArrayList<>();

        public Group(boolean all) {
            this.all = all;
        }
    }

    public static final class Term {
        /** Null or blank until the author picks a stage. */
        @Nullable public String stageId;
        /** Null = the owning stage's scope. */
        @Nullable public StageScope scope;
        public boolean unlocked;

        public Term(@Nullable String stageId, @Nullable StageScope scope, boolean unlocked) {
            this.stageId = stageId;
            this.scope = scope;
            this.unlocked = unlocked;
        }
    }

    public static LogicDraft read(@Nullable JsonArray logic) {
        LogicDraft draft = new LogicDraft();
        for (LogicBlock block : LogicCodec.read(logic)) {
            var groups = LogicBlockTypes.isKnown(block.type())
                    ? LogicGroups.fromCondition(block.condition()) : java.util.Optional.<LogicGroups>empty();
            if (groups.isEmpty()) {
                draft.blocks.add(new Block(block.type() == null ? "?" : block.type(), null, block.raw(), block));
                continue;
            }
            List<Group> editable = new ArrayList<>();
            for (LogicGroups.Group g : groups.get().groups()) {
                Group group = new Group(g.all());
                for (LogicGroups.Term t : g.terms()) group.terms.add(new Term(t.stageId(), t.scope(), t.unlocked()));
                editable.add(group);
            }
            draft.blocks.add(new Block(block.type(), editable, block.raw(), null));
        }
        return draft;
    }

    /**
     * The array to store, or null when nothing is left. Blocks without a single filled-in term are
     * dropped: an empty condition evaluates to "always", and a half-built rule must not block a
     * stage for good. The screen warns about them before this runs.
     */
    @Nullable
    public JsonArray write() {
        List<LogicBlock> out = new ArrayList<>();
        for (Block block : blocks) {
            if (block.isEmpty()) continue;
            out.add(block.toLogicBlock());
        }
        return out.isEmpty() ? null : LogicCodec.write(out);
    }
}
