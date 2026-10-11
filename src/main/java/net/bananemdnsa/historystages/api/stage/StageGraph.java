package net.bananemdnsa.historystages.api.stage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.graph.GraphReachability;
import net.bananemdnsa.historystages.data.graph.NodeState;
import net.bananemdnsa.historystages.data.logic.StageLogicGate;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.server.level.ServerPlayer;

/**
 * The stages as a graph: which stage requires which, and where a player stands in it. For tech
 * trees, quest books and "what next" hints.
 *
 * <p>An edge is a stage named in another stage's dependency groups, from either tree. Only stage
 * requirements make edges; items, XP and the rest do not. Reachability follows the stage graph
 * screen exactly, so an addon and the graph never disagree about it.
 */
public final class StageGraph {

    private StageGraph() {}

    /** Where a stage stands for one viewer, as the graph colours it. */
    public enum Node {
        /** The viewer has it. */
        UNLOCKED,
        /** Its stage requirements are met; it can be worked towards now. */
        REACHABLE,
        /** Its stage requirements are met, but its logic blocks it right now. */
        BLOCKED,
        /** A stage it requires is still missing. */
        LOCKED
    }

    /** The stages this one names in its dependency groups, in order, each once. */
    public static List<StageRef> requires(StageRef stage) {
        Set<String> keys = StageManager.graphPrerequisites().getOrDefault(key(stage), Set.of());
        List<StageRef> out = new ArrayList<>(keys.size());
        for (String k : keys) out.add(refOf(k));
        return out;
    }

    /** The stages that name this one in their dependency groups. */
    public static List<StageRef> requiredBy(StageRef stage) {
        String target = key(stage);
        List<StageRef> out = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : StageManager.graphPrerequisites().entrySet()) {
            if (e.getValue().contains(target)) out.add(refOf(e.getKey()));
        }
        return out;
    }

    /**
     * Where the stage stands for this player: global stages by the world's state, individual ones
     * by the player's. AND and OR groups are honoured the way the graph does.
     */
    public static Node state(StageRef stage, ServerPlayer player) {
        NodeState state = GraphReachability.resolve(key(stage), StageManager.graphDependencyGroups(), unlocked(player));
        return switch (state) {
            case UNLOCKED -> Node.UNLOCKED;
            case LOCKED -> Node.LOCKED;
            case REACHABLE, BLOCKED -> isBlocked(stage, player) ? Node.BLOCKED : Node.REACHABLE;
        };
    }

    /**
     * Every stage this player still lacks on the way to {@code target}, requirements before the
     * stages that need them, each once. The target itself is not listed. A stage the player
     * already has ends its branch: what lies behind it no longer matters.
     *
     * <p>Alternatives in OR groups are all listed, since any of them could be the way; a quest
     * book showing one route has to pick among them itself.
     */
    public static List<StageRef> missingOnPathTo(StageRef target, ServerPlayer player) {
        Map<String, Set<String>> prereqs = StageManager.graphPrerequisites();
        Predicate<String> has = unlocked(player);
        Set<String> out = new LinkedHashSet<>();
        visit(key(target), prereqs, has, new HashSet<>(), out);
        out.remove(key(target));
        List<StageRef> refs = new ArrayList<>(out.size());
        for (String k : out) refs.add(refOf(k));
        return refs;
    }

    private static void visit(String key, Map<String, Set<String>> prereqs, Predicate<String> has,
                              Set<String> seen, Set<String> out) {
        // The editor refuses cycles, but a hand-edited file might still hold one.
        if (!seen.add(key) || has.test(key)) return;
        for (String dep : prereqs.getOrDefault(key, Set.of())) visit(dep, prereqs, has, seen, out);
        out.add(key);
    }

    private static boolean isBlocked(StageRef stage, ServerPlayer player) {
        return stage.isIndividual()
                ? StageLogicGate.individual(stage.id(), player.getUUID()).isBlocked()
                : StageLogicGate.global(stage.id()).isBlocked();
    }

    private static Predicate<String> unlocked(ServerPlayer player) {
        StageData global = StageData.get(player.serverLevel());
        IndividualStageData individual = IndividualStageData.get(player.serverLevel());
        return key -> key.startsWith("i:")
                ? individual.hasStage(player.getUUID(), key.substring(2))
                : global.hasStage(key.substring(2));
    }

    private static String key(StageRef stage) {
        return StageManager.graphKey(stage.id(), stage.isIndividual());
    }

    private static StageRef refOf(String key) {
        return key.startsWith("i:") ? StageRef.individual(key.substring(2)) : StageRef.global(key.substring(2));
    }
}
