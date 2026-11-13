package net.bananemdnsa.historystages.api.trigger;

/**
 * What a trigger type describes. Events happen once (a kill, a placed block, an advancement);
 * states can stop being true again (being in a biome, having an effect, rain).
 *
 * <p>Only states can keep a conditional stage open or be negated in a lock list. A player state
 * belongs to one player; a world state is the same for everyone, which is why global conditional
 * stages accept world states only.
 */
public enum TriggerKind {
    EVENT, PLAYER_STATE, WORLD_STATE;

    public boolean isState() { return this != EVENT; }
}
