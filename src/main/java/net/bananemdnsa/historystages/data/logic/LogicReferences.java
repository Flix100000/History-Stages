package net.bananemdnsa.historystages.data.logic;

import net.bananemdnsa.historystages.api.stage.StageScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What is wrong with the stage references in a stage's logic, for the load log and the editor.
 *
 * <p>None of these stop anything from loading. Each case already has a defined meaning at runtime
 * (the term is simply false); this only makes sure an author finds out instead of wondering why a
 * rule never fires.
 */
public final class LogicReferences {

    private LogicReferences() {}

    public enum Kind { MISSING, SCOPE, SELF, UNKNOWN_TYPE, UNKNOWN_NODE }

    public record Problem(Kind kind, String stageId, int blockIndex) {}

    public static List<Problem> problems(String ownerId, StageScope owner, List<LogicBlock> blocks,
                                         Set<String> globalIds, Set<String> individualIds) {
        List<Problem> out = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            LogicBlock block = blocks.get(i);
            final int index = i;
            if (!LogicBlockTypes.isKnown(block.type())) {
                out.add(new Problem(Kind.UNKNOWN_TYPE, String.valueOf(block.type()), index));
                continue;
            }
            if (Condition.containsUnknown(block.condition())) {
                out.add(new Problem(Kind.UNKNOWN_NODE, "", index));
            }
            Condition.forEachTerm(block.condition(), term -> {
                StageScope scope = term.scope() != null ? term.scope() : owner;
                if (owner == StageScope.GLOBAL && scope == StageScope.INDIVIDUAL) {
                    out.add(new Problem(Kind.SCOPE, term.stageId(), index));
                    return;
                }
                if (scope == owner && term.stageId().equals(ownerId)) {
                    out.add(new Problem(Kind.SELF, term.stageId(), index));
                    return;
                }
                Set<String> known = scope == StageScope.GLOBAL ? globalIds : individualIds;
                if (!known.contains(term.stageId())) {
                    out.add(new Problem(Kind.MISSING, term.stageId(), index));
                }
            });
        }
        return out;
    }
}
