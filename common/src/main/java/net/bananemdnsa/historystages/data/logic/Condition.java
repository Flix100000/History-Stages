package net.bananemdnsa.historystages.data.logic;

import com.google.gson.JsonElement;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.stage.StageStateView;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * The "while …" half of a logic block: a small tree over which stages are unlocked.
 *
 * <p>Deliberately free of Minecraft so the whole of stage logic can be tested without a game.
 * A node this version does not understand is kept as {@link Unknown}, never dropped, so a file
 * written by a newer version survives being opened and saved here.
 */
public sealed interface Condition {

    record All(List<Condition> children) implements Condition {}

    record Any(List<Condition> children) implements Condition {}

    record Not(Condition child) implements Condition {}

    /**
     * True when {@code stageId} is unlocked.
     *
     * @param scope which set to read. Null means the scope of the stage that owns the block, which
     *              is what a term written without one has always meant.
     */
    record Unlocked(String stageId, @Nullable StageScope scope) implements Condition {}

    /** Something from a newer version or a typo. Kept verbatim; a block containing it never applies. */
    record Unknown(JsonElement raw) implements Condition {}

    /**
     * Evaluates the tree for one viewer.
     *
     * <p>A global stage has no player to ask, so a term naming an individual stage is false there
     * rather than guessed at. {@link Unknown} evaluates false; {@link #containsUnknown} is what keeps
     * a {@code not} around one from turning it into "always true".
     */
    static boolean evaluate(Condition c, StageScope owner, StageStateView global, StageStateView individual) {
        return switch (c) {
            case All all -> {
                for (Condition child : all.children()) {
                    if (!evaluate(child, owner, global, individual)) yield false;
                }
                yield true;
            }
            case Any any -> {
                for (Condition child : any.children()) {
                    if (evaluate(child, owner, global, individual)) yield true;
                }
                yield false;
            }
            case Not not -> !evaluate(not.child(), owner, global, individual);
            case Unlocked u -> {
                StageScope scope = u.scope() != null ? u.scope() : owner;
                if (owner == StageScope.GLOBAL && scope == StageScope.INDIVIDUAL) yield false;
                yield (scope == StageScope.GLOBAL ? global : individual).isUnlocked(u.stageId());
            }
            case Unknown ignored -> false;
        };
    }

    static boolean containsUnknown(Condition c) {
        return switch (c) {
            case All all -> all.children().stream().anyMatch(Condition::containsUnknown);
            case Any any -> any.children().stream().anyMatch(Condition::containsUnknown);
            case Not not -> containsUnknown(not.child());
            case Unlocked ignored -> false;
            case Unknown ignored -> true;
        };
    }

    /** Visits every stage reference in the tree. */
    static void forEachTerm(Condition c, Consumer<Unlocked> visitor) {
        switch (c) {
            case All all -> all.children().forEach(child -> forEachTerm(child, visitor));
            case Any any -> any.children().forEach(child -> forEachTerm(child, visitor));
            case Not not -> forEachTerm(not.child(), visitor);
            case Unlocked u -> visitor.accept(u);
            case Unknown ignored -> { }
        }
    }
}
