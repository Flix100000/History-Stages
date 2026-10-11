package net.bananemdnsa.historystages.api.client;

import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.stage.StageStateView;
import net.bananemdnsa.historystages.api.stage.StageStates;
import net.bananemdnsa.historystages.client.cache.ClientStageCache;
import net.bananemdnsa.historystages.client.cache.ClientStageStates;
import net.bananemdnsa.historystages.client.display.BlockedLines;
import org.jetbrains.annotations.Nullable;

/**
 * What the local player has, on the client: for a screen that greys something out or a tooltip
 * that names a stage before the server has been asked.
 *
 * <p><strong>Client only.</strong> Touching this class from code that also runs on a dedicated
 * server crashes it there. Keep the call behind a client-only class, as with any client API.
 *
 * <p>The answers come from what the server last synced, so they trail a change by one packet.
 * The server stays the authority: never let a client answer decide something that matters.
 */
public final class ClientStages {

    private ClientStages() {}

    /** True if the world has this global stage, as last synced. */
    public static boolean isUnlockedGlobal(String stageId) {
        return ClientStageCache.isStageUnlocked(stageId);
    }

    /** True if the local player has this individual stage, as last synced. */
    public static boolean isUnlockedIndividual(String stageId) {
        return ClientStageStates.individual().isUnlocked(stageId);
    }

    /** True if the stage is open for the local player in whichever scope it lives in. */
    public static boolean isUnlocked(String stageId) {
        StageScope scope = StageStates.scopeOf(stageId);
        if (scope == null) return false;
        return scope == StageScope.GLOBAL ? isUnlockedGlobal(stageId) : isUnlockedIndividual(stageId);
    }

    /** The global stages as a live view. */
    public static StageStateView globalView() {
        return ClientStageStates.global();
    }

    /** The local player's individual stages as a live view. */
    public static StageStateView individualView() {
        return ClientStageStates.individual();
    }

    /**
     * The stage's name as the local player may see it: {@code "???"} while its logic hides it,
     * the same as the mod's own tooltips. Unknown ids come back unchanged.
     */
    public static String displayName(String stageId) {
        StageScope scope = StageStates.scopeOf(stageId);
        if (scope == null) return stageId;
        return BlockedLines.displayName(stageId, scope == StageScope.INDIVIDUAL);
    }

    /** {@link StageStates#scopeOf}, which reads the synced definitions on the client. */
    @Nullable
    public static StageScope scopeOf(String stageId) {
        return StageStates.scopeOf(stageId);
    }
}
