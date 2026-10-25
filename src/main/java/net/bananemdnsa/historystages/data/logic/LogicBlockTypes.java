package net.bananemdnsa.historystages.data.logic;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The block types this version understands.
 *
 * <p>A type is listed only once it exists: a type in this list is a type the evaluator acts on,
 * and a file naming anything else must stay inert, not half-work.
 */
public final class LogicBlockTypes {

    public static final String BLOCKED_WHILE = "blocked_while";
    public static final String HIDDEN_WHILE = "hidden_while";
    public static final String COST_WHILE = "cost_while";
    public static final String REVOKE_WHEN = "revoke_when";

    public static final List<String> KNOWN = List.of(BLOCKED_WHILE, HIDDEN_WHILE, COST_WHILE, REVOKE_WHEN);

    private LogicBlockTypes() {}

    public static boolean isKnown(@Nullable String type) {
        return type != null && KNOWN.contains(type);
    }
}
