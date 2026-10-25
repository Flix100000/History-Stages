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
        if (c instanceof All all) {
            for (Condition child : all.children()) {
                if (!evaluate(child, owner, global, individual)) return false;
            }
            return true;
        } else if (c instanceof Any any) {
            for (Condition child : any.children()) {
                if (evaluate(child, owner, global, individual)) return true;
            }
            return false;
        } else if (c instanceof Not not) {
            return !evaluate(not.child(), owner, global, individual);
        } else if (c instanceof Unlocked u) {
            StageScope scope = u.scope() != null ? u.scope() : owner;
            if (owner == StageScope.GLOBAL && scope == StageScope.INDIVIDUAL) return false;
            return (scope == StageScope.GLOBAL ? global : individual).isUnlocked(u.stageId());
        } else if (c instanceof Unknown ignored) {
            return false;
        }
        throw new IllegalStateException("Unexpected value: " + c);
    }

    static boolean containsUnknown(Condition c) {
        if (c instanceof All all) {
            return all.children().stream().anyMatch(Condition::containsUnknown);
        } else if (c instanceof Any any) {
            return any.children().stream().anyMatch(Condition::containsUnknown);
        } else if (c instanceof Not not) {
            return containsUnknown(not.child());
        } else if (c instanceof Unlocked ignored) {
            return false;
        } else if (c instanceof Unknown ignored) {
            return true;
        }
        throw new IllegalStateException("Unexpected value: " + c);
    }

    /** Visits every stage reference in the tree. */
    static void forEachTerm(Condition c, Consumer<Unlocked> visitor) {
        if (c instanceof All all) {
            all.children().forEach(child -> forEachTerm(child, visitor));
        } else if (c instanceof Any any) {
            any.children().forEach(child -> forEachTerm(child, visitor));
        } else if (c instanceof Not not) {
            forEachTerm(not.child(), visitor);
        } else if (c instanceof Unlocked u) {
            visitor.accept(u);
        }
    }
}
