package net.bananemdnsa.historystages.api.stage;

import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Custom Forge events fired when stages are unlocked or locked.
 * These can be listened to by other mods, KubeJS, or CraftTweaker.
 *
 * <p>Example usage with KubeJS:</p>
 * <pre>
 * ForgeEvents.onEvent('net.bananemdnsa.historystages.api.stage.StageEvent$Unlocked', event => {
 *     console.log('Stage unlocked: ' + event.getStageId());
 * });
 * </pre>
 */
public abstract class StageEvent extends Event {
    private final String stageId;
    private final String displayName;
    // Read when the event is built, so whichever path posts it carries its own cause.
    private final StageChangeCause cause = net.bananemdnsa.historystages.data.StageCauses.current();

    protected StageEvent(String stageId, String displayName) {
        this.stageId = stageId;
        this.displayName = displayName;
    }

    public String getStageId() {
        return stageId;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Why this happened: research, a trigger, a command, another mod, and so on. */
    public StageChangeCause getCause() {
        return cause;
    }

    /**
     * Fired after a stage has been unlocked (via command or Research Pedestal).
     */
    public static class Unlocked extends StageEvent {
        public Unlocked(String stageId, String displayName) {
            super(stageId, displayName);
        }
    }

    /**
     * Fired after a stage has been locked (via command).
     */
    public static class Locked extends StageEvent {
        public Locked(String stageId, String displayName) {
            super(stageId, displayName);
        }
    }

    /**
     * Fired after an individual stage has been unlocked for a specific player.
     *
     * <p>A stage shared with a team fires this once per teammate who receives it, each time with
     * that teammate's UUID. A teammate who is offline gets theirs when they next log in.
     */
    public static class IndividualUnlocked extends StageEvent {
        private final UUID playerUUID;

        public IndividualUnlocked(String stageId, String displayName, UUID playerUUID) {
            super(stageId, displayName);
            this.playerUUID = playerUUID;
        }

        public UUID getPlayerUUID() {
            return playerUUID;
        }
    }

    /**
     * Fired after an individual stage has been locked for a specific player.
     *
     * <p>When the server shares losses with the team, this fires once per teammate as well, with
     * the same delay for anyone offline.
     */
    public static class IndividualLocked extends StageEvent {
        private final UUID playerUUID;

        public IndividualLocked(String stageId, String displayName, UUID playerUUID) {
            super(stageId, displayName);
            this.playerUUID = playerUUID;
        }

        public UUID getPlayerUUID() {
            return playerUUID;
        }
    }

    /**
     * Fired before a stage is unlocked the normal way, while it can still be refused. Cancel it to
     * keep the stage locked — for example while a player cannot pay what an addon charges for it.
     *
     * <p>Fired by every unlock that a logic block or a permanent loss could also stop: the research
     * pedestal, auto-triggers, FTB Quests rewards, scripts, team sharing and any addon calling
     * {@link StageStates#unlockGlobal} or {@link StageStates#unlockIndividual}. It comes after those
     * checks, so it only fires for an unlock that would otherwise happen.
     *
     * <p>Admin unlocks — commands, the editor, the creative scroll — skip it, the same way they skip
     * a logic block. A refusal is silent: tell the player yourself if they should know why.
     *
     * <p>A pedestal whose research is refused keeps the scroll and starts over.
     */
    @Cancelable
    public static class BeforeUnlock extends StageEvent {
        @Nullable
        private final UUID playerUUID;

        public BeforeUnlock(String stageId, String displayName, @Nullable UUID playerUUID) {
            super(stageId, displayName);
            this.playerUUID = playerUUID;
        }

        /** True for a global stage; {@link #getPlayerUUID()} is then null. */
        public boolean isGlobal() {
            return playerUUID == null;
        }

        /** The player an individual stage is for, or null for a global stage. */
        @Nullable
        public UUID getPlayerUUID() {
            return playerUUID;
        }
    }

    /**
     * Fired when a stage is marked lost for good — a lock trigger with permanent loss closed it,
     * and a normal unlock can no longer bring it back. Comes after the matching
     * {@link Locked} or {@link IndividualLocked}. Only an admin unlock clears the mark.
     */
    public static class LostForGood extends StageEvent {
        @Nullable
        private final UUID playerUUID;

        public LostForGood(String stageId, String displayName, @Nullable UUID playerUUID) {
            super(stageId, displayName);
            this.playerUUID = playerUUID;
        }

        /** True for a global stage; {@link #getPlayerUUID()} is then null. */
        public boolean isGlobal() {
            return playerUUID == null;
        }

        /** The player who lost an individual stage, or null for a global one. */
        @Nullable
        public UUID getPlayerUUID() {
            return playerUUID;
        }
    }
}
