package net.bananemdnsa.historystages.platform.bus;

/** Order in which handlers for the same event are called. Highest first, as on the other loader. */
public enum EventPriority {
    HIGHEST,
    HIGH,
    NORMAL,
    LOW,
    LOWEST
}
