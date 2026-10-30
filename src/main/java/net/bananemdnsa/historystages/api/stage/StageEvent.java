package net.bananemdnsa.historystages.api.stage;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import java.util.UUID;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.bananemdnsa.historystages.platform.bus.ICancellableEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when stages are unlocked or locked, for other mods to listen to.
 *
 * <p>There is no scripting language to reach these from on this loader, so a listener is Java:</p>
 * <pre>
 * EventBus.addListener(StageEvent.Unlocked.class,
 *         event -> LOGGER.info("Stage unlocked: {}", event.getStageId()));
 * </pre>
 *
 * <p>The place to register one is a {@code historystages} entrypoint, which runs once this mod is
 * far enough along to have something to announce.</p>
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
    public static class BeforeUnlock extends StageEvent implements ICancellableEvent {
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
