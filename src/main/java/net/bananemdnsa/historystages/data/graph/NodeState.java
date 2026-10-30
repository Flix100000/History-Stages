package net.bananemdnsa.historystages.data.graph;

/**
 * A node's lock state as far as styling is concerned. A stage removed by the visibility filter is
 * not drawn at all, so there is no {@code HIDDEN} style to resolve.
 *
 * <p>{@code BLOCKED} is a reachable stage that its logic currently blocks: the prerequisites are
 * met, it just cannot be unlocked right now. It takes the place of {@code REACHABLE}, never of
 * {@code LOCKED}, so a blocked node appears exactly where a reachable one would.
 */
public enum NodeState {
    UNLOCKED,
    REACHABLE,
    LOCKED,
    BLOCKED
}
