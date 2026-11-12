package net.bananemdnsa.historystages.api.diagnostics;

/** What a runtime log line is about. Shown as a fixed-width column so a reader can skim for one kind. */
public enum Kind {
    /** A lock stopped or changed something a player did. */
    LOCK,
    /** A stage was gained, lost, or refused. */
    STAGE,
    /** A command or the editor changed something. */
    ADMIN,
    /** The mod itself: start, stop, scans, caches, failures. */
    SYSTEM
}
