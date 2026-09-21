package net.bananemdnsa.historystages.api.stage;

import net.bananemdnsa.historystages.platform.bus.Event;

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
     */
    public static class IndividualUnlocked extends StageEvent {
        private final java.util.UUID playerUUID;

        public IndividualUnlocked(String stageId, String displayName, java.util.UUID playerUUID) {
            super(stageId, displayName);
            this.playerUUID = playerUUID;
        }

        public java.util.UUID getPlayerUUID() {
            return playerUUID;
        }
    }

    /**
     * Fired after an individual stage has been locked for a specific player.
     */
    public static class IndividualLocked extends StageEvent {
        private final java.util.UUID playerUUID;

        public IndividualLocked(String stageId, String displayName, java.util.UUID playerUUID) {
            super(stageId, displayName);
            this.playerUUID = playerUUID;
        }

        public java.util.UUID getPlayerUUID() {
            return playerUUID;
        }
    }
}
