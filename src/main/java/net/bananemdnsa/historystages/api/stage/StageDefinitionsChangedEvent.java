package net.bananemdnsa.historystages.api.stage;

import net.bananemdnsa.historystages.platform.bus.Event;

/**
 * The loaded stage definitions changed: a {@code /reload}, a stage saved, moved or deleted in the
 * editor, or a new set synced from the server to the client.
 *
 * <p>Listen for it when an addon keeps anything derived from the stages, such as an index over
 * its own category's entries. Read the new state through {@link StageStates#loadedStages()} and
 * rebuild.
 *
 * <p>Posted on the game bus once, at the end of the tick in which the change happened, so the
 * definitions are complete by the time a listener sees them. Several changes in one tick arrive
 * as one event. On a dedicated server it comes from the server tick, on a client connected to a
 * remote server from the client tick. In single player the client and the integrated server share
 * one set of definitions, so it is posted once for both.
 *
 * <p>This is about definitions, not about who has what. Unlocks and relocks are
 * {@link StageEvent}.
 */
public class StageDefinitionsChangedEvent extends Event {
}
