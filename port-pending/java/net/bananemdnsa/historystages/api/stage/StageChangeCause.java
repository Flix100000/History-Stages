package net.bananemdnsa.historystages.api.stage;

/**
 * Why a stage was unlocked, locked or lost. Carried by every {@link StageEvent} and by
 * {@link StageAnnounceEvent}.
 *
 * <p>Nested changes keep their own cause: a teammate who receives a stage through team sharing
 * sees {@link #TEAM} even though the original unlock was {@link #RESEARCH}.
 */
public enum StageChangeCause {
    /** A scroll finished at a research pedestal. */
    RESEARCH,
    /** The creative scroll, which unlocks everything. */
    CREATIVE,
    /** An auto-trigger fired. */
    AUTO_TRIGGER,
    /** A conditional stage followed its states. */
    CONDITIONAL,
    /** A lock trigger closed the stage again. */
    LOCK_TRIGGER,
    /** A temporary stage ran out. */
    TEMPORARY,
    /** The player died and the stage was set to be lost on death. */
    DEATH,
    /** A "revoke when" logic block took the stage away. */
    LOGIC,
    /** Passed on to or taken from a teammate by team sharing. */
    TEAM,
    /** An FTB Quests reward. */
    QUEST,
    /** A KubeJS or CraftTweaker script. */
    SCRIPT,
    /** A {@code /history} command. */
    COMMAND,
    /** The in-game editor. */
    EDITOR,
    /** Another mod, through the api. The default when nothing above applies. */
    ADDON
}
