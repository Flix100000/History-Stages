package net.bananemdnsa.historystages.platform.bus;

/**
 * Marks an event that is fired once while the mod is starting, rather than during play.
 *
 * <p>On NeoForge this picks the bus the event travels on. There is only one bus here, so it
 * carries no dispatch meaning — but it still says the thing that matters about these events:
 * they happen once, before anything is playing, and a registry filled by one of them is closed
 * afterwards. Everything that walks such a registry is written assuming exactly that.
 */
public interface IModBusEvent {
}
