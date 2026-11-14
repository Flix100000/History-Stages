package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.api.dependency.RequirementResult;
import net.bananemdnsa.historystages.client.cache.ClientDependencyCache;
import net.bananemdnsa.historystages.client.cache.ClientIndividualStageCache;
import net.bananemdnsa.historystages.client.cache.ClientLostStages;
import net.bananemdnsa.historystages.client.cache.ClientPlayerStageCache;
import net.bananemdnsa.historystages.client.cache.ClientZoneSelection;
import net.bananemdnsa.historystages.client.cache.ClientZoneShapes;
import net.bananemdnsa.historystages.client.editor.graph.GraphLayoutHistory;
import net.bananemdnsa.historystages.data.graph.GraphPos;
import net.bananemdnsa.historystages.data.lock.TradeLockKind;
import net.bananemdnsa.historystages.data.lock.TradePreview;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Leaving a server must take everything that server told the client with it. Bugs that only go away
 * "after a restart" are a cache this list forgot.
 */
class ClientDisconnectHandlerTest {

    @Test
    void leavingAServerForgetsWhatItSaid() {
        UUID someone = UUID.randomUUID();
        ClientIndividualStageCache.setUnlockedStages(Set.of("bronze"), Map.of("bronze", 1L));
        ClientPlayerStageCache.set(Map.of(someone, Set.of("bronze")));
        ClientLostStages.set(Set.of("iron"), Set.of("bronze"));
        ClientDependencyCache.update("iron", false, RequirementResult.noDependencies());
        LockBorderClientCache.set(List.of(new BoundingBox(0, 0, 0, 4, 4, 4)));
        TradeLockNotice.set(7, List.of("Iron Age"), TradeLockKind.GLOBAL);
        ClientTradeGoods.addFromServer(List.of(
                new TradePreview("minecraft:farmer", 1, "minecraft:bread", 1, "minecraft:wheat", 20, "", 0)));
        ClientZoneSelection.update("minecraft:overworld", true, 1, 2, 3, true, 4, 5, 6);
        ClientZoneShapes.update(List.of(new ClientZoneShapes.Visible(true, true, true, false, List.of())));
        GraphLayoutHistory.CLIENT.record(GraphLayoutHistory.Step.between(
                Map.of(false, GraphLayoutHistory.TreeState.of(true, Map.of())),
                Map.of(false, GraphLayoutHistory.TreeState.of(true, Map.of("iron", new GraphPos(1, 2))))));
        int recipeVersion = ClientRecipes.version();

        assertTrue(ClientIndividualStageCache.isStageUnlocked("bronze"), "setup");
        assertTrue(GraphLayoutHistory.CLIENT.canUndo(), "setup");
        assertFalse(ClientTradeGoods.isEmpty(), "setup");

        ClientDisconnectHandler.forgetServer();

        assertFalse(ClientIndividualStageCache.isStageUnlocked("bronze"), "individual stages");
        assertFalse(ClientPlayerStageCache.hasStage(someone, "bronze"), "other players' stages");
        assertFalse(ClientLostStages.isLost("iron", false), "lost global stages");
        assertFalse(ClientLostStages.isLost("bronze", true), "lost individual stages");
        assertNull(ClientDependencyCache.get("iron", false), "dependency results");
        assertTrue(LockBorderClientCache.get().isEmpty(), "structure lock borders");
        assertFalse(TradeLockNotice.appliesTo(7), "trade notice");
        assertTrue(ClientTradeGoods.isEmpty(), "merchant goods");
        assertFalse(ClientZoneSelection.hasFirst() || ClientZoneSelection.hasSecond(), "zone selection");
        assertTrue(ClientZoneShapes.isEmpty(), "zone shapes");
        assertFalse(GraphLayoutHistory.CLIENT.canUndo(), "graph undo history");
        assertEquals(recipeVersion + 1, ClientRecipes.version(), "the server's recipe list is dropped");
    }
}
