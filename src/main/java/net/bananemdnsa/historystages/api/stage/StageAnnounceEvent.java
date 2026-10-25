package net.bananemdnsa.historystages.api.stage;

import java.util.List;
import java.util.UUID;

import net.bananemdnsa.historystages.data.StageCauses;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.Cancelable;
import org.jetbrains.annotations.Nullable;

/**
 * Fired just before the mod tells players about an unlock or a relock: the chat line, the
 * actionbar, the sound and the toast. Cancel it to show something of your own instead — the
 * stage change itself has already happened and is not affected.
 *
 * <p>Not fired for a stage set not to notify. A batch unlock arrives as one event naming all its
 * stages, and so do the "unlock everything" command and the creative scroll. Which of chat,
 * actionbar, sound and toast actually go out is still up to the server's config.
 */
@Cancelable
public class StageAnnounceEvent extends Event {
    private final List<String> stageIds;
    private final List<String> displayNames;
    @Nullable
    private final UUID playerUUID;
    private final boolean unlocked;
    private final StageChangeCause cause = StageCauses.current();

    public StageAnnounceEvent(List<String> stageIds, List<String> displayNames, @Nullable UUID playerUUID,
                              boolean unlocked) {
        this.stageIds = List.copyOf(stageIds);
        this.displayNames = List.copyOf(displayNames);
        this.playerUUID = playerUUID;
        this.unlocked = unlocked;
    }

    /** The stages announced, usually one. */
    public List<String> getStageIds() {
        return stageIds;
    }

    /** Their display names, in the same order. */
    public List<String> getDisplayNames() {
        return displayNames;
    }

    /** The one player told, for an individual stage; null when every player is told. */
    @Nullable
    public UUID getPlayerUUID() {
        return playerUUID;
    }

    /** True for an unlock, false for a relock. */
    public boolean isUnlock() {
        return unlocked;
    }

    public StageChangeCause getCause() {
        return cause;
    }
}
