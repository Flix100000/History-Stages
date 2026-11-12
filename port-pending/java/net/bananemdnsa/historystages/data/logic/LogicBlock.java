package net.bananemdnsa.historystages.data.logic;

import com.google.gson.JsonElement;
import org.jetbrains.annotations.Nullable;

/**
 * One rule on a stage: an effect ({@code type}) and the condition it applies under.
 *
 * <p>{@code raw} is the element as it was read. Writing starts from it, so fields a newer version
 * put on a block — or a block type this version does not know at all — come back out unchanged.
 *
 * @param type      the block type id, or null when the element was not even an object
 * @param condition the parsed condition; {@link Condition.Unknown} when missing or unreadable
 */
public record LogicBlock(@Nullable String type, Condition condition, @Nullable JsonElement raw) {

    /** A new block built in the editor, with nothing extra to carry. */
    public static LogicBlock of(String type, Condition condition) {
        return new LogicBlock(type, condition, null);
    }

    public LogicBlock withCondition(Condition newCondition) {
        return new LogicBlock(type, newCondition, raw);
    }

    public boolean isType(String id) {
        return id.equals(type);
    }

    /** Whether this version can evaluate the block. An unknown type or node makes it inert. */
    public boolean isUnderstood() {
        return LogicBlockTypes.isKnown(type) && !Condition.containsUnknown(condition);
    }
}
