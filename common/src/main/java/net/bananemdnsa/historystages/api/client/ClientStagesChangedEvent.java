package net.bananemdnsa.historystages.api.client;

import net.bananemdnsa.historystages.platform.bus.Event;

/**
 * The stages the local player has changed: the server synced a new global set or a new
 * individual set. Posted on the game bus, client side, once at the end of the client tick in
 * which it arrived.
 *
 * <p>For a screen or overlay that shows what is locked and should redraw rather than wait to be
 * reopened. Read the new state through {@link ClientStages}. A resync that changed nothing may
 * still post it, so keep the listener cheap.
 */
public class ClientStagesChangedEvent extends Event {
}
