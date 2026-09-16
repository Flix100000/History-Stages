package net.bananemdnsa.historystages.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.EventJS;
import dev.latvian.mods.kubejs.event.Extra;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code HistoryStagesEvents} in scripts — the four stage changes as a real event group.
 *
 * <p>Before this, the only way to react was to name the event class as a string:
 * {@code ForgeEvents.onEvent('net.bananemdnsa.historystages.api.stage.StageEvent$Unlocked', …)}.
 * Nothing checked that string, so a typo or a package move failed silently. That form still
 * works; it is simply no longer the way to do it.
 *
 * <p>Each handler takes the stage id as an optional extra, which is what makes the first argument
 * optional in scripts: {@code HistoryStagesEvents.unlocked(event => …)} hears every stage,
 * {@code HistoryStagesEvents.unlocked('bronze', event => …)} hears one. Deliberately
 * {@code Extra.STRING} and not {@code Extra.REQUIRES_STRING} — a listener for every unlock is the
 * common case and should not have to name a stage it does not care about.
 */
public final class HistoryStagesKubeEvents {

    private HistoryStagesKubeEvents() {}

    public static final EventGroup GROUP = EventGroup.of("HistoryStagesEvents");

    public static final EventHandler UNLOCKED =
            GROUP.server("unlocked", () -> StageKubeEvent.class).extra(Extra.STRING);

    public static final EventHandler LOCKED =
            GROUP.server("locked", () -> StageKubeEvent.class).extra(Extra.STRING);

    public static final EventHandler INDIVIDUAL_UNLOCKED =
            GROUP.server("individualUnlocked", () -> IndividualStageKubeEvent.class)
                    .extra(Extra.STRING);

    public static final EventHandler INDIVIDUAL_LOCKED =
            GROUP.server("individualLocked", () -> IndividualStageKubeEvent.class)
                    .extra(Extra.STRING);

    /** A global stage was unlocked or relocked. */
    public static class StageKubeEvent extends EventJS {

        private final String stage;
        private final String displayName;

        public StageKubeEvent(String stage, String displayName) {
            this.stage = stage;
            this.displayName = displayName;
        }

        public String getStage() {
            return stage;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /** An individual stage changed for one player. */
    public static class IndividualStageKubeEvent extends StageKubeEvent {

        private final ServerPlayer player;

        public IndividualStageKubeEvent(String stage, String displayName, ServerPlayer player) {
            super(stage, displayName);
            this.player = player;
        }

        /**
         * May be null when the player is offline — an individual stage can be relocked by a
         * timer while nobody is looking. Scripts that tell the player something have to check.
         */
        public ServerPlayer getPlayer() {
            return player;
        }
    }
}
