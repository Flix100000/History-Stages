package net.bananemdnsa.historystages.client.editor.graph;

import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.client.cache.ClientIndividualStageCache;
import net.bananemdnsa.historystages.client.cache.ClientStageCache;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.graph.CanvasBackgroundStyle;
import net.bananemdnsa.historystages.data.graph.GraphAutoLayout;
import net.bananemdnsa.historystages.data.graph.GraphStageData;
import net.bananemdnsa.historystages.data.graph.ResolvedCanvasBackground;
import net.bananemdnsa.historystages.data.graph.UnlockBackgroundResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The canvas background for the editor (graph.toml alone) and for the local player's map. */
public final class CanvasBackgrounds {

    private CanvasBackgrounds() {}

    public static ResolvedCanvasBackground fromConfig() {
        return withOverride(null);
    }

    public static ResolvedCanvasBackground withOverride(CanvasBackgroundStyle override) {
        return ResolvedCanvasBackground.resolve(
                GraphConfig.GRAPH.background.get().name(),
                GraphConfig.GRAPH.backgroundTexture.get(),
                GraphConfig.GRAPH.backgroundColor.get(),
                override);
    }

    /**
     * Walks every stage, not just the model's nodes: a stage the view filter hides is still
     * unlocked, and its background should not disappear just because its node does.
     */
    public static ResolvedCanvasBackground forPlayer() {
        Map<String, Integer> depth = GraphAutoLayout.layers(StageManager.graphPrerequisites());
        GraphStageData.Snapshot data = GraphStageData.get();

        List<UnlockBackgroundResolver.Candidate> candidates = new ArrayList<>();
        collect(StageManager.getStages().keySet(), false, data, depth, candidates);
        collect(StageManager.getIndividualStages().keySet(), true, data, depth, candidates);
        return withOverride(UnlockBackgroundResolver.pick(candidates));
    }

    private static void collect(Set<String> ids, boolean individual, GraphStageData.Snapshot data,
                                Map<String, Integer> depth,
                                List<UnlockBackgroundResolver.Candidate> out) {
        Map<String, GraphStageData.Entry> entries = data.tree(individual);
        for (String id : ids) {
            GraphStageData.Entry entry = entries.get(id);
            if (entry == null || entry.background == null || entry.background.isEmpty()) continue;

            boolean unlocked = individual
                    ? ClientIndividualStageCache.isStageUnlocked(id)
                    : ClientStageCache.isStageUnlocked(id);
            if (!unlocked) continue;

            Long time = individual
                    ? ClientIndividualStageCache.unlockTime(id)
                    : ClientStageCache.unlockTime(id);
            String key = StageManager.graphKey(id, individual);
            out.add(new UnlockBackgroundResolver.Candidate(
                    key, time, depth.getOrDefault(key, 0), entry.background));
        }
    }
}
